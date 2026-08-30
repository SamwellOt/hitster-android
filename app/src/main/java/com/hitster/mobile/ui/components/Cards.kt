package com.hitster.mobile.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.hitster.mobile.ui.theme.DisplayFont
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hitster.mobile.net.Card
import com.hitster.mobile.ui.theme.NeonBrush
import com.hitster.mobile.ui.theme.NeonPink
import com.hitster.mobile.ui.theme.NeonYellow
import com.hitster.mobile.ui.theme.Outline
import com.hitster.mobile.ui.theme.Surface2
import com.hitster.mobile.ui.theme.Surface3
import com.hitster.mobile.ui.theme.TextTertiary
import com.hitster.mobile.ui.theme.decadeColor
import com.hitster.mobile.ui.theme.onDecadeColor
import com.hitster.mobile.ui.theme.parseHex

val CardWidth = 104.dp
val CardHeight = 140.dp

/** Solution side of a card: artist on top, big year, title at the bottom – like the printed card. */
@Composable
fun YearCard(card: Card, modifier: Modifier = Modifier, width: Dp = CardWidth, height: Dp = CardHeight, highlight: Boolean = false) {
    val bg = decadeColor(card.year)
    val fg = onDecadeColor(card.year)
    val scale by animateFloatAsState(if (highlight) 1.06f else 1f, tween(250), label = "cardScale")
    // type scales with the card: 104dp → 11/30/10, 120dp → 12/34/11, 78dp → 10/24/9
    val k = (width / CardWidth).coerceIn(0.75f, 1.3f)
    Column(
        modifier
            .scale(scale)
            .size(width, height)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .then(if (highlight) Modifier.border(2.dp, Color.White, RoundedCornerShape(12.dp)) else Modifier)
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .semantics { contentDescription = "${card.artist ?: ""}, ${card.year ?: ""}, ${card.title ?: ""}" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            card.artist ?: "CANTOR(A)",
            color = fg, fontSize = (11 * k).sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = (13 * k).sp,
        )
        Text(card.year?.toString() ?: "????", color = fg, fontFamily = DisplayFont, fontSize = (30 * k).sp, lineHeight = (32 * k).sp)
        Text(
            card.title ?: "MÚSICA",
            color = fg.copy(alpha = 0.92f), fontSize = (10 * k).sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
            maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = (12 * k).sp,
        )
    }
}

/** Face‑down card (the one being guessed). Shows "?" or a vector icon. */
@Composable
fun HiddenCard(modifier: Modifier = Modifier, width: Dp = CardWidth, height: Dp = CardHeight, label: String = "?", icon: ImageVector? = null) {
    Box(
        modifier
            .size(width, height)
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF1B1B26), Color(0xFF101017))))
            .border(2.dp, NeonBrush, RoundedCornerShape(12.dp))
            .semantics { contentDescription = "Carta escondida" },
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(width * 0.42f))
        else Text(label, style = MaterialTheme.typography.displayMedium, color = Color.White)
    }
}

/**
 * A timeline: cards in chronological order with tappable slots in between.
 *
 * @param selectable   whether slots can be tapped
 * @param selectedSlot slot currently chosen by the local user (drawn as a "?" card)
 * @param markers      slot -> colour for tokens placed by challengers (and the owner's choice)
 */
@Composable
fun Timeline(
    cards: List<Card>,
    modifier: Modifier = Modifier,
    selectable: Boolean = false,
    selectedSlot: Int? = null,
    disabledSlots: Set<Int> = emptySet(),
    markers: Map<Int, List<String>> = emptyMap(),
    highlightCardId: String? = null,
    onSlotSelected: (Int) -> Unit = {},
    cardWidth: Dp = CardWidth,
    cardHeight: Dp = CardHeight,
    listState: LazyListState = rememberLazyListState(),
    autoScrollTo: Int? = null,
) {
    val density = LocalDensity.current
    LaunchedEffect(autoScrollTo, cards.size) {
        val target = autoScrollTo ?: return@LaunchedEffect
        // centre the target (slot i = item 2i) in the viewport instead of pinning it to the left edge
        val viewport = listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset
        val itemPx = with(density) { (if (selectedSlot == target) cardWidth else 36.dp).roundToPx() }
        val offset = -((viewport - itemPx) / 2).coerceAtLeast(0)
        listState.animateScrollToItem((target * 2).coerceIn(0, cards.size * 2), offset)
    }
    LazyRow(
        modifier = modifier,
        state = listState,
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) {
        for (slot in 0..cards.size) {
            item(key = "slot$slot") {
                Slot(
                    selectable = selectable && slot !in disabledSlots,
                    selected = selectedSlot == slot,
                    markers = markers[slot].orEmpty(),
                    cardWidth = cardWidth,
                    height = cardHeight,
                    onClick = { onSlotSelected(slot) },
                )
            }
            if (slot < cards.size) {
                val c = cards[slot]
                // keyed by card id so a newly inserted card slides in and neighbours animate apart
                item(key = "card:" + c.id) {
                    YearCard(c, width = cardWidth, height = cardHeight, highlight = c.id == highlightCardId, modifier = Modifier.animateItem())
                }
            }
        }
    }
}

@Composable
private fun Slot(selectable: Boolean, selected: Boolean, markers: List<String>, cardWidth: Dp, height: Dp, onClick: () -> Unit) {
    val w by animateFloatAsState(if (selected) 1f else 0f, tween(220), label = "slot")
    // 36dp keeps the "+" tappable (≥44dp tall column, 8dp gap from the cards); grows to a card when selected
    val slotWidth = 36.dp + (cardWidth - 36.dp) * w
    val lineColor by animateColorAsState(if (selectable) NeonYellow.copy(alpha = 0.7f) else Outline, label = "line")
    Box(
        Modifier
            .width(slotWidth)
            .height(height)
            .padding(horizontal = 4.dp)
            // Only selectable slots react to taps: a disabled slot that is merely displayed as selected
            // (e.g. the owner's "?" while an opponent picks a challenge position) must not be pickable.
            .then(if (selectable) Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            HiddenCard(width = slotWidth - 8.dp, height = height)
        } else {
            // dotted connector + optional "+" affordance
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxSize()) {
                Box(Modifier.width(2.dp).height(height * 0.28f).background(lineColor))
                Box(
                    Modifier
                        .size(if (selectable) 22.dp else 8.dp)
                        .clip(CircleShape)
                        .background(if (selectable) Surface3 else lineColor)
                        .then(if (selectable) Modifier.border(BorderStroke(1.dp, NeonYellow), CircleShape) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selectable) Text("+", color = NeonYellow, fontWeight = FontWeight.Black, fontSize = 14.sp)
                }
                Box(Modifier.width(2.dp).height(height * 0.28f).background(lineColor))
            }
        }
        if (markers.isNotEmpty()) {
            Column(Modifier.align(Alignment.TopCenter).padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                markers.forEach { hex -> HitsterToken(color = parseHex(hex), size = 18.dp) }
            }
        }
    }
}

/** The round "H" token. */
@Composable
fun HitsterToken(modifier: Modifier = Modifier, color: Color = NeonPink, size: Dp = 22.dp, dim: Boolean = false) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(if (dim) Surface2 else Color(0xFF111116))
            .border(if (size > 20.dp) 2.dp else 1.5.dp, if (dim) TextTertiary else color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        // the glyph is illegible below ~14dp – small tokens read as plain dots
        if (size >= 14.dp) Text("H", color = if (dim) TextTertiary else color, fontWeight = FontWeight.Black, fontSize = (size.value * 0.5f).sp)
    }
}
