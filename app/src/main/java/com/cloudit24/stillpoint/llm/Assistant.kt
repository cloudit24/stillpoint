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
        ModelChoice("qwen1b", "Qwen 2.5 1.5B", "1.1 GB · faster, a little simpler",
            "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf", "qwen2.5-1.5b-instruct-q4_k_m.gguf"),
    )

    fun dir(context: Context): File = File(context.getExternalFilesDir(null), "models").also { it.mkdirs() }
    fun file(context: Context, c: ModelChoice): File = File(dir(context), c.file)
}

class Turn(val role: String, val text: String)

/** Something the model suggested doing; shown as a chip under its answer. */
class Suggested(val label: String, val rule: Rule? = null, val focus: String? = null)

/**
 * The on-device assistant. Loads the model on the first question, keeps it a few minutes for follow-ups,
 * then lets it go so home stays light. Facts come from Stillpoint; the model only puts them in words.
 */
class Assistant(private val scope: CoroutineScope) {
    var turns by mutableStateOf(listOf<Turn>())
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

    fun ask(question: String, s: LauncherSettings, facts: List<String>) {
        if (busy || question.isBlank()) return
        val path = s.assistantModel
        if (path.isBlank() || !File(path).exists()) { status = "No model yet. Choose one in Settings, Assistant."; return }
        turns = turns + Turn("user", question.trim())
        draft = ""; suggestions = emptyList(); busy = true; stop = false
        scope.launch {
            val result = withContext(worker) { run(path, s, facts) }
            val (text, chips) = parse(result, s)
            turns = turns + Turn("assistant", text)
            suggestions = chips
            draft = ""; busy = false
            idle?.cancel()
            idle = scope.launch { delay(s.assistantKeepMin * 60_000L); unload() }
        }
    }

    fun cancel() { stop = true }

    fun clear() { turns = emptyList(); suggestions = emptyList(); draft = "" }

    fun unload() {
        scope.launch { withContext(worker) { if (handle != 0L) { LlmNative.free(handle); handle = 0L; loadedPath = "" } }; loaded = false }
    }

    /** Loads [path] if it isn't the loaded model. Returns the load time in ms, or -1 when it failed. */
    private suspend fun ensure(path: String): Long {
        if (handle != 0L && loadedPath == path) return 0L
        if (handle != 0L) { LlmNative.free(handle); handle = 0L }
        withContext(Dispatchers.Main) { status = "Loading the model…" }
        val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)
        val t0 = System.currentTimeMillis()
        handle = runCatching { LlmNative.load(path, threads, 4096) }.getOrDefault(0L)
        if (handle == 0L) { withContext(Dispatchers.Main) { status = "Couldn't load that model file." }; return -1L }
        loadedPath = path
        withContext(Dispatchers.Main) { status = ""; loaded = true }
        return System.currentTimeMillis() - t0
    }

    /** Results of "Test on this phone", by model path: "11 tok/s · loads in 3.1 s · smooth". */
    var bench by mutableStateOf(mapOf<String, String>())

    /** Times the model on this phone: load, then 48 tokens of a fixed question. */
    fun test(path: String) {
        if (busy || !File(path).exists()) return
        busy = true; stop = false
        scope.launch {
            val result = withContext(worker) {
                if (handle != 0L) { LlmNative.free(handle); handle = 0L }   // measure a cold load
                val loadMs = ensure(path)
                if (loadMs < 0) return@withContext "couldn't load"
                val prompt = LlmNative.prompt(handle, arrayOf("user"), arrayOf("Count from one to thirty in words."))
                var tokens = 0
                val t0 = System.nanoTime()
                LlmNative.generate(handle, prompt, 48, object : LlmNative.Sink { override fun onToken(b: ByteArray): Boolean { tokens++; return !stop } })
                val secs = (System.nanoTime() - t0) / 1e9
                val tps = if (secs > 0) tokens / secs else 0.0
                val verdict = when { tps >= 8 -> "smooth"; tps >= 4 -> "usable"; else -> "too slow for this phone" }
                "%.0f tok/s · loads in %.1f s · %s".format(tps, loadMs / 1000.0, verdict)
            }
            bench = bench + (path to result)
            busy = false
            idle?.cancel()
            idle = scope.launch { delay(2 * 60_000L); unload() }
        }
    }

    private suspend fun run(path: String, s: LauncherSettings, facts: List<String>): String {
        if (ensure(path) < 0) return ""
        val roles = ArrayList<String>(); val texts = ArrayList<String>()
        roles += "system"; texts += system(s, facts)
        turns.takeLast(8).forEach { roles += it.role; texts += it.text }
        val prompt = LlmNative.prompt(handle, roles.toTypedArray(), texts.toTypedArray())
        val bytes = ByteArrayOutputStream()
        val code = LlmNative.generate(handle, prompt, 400, object : LlmNative.Sink {
            override fun onToken(b: ByteArray): Boolean {
                bytes.write(b)
                val soFar = String(bytes.toByteArray(), Charsets.UTF_8)
                scope.launch(Dispatchers.Main) { draft = soFar.substringBefore("\nRULE:").substringBefore("\nFOCUS:") }
                return !stop
            }
        })
        if (code == 1) withContext(Dispatchers.Main) { status = "That's too long for the model; try a shorter question." }
        if (code == 2) withContext(Dispatchers.Main) { status = "The model stumbled. Try again." }
        return String(bytes.toByteArray(), Charsets.UTF_8).trim()
    }

    private fun system(s: LauncherSettings, facts: List<String>): String = buildString {
        append("You are Stillpoint, a calm assistant inside an Android launcher. Answer briefly, in plain words, in the user's language. ")
        append("Trust these facts over your own guesses; say so if something isn't in them.\n")
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
    private fun parse(raw: String, s: LauncherSettings): Pair<String, List<Suggested>> {
        val chips = ArrayList<Suggested>()
        val lines = raw.lines().filter { line ->
            val t = line.trim()
            when {
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
