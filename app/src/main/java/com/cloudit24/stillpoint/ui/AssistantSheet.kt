package com.cloudit24.stillpoint.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloudit24.stillpoint.LauncherViewModel
import com.cloudit24.stillpoint.R
import com.cloudit24.stillpoint.data.AppTheme
import com.cloudit24.stillpoint.llm.Suggested
import java.io.File

/**
 * The assistant, as a sheet over home: long-press home (when Stillpoint is the phone's assistant),
 * or tap the terminal display's name. In Neon Alley it speaks in terminal type.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantSheet(vm: LauncherViewModel) {
    val context = LocalContext.current
    val s = vm.settings
    val a = vm.assistant
    val look = LocalLook.current
    val neon = look.theme != AppTheme.STILLPOINT
    val ink = if (neon) look.line else Ink
    val font = if (neon) FontFamily.Monospace else FontFamily.Default
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var text by remember { mutableStateOf("") }
    val list = rememberLazyListState()
    val now by rememberTicker(30_000)
    val facts = remember(now) { statusFacts(vm, s, now, context) }

    fun send() {
        val q = text.trim()
        if (q.isEmpty()) return
        text = ""
        a.ask(q, s, facts)
    }

    LaunchedEffect(a.turns.size, a.draft.length) { if (a.turns.isNotEmpty()) list.animateScrollToItem(a.turns.size) }

    ModalBottomSheet(onDismissRequest = { vm.assistOpen = false }, sheetState = sheet, containerColor = look.surface) {
        Column(Modifier.fillMaxWidth().imePadding().padding(horizontal = 18.dp).padding(bottom = 18.dp)) {
            LazyColumn(Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 420.dp), state = list) {
                if (a.turns.isEmpty() && a.draft.isEmpty()) item {
                    Text(stringResource(R.string.a_hello), color = Muted, fontSize = 14.sp, fontFamily = font, modifier = Modifier.padding(vertical = 10.dp))
                }
                items(a.turns) { t ->
                    val me = t.role == "user"
                    Text(if (me) stringResource(R.string.a_you) else "Stillpoint", color = Muted, fontSize = 12.sp, fontFamily = font,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
                    Text((if (neon && !me) "> " else "") + t.text, color = if (me) Muted else ink, fontSize = 15.sp, fontFamily = font, lineHeight = 22.sp)
                }
                if (a.busy) item {
                    Text("Stillpoint", color = Muted, fontSize = 12.sp, fontFamily = font, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
                    Text((if (neon) "> " else "") + a.draft.ifEmpty { a.status.ifEmpty { "…" } }, color = ink, fontSize = 15.sp, fontFamily = font, lineHeight = 22.sp)
                }
            }
            if (!a.busy && a.status.isNotEmpty()) Text(a.status, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            if (a.suggestions.isNotEmpty()) Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                a.suggestions.forEach { c -> Chip(c, neon) {
                    c.rule?.let { vm.saveRule(it) }
                    c.focus?.let { name -> s.focusProfiles.firstOrNull { it.name == name }?.let { vm.startFocus(it) } }
                    a.suggestions = a.suggestions - c
                } }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(text, { text = it }, modifier = Modifier.weight(1f), singleLine = true,
                    placeholder = { Text(stringResource(R.string.a_ask_hint), color = Muted) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { send() }),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = ink, unfocusedTextColor = ink, focusedBorderColor = look.line.copy(alpha = 0.5f),
                        unfocusedBorderColor = look.line.copy(alpha = 0.25f), cursorColor = look.line))
                Spacer(Modifier.width(6.dp))
                if (a.busy) TextButton(onClick = { a.cancel() }) { Text(stringResource(R.string.a_stop), color = Muted) }
                else TextButton(onClick = { send() }) { Text(stringResource(R.string.a_send), color = look.line) }
            }
            val model = File(s.assistantModel).name.ifEmpty { stringResource(R.string.a_no_model) }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.a_footer, model), color = Muted, fontSize = 11.sp, fontFamily = font)
                if (a.turns.isNotEmpty()) Text(stringResource(R.string.a_clear), color = Muted, fontSize = 11.sp, fontFamily = font,
                    modifier = Modifier.clickable { a.clear() })
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Chip(c: Suggested, neon: Boolean, onClick: () -> Unit) {
    val look = LocalLook.current
    val shape = RoundedCornerShape(14.dp)
    Text(c.label, color = if (neon) look.glow else Ink, fontSize = 12.sp,
        fontFamily = if (neon) FontFamily.Monospace else FontFamily.Default,
        modifier = Modifier.clip(shape).border(1.dp, look.line.copy(alpha = 0.35f), shape).background(look.line.copy(alpha = 0.06f))
            .clickable(onClick = onClick).padding(horizontal = 11.dp, vertical = 6.dp))
}
