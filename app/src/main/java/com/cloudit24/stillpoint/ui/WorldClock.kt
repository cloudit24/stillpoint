package com.cloudit24.stillpoint.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Cities for the world clock card: the Gulf, South Asia, and where family often lives. Saved as "Label|Zone". */
val WORLD_CITIES = listOf(
    "Dubai" to "Asia/Dubai", "Riyadh" to "Asia/Riyadh", "Doha" to "Asia/Qatar", "Kuwait" to "Asia/Kuwait",
    "Muscat" to "Asia/Muscat", "Bahrain" to "Asia/Bahrain", "India" to "Asia/Kolkata", "Pakistan" to "Asia/Karachi",
    "Bangladesh" to "Asia/Dhaka", "Nepal" to "Asia/Kathmandu", "Sri Lanka" to "Asia/Colombo", "Philippines" to "Asia/Manila",
    "Singapore" to "Asia/Singapore", "Kuala Lumpur" to "Asia/Kuala_Lumpur", "Jakarta" to "Asia/Jakarta",
    "Cairo" to "Africa/Cairo", "Nairobi" to "Africa/Nairobi", "Istanbul" to "Europe/Istanbul", "London" to "Europe/London",
    "Paris" to "Europe/Paris", "Berlin" to "Europe/Berlin", "New York" to "America/New_York", "Toronto" to "America/Toronto",
    "Chicago" to "America/Chicago", "Los Angeles" to "America/Los_Angeles", "Sydney" to "Australia/Sydney",
    "Tokyo" to "Asia/Tokyo",
)

@Composable
fun WorldCitiesDialog(current: List<String>, onDismiss: () -> Unit, onSave: (List<String>) -> Unit) {
    var picked by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onSave(picked) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("World clock") },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                Text("Up to three cities, shown in the order you pick them.", color = Muted, fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 8.dp))
                WORLD_CITIES.forEach { (label, zone) ->
                    val id = "$label|$zone"
                    val at = picked.indexOf(id)
                    Row(Modifier.fillMaxWidth().clickable {
                        picked = when {
                            at >= 0 -> picked - id
                            picked.size < 3 -> picked + id
                            else -> picked
                        }
                    }.padding(vertical = 10.dp)) {
                        Text(label, color = if (at >= 0) Accent else Ink, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        if (at >= 0) Text("${at + 1}", color = Accent, fontSize = 14.sp)
                    }
                }
            }
        },
    )
}
