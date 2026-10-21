package com.cloudit24.stillpoint.llm

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.cloudit24.stillpoint.data.Action
import com.cloudit24.stillpoint.data.LauncherSettings
import com.cloudit24.stillpoint.data.Rule
import com.cloudit24.stillpoint.data.Trigger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.Executors

/** The llama.cpp library built into the app. One model at a time. */
object LlmNative {
    init { System.loadLibrary("stillpoint_llm") }
    external fun load(path: String, threads: Int, nCtx: Int): Long
    external fun free(handle: Long)
    external fun prompt(handle: Long, roles: Array<String>, texts: Array<String>): String
    external fun generate(handle: Long, prompt: String, maxTokens: Int, sink: Sink): Int
    external fun sampling(handle: Long, temp: Float)

    /** Gets each piece of text as raw UTF-8 bytes; return false to stop. */
    interface Sink { fun onToken(bytes: ByteArray): Boolean }
}

/** Models we know how to fetch. Sizes are what the download will be. */
class ModelChoice(val id: String, val label: String, val size: String, val url: String, val file: String)

object Models {
    /** What this phone can carry, from its memory: the model to suggest, and why, in one line. */
    fun advice(context: Context): Pair<ModelChoice?, String> {
        val mem = android.app.ActivityManager.MemoryInfo().also { context.getSystemService(android.app.ActivityManager::class.java)?.getMemoryInfo(it) }
        val gb = mem.totalMem / 1e9
        val cores = Runtime.getRuntime().availableProcessors()
        val arm64 = android.os.Build.SUPPORTED_64_BIT_ABIS.any { it == "arm64-v8a" }
        val base = "This phone: %.0f GB memory, %d cores".format(gb, cores)
        return when {
            !arm64 -> null to "$base · not a 64-bit ARM phone; the assistant can't run here"
            gb >= 10 -> CHOICES[0] to "$base · Qwen 2.5 3B fits comfortably"
            gb >= 5.5 -> CHOICES[1] to "$base · Qwen 2.5 1.5B is the safe choice; 3B may stutter"
            else -> null to "$base · too little memory for these models"
        }
    }

    val CHOICES = listOf(
        ModelChoice("qwen3b", "Qwen 2.5 3B", "2.1 GB · the default for a strong phone",
            "https://huggingface.co/Qwen/Qwen2.5-3B-Instruct-GGUF/resolve/main/qwen2.5-3b-instruct-q4_k_m.gguf", "qwen2.5-3b-instruct-q4_k_m.gguf"),
        ModelChoice("hermes3b", "Hermes 3 · Llama 3.2 3B", "2.0 GB · Nous Research; more personality, good at following a soul",
            "https://huggingface.co/NousResearch/Hermes-3-Llama-3.2-3B-GGUF/resolve/main/Hermes-3-Llama-3.2-3B.Q4_K_M.gguf", "Hermes-3-Llama-3.2-3B.Q4_K_M.gguf"),
        ModelChoice("qwen1b", "Qwen 2.5 1.5B", "1.1 GB · faster, a little simpler",
            "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf", "qwen2.5-1.5b-instruct-q4_k_m.gguf"),
    )

    fun dir(context: Context): File = File(context.getExternalFilesDir(null), "models").also { it.mkdirs() }
    fun file(context: Context, c: ModelChoice): File = File(dir(context), c.file)
}

class Turn(val role: String, val text: String)

/** The [Suggested.open] value that means car mode rather than an app. */
const val CAR_MODE = "\u0000car"

/** Something the model suggested doing; shown as a chip under its answer. */
class Suggested(val label: String, val rule: Rule? = null, val focus: String? = null, val open: String? = null)

/**
 * The on-device assistant. Loads the model on the first question, keeps it a few minutes for follow-ups,
 * then lets it go so home stays light. Facts come from Stillpoint; the model only puts them in words.
 */
class Assistant(private val scope: CoroutineScope, private val prefs: com.cloudit24.stillpoint.data.Prefs) {
    var turns by mutableStateOf(prefs.chatHistory().map { (r, t) -> Turn(r, t) })
    /** Called with a fact worth keeping ("wife's name is Mariam"); the owner stores it in the settings. */
    var onMemory: ((String) -> Unit)? = null
    var draft by mutableStateOf("")       // the answer as it streams in
    var busy by mutableStateOf(false)
    var status by mutableStateOf("")      // "loading the model", "model file missing", ...
    var suggestions by mutableStateOf(listOf<Suggested>())
    var loaded by mutableStateOf(false)

    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "stillpoint-llm").apply { priority = Thread.NORM_PRIORITY } }.asCoroutineDispatcher()
    private var handle = 0L
    private var loadedPath = ""
    private var idle: Job? = null
    @Volatile private var stop = false

    fun ask(question: String, s: LauncherSettings, facts: List<String>, apps: List<String> = emptyList()) {
        if (busy || question.isBlank()) return
        // "car mode": no need to wake the model.
        if (Regex("^(?:open |start )?car mode\\.?$", RegexOption.IGNORE_CASE).matches(question.trim())) {
            turns = turns + Turn("user", question.trim()) + Turn("assistant", "Opening car mode.")
            suggestions = listOf(Suggested("Car mode", open = CAR_MODE))
            return
        }
        // "open whatsapp": no need to wake the model.
        Regex("^(?:open|launch|start)\\s+(.{2,40})$", RegexOption.IGNORE_CASE).matchEntire(question.trim())?.let { m ->
            val want = m.groupValues[1].trim().trimEnd('.', '!')
            val app = apps.firstOrNull { it.equals(want, true) } ?: apps.firstOrNull { it.contains(want, true) || want.contains(it, true) }
            if (app != null) {
                turns = turns + Turn("user", question.trim()) + Turn("assistant", "Opening $app.")
                suggestions = listOf(Suggested("Open $app", open = app))
                prefs.saveChatHistory(turns.takeLast(12).map { it.role to it.text })
                return
            }
        }
        val path = s.assistantModel
        problemWith(path)?.let { status = it; return }
        turns = turns + Turn("user", question.trim())
        draft = ""; suggestions = emptyList(); busy = true; stop = false
        scope.launch {
            // Anything that goes wrong (a bad file, the native library missing, out of memory) becomes a line, not a crash.
            val result = withContext(worker) { runCatching { run(path, s, facts, apps) }.getOrElse { e -> report(e); "" } }
            val (text, chips) = parse(result, s, apps)
            turns = turns + Turn("assistant", text)
            suggestions = chips
            prefs.saveChatHistory(turns.takeLast(12).map { it.role to it.text })
            draft = ""; busy = false
            idle?.cancel()
            idle = scope.launch { delay(s.assistantKeepMin * 60_000L); unload() }
        }
    }

    fun cancel() { stop = true }

    /** Why [path] can't be used right now, in a line; null when it looks fine. */
    fun problemWith(path: String): String? {
        if (path.isBlank() || !File(path).exists()) return "No model yet. Choose one in Settings, Assistant."
        val name = File(path).name
        val choice = Models.CHOICES.firstOrNull { it.file == name }
        if (choice != null && prefs.downloadId(choice.id) != 0L) return "That model is still downloading. Give it a moment."
        val magic = runCatching { File(path).inputStream().use { val b = ByteArray(4); it.read(b); String(b, Charsets.US_ASCII) } }.getOrDefault("")
        if (magic != "GGUF") return "That file isn't a GGUF model. Choose another in Settings, Assistant."
        return null
    }

    private suspend fun report(e: Throwable) {
        val why = when (e) {
            is UnsatisfiedLinkError, is ExceptionInInitializerError -> "the assistant's engine isn't in this build"
            is OutOfMemoryError -> "the phone ran out of memory for it"
            else -> e.message?.take(80) ?: e.javaClass.simpleName
        }
        withContext(Dispatchers.Main) { status = "The assistant hit a problem: $why." }
        if (handle != 0L) runCatching { LlmNative.free(handle) }; handle = 0L; loadedKey = ""
    }

    fun clear() { turns = emptyList(); suggestions = emptyList(); draft = ""; prefs.saveChatHistory(emptyList()) }

    fun unload() {
        scope.launch { withContext(worker) { if (handle != 0L) { LlmNative.free(handle); handle = 0L; loadedPath = "" } }; loaded = false }
    }

    /** Loads [path] if it isn't the loaded model. Returns the load time in ms, or -1 when it failed. */
    private var loadedKey = ""   // path + threads + context: a change means a reload

    private suspend fun ensure(path: String, s: LauncherSettings): Long {
        // Four threads by default: the phone's big cores. More pulls in the slow cores and gets slower, not faster.
        val threads = s.assistantThreads.takeIf { it > 0 } ?: Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
        val key = "$path|$threads|${s.assistantCtx}"
        if (handle != 0L && loadedKey == key) { LlmNative.sampling(handle, s.assistantTemp / 10f); return 0L }
        if (handle != 0L) { LlmNative.free(handle); handle = 0L }
        withContext(Dispatchers.Main) { status = "Loading the model…" }
        val t0 = System.currentTimeMillis()
        handle = runCatching { LlmNative.load(path, threads, s.assistantCtx) }.getOrDefault(0L)
        if (handle == 0L) { withContext(Dispatchers.Main) { status = "Couldn't load that model file." }; return -1L }
        loadedPath = path; loadedKey = key
        LlmNative.sampling(handle, s.assistantTemp / 10f)
        withContext(Dispatchers.Main) { status = ""; loaded = true }
        return System.currentTimeMillis() - t0
    }

    /** Results of "Test on this phone", by model path: "11 tok/s · loads in 3.1 s · smooth". */
    var bench by mutableStateOf(mapOf<String, String>())

    /** Times the model on this phone: load, then 48 tokens of a fixed question. */
    fun test(path: String, s: LauncherSettings) {
        if (busy || problemWith(path) != null) return
        busy = true; stop = false
        scope.launch {
            val result = withContext(worker) { runCatching {
                if (handle != 0L) { LlmNative.free(handle); handle = 0L }   // measure a cold load
                val loadMs = ensure(path, s)
                if (loadMs < 0) return@runCatching "couldn't load"
                val prompt = LlmNative.prompt(handle, arrayOf("user"), arrayOf("Count from one to thirty in words."))
                var tokens = 0
                val t0 = System.nanoTime()
                LlmNative.generate(handle, prompt, 48, object : LlmNative.Sink { override fun onToken(b: ByteArray): Boolean { tokens++; return !stop } })
                val secs = (System.nanoTime() - t0) / 1e9
                val tps = if (secs > 0) tokens / secs else 0.0
                val verdict = when { tps >= 8 -> "smooth"; tps >= 4 -> "usable"; else -> "too slow for this phone" }
                "%.0f tok/s · loads in %.1f s · %s".format(tps, loadMs / 1000.0, verdict)
            }.getOrElse { e -> report(e); "couldn't run" } }
            bench = bench + (path to result)
            busy = false
            idle?.cancel()
            idle = scope.launch { delay(2 * 60_000L); unload() }
        }
    }

    private suspend fun run(path: String, s: LauncherSettings, facts: List<String>, apps: List<String>): String {
        if (ensure(path, s) < 0) return ""
        val bytes = ByteArrayOutputStream()
        var shown = 0L
        val sink = object : LlmNative.Sink {
            override fun onToken(b: ByteArray): Boolean {
                bytes.write(b)
                // The streaming draft is refreshed a few times a second, not on every token.
                val now = System.currentTimeMillis()
                if (now - shown > 80) {
                    shown = now
                    val soFar = String(bytes.toByteArray(), Charsets.UTF_8)
                    scope.launch(Dispatchers.Main) { draft = soFar.substringBefore("\nRULE:").substringBefore("\nFOCUS:").substringBefore("\nMEMORY:").substringBefore("\nOPEN:") }
                }
                return !stop
            }
        }
        // The last turns go in; when they don't fit the context, fewer of them, down to the question alone.
        var code = 1
        for (take in listOf(8, 4, 2, 1)) {
            val roles = ArrayList<String>(); val texts = ArrayList<String>()
            roles += "system"; texts += system(s, facts, apps)
            turns.takeLast(take).forEach { roles += it.role; texts += it.text }
            val prompt = LlmNative.prompt(handle, roles.toTypedArray(), texts.toTypedArray())
            code = LlmNative.generate(handle, prompt, s.assistantMaxTokens, sink)
            if (code != 1) break
        }
        if (code == 1) withContext(Dispatchers.Main) { status = "That's too long for the model: raise Context in Advanced." }
        if (code == 2) withContext(Dispatchers.Main) { status = "The model stumbled. Try again." }
        return String(bytes.toByteArray(), Charsets.UTF_8).trim()
    }

    private fun system(s: LauncherSettings, facts: List<String>, apps: List<String>): String = buildString {
        val name = s.assistantName.ifBlank { "Stillpoint" }
        append("You are $name, ")
        append(s.assistantSoul.ifBlank { "a calm, warm assistant living inside the user's phone launcher. You are brief and plain, never gushing, and you care about the user's peace of mind" + (if (s.prayerOn) " and their prayers" else "") }.trim().trimEnd('.'))
        append(". Answer in the user's language, in one to three short sentences unless asked for more. No headings, no bullet lists, no emoji. ")
        if (s.assistantNotes.isNotBlank()) append(s.assistantNotes.trim()).append(' ')
        if (s.assistantMemory.isNotEmpty()) {
            append("\nWhat you remember about the user:\n")
            s.assistantMemory.takeLast(40).forEach { append("- ").append(it).append('\n') }
        }
        if (apps.isNotEmpty()) append("Apps on the phone: ").append(apps.take(60).joinToString(", ")).append('\n')
        append("When the user tells you something worth keeping (a name, a preference, a plan, a habit), finish with one extra line: MEMORY: <the fact, short>.\n")
        append("If the user asks to open an app, finish with: OPEN: <app name exactly as listed>.\n")
        append("Trust these facts over your own guesses; if something isn't in them, say you don't know rather than inventing it.\n")
        append("Facts now:\n")
        facts.forEach { append("- ").append(it).append('\n') }
        if (s.focusProfiles.isNotEmpty()) append("Focus profiles: ").append(s.focusProfiles.joinToString { it.name }).append('\n')
        append("If the user asks for a rule or automation, finish with one extra line exactly like\n")
        append("RULE: <trigger> -> <action> <argument>\n")
        append("triggers: ").append(Trigger.entries.joinToString { it.name }).append('\n')
        append("actions: ").append(Action.entries.joinToString { it.name }).append(" (argument: a focus name, a theme name, minutes, or the line to show)\n")
        append("If the user asks to start a focus, finish with: FOCUS: <focus name>\nOtherwise add no such line.")
    }

    /** Splits the answer from the RULE/FOCUS line and turns that into a chip. */
    private fun parse(raw: String, s: LauncherSettings, apps: List<String>): Pair<String, List<Suggested>> {
        val chips = ArrayList<Suggested>()
        val lines = raw.lines().filter { line ->
            val t = line.trim()
            when {
                t.startsWith("MEMORY:") -> { t.removePrefix("MEMORY:").trim().takeIf { it.length in 3..160 }?.let { onMemory?.invoke(it) }; false }
                t.startsWith("OPEN:") -> {
                    val want = t.removePrefix("OPEN:").trim().trimEnd('.')
                    (apps.firstOrNull { it.equals(want, true) } ?: apps.firstOrNull { it.contains(want, true) })?.let { chips += Suggested("Open $it", open = it) }
                    false
                }
                t.startsWith("RULE:") -> { ruleFrom(t.removePrefix("RULE:"), s)?.let { chips += Suggested("Add rule: ${it.sentence(s)}", rule = it) }; false }
                t.startsWith("FOCUS:") -> {
                    val name = t.removePrefix("FOCUS:").trim()
                    s.focusProfiles.firstOrNull { it.name.equals(name, true) }?.let { chips += Suggested("Start ${it.name} focus", focus = it.name) }
                    false
                }
                else -> true
            }
        }
        return lines.joinToString("\n").trim() to chips
    }

    private fun ruleFrom(spec: String, s: LauncherSettings): Rule? {
        val m = Regex("""\s*(\w+)\s*->\s*(\w+)\s*(.*)""").matchEntire(spec) ?: return null
        val trigger = Trigger.entries.firstOrNull { it.name.equals(m.groupValues[1], true) } ?: return null
        val action = Action.entries.firstOrNull { it.name.equals(m.groupValues[2], true) } ?: return null
        val argRaw = m.groupValues[3].trim().trim('"', '\'')
        val arg = when (action) {
            Action.START_FOCUS -> s.focusProfiles.firstOrNull { it.name.equals(argRaw, true) || argRaw.contains(it.name, true) }?.id?.toString()
                ?: s.focusProfiles.firstOrNull()?.id?.toString() ?: return null
            Action.THEME -> com.cloudit24.stillpoint.data.AppTheme.entries.firstOrNull { argRaw.contains(it.label, true) || argRaw.contains(it.name, true) }?.name
                ?: return null
            Action.SILENT -> (Regex("""\d+""").find(argRaw)?.value ?: "20")
            else -> argRaw
        }
        return Rule(System.currentTimeMillis(), trigger, "", action, arg)
    }
}
