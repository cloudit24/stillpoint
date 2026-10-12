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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Stillpoint's one surface language. Every pane, card, bar and chip is a sheet of tinted glass:
 * a translucent fill that is lighter at the top, a hairline that catches the light along the top edge,
 * a soft specular bloom in the top-left corner, and the user's accent colour in the tint, so the whole app
 * carries the chosen colour. No real blur (that needs a library and a GPU pass); on a dark screen the layering
 * reads as glass, and the [aurora] light behind a screen gives the glass something to refract.
 */

/** The default pane corner. Pills use [RoundedCornerShape] 50. */
val GlassShape = RoundedCornerShape(18.dp)

/** How strong a pane is: SOFT for chips and tracks, PANEL for cards, RAISED for the thing that is selected. */
enum class GlassLevel(val k: Float) { SOFT(0.7f), PANEL(1f), RAISED(1.7f) }

/** White leaning toward the accent: the colour the glass is made of. */
val glassTint: Color
    @Composable @ReadOnlyComposable get() = lerp(Color.White, LocalAccent.current, 0.38f)

@Composable
fun Modifier.glass(shape: Shape = GlassShape, level: GlassLevel = GlassLevel.PANEL, tint: Color = glassTint): Modifier {
    val k = level.k
    // The brushes are made once per tint and strength, not on every draw; the bloom sits behind the content.
    val fill = remember(tint, k) { Brush.verticalGradient(listOf(tint.copy(alpha = 0.17f * k), tint.copy(alpha = 0.06f * k))) }
    val edge = remember(tint, k) { Brush.verticalGradient(listOf(tint.copy(alpha = 0.60f * k), tint.copy(alpha = 0.10f * k), tint.copy(alpha = 0.26f * k))) }
    val bloom = remember(tint, k) { listOf(tint.copy(alpha = 0.20f * k), Color.Transparent) }
    return this
        .clip(shape)
        .drawBehind {
            drawRect(fill)
            drawRect(Brush.radialGradient(bloom, center = Offset(size.width * 0.18f, 0f), radius = (size.width * 0.5f).coerceAtLeast(1f)))
        }
        .border(1.dp, edge, shape)
}

/** A round glass pill; the common case. */
@Composable
fun Modifier.glassPill(level: GlassLevel = GlassLevel.SOFT, tint: Color = glassTint): Modifier = glass(RoundedCornerShape(50), level, tint)

/**
 * Ambient light behind a screen, in the accent and a neighbouring hue: two soft pools in opposite corners.
 * Most of the screen stays black; the glass panes pick the light up and show their colour.
 */
@Composable
fun Modifier.aurora(): Modifier {
    val a = LocalAccent.current
    val b = shiftHue(a, 42f)
    return drawBehind {
        drawRect(Brush.radialGradient(listOf(a.copy(alpha = 0.15f), Color.Transparent),
            center = Offset(size.width * 0.88f, -size.height * 0.04f), radius = size.width * 0.95f))
        drawRect(Brush.radialGradient(listOf(b.copy(alpha = 0.11f), Color.Transparent),
            center = Offset(size.width * 0.08f, size.height * 1.02f), radius = size.width * 0.85f))
    }
}

/**
 * A glass segmented control: a dim track, and a brighter accent-tinted pill that slides under the chosen tab.
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
    val pillTint = lerp(Color.White, LocalAccent.current, 0.55f)
    // When the tabs are wider than the track, the chosen one is brought into view and the far edge fades.
    LaunchedEffect(selected, cur, scroll.viewportSize) {
        val c = cur ?: return@LaunchedEffect
        val view = scroll.viewportSize
        if (view <= 0 || scroll.maxValue == 0) return@LaunchedEffect
        val left = with(density) { c.first.toPx() }
        val right = with(density) { (c.first + c.second).toPx() }
        val margin = with(density) { 20.dp.toPx() }
        val target = when {
            left - margin < scroll.value -> left - margin
            right + margin > scroll.value + view -> right + margin - view
            else -> return@LaunchedEffect
        }
        scroll.animateScrollTo(target.toInt().coerceIn(0, scroll.maxValue))
    }
    val fade = Modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
        drawContent()
        val edge = 26.dp.toPx()
        if (scroll.value < scroll.maxValue) drawRect(
            Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = size.width - edge, endX = size.width),
            blendMode = BlendMode.DstIn)
        if (scroll.value > 0) drawRect(
            Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), startX = 0f, endX = edge),
            blendMode = BlendMode.DstIn)
    }
    Box(modifier.glassPill(GlassLevel.SOFT).then(if (scrollable) fade.horizontalScroll(scroll) else Modifier).padding(3.dp)) {
        if (cur != null) Box(Modifier.matchParentSize().offset(x = x).width(w).glass(RoundedCornerShape(50), GlassLevel.RAISED, pillTint))
        Row(verticalAlignment = Alignment.CenterVertically) {
            labels.forEachIndexed { i, label ->
                val on = i == selected
                val color by animateColorAsState(if (on) Color.White else Muted, label = "tabColor")
                Text(label, color = color, fontSize = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1,
                    modifier = Modifier
                        .onGloballyPositioned { c -> with(density) { pos[i] = c.positionInParent().x.toDp() to c.size.width.toDp() } }
                        .clip(RoundedCornerShape(50))
                        .combinedClickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
                            onClick = { onSelect(i) }, onLongClick = onLongPress?.let { { it(i) } })
                        .padding(horizontal = 13.dp, vertical = 9.dp))
            }
            trailing()
        }
    }
}

/** A small round glass button: Edit, Tools, +. */
@Composable
fun GlassButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(label, color = Ink, fontSize = 14.sp, maxLines = 1,
        modifier = modifier.glassPill(GlassLevel.PANEL).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp))
}
