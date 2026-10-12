package com.cloudit24.stillpoint.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Glass, the way iOS draws it: a translucent fill that is lighter at the top, a hairline that catches the
 * light along the top edge and fades down the sides, and a soft specular bloom in the top-left corner.
 * No real blur (that needs a library and a GPU pass); the layering reads as glass on a dark screen.
 */
fun Modifier.glass(shape: Shape = RoundedCornerShape(50), tint: Color = Color.White, strength: Float = 1f): Modifier = this
    .clip(shape)
    .background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.13f * strength), tint.copy(alpha = 0.05f * strength))))
    .border(1.dp, Brush.verticalGradient(listOf(tint.copy(alpha = 0.42f * strength), tint.copy(alpha = 0.06f * strength))), shape)
    .drawWithContent {
        drawContent()
        drawRect(
            Brush.radialGradient(listOf(tint.copy(alpha = 0.16f * strength), Color.Transparent),
                center = Offset(size.width * 0.22f, 0f), radius = size.width * 0.55f),
            size = Size(size.width, size.height),
        )
    }

/**
 * A glass segmented control: a dim track, and a brighter glass pill that slides under the chosen tab.
 * [trailing] goes after the tabs, inside the track (a "+" for a new shelf, say).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GlassTabs(
    labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier,
    onLongPress: ((Int) -> Unit)? = null, scrollable: Boolean = true, trailing: @Composable RowScope.() -> Unit = {},
) {
    val density = LocalDensity.current
    val pos = remember { mutableStateMapOf<Int, Pair<Dp, Dp>>() }   // tab -> (x, width) inside the track
    val cur = pos[selected.coerceIn(0, (labels.size - 1).coerceAtLeast(0))]
    val x by animateDpAsState(cur?.first ?: 0.dp, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow), label = "pillX")
    val w by animateDpAsState(cur?.second ?: 0.dp, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow), label = "pillW")
    val scroll = rememberScrollState()
    Box(modifier.glass(strength = 0.6f).then(if (scrollable) Modifier.horizontalScroll(scroll) else Modifier).padding(3.dp)) {
        if (cur != null) Box(Modifier.matchParentSize().offset(x = x).width(w).glass(strength = 1.4f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            labels.forEachIndexed { i, label ->
                val on = i == selected
                val color by animateColorAsState(if (on) Ink else Muted, label = "tabColor")
                Text(label, color = color, fontSize = 15.sp, fontWeight = if (on) FontWeight.Medium else FontWeight.Normal, maxLines = 1,
                    modifier = Modifier
                        .onGloballyPositioned { c -> with(density) { pos[i] = c.positionInParent().x.toDp() to c.size.width.toDp() } }
                        .clip(RoundedCornerShape(50))
                        .combinedClickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
                            onClick = { onSelect(i) }, onLongClick = onLongPress?.let { { it(i) } })
                        .padding(horizontal = 16.dp, vertical = 9.dp))
            }
            trailing()
        }
    }
}

/** A small round glass button: Edit, Tools, +. */
@Composable
fun GlassButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(label, color = Ink, fontSize = 14.sp, maxLines = 1,
        modifier = modifier.glass(strength = 0.8f).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp))
}
