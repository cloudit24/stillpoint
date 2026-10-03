package com.cloudit24.stillpoint.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloudit24.stillpoint.LauncherViewModel
import com.cloudit24.stillpoint.R

/** "Note deleted · Undo", for a few seconds after something is deleted on the Shelf. */
@Composable
fun UndoBar(vm: LauncherViewModel, modifier: Modifier = Modifier) {
    val u = vm.undo ?: return
    Row(modifier.clip(RoundedCornerShape(50)).background(Color(0xFF262624)).padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(u.label, color = Ink, fontSize = 14.sp)
        Text(stringResource(R.string.s_undo), color = Accent, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp).clip(RoundedCornerShape(50))
            .clickable { vm.runUndo() }.padding(horizontal = 12.dp, vertical = 10.dp))
    }
}
