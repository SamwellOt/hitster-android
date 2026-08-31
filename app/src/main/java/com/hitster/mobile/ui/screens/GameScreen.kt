package com.hitster.mobile.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hitster.mobile.audio.PlaybackState
import com.hitster.mobile.net.Card
import com.hitster.mobile.net.GamePlayer
import com.hitster.mobile.net.GameView
import com.hitster.mobile.net.Phase
import com.hitster.mobile.net.Room
import com.hitster.mobile.ui.components.Avatar
import com.hitster.mobile.ui.components.Banner
import com.hitster.mobile.ui.components.CardHeight
import com.hitster.mobile.ui.components.CardWidth
import com.hitster.mobile.ui.components.Countdown
import com.hitster.mobile.ui.components.GhostButton
import com.hitster.mobile.ui.components.HiddenCard
import com.hitster.mobile.ui.components.HitsterToken
import com.hitster.mobile.ui.components.NeonButton
import com.hitster.mobile.ui.components.NeonTitle
import com.hitster.mobile.ui.components.Pill
import com.hitster.mobile.ui.components.PlayerChip
import com.hitster.mobile.ui.components.ProgressBar
import com.hitster.mobile.ui.components.SectionLabel
import com.hitster.mobile.ui.components.Timeline
import com.hitster.mobile.ui.components.TokenRow
import com.hitster.mobile.ui.components.VSpace
import com.hitster.mobile.ui.components.Waveform
import com.hitster.mobile.ui.components.YearCard
import com.hitster.mobile.ui.theme.Danger
import com.hitster.mobile.ui.theme.Ink
import com.hitster.mobile.ui.theme.NeonBrush
import com.hitster.mobile.ui.theme.NeonCyan
import com.hitster.mobile.ui.theme.NeonGreen
import com.hitster.mobile.ui.theme.NeonPink
import com.hitster.mobile.ui.theme.NeonYellow
import com.hitster.mobile.ui.theme.Outline
import com.hitster.mobile.ui.theme.PurpleBrush
import com.hitster.mobile.ui.theme.Surface1
import com.hitster.mobile.ui.theme.Surface2
import com.hitster.mobile.ui.theme.TextPrimary
import com.hitster.mobile.ui.theme.TextSecondary
import com.hitster.mobile.ui.theme.TextTertiary
import com.hitster.mobile.ui.theme.parseHex

/** Everything the game screen needs, gathered by MainActivity from the ViewModel. */
data class GameUi(
    val room: Room,
    val game: GameView,
    val myId: String,
    val isHost: Boolean,
    val clockOffset: Long,
    val playback: PlaybackState,
    val selectedSlot: Int?,
    val viewingTimelineOf: String?,
)

class GameActions(
    val selectSlot: (Int) -> Unit,
    val confirmPlacement: () -> Unit,
    val skip: () -> Unit,
    val buyCard: () -> Unit,
    val play: () -> Unit,
    val pause: () -> Unit,
    val replay: () -> Unit,
    val pass: () -> Unit,
    val startChallenge: () -> Unit,
    val vote: (Boolean) -> Unit,
    val continueGame: () -> Unit,
    val openTimeline: (String?) -> Unit,
    val restart: () -> Unit,
    val leave: () -> Unit,
)

/** Per‑screen layout facts derived once from the window size. */
private class Layout(val compact: Boolean) {
    val cardW: Dp get() = if (compact) 78.dp else CardWidth
    val cardH: Dp get() = if (compact) 104.dp else CardHeight
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(ui: GameUi, a: GameActions) {
    val g = ui.game
    val t = g.turn
    val me = g.player(ui.myId)
    val current = g.currentPlayer
    val myTurn = t?.playerId == ui.myId
    val goal = g.options.cardsToWin

    BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        val landscape = maxWidth > maxHeight
        val lay = Layout(compact = landscape)
        if (!landscape) {
            // ---------------------------------------------------------- portrait: stacked
            Column(Modifier.fillMaxSize()) {
                Header(g, me, a, lay)
                PlayersStrip(ui, a, goal)
                Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    CenterPanel(ui, a, me, current, myTurn, lay)
                }
                if (me != null && t != null) MyTimeline(ui, a, me, t.phase, myTurn, lay)
            }
        } else {
            // ---------------------------------------------------------- landscape: compact header, panel + players, full‑width timeline
            Column(Modifier.fillMaxSize()) {
                Header(g, me, a, lay)
                Row(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f).fillMaxHeight()) { CenterPanel(ui, a, me, current, myTurn, lay) }
                    PlayersColumn(ui, a, goal, modifier = Modifier.width(150.dp).fillMaxHeight())
                }
                if (me != null && t != null) MyTimeline(ui, a, me, t.phase, myTurn, lay)
            }
        }
    }

    // ---------------------------------------------------------------- timeline sheet
    val viewing = ui.viewingTimelineOf?.let { g.player(it) }
    if (viewing != null) {
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        // sheetMaxWidth unspecified: in landscape Material would cap it at 640dp and centre it – the timeline wants the whole width.
        ModalBottomSheet(onDismissRequest = { a.openTimeline(null) }, sheetState = sheet, containerColor = Surface1, sheetMaxWidth = Dp.Unspecified) {
            Column(Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
                Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Avatar(viewing.name, parseHex(viewing.color), size = 40.dp)
                    Column(Modifier.weight(1f)) {
                        Text(if (viewing.id == ui.myId) "Sua linha do tempo" else "Linha do tempo de ${viewing.name}", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                        Text("${viewing.timeline.size} de $goal cartas", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                    TokenRow(viewing.tokens, color = parseHex(viewing.color))
                }
                VSpace(16.dp)
                Timeline(cards = viewing.timeline, modifier = Modifier.fillMaxWidth())
                VSpace(12.dp)
                Text(
                    viewing.timeline.joinToString("  ·  ") { it.year.toString() },
                    color = TextTertiary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }
    }

    // ---------------------------------------------------------------- winner overlay
    if (g.finished) WinnerOverlay(ui, a)
}

// ======================================================================= header & HUD

/**
 * Always visible: wordmark, round/deck, MY TOKENS (the HUD), leave. In landscape it also carries the card
 * count and the 3‑token trade so the timeline strip below needs no label row.
 */
@Composable
private fun Header(g: GameView, me: GamePlayer?, a: GameActions, lay: Layout) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = if (lay.compact) 2.dp else 6.dp, bottom = if (lay.compact) 2.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        NeonTitle(size = if (lay.compact) 18 else 20)
        if (lay.compact) Pill("R${g.round} · ${g.deckCount} cartas", color = TextSecondary)
        Spacer(Modifier.weight(1f))
        if (me != null) {
            if (lay.compact) Pill("${me.timeline.size}/${g.options.cardsToWin}", color = NeonYellow)
            TokensHud(me.tokens, parseHex(me.color))
            if (lay.compact) BuyButton(tokens = me.tokens, enabled = canBuy(g, me), compact = true, onClick = a.buyCard)
        }
        IconButton(onClick = a.leave, modifier = Modifier.size(44.dp)) {
            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sair da sessão", tint = TextSecondary)
        }
    }
}

/** My HITSTER tokens, always on screen: "H ×2" (semantics read "2 fichas HITSTER"). */
@Composable
private fun TokensHud(tokens: Int, color: Color) {
    Row(
        Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(50))
            .background(Surface2)
            .border(1.dp, if (tokens > 0) color.copy(alpha = 0.8f) else Outline, RoundedCornerShape(50))
            .padding(horizontal = 10.dp)
            .semantics { contentDescription = "$tokens fichas HITSTER" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        HitsterToken(color = color, size = 20.dp, dim = tokens == 0)
        Text(if (tokens == 1) "1 ficha" else "$tokens fichas", color = if (tokens > 0) TextPrimary else TextTertiary, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
private fun PlayersStrip(ui: GameUi, a: GameActions, goal: Int) {
    val g = ui.game
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(g.order.mapNotNull { g.player(it) }, key = { it.id }) { p ->
            val info = ui.room.players.firstOrNull { it.id == p.id }
            PlayerChip(
                name = p.name, color = parseHex(p.color), tokens = p.tokens, cards = p.timeline.size,
                isCurrent = p.id == g.turn?.playerId, isMe = p.id == ui.myId, connected = info?.connected ?: true, goal = goal,
                onClick = { a.openTimeline(p.id) },
            )
        }
    }
}

/** Landscape: players stacked on the right; the whole row is tappable to open a timeline. */
@Composable
private fun PlayersColumn(ui: GameUi, a: GameActions, goal: Int, modifier: Modifier = Modifier) {
    val g = ui.game
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
        items(g.order.mapNotNull { g.player(it) }, key = { it.id }) { p ->
            val cur = p.id == g.turn?.playerId
            val color = parseHex(p.color)
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (cur) Surface2 else Surface1)
                    .border(1.dp, if (cur) color else Outline, RoundedCornerShape(12.dp))
                    .clickable(onClickLabel = "Ver linha do tempo de ${p.name}") { a.openTimeline(p.id) }
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Avatar(p.name, color, size = 24.dp, ring = cur)
                Column(Modifier.weight(1f)) {
                    Text(if (p.id == ui.myId) "Você" else p.name, color = TextPrimary, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${p.timeline.size}/$goal", color = NeonYellow, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        TokenRow(p.tokens, color = color, size = 9.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun CenterPanel(ui: GameUi, a: GameActions, me: GamePlayer?, current: GamePlayer?, myTurn: Boolean, lay: Layout) {
    val t = ui.game.turn ?: return
    if (current == null) return
    AnimatedContent(
        targetState = "${t.phase}:${myTurn}",
        transitionSpec = { (fadeIn(tween(220)) + slideInVertically { it / 12 }) togetherWith fadeOut(tween(150)) },
        label = "phase",
    ) { key ->
        val phase = key.substringBefore(':')
        when {
            phase == Phase.LISTEN && myTurn -> ListenPanel(ui, a, me, lay)
            phase == Phase.LISTEN -> WaitingPanel(current, lay)
            phase == Phase.CHALLENGE && myTurn -> OwnerChallengePanel(ui, lay)
            phase == Phase.CHALLENGE -> OpponentChallengePanel(ui, a, current, me, lay)
            phase == Phase.VOTE -> VotePanel(ui, a, current, myTurn, lay)
            phase == Phase.RESULT -> ResultPanel(ui, a, current, lay)
            else -> Box {}
        }
    }
}

// ======================================================================= panels

/** Card‑like container. Scrolls only as a safety net – the compact variants are sized to fit without it. */
@Composable
private fun Panel(lay: Layout, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(if (lay.compact) 18.dp else 22.dp))
            .background(Surface1)
            .border(1.dp, Outline, RoundedCornerShape(if (lay.compact) 18.dp else 22.dp))
            .padding(horizontal = if (lay.compact) 12.dp else 16.dp, vertical = if (lay.compact) 8.dp else 16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

@Composable
private fun ListenPanel(ui: GameUi, a: GameActions, me: GamePlayer?, lay: Layout) {
    val pb = ui.playback
    val tokens = me?.tokens ?: 0
    val confirmLabel = ui.selectedSlot?.let { "CONFIRMAR NA ${it + 1}ª POSIÇÃO" } ?: "TOQUE EM  +  NA LINHA DO TEMPO"
    // The clip ran to the end: the main button becomes "listen again" instead of a play that looks stuck.
    val finished = pb.ended && !pb.isPlaying
    val controls: @Composable () -> Unit = {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIcon(Icons.Default.Replay, "Recomeçar", size = if (lay.compact) 40.dp else 48.dp, enabled = pb.url != null) { a.replay() }
            RoundIcon(
                when { pb.isPlaying -> Icons.Default.Pause; finished -> Icons.Default.Replay; else -> Icons.Default.PlayArrow },
                when { pb.isPlaying -> "Pausar"; finished -> "Ouvir de novo"; else -> "Tocar" },
                size = if (lay.compact) 56.dp else 72.dp, brush = NeonBrush,
            ) { if (pb.isPlaying) a.pause() else if (finished) a.replay() else a.play() }
            RoundIcon(Icons.Default.SkipNext, "Pular · 1 ficha", size = if (lay.compact) 40.dp else 48.dp, enabled = tokens >= 1) { a.skip() }
        }
    }
    val status = when {
        pb.isBuffering -> "carregando…"
        pb.error != null -> "erro ao tocar"
        finished -> "fim da prévia"
        else -> "prévia · 30 s"
    }

    Panel(lay) {
        if (!lay.compact) {
            SectionLabel("Sua vez", color = NeonYellow)
            VSpace(6.dp)
            Text("Ouça e posicione a carta", style = MaterialTheme.typography.headlineSmall, color = TextPrimary, textAlign = TextAlign.Center)
            VSpace(14.dp)
            Waveform(active = pb.isPlaying, height = 48.dp)
            VSpace(10.dp)
            ProgressBar(pb.progress)
            VSpace(4.dp)
            Row(Modifier.fillMaxWidth()) {
                Text(fmt(pb.positionMs), color = TextTertiary, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.weight(1f))
                Text(status, color = TextSecondary, style = MaterialTheme.typography.labelSmall)
            }
            VSpace(12.dp)
            controls()
            VSpace(6.dp)
            Text("Pular custa 1 ficha HITSTER", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            VSpace(14.dp)
            Text(
                "Diga o nome da música e o artista em voz alta: os outros confirmam depois da revelação e você ganha 1 ficha.",
                color = TextTertiary, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
            )
            VSpace(10.dp)
            Banner(
                if (ui.selectedSlot == null) "Toque em um  +  da sua linha do tempo para escolher a posição"
                else "Posição escolhida. Confirme abaixo quando estiver pronto(a).",
                color = if (ui.selectedSlot == null) NeonCyan else NeonGreen,
            )
        } else {
            // landscape: two rows – [controls | waveform+progress] then [claim toggle] then [confirm]
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                controls()
                Column(Modifier.weight(1f)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel("Sua vez · ouça e posicione", color = NeonYellow)
                        Spacer(Modifier.weight(1f))
                        Text("${fmt(pb.positionMs)} · $status", color = TextSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                    VSpace(4.dp)
                    Waveform(active = pb.isPlaying, height = 28.dp, bars = 18)
                    VSpace(4.dp)
                    ProgressBar(pb.progress)
                }
            }
            VSpace(6.dp)
            Text("Diga o nome e o artista em voz alta — os outros confirmam na revelação (+1 ficha).", color = TextTertiary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            VSpace(6.dp)
            NeonButton(confirmLabel, enabled = ui.selectedSlot != null, height = 44.dp, onClick = a.confirmPlacement)
        }
    }
}

@Composable
private fun WaitingPanel(current: GamePlayer, lay: Layout) {
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(0.92f, 1.06f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "p")
    val hint = "Quando ${current.name} posicionar a carta, você pode gritar HITSTER."
    Panel(lay) {
        if (!lay.compact) {
            SectionLabel("Vez de ${current.name}", color = parseHex(current.color))
            VSpace(18.dp)
            Box(Modifier.scale(pulse)) { HiddenCard(icon = Icons.Default.MusicNote) }
            VSpace(18.dp)
            Text("${current.name} está ouvindo a música…", style = MaterialTheme.typography.titleLarge, color = TextPrimary, textAlign = TextAlign.Center)
            VSpace(8.dp)
            Text(hint, color = TextTertiary, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            VSpace(6.dp)
            Text("A música toca apenas no celular de quem está jogando.", color = TextTertiary, style = MaterialTheme.typography.labelSmall)
        } else {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.scale(pulse)) { HiddenCard(width = 66.dp, height = 88.dp, icon = Icons.Default.MusicNote) }
                Column(Modifier.weight(1f)) {
                    SectionLabel("Vez de ${current.name}", color = parseHex(current.color))
                    VSpace(4.dp)
                    Text("${current.name} está ouvindo a música…", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    VSpace(4.dp)
                    Text(hint, color = TextTertiary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun OwnerChallengePanel(ui: GameUi, lay: Layout) {
    val t = ui.game.turn!!
    Panel(lay) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Countdown(t.deadline, ui.clockOffset)
            Column {
                SectionLabel("Carta posicionada", color = NeonGreen)
                Text("Aguardando desafios…", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            }
        }
        VSpace(if (lay.compact) 6.dp else 12.dp)
        if (t.challenges.isEmpty()) {
            Text("Ninguém desafiou ainda.", color = TextTertiary, style = MaterialTheme.typography.bodySmall)
        } else {
            t.challenges.forEach { c ->
                val p = ui.game.player(c.playerId)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                    HitsterToken(color = parseHex(p?.color), size = 18.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("${p?.name} gritou HITSTER — aposta que você errou", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        VSpace(if (lay.compact) 4.dp else 10.dp)
        Text("A carta será revelada quando o tempo acabar ou quando todos decidirem.", color = TextTertiary, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
    }
}

@Composable
private fun OpponentChallengePanel(ui: GameUi, a: GameActions, current: GamePlayer, me: GamePlayer?, lay: Layout) {
    val t = ui.game.turn!!
    val tokens = me?.tokens ?: 0
    val alreadyChallenged = t.challenges.any { it.playerId == ui.myId }
    val passed = ui.myId in t.passed
    val glow by rememberInfiniteTransition(label = "hglow").animateFloat(0.85f, 1.05f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "hg")
    Panel(lay) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Countdown(t.deadline, ui.clockOffset)
            Column(Modifier.weight(1f)) {
                SectionLabel("${current.name} posicionou a carta", color = parseHex(current.color))
                Text(if (lay.compact) "Aposte 1 ficha que a posição está errada" else "Segundos para gritar HITSTER", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
        VSpace(if (lay.compact) 4.dp else 10.dp)
        if (!lay.compact) { SectionLabel("Linha do tempo de ${current.name}"); VSpace(6.dp) }
        Timeline(
            cards = current.timeline, selectedSlot = t.slot,
            markers = challengeMarkers(ui), cardWidth = if (lay.compact) 66.dp else 78.dp, cardHeight = if (lay.compact) 80.dp else 104.dp,
            autoScrollTo = t.slot, modifier = Modifier.fillMaxWidth(),
        )
        VSpace(if (lay.compact) 6.dp else 14.dp)
        when {
            alreadyChallenged -> Banner("Você gritou HITSTER! Se ${current.name} errar, a carta é sua.", color = NeonPink)
            passed -> Banner("Você passou. Aguardando a revelação…", color = TextSecondary)
            lay.compact -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1.6f).scale(glow)) { NeonButton("GRITAR HITSTER!", enabled = tokens >= 1, height = 48.dp, onClick = a.startChallenge) }
                GhostButton("Não desafiar", modifier = Modifier.weight(1f), color = TextSecondary, onClick = a.pass)
            }
            else -> {
                Box(Modifier.scale(glow).fillMaxWidth()) {
                    NeonButton("GRITAR HITSTER!", enabled = tokens >= 1, height = 60.dp, onClick = a.startChallenge)
                }
                VSpace(6.dp)
                Text(
                    if (tokens >= 1) "Aposta de 1 ficha que ${current.name} errou: se errar, a carta é sua; se acertar, a ficha é perdida." else "Você precisa de 1 ficha HITSTER para desafiar.",
                    color = TextTertiary, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                )
                VSpace(10.dp)
                GhostButton("Não desafiar", color = TextSecondary, modifier = Modifier.fillMaxWidth(), onClick = a.pass)
            }
        }
    }
}

/** Challenger tokens are drawn on the slot the owner chose – that is what they are betting against. */
private fun challengeMarkers(ui: GameUi): Map<Int, List<String>> {
    val t = ui.game.turn ?: return emptyMap()
    val slot = t.slot ?: return emptyMap()
    if (t.challenges.isEmpty()) return emptyMap()
    return mapOf(slot to t.challenges.map { ui.game.player(it.playerId)?.color ?: "#FFFFFF" })
}

@Composable
private fun VotePanel(ui: GameUi, a: GameActions, current: GamePlayer, myTurn: Boolean, lay: Layout) {
    val t = ui.game.turn!!
    val card = t.card ?: return
    val voted = ui.myId in t.votes
    val question: @Composable () -> Unit = {
        when {
            myTurn -> Text("Os outros estão confirmando se você acertou o nome e o artista…", color = TextSecondary, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            voted -> Banner("Voto registrado. Aguardando os demais…", color = TextSecondary)
            else -> {
                Text("${current.name} disse o nome da música e o artista corretamente?", style = MaterialTheme.typography.titleSmall, color = TextPrimary, textAlign = TextAlign.Center)
                VSpace(8.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NeonButton("ACERTOU", modifier = Modifier.weight(1f), height = if (lay.compact) 44.dp else 56.dp, brush = Brush.linearGradient(listOf(NeonGreen, NeonCyan))) { a.vote(true) }
                    NeonButton("ERROU", modifier = Modifier.weight(1f), height = if (lay.compact) 44.dp else 56.dp, brush = Brush.linearGradient(listOf(Danger, NeonPink))) { a.vote(false) }
                }
            }
        }
    }
    Panel(lay) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Countdown(t.deadline, ui.clockOffset)
            SectionLabel("Revelação · votação", color = NeonYellow)
        }
        VSpace(if (lay.compact) 4.dp else 10.dp)
        if (lay.compact) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                RevealCard(card, t.result?.correct, small = true)
                Column(Modifier.weight(1f)) { question() }
            }
        } else {
            RevealCard(card, t.result?.correct, small = false)
            VSpace(12.dp)
            question()
        }
    }
}

@Composable
private fun ResultPanel(ui: GameUi, a: GameActions, current: GamePlayer, lay: Layout) {
    val t = ui.game.turn!!
    val card = t.card ?: return
    val r = t.result
    val stolenBy = r?.stolenBy?.let { ui.game.player(it) }
    val who = if (current.id == ui.myId) "Você" else current.name
    val (headline, color) = when {
        r == null -> "" to TextSecondary
        r.correct -> "$who acertou! A carta fica na linha do tempo." to NeonGreen
        stolenBy != null -> "$who errou — ${if (stolenBy.id == ui.myId) "você" else stolenBy.name} gritou HITSTER e roubou a carta!" to NeonPink
        else -> "$who errou. A carta vai para o descarte." to Danger
    }
    val details: @Composable () -> Unit = {
        Text(headline, color = color, style = MaterialTheme.typography.titleSmall, textAlign = if (lay.compact) TextAlign.Start else TextAlign.Center)
        r?.challenges?.forEach { c ->
            val p = ui.game.player(c.playerId)
            val name = if (c.playerId == ui.myId) "Você" else p?.name
            Text(
                if (c.correct == true) (if (c.playerId == r.stolenBy) "$name gritou HITSTER e levou a carta (1 ficha gasta)." else "$name também gritou HITSTER, mas ${ui.game.player(r.stolenBy)?.name ?: "outro jogador"} foi mais rápido (1 ficha gasta).")
                else "$name gritou HITSTER e ${current.name} acertou: perdeu 1 ficha.",
                color = TextSecondary, style = MaterialTheme.typography.bodySmall, textAlign = if (lay.compact) TextAlign.Start else TextAlign.Center,
            )
        }
        if (r?.tokenEarned == true) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HitsterToken(color = NeonYellow, size = 18.dp); Spacer(Modifier.width(6.dp))
                Text("$who ganhou 1 ficha por dizer o nome e o artista!", color = NeonYellow, style = MaterialTheme.typography.bodySmall)
            }
        } else if (r?.tokenEarned == false) {
            Text("Os outros não confirmaram o nome/artista: sem ficha extra.", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
    Panel(lay) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!ui.game.finished) Countdown(t.deadline, ui.clockOffset)
            SectionLabel("Resultado", color = NeonYellow)
            Spacer(Modifier.weight(1f))
            if (lay.compact && !ui.game.finished) NeonButton("PRÓXIMA RODADA", modifier = Modifier.widthIn(max = 200.dp), height = 40.dp, onClick = a.continueGame)
        }
        VSpace(if (lay.compact) 4.dp else 10.dp)
        if (lay.compact) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                RevealCard(card, r?.correct, small = true)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { details() }
            }
        } else {
            RevealCard(card, r?.correct, small = false)
            VSpace(12.dp)
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) { details() }
            VSpace(14.dp)
            if (!ui.game.finished) NeonButton("PRÓXIMA RODADA", onClick = a.continueGame)
        }
    }
}

/** The card flips from its back ("?") to the solution side the first time it is shown. */
@Composable
private fun RevealCard(card: Card, correct: Boolean?, small: Boolean) {
    var flipped by remember(card.id) { mutableStateOf(false) }
    LaunchedEffect(card.id) { flipped = true }
    val rotation by animateFloatAsState(if (flipped) 180f else 0f, tween(650, easing = FastOutSlowInEasing), label = "flip")
    val density = LocalDensity.current.density
    val w = if (small) 84.dp else 120.dp
    val h = if (small) 112.dp else 160.dp
    Row(if (small) Modifier else Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.graphicsLayer { rotationY = rotation; cameraDistance = 14f * density }) {
            if (rotation <= 90f) {
                HiddenCard(width = w, height = h)
            } else {
                Box(Modifier.graphicsLayer { rotationY = 180f }) {
                    YearCard(card, width = w, height = h)
                    if (correct != null) {
                        Box(
                            Modifier.align(Alignment.TopEnd).padding(4.dp).size(24.dp).clip(CircleShape).background(if (correct) NeonGreen else Danger),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (correct) Icons.Default.Check else Icons.Default.Close,
                                contentDescription = if (correct) "Posição certa" else "Posição errada",
                                tint = Color.White, modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }
        if (!small) AnimatedVisibility(visible = rotation > 90f, enter = fadeIn(tween(250)) + slideInVertically { it / 6 }, modifier = Modifier.weight(1f)) {
            Column(Modifier.fillMaxWidth()) {
                Text(card.artist ?: "", color = TextSecondary, style = MaterialTheme.typography.labelMedium)
                Text(card.title ?: "", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                VSpace(4.dp)
                Text(card.year?.toString() ?: "", color = NeonYellow, style = MaterialTheme.typography.displaySmall)
            }
        }
    }
}

// ======================================================================= my timeline

/**
 * My timeline. Portrait: label row, cards, action row (confirm + 3‑token trade).
 * Landscape: just the cards – count/tokens/trade live in the header and confirm inside the panel.
 */
@Composable
private fun MyTimeline(ui: GameUi, a: GameActions, me: GamePlayer, phase: String, myTurn: Boolean, lay: Layout) {
    val t = ui.game.turn!!
    val placing = myTurn && phase == Phase.LISTEN
    val revealedCardId = if (phase == Phase.RESULT || phase == Phase.VOTE) t.card?.id else null
    val markers = if (myTurn && phase == Phase.CHALLENGE) challengeMarkers(ui) else emptyMap()
    val confirmLabel = ui.selectedSlot?.let { "CONFIRMAR NA ${it + 1}ª POSIÇÃO" } ?: "ESCOLHA A POSIÇÃO"
    // keep the interesting item in view: the chosen slot while placing, otherwise the card just revealed/inserted
    val scrollTarget = when {
        placing -> ui.selectedSlot
        myTurn && phase == Phase.CHALLENGE -> t.slot
        revealedCardId != null -> me.timeline.indexOfFirst { it.id == revealedCardId }.takeIf { it >= 0 }
        else -> null
    }
    Column(Modifier.fillMaxWidth().background(Ink)) {
        if (!lay.compact) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Sua linha do tempo")
                Pill("${me.timeline.size}/${ui.game.options.cardsToWin}", color = NeonYellow)
                Spacer(Modifier.weight(1f))
                Text("Rodada ${ui.game.round} · ${ui.game.deckCount} no baralho", color = TextTertiary, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        } else VSpace(2.dp)
        Timeline(
            cards = me.timeline,
            selectable = placing,
            selectedSlot = if (placing) ui.selectedSlot else if (myTurn && phase == Phase.CHALLENGE) t.slot else null,
            markers = markers,
            highlightCardId = revealedCardId,
            onSlotSelected = a.selectSlot,
            autoScrollTo = scrollTarget,
            cardWidth = lay.cardW,
            cardHeight = lay.cardH,
            modifier = Modifier.fillMaxWidth(),
        )
        if (!lay.compact) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                AnimatedVisibility(visible = placing, modifier = Modifier.weight(1f)) {
                    NeonButton(confirmLabel, enabled = ui.selectedSlot != null, height = 50.dp, onClick = a.confirmPlacement)
                }
                if (!placing) Spacer(Modifier.weight(1f))
                BuyButton(tokens = me.tokens, enabled = canBuy(ui.game, me), onClick = a.buyCard)
            }
        } else VSpace(4.dp)
    }
}

/**
 * The 3‑token trade is off while my own placement is waiting for the reveal: the bought card would slide
 * into my timeline and move the slot the engine is about to judge (the host rejects it anyway).
 */
private fun canBuy(g: GameView, me: GamePlayer?): Boolean =
    me != null && !g.finished && g.deckCount > 0 && !(g.turn?.playerId == me.id && g.turn?.phase == Phase.CHALLENGE)

/** "3 fichas → carta" trade. `compact` (header) shows just the tokens and an arrow. */
@Composable
private fun BuyButton(tokens: Int, enabled: Boolean, compact: Boolean = false, onClick: () -> Unit) {
    val can = tokens >= 3 && enabled
    Row(
        Modifier
            .height(if (compact) 36.dp else 50.dp)
            .alpha(if (can) 1f else 0.55f)
            .clip(RoundedCornerShape(14.dp))
            .background(Surface2)
            .border(1.dp, if (can) NeonYellow else Outline, RoundedCornerShape(14.dp))
            .clickable(enabled = can, onClickLabel = "Trocar 3 fichas por uma carta", onClick = onClick)
            .padding(horizontal = if (compact) 8.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(3) { HitsterToken(color = NeonYellow, size = 14.dp, dim = !can) }
        Text(if (compact) "→ carta" else "3 fichas → carta", color = if (can) NeonYellow else TextSecondary, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@Composable
private fun RoundIcon(
    icon: ImageVector,
    desc: String,
    size: Dp,
    enabled: Boolean = true,
    brush: Brush = Brush.linearGradient(listOf(Surface2, Surface2)),
    showLabel: Boolean = true,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(size)
                .alpha(if (enabled) 1f else 0.4f)
                .clip(CircleShape)
                .background(brush)
                .border(1.dp, Outline, CircleShape)
                .clickable(enabled = enabled, onClickLabel = desc, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = desc, tint = Color.White, modifier = Modifier.size(size * 0.5f))
        }
        if (showLabel) Text(desc, color = TextTertiary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp), maxLines = 1)
    }
}

private fun fmt(ms: Long): String {
    val s = (ms / 1000).toInt()
    return "%d:%02d".format(s / 60, s % 60)
}

// ======================================================================= winner

@Composable
private fun WinnerOverlay(ui: GameUi, a: GameActions) {
    val g = ui.game
    val winner = g.player(g.winnerId)
    val ranking = g.players.sortedByDescending { it.timeline.size }
    // A scrim that swallows every touch (an *enabled* no‑op clickable, no ripple) so the game below is inert.
    val scrimInteraction = remember { MutableInteractionSource() }
    Box(
        Modifier.fillMaxSize().background(Ink.copy(alpha = 0.94f)).clickable(interactionSource = scrimInteraction, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        var shown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { shown = true }
        AnimatedVisibility(visible = shown, enter = scaleIn(tween(400)) + fadeIn()) {
            Column(
                Modifier
                    .padding(24.dp)
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(Surface1)
                    .border(2.dp, NeonBrush, RoundedCornerShape(26.dp))
                    .padding(horizontal = 24.dp, vertical = 20.dp)
                    .verticalScroll(rememberScrollState()),   // 10 players in landscape must still fit
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Default.EmojiEvents, contentDescription = "Troféu", tint = NeonYellow, modifier = Modifier.size(56.dp))
                VSpace(6.dp)
                NeonTitle(size = 32)
                VSpace(4.dp)
                Text(if (winner?.id == ui.myId) "Você é o(a) HITSTER!" else "${winner?.name ?: "Alguém"} é o(a) HITSTER!", style = MaterialTheme.typography.headlineSmall, color = TextPrimary, textAlign = TextAlign.Center)
                VSpace(12.dp)
                ranking.forEachIndexed { i, p ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("${i + 1}º", color = if (i == 0) NeonYellow else TextTertiary, fontWeight = FontWeight.Black, modifier = Modifier.width(28.dp))
                        Avatar(p.name, parseHex(p.color), size = 28.dp)
                        Text(if (p.id == ui.myId) "Você" else p.name, color = TextPrimary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${p.timeline.size} cartas", color = TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                }
                VSpace(16.dp)
                if (ui.isHost) NeonButton("JOGAR DE NOVO", onClick = a.restart) else Text("Aguardando o anfitrião iniciar outra partida…", color = TextTertiary, textAlign = TextAlign.Center)
                VSpace(10.dp)
                GhostButton("Sair da sessão", color = TextSecondary, modifier = Modifier.fillMaxWidth(), onClick = a.leave)
            }
        }
    }
}
