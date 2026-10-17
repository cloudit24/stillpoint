package com.cloudit24.stillpoint.ui

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.unit.sp
import com.cloudit24.stillpoint.LauncherViewModel
import com.cloudit24.stillpoint.R
import com.cloudit24.stillpoint.data.Prefs
import com.cloudit24.stillpoint.llm.ModelChoice
import com.cloudit24.stillpoint.llm.Models
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Settings → Productivity → Assistant: the model (download or your own GGUF), and how to open it. */
@Composable
fun AssistantSettings(vm: LauncherViewModel) {
    val ctx = LocalContext.current
    val s = vm.settings
    val tick by rememberTicker(2_000)
    val prefs = remember { Prefs(ctx) }
    var copying by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            copying = ctx.getString(R.string.a_copying)
            val out = withContext(Dispatchers.IO) { copyIn(ctx, uri) }
            copying = ""
            if (out != null) vm.updateSettings { it.copy(assistantModel = out.absolutePath, assistantOn = true) }
        }
    }

    Group(stringResource(R.string.a_assistant)) {
        ToggleRow(stringResource(R.string.a_on), s.assistantOn) { on -> vm.updateSettings { it.copy(assistantOn = on) } }
        Note(stringResource(R.string.a_on_note))
        if (s.assistantOn) {
            ActionRow(stringResource(R.string.a_open)) { vm.assistOpen = true }
            ActionRow(stringResource(R.string.a_default), stringResource(R.string.a_default_sum)) {
                runCatching { ctx.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) }
            }
            Stepper(stringResource(R.string.a_keep), "${s.assistantKeepMin} min",
                onMinus = { vm.updateSettings { it.copy(assistantKeepMin = (it.assistantKeepMin - 5).coerceAtLeast(1)) } },
                onPlus = { vm.updateSettings { it.copy(assistantKeepMin = (it.assistantKeepMin + 5).coerceAtMost(60)) } })
        }
    }

    if (s.assistantOn) Group(stringResource(R.string.a_soul)) {
        var name by remember(s.assistantName) { mutableStateOf(s.assistantName) }
        var soul by remember(s.assistantSoul) { mutableStateOf(s.assistantSoul) }
        androidx.compose.material3.OutlinedTextField(name, { name = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(stringResource(R.string.a_name)) })
        androidx.compose.material3.OutlinedTextField(soul, { soul = it }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), minLines = 3,
            label = { Text(stringResource(R.string.a_character)) }, placeholder = { Text(stringResource(R.string.a_character_hint)) })
        if (name != s.assistantName || soul != s.assistantSoul) ActionRow(stringResource(R.string.s_save)) {
            vm.updateSettings { it.copy(assistantName = name.ifBlank { "Stillpoint" }, assistantSoul = soul) }
        }
        Note(stringResource(R.string.a_soul_note))
    }

    if (s.assistantOn) Group(stringResource(R.string.a_memory)) {
        if (s.assistantMemory.isEmpty()) Note(stringResource(R.string.a_memory_empty))
        s.assistantMemory.asReversed().forEach { m ->
            ActionRow(m, stringResource(R.string.a_tap_to_forget)) { vm.updateSettings { it.copy(assistantMemory = it.assistantMemory - m) } }
        }
        if (s.assistantMemory.isNotEmpty()) ActionRow(stringResource(R.string.a_forget_all)) { vm.updateSettings { it.copy(assistantMemory = emptyList()) } }
        ActionRow(stringResource(R.string.a_clear_chat), stringResource(R.string.a_clear_chat_sum)) { vm.assistant.clear() }
        Note(stringResource(R.string.a_memory_note))
    }

    if (s.assistantOn) Group(stringResource(R.string.a_advanced)) {
        val cores = Runtime.getRuntime().availableProcessors()
        Stepper(stringResource(R.string.a_threads), if (s.assistantThreads == 0) stringResource(R.string.a_auto) else "${s.assistantThreads}",
            onMinus = { vm.updateSettings { it.copy(assistantThreads = (it.assistantThreads - 1).coerceAtLeast(0)) } },
            onPlus = { vm.updateSettings { it.copy(assistantThreads = (if (it.assistantThreads == 0) 2 else it.assistantThreads + 1).coerceAtMost(cores)) } })
        Stepper(stringResource(R.string.a_temp), "%.1f".format(s.assistantTemp / 10f),
            onMinus = { vm.updateSettings { it.copy(assistantTemp = (it.assistantTemp - 1).coerceAtLeast(0)) } },
            onPlus = { vm.updateSettings { it.copy(assistantTemp = (it.assistantTemp + 1).coerceAtMost(12)) } })
        Stepper(stringResource(R.string.a_ctx), "${s.assistantCtx}",
            onMinus = { vm.updateSettings { it.copy(assistantCtx = (it.assistantCtx / 2).coerceAtLeast(1024)) } },
            onPlus = { vm.updateSettings { it.copy(assistantCtx = (it.assistantCtx * 2).coerceAtMost(16384)) } })
        Stepper(stringResource(R.string.a_max), "${s.assistantMaxTokens}",
            onMinus = { vm.updateSettings { it.copy(assistantMaxTokens = (it.assistantMaxTokens - 100).coerceAtLeast(100)) } },
            onPlus = { vm.updateSettings { it.copy(assistantMaxTokens = (it.assistantMaxTokens + 100).coerceAtMost(1500)) } })
        var notes by remember(s.assistantNotes) { mutableStateOf(s.assistantNotes) }
        androidx.compose.material3.OutlinedTextField(notes, { notes = it }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp), minLines = 2,
            label = { Text(stringResource(R.string.a_notes)) }, placeholder = { Text(stringResource(R.string.a_notes_hint)) })
        if (notes != s.assistantNotes) ActionRow(stringResource(R.string.s_save)) { vm.updateSettings { it.copy(assistantNotes = notes) } }
        Note(stringResource(R.string.a_advanced_note))
    }

    if (!com.cloudit24.stillpoint.BuildConfig.LLM) { Note(stringResource(R.string.a_light_build)); return }
    Group(stringResource(R.string.a_model)) {
        val (suggest, why) = remember { Models.advice(ctx) }
        Note(why)
        Models.CHOICES.forEach { c ->
            val f = Models.file(ctx, c)
            val chosen = s.assistantModel == f.absolutePath
            val detail = when {
                f.exists() && chosen -> stringResource(R.string.a_in_use)
                f.exists() -> stringResource(R.string.a_downloaded)
                else -> downloadState(ctx, prefs, c, tick) ?: c.size
            }
            ActionRow((if (chosen) "● " else "") + c.label + (if (c == suggest) " · suggested" else ""), detail) {
                if (f.exists()) vm.updateSettings { it.copy(assistantModel = f.absolutePath, assistantOn = true) }
                else startDownload(ctx, prefs, c)
            }
            if (f.exists()) TestRow(vm, f.absolutePath)
        }
        ActionRow(stringResource(R.string.a_pick), copying.ifEmpty { stringResource(R.string.a_pick_sum) }) {
            if (copying.isEmpty()) pick.launch(arrayOf("*/*"))
        }
        val own = Models.dir(ctx).listFiles()?.filter { f -> Models.CHOICES.none { it.file == f.name } }.orEmpty()
        own.forEach { f ->
            val chosen = s.assistantModel == f.absolutePath
            ActionRow((if (chosen) "● " else "") + f.name, "%.1f GB".format(f.length() / 1e9)) {
                vm.updateSettings { it.copy(assistantModel = f.absolutePath, assistantOn = true) }
            }
            TestRow(vm, f.absolutePath)
        }
        val used = Models.dir(ctx).listFiles()?.sumOf { it.length() } ?: 0L
        if (used > 0) ActionRow(stringResource(R.string.a_delete_models), "%.1f GB".format(used / 1e9)) {
            vm.assistant.unload()
            Models.dir(ctx).listFiles()?.forEach { it.delete() }
            vm.updateSettings { it.copy(assistantModel = "") }
        }
        Note(stringResource(R.string.a_model_note))
    }
}

/** "Test on this phone": runs the model for a few seconds and shows the speed. */
@Composable
private fun TestRow(vm: LauncherViewModel, path: String) {
    val a = vm.assistant
    val r = a.bench[path]
    ActionRow("    " + stringResource(R.string.a_test), r ?: if (a.busy) stringResource(R.string.a_testing) else stringResource(R.string.a_test_sum)) {
        if (!a.busy) a.test(path, vm.settings)
    }
}

/** "downloading · 43%" while the system download runs, or null. */
private fun downloadState(ctx: Context, prefs: Prefs, c: ModelChoice, tick: Long): String? {
    val id = prefs.downloadId(c.id)
    if (id <= 0) return null
    val dm = ctx.getSystemService(DownloadManager::class.java) ?: return null
    dm.query(DownloadManager.Query().setFilterById(id)).use { cur ->
        if (!cur.moveToFirst()) { prefs.setDownloadId(c.id, 0); return null }
        val status = cur.getInt(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
        val done = cur.getLong(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
        val total = cur.getLong(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
        return when (status) {
            DownloadManager.STATUS_SUCCESSFUL -> { prefs.setDownloadId(c.id, 0); null }
            DownloadManager.STATUS_FAILED -> { prefs.setDownloadId(c.id, 0); ctx.getString(R.string.a_download_failed) }
            DownloadManager.STATUS_PAUSED -> ctx.getString(R.string.a_waiting_wifi)
            else -> ctx.getString(R.string.a_downloading, if (total > 0) (done * 100 / total).toInt() else 0)
        }
    }
}

private fun startDownload(ctx: Context, prefs: Prefs, c: ModelChoice) {
    val dm = ctx.getSystemService(DownloadManager::class.java) ?: return
    val req = DownloadManager.Request(Uri.parse(c.url))
        .setTitle("Stillpoint · ${c.label}")
        .setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI)
        .setDestinationInExternalFilesDir(ctx, null, "models/${c.file}")
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
    prefs.setDownloadId(c.id, runCatching { dm.enqueue(req) }.getOrDefault(0L))
}

/** Copies a GGUF you picked into the app's models folder (llama.cpp needs a plain file path). */
private fun copyIn(ctx: Context, uri: Uri): File? = runCatching {
    val name = ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
        val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (c.moveToFirst() && i >= 0) c.getString(i) else null
    } ?: "model.gguf"
    if (!name.endsWith(".gguf", true)) return null
    val out = File(Models.dir(ctx), name)
    ctx.contentResolver.openInputStream(uri)!!.use { i -> out.outputStream().use { o -> i.copyTo(o, 1 shl 20) } }
    out
}.getOrNull()
