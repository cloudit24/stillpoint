package com.cloudit24.stillpoint.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.cloudit24.stillpoint.widget.PrayerAlerts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

/**
 * What a rule waits for. A [sticky] one is a condition that holds for a while (Friday, night, low battery):
 * the rule applies when it begins and, for settings it changed, puts them back when it ends.
 */
enum class Trigger(val label: String, val sticky: Boolean = false, val picksFocus: Boolean = false) {
    PRAYER_TIME("At prayer time"),
    IQAMA("At iqama"),
    PRAYER_NEAR("Before a prayer"),
    FOCUS_START("When a focus starts", picksFocus = true),
    FOCUS_END("When a focus ends", picksFocus = true),
    SPECIAL("When my special person messages"),
    FRIDAY("On Friday", sticky = true),
    NIGHT("At night, 11 PM to 6 AM", sticky = true),
    BATTERY_LOW("Battery under 20%", sticky = true),
}

/** What a rule does. A [setting] one changes a setting and is undone when a sticky trigger ends. */
enum class Action(val label: String, val setting: Boolean = false) {
    START_FOCUS("Start a focus"),
    END_FOCUS("End the focus"),
    SILENT("Phone on silent"),
    THEME("Switch theme", setting = true),
    EDGE_OFF("Edge light off", setting = true),
    CALM("Calm the display", setting = true),
    LINE("Show a line in the terminal display"),
    TELL_TASKER("Tell Tasker"),
}

/** One sentence: when [trigger] (for [triggerArg]), do [action] (with [actionArg]). */
data class Rule(
    val id: Long, val trigger: Trigger, val triggerArg: String = "", val action: Action, val actionArg: String = "",
    val enabled: Boolean = true,
) {
    fun sentence(s: LauncherSettings): String {
        val profile = { id: String -> s.focusProfiles.firstOrNull { it.id.toString() == id }?.name }
        val w = when (trigger) {
            Trigger.FOCUS_START -> profile(triggerArg)?.let { "When $it focus starts" } ?: trigger.label
            Trigger.FOCUS_END -> profile(triggerArg)?.let { "When $it focus ends" } ?: trigger.label
            else -> trigger.label
        }
        val t = when (action) {
            Action.START_FOCUS -> "start ${profile(actionArg) ?: "a"} focus"
            Action.SILENT -> "phone on silent for ${actionArg.toIntOrNull() ?: 20} min"
            Action.THEME -> "theme to ${runCatching { AppTheme.valueOf(actionArg).label }.getOrDefault(AppTheme.STILLPOINT.label)}"
            Action.LINE -> "show \"${actionArg.ifBlank { "…" }}\""
            else -> action.label.replaceFirstChar { it.lowercase() }
        }
        return "$w, $t" + if (trigger.sticky && action.setting) " · back after" else ""
    }

    fun toJson(): JSONObject = JSONObject().put("id", id).put("t", trigger.name).put("ta", triggerArg)
        .put("a", action.name).put("aa", actionArg).put("on", enabled)

    companion object {
        fun fromJson(o: JSONObject): Rule? = runCatching {
            Rule(o.getLong("id"), Trigger.valueOf(o.getString("t")), o.optString("ta"), Action.valueOf(o.getString("a")),
                o.optString("aa"), o.optBoolean("on", true))
        }.getOrNull()
    }
}

/**
 * Runs the rules, and the Tasker bridge. Works without the ViewModel (prayer alarms and notifications arrive
 * in the background), so it reads and writes the settings itself and raises [changed] for the ViewModel to reload.
 */
object Automation {
    /** Stillpoint → Tasker: a broadcast with extras "event" and "name". */
    const val ACTION_EVENT = "com.cloudit24.stillpoint.EVENT"
    /** Tasker → Stillpoint: a broadcast with extras "command", "name" and "text". */
    const val ACTION_COMMAND = "com.cloudit24.stillpoint.COMMAND"
    private const val LINE_HOURS = 2L

    private val _changed = MutableStateFlow(0L)
    /** Bumps when a rule changed the settings behind the ViewModel's back. */
    val changed: StateFlow<Long> = _changed

    /** A moment: a prayer time, the iqama, a message from your special person. */
    fun fire(context: Context, event: Trigger, name: String = "") {
        val prefs = Prefs(context)
        val s = prefs.loadSettings()
        val who = if (event == Trigger.SPECIAL && name.isEmpty()) s.specialPerson.trim() else name
        if (s.taskerOut) tell(context, event.name.lowercase(), who)
        val rules = s.rules.filter { it.enabled && it.trigger == event && (it.triggerArg.isEmpty() || it.triggerArg == name) }
        if (rules.isEmpty()) return
        var cur = s
        rules.forEach { cur = apply(context, cur, it, who, null) }
        if (cur != s) { prefs.saveSettings(cur); _changed.value = System.currentTimeMillis() }
    }

    /**
     * Checks the conditions that hold for a while (Friday, night, low battery) and notices a focus starting or
     * ending, scheduled or by hand. Cheap; called every minute while Stillpoint is alive and whenever home opens.
     */
    fun tick(context: Context) {
        val prefs = Prefs(context)
        val s = prefs.loadSettings()
        if (s.rules.isEmpty() && !s.taskerOut) return
        val now = System.currentTimeMillis()
        val zt = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())
        val battery = batteryNow(context)
        val holds = mapOf(
            Trigger.FRIDAY to (zt.dayOfWeek == DayOfWeek.FRIDAY),
            Trigger.NIGHT to (zt.hour >= 23 || zt.hour < 6),
            Trigger.BATTERY_LOW to (battery.percent in 0..20 && !battery.charging && !battery.full),
        )
        val state = prefs.autoState()
        var cur = s
        for ((trigger, on) in holds) {
            if (state.optBoolean(trigger.name, false) == on) continue
            state.put(trigger.name, on)
            if (s.taskerOut) tell(context, trigger.name.lowercase() + if (on) "" else "_end", "")
            val rules = s.rules.filter { it.enabled && it.trigger == trigger }
            rules.forEach { cur = if (on) apply(context, cur, it, "", state) else revert(cur, it, state) }
        }
        if (cur != s) { prefs.saveSettings(cur); _changed.value = now }

        // A focus starting or ending, scheduled or by hand. Both go through fire() so Tasker hears them too.
        val focus = cur.focusAt(now)
        val wasId = state.optLong("focus", -1L)
        val nowId = focus?.id ?: -1L
        if (wasId != nowId) {
            state.put("focus", nowId)
            prefs.saveAutoState(state)
            if (wasId >= 0) fire(context, Trigger.FOCUS_END, cur.focusProfiles.firstOrNull { it.id == wasId }?.name ?: "Focus")
            if (focus != null) fire(context, Trigger.FOCUS_START, focus.name)
        } else prefs.saveAutoState(state)
    }

    /** Tasker → Stillpoint, when you've allowed it in Settings. */
    fun command(context: Context, intent: Intent) {
        val prefs = Prefs(context)
        val s = prefs.loadSettings()
        if (!s.taskerIn) return
        val name = intent.getStringExtra("name").orEmpty().trim()
        val text = intent.getStringExtra("text").orEmpty().trim()
        val rule = when (intent.getStringExtra("command")?.lowercase()) {
            "focus_start" -> s.focusProfiles.firstOrNull { it.name.equals(name, true) }
                ?.let { Rule(0, Trigger.IQAMA, "", Action.START_FOCUS, it.id.toString()) }
            "focus_end" -> Rule(0, Trigger.IQAMA, "", Action.END_FOCUS)
            "theme" -> AppTheme.entries.firstOrNull { it.label.equals(name, true) || it.name.equals(name, true) }
                ?.let { Rule(0, Trigger.IQAMA, "", Action.THEME, it.name) }
            "silent" -> Rule(0, Trigger.IQAMA, "", Action.SILENT, name.ifEmpty { "20" })
            "line" -> Rule(0, Trigger.IQAMA, "", Action.LINE, text.ifEmpty { name })
            "calm" -> Rule(0, Trigger.IQAMA, "", Action.CALM)
            else -> null
        } ?: return
        val cur = apply(context, s, rule, "", null)
        if (cur != s) { prefs.saveSettings(cur); _changed.value = System.currentTimeMillis() }
    }

    /** For Settings → Automation → Copy event names. */
    fun cheatSheet(): String = """
        Stillpoint → Tasker
        Event: Intent Received, action $ACTION_EVENT
        %event: prayer_time, iqama, prayer_near, focus_start, focus_end, special, friday, night, battery_low
                (friday_end, night_end, battery_low_end when they end)
        %name: the prayer, the focus profile, or the person

        Tasker → Stillpoint (turn on "Accept commands from Tasker")
        Action: Send Intent, target Broadcast Receiver, package com.cloudit24.stillpoint, action $ACTION_COMMAND
        extras, one per line:
          command:focus_start  name:Work
          command:focus_end
          command:theme  name:Neon Alley
          command:silent  name:20
          command:line  text:Laundry done
          command:calm
    """.trimIndent()

    private fun tell(context: Context, event: String, name: String) {
        runCatching {
            context.sendBroadcast(Intent(ACTION_EVENT).putExtra("event", event).putExtra("name", name)
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES))
        }
    }

    /** Does what the rule says; [state] gets a snapshot of changed settings so a sticky rule can put them back. */
    private fun apply(context: Context, s: LauncherSettings, r: Rule, name: String, state: JSONObject?): LauncherSettings {
        val now = System.currentTimeMillis()
        if (r.action.setting && state != null) {
            val snap = JSONObject().put("theme", s.theme.name).put("edge", s.edgeStyle.name)
                .put("drone", s.themeDrone).put("motion", s.edgeMotion)
            state.put("restore_${r.id}", snap)
        }
        return when (r.action) {
            Action.START_FOCUS -> {
                val p = s.focusProfiles.firstOrNull { it.id.toString() == r.actionArg } ?: return s
                if (s.focusAt(now)?.id == p.id) s
                else s.copy(focusProfileId = p.id, focusEndsAt = now + p.minutes * 60_000L, focusSkipUntil = 0L)
            }
            Action.END_FOCUS -> if (s.focusEndsAt > now) s.copy(focusEndsAt = 0L) else s.copy(focusSkipUntil = s.focusUntil(now))
            Action.SILENT -> { PrayerAlerts.silence(context, r.actionArg.toIntOrNull() ?: 20); s }
            Action.THEME -> s.copy(theme = runCatching { AppTheme.valueOf(r.actionArg) }.getOrDefault(AppTheme.STILLPOINT))
            Action.EDGE_OFF -> s.copy(edgeStyle = EdgeStyle.OFF)
            Action.CALM -> s.copy(themeDrone = false, edgeMotion = false)
            Action.LINE -> { Prefs(context).setAutoLine(r.actionArg.replace("{name}", name), now + LINE_HOURS * 3_600_000L); s }
            Action.TELL_TASKER -> { tell(context, r.trigger.name.lowercase(), name); s }
        }
    }

    /** A sticky trigger ended: put back what this rule changed, if the user hasn't changed it since. */
    private fun revert(s: LauncherSettings, r: Rule, state: JSONObject): LauncherSettings {
        val snap = state.optJSONObject("restore_${r.id}") ?: return s
        state.remove("restore_${r.id}")
        return when (r.action) {
            Action.THEME -> s.copy(theme = runCatching { AppTheme.valueOf(snap.getString("theme")) }.getOrDefault(s.theme))
            Action.EDGE_OFF -> s.copy(edgeStyle = runCatching { EdgeStyle.valueOf(snap.getString("edge")) }.getOrDefault(s.edgeStyle))
            Action.CALM -> s.copy(themeDrone = snap.optBoolean("drone", true),
                edgeMotion = snap.optBoolean("motion", true))
            else -> s
        }
    }
}

/** Tasker's "Send Intent" lands here. */
class AutomationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Automation.ACTION_COMMAND) runCatching { Automation.command(context, intent) }
    }
}
