package com.hitster.mobile.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.hitster.mobile.ui.theme.DisplayFont
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hitster.mobile.ui.theme.Ink
import com.hitster.mobile.ui.theme.NeonBrush
import com.hitster.mobile.ui.theme.NeonPink
import com.hitster.mobile.ui.theme.NeonYellow
import com.hitster.mobile.ui.theme.Outline
import com.hitster.mobile.ui.theme.Surface1
import com.hitster.mobile.ui.theme.Surface2
import com.hitster.mobile.ui.theme.TextPrimary
import com.hitster.mobile.ui.theme.TextSecondary
import com.hitster.mobile.ui.theme.TextTertiary
import kotlinx.coroutines.delay
import kotlin.math.sin

/** The HITSTER wordmark with the pink→yellow neon gradient. */
@Composable
fun NeonTitle(text: String = "HITSTER", modifier: Modifier = Modifier, size: Int = 44) {
    Text(
        text,
        modifier = modifier,
        style = TextStyle(
            brush = NeonBrush,
            fontFamily = DisplayFont,
            fontSize = size.sp,
            letterSpacing = (size * 0.06f).sp,
            shadow = Shadow(NeonPink.copy(alpha = 0.55f), Offset(0f, 0f), blurRadius = size * 0.6f),
        ),
    )
}

/** Scale‑on‑press feedback (0.97) shared by the buttons – transform only, no layout shift. */
@Composable
private fun Modifier.pressScale(interaction: MutableInteractionSource, enabled: Boolean): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) 0.97f else 1f, spring(stiffness = Spring.StiffnessMedium), label = "press")
    return this.scale(scale)
}

@Composable
fun NeonButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    brush: Brush = NeonBrush,
    height: Dp = 56.dp,
    textColor: Color = Color.White,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = height)
            .pressScale(interaction, enabled)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(16.dp))
            .background(brush)
            .clickable(enabled = enabled, interactionSource = interaction, indication = LocalIndication.current, onClick = onClick)
            .semantics { role = Role.Button }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        // the gradient ends in yellow (and the vote buttons in cyan): white text needs a shadow to stay ≥ 3:1 there
        Text(
            text, color = textColor, textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp, letterSpacing = 1.sp, shadow = Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, 1f), blurRadius = 3f)),
        )
    }
}

@Composable
fun GhostButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = TextPrimary,
    height: Dp = 48.dp,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .heightIn(min = maxOf(height, 44.dp))
            .pressScale(interaction, enabled)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(14.dp))
            .background(Surface2)
            .border(1.dp, Outline, RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, interactionSource = interaction, indication = LocalIndication.current, onClick = onClick)
            .semantics { role = Role.Button }
            .padding(horizontal = 18.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = color, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = TextTertiary) {
    Text(text.uppercase(), modifier = modifier, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
fun Pill(text: String, modifier: Modifier = Modifier, color: Color = TextSecondary, bg: Color = Surface2) {
    Box(modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(text, color = color, style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false)
    }
}

@Composable
fun Avatar(name: String, color: Color, size: Dp = 36.dp, ring: Boolean = false, dim: Boolean = false) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (dim) Surface2 else color.copy(alpha = 0.22f))
            .border(if (ring) 2.dp else 1.dp, if (ring) Color.White else color.copy(alpha = if (dim) 0.3f else 0.8f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(name.take(1).uppercase(), color = if (dim) TextTertiary else color, fontWeight = FontWeight.Black, fontSize = (size.value * 0.45f).sp)
    }
}

/** Row of 5 token positions: filled ones in colour, the rest dimmed. */
@Composable
fun TokenRow(tokens: Int, color: Color = NeonPink, size: Dp = 18.dp, max: Int = 5) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(max) { i -> HitsterToken(color = color, size = size, dim = i >= tokens) }
    }
}

/** Compact player summary used in the players strip. Tap to open the timeline. */
@Composable
fun PlayerChip(
    name: String,
    color: Color,
    tokens: Int,
    cards: Int,
    isCurrent: Boolean,
    isMe: Boolean,
    connected: Boolean,
    goal: Int,
    onClick: () -> Unit,
) {
    // only the active player's chip pulses – 10 always‑on infinite transitions were pure battery drain
    val borderColor = if (isCurrent) {
        val glow by rememberInfiniteTransition(label = "glow").animateFloat(0.55f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "g")
        color.copy(alpha = glow)
    } else Outline
    Column(
        Modifier
            .width(96.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (isCurrent) Surface2 else Surface1)
            .border(1.5.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Avatar(name, color, size = 32.dp, ring = isCurrent, dim = !connected)
        Text(
            if (isMe) "Você" else name, color = if (connected) TextPrimary else TextTertiary,
            style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        )
        Text("$cards/$goal", color = NeonYellow, style = MaterialTheme.typography.labelSmall)
        TokenRow(tokens, color = color, size = 12.dp)
    }
}

/** Countdown to a server deadline (epoch ms + clock offset). */
@Composable
fun Countdown(deadline: Long?, clockOffset: Long, modifier: Modifier = Modifier, color: Color = NeonYellow, label: String? = null) {
    fun remaining() = if (deadline == null) 0L else ((deadline - (System.currentTimeMillis() + clockOffset)) / 1000L).coerceAtLeast(0)
    var left by remember(deadline) { mutableLongStateOf(remaining()) }   // start at the real value, not a "0" frame
    LaunchedEffect(deadline, clockOffset) {   // a late clock sync must restart the countdown, not be ignored
        while (deadline != null) {
            left = remaining()
            delay(200)
        }
    }
    if (deadline == null) return
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(Surface2).border(2.dp, color, CircleShape), contentAlignment = Alignment.Center) {
            Text(left.toString(), color = color, fontWeight = FontWeight.Black, fontSize = 15.sp)
        }
        if (label != null) Text(label, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
    }
}

/** Animated equalizer bars – used while the preview plays. */
@Composable
fun Waveform(active: Boolean, modifier: Modifier = Modifier, bars: Int = 24, color: Brush = NeonBrush, height: Dp = 44.dp) {
    // no animation clock while paused – the bars are flat anyway
    val t = if (active) rememberInfiniteTransition(label = "wave").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "t",
    ).value else 0f
    Row(modifier.height(height), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(bars) { i ->
            val phase = (t * 2 * Math.PI + i * 0.7).toFloat()
            val h = if (active) 0.25f + 0.75f * (0.5f + 0.5f * sin(phase.toDouble()).toFloat() * (0.4f + 0.6f * ((i * 7) % 5) / 4f)) else 0.18f
            Box(
                Modifier
                    .width(4.dp)
                    .height(height * h)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (active) color else Brush.linearGradient(listOf(Outline, Outline))),
            )
        }
    }
}

@Composable
fun ProgressBar(progress: Float, modifier: Modifier = Modifier, brush: Brush = NeonBrush) {
    Box(modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Surface2)) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(6.dp).background(brush))
    }
}

@Composable
fun Banner(text: String, modifier: Modifier = Modifier, color: Color = NeonYellow) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(text, color = color, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun VSpace(h: Dp) = Spacer(Modifier.height(h))

@Composable
fun ScreenBackground(content: @Composable () -> Unit) {
    Box(
        Modifier.background(
            Brush.verticalGradient(0f to Ink, 0.35f to Ink, 1f to Color(0xFF130B1A)),
        ),
    ) { content() }
}
