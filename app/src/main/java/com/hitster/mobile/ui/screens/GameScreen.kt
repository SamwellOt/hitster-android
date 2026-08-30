package com.hitster.mobile.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.ui.text.style.TextOverflow
import com.hitster.mobile.ui.components.CardWidth
import com.hitster.mobile.ui.components.CardHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hitster.mobile.audio.PlaybackState
import com.hitster.mobile.net.Card
import com.hitster.mobile.net.GamePlayer
import com.hitster.mobile.net.GameView
import com.hitster.mobile.net.Phase
import com.hitster.mobile.net.Room
import com.hitster.mobile.ui.components.Avatar
import com.hitster.mobile.ui.components.Banner
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
    val previewReady: Boolean,
    val selectedSlot: Int?,
    val claimsTitle: Boolean,
    val challengeMode: Boolean,
    val challengeSlot: Int?,
    val viewingTimelineOf: String?,
)

class GameActions(
    val selectSlot: (Int) -> Unit,
    val confirmPlacement: () -> Unit,
    val toggleClaim: () -> Unit,
    val skip: () -> Unit,
    val buyCard: () -> Unit,
    val play: () -> Unit,
    val pause: () -> Unit,
    val replay: () -> Unit,
    val pass: () -> Unit,
    val startChallenge: () -> Unit,
    val cancelChallenge: () -> Unit,
    val pickChallengeSlot: (Int) -> Unit,
    val confirmChallenge: () -> Unit,
    val vote: (Boolean) -> Unit,
    val continueGame: () -> Unit,
    val openTimeline: (String?) -> Unit,
    val restart: () -> Unit,
    val leave: () -> Unit,
)

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
        if (!landscape) {
            // ---------------------------------------------------------- portrait: stacked
            Column(Modifier.fillMaxSize()) {
                Header(g, a)
                PlayersStrip(ui, a, goal)
                Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    CenterPanel(ui, a, me, current, myTurn)
                }
                if (me != null && t != null) MyTimeline(ui, a, me, t.phase, myTurn, compact = false)
            }
        } else {
            // ---------------------------------------------------------- landscape: panel + players on top, full‑width timeline below
            Column(Modifier.fillMaxSize()) {
                Header(g, a, compact = true)
                Row(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f).fillMaxHeight()) { CenterPanel(ui, a, me, current, myTurn) }
                    PlayersColumn(ui, a, goal, modifier = Modifier.width(150.dp).fillMaxHeight())
                }
                if (me != null && t != null) MyTimeline(ui, a, me, t.phase, myTurn, compact = true)
            }
        }
    }

    // ---------------------------------------------------------------- timeline sheet
    val viewing = ui.viewingTimelineOf?.let { g.player(it) }
    if (viewing != null) {
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { a.openTimeline(null) }, sheetState = sheet, containerColor = Surface1) {
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

// ======================================================================= layout pieces

@Composable
private fun Header(g: GameView, a: GameActions, compact: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = if (compact) 4.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NeonTitle(size = if (compact) 18 else 22)
        Spacer(Modifier.width(10.dp))
        // one pill so it never wraps on narrow phones
        Pill("Rodada ${g.round} · ${g.deckCount} cartas", color = TextSecondary)
        Spacer(Modifier.weight(1f))
        GhostButton("Sair", color = TextSecondary, height = if (compact) 40.dp else 44.dp, onClick = a.leave)
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
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (cur) Surface2 else Surface1)
                    .border(1.dp, if (cur) color else Outline, RoundedCornerShape(12.dp))
                    .clickable(onClickLabel = "Ver linha do tempo de ${p.name}") { a.openTimeline(p.id) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
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
private fun CenterPanel(ui: GameUi, a: GameActions, me: GamePlayer?, current: GamePlayer?, myTurn: Boolean) {
    val t = ui.game.turn ?: return
    if (current == null) return
    AnimatedContent(
        targetState = "${t.phase}:${myTurn}:${ui.challengeMode}",
        transitionSpec = { (fadeIn(tween(220)) + slideInVertically { it / 12 }) togetherWith fadeOut(tween(150)) },
        label = "phase",
    ) { key ->
        val phase = key.substringBefore(':')
        when {
            phase == Phase.LISTEN && myTurn -> ListenPanel(ui, a, me)
            phase == Phase.LISTEN -> WaitingPanel(current, "está ouvindo a música…", "Fique de olho: quando ${current.name} posicionar a carta, você pode gritar HITSTER.")
            phase == Phase.CHALLENGE && myTurn -> OwnerChallengePanel(ui, a)
            phase == Phase.CHALLENGE && ui.challengeMode -> ChallengePicker(ui, a, current)
            phase == Phase.CHALLENGE -> OpponentChallengePanel(ui, a, current, me)
            phase == Phase.VOTE -> VotePanel(ui, a, current, myTurn)
            phase == Phase.RESULT -> ResultPanel(ui, a, current)
            else -> Box {}
        }
    }
}

// ======================================================================= panels

@Composable
private fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(22.dp))
            .background(Surface1)
            .border(1.dp, Outline, RoundedCornerShape(22.dp))
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

@Composable
private fun ListenPanel(ui: GameUi, a: GameActions, me: GamePlayer?) {
    val pb = ui.playback
    val tokens = me?.tokens ?: 0
    Panel {
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
            Text(if (pb.isBuffering) "carregando…" else if (pb.error != null) "erro ao tocar" else "prévia · 30 s", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
        }
        VSpace(12.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIcon(Icons.Default.Replay, "Recomeçar", size = 48.dp, enabled = pb.url != null) { a.replay() }
            RoundIcon(
                if (pb.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                if (pb.isPlaying) "Pausar" else "Tocar", size = 72.dp, brush = NeonBrush,
            ) { if (pb.isPlaying) a.pause() else a.play() }
            RoundIcon(Icons.Default.SkipNext, "Pular · 1 ficha", size = 48.dp, enabled = tokens >= 1) { a.skip() }
        }
        VSpace(6.dp)
        Text("Pular a música custa 1 ficha HITSTER (você tem $tokens)", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        VSpace(14.dp)
        ClaimToggle(ui.claimsTitle, enabled = tokens < 5, onToggle = a.toggleClaim)
        VSpace(10.dp)
        Banner(
            if (ui.selectedSlot == null) "Toque em um  +  da sua linha do tempo para escolher a posição"
            else "Posição escolhida. Confirme abaixo quando estiver pronto(a).",
            color = if (ui.selectedSlot == null) NeonCyan else NeonGreen,
        )
    }
}

@Composable
private fun ClaimToggle(on: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (on) NeonYellow.copy(alpha = 0.14f) else Surface2)
            .border(1.dp, if (on) NeonYellow else Outline, RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HitsterToken(color = NeonYellow, size = 26.dp, dim = !on)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Sei o nome da música e o artista!", color = TextPrimary, fontWeight = FontWeight.Bold)
            Text(
                if (!enabled) "Você já tem 5 fichas (máximo)."
                else if (on) "Diga em voz alta. Os outros confirmam depois da revelação: +1 ficha." else "Diga em voz alta para ganhar 1 ficha HITSTER.",
                color = TextTertiary, style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun WaitingPanel(current: GamePlayer, action: String, hint: String) {
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(0.92f, 1.06f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "p")
    Panel {
        SectionLabel("Vez de ${current.name}", color = parseHex(current.color))
        VSpace(18.dp)
        Box(Modifier.scale(pulse)) { HiddenCard(icon = Icons.Default.MusicNote) }
        VSpace(18.dp)
        Text("${current.name} $action", style = MaterialTheme.typography.titleLarge, color = TextPrimary, textAlign = TextAlign.Center)
        VSpace(8.dp)
        Text(hint, color = TextTertiary, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        VSpace(6.dp)
        Text("A música toca apenas no celular de quem está jogando.", color = TextTertiary, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun OwnerChallengePanel(ui: GameUi, a: GameActions) {
    val t = ui.game.turn!!
    Panel {
        SectionLabel("Carta posicionada", color = NeonGreen)
        VSpace(10.dp)
        Countdown(t.deadline, ui.clockOffset, label = "para os outros gritarem HITSTER")
        VSpace(14.dp)
        Text("Aguardando desafios…", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
        VSpace(10.dp)
        if (t.challenges.isEmpty()) {
            Text("Ninguém desafiou ainda.", color = TextTertiary, style = MaterialTheme.typography.bodySmall)
        } else {
            t.challenges.forEach { c ->
                val p = ui.game.player(c.playerId)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                    HitsterToken(color = parseHex(p?.color), size = 20.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("${p?.name} gritou HITSTER (posição ${c.slot + 1})", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        VSpace(10.dp)
        Text("A carta será revelada quando o tempo acabar ou quando todos decidirem.", color = TextTertiary, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
    }
}

@Composable
private fun OpponentChallengePanel(ui: GameUi, a: GameActions, current: GamePlayer, me: GamePlayer?) {
    val t = ui.game.turn!!
    val tokens = me?.tokens ?: 0
    val alreadyChallenged = t.challenges.any { it.playerId == ui.myId }
    val passed = ui.myId in t.passed
    val glow by rememberInfiniteTransition(label = "hglow").animateFloat(0.85f, 1.05f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "hg")
    Panel {
        SectionLabel("${current.name} posicionou a carta", color = parseHex(current.color))
        VSpace(8.dp)
        Countdown(t.deadline, ui.clockOffset, label = "para gritar HITSTER")
        VSpace(14.dp)
        SectionLabel("Linha do tempo de ${current.name}")
        VSpace(6.dp)
        Timeline(
            cards = current.timeline, selectedSlot = t.slot,
            markers = challengeMarkers(ui), cardWidth = 78.dp, cardHeight = 104.dp,
            modifier = Modifier.fillMaxWidth(),
        )
        VSpace(14.dp)
        when {
            alreadyChallenged -> Banner("Você gritou HITSTER! Ficha colocada na posição ${t.challenges.first { it.playerId == ui.myId }.slot + 1}.", color = NeonPink)
            passed -> Banner("Você passou. Aguardando a revelação…", color = TextSecondary)
            else -> {
                Box(Modifier.scale(glow).fillMaxWidth()) {
                    NeonButton("GRITAR HITSTER!", enabled = tokens >= 1, height = 60.dp, onClick = a.startChallenge)
                }
                VSpace(6.dp)
                Text(
                    if (tokens >= 1) "Acha que ${current.name} errou? Pague 1 ficha, aponte a posição certa e roube a carta." else "Você precisa de 1 ficha HITSTER para desafiar.",
                    color = TextTertiary, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                )
                VSpace(10.dp)
                GhostButton("Não desafiar", color = TextSecondary, modifier = Modifier.fillMaxWidth(), onClick = a.pass)
            }
        }
    }
}

private fun challengeMarkers(ui: GameUi): Map<Int, List<String>> {
    val t = ui.game.turn ?: return emptyMap()
    return t.challenges.groupBy({ it.slot }, { ui.game.player(it.playerId)?.color ?: "#FFFFFF" })
}

@Composable
private fun ChallengePicker(ui: GameUi, a: GameActions, current: GamePlayer) {
    val t = ui.game.turn!!
    val disabled = (t.challenges.map { it.slot } + listOfNotNull(t.slot)).toSet()
    Panel {
        SectionLabel("HITSTER!", color = NeonPink)
        VSpace(6.dp)
        Text("Onde a carta deveria estar?", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
        VSpace(4.dp)
        Text("Toque em um  +  na linha do tempo de ${current.name}. A posição escolhida por ${current.name} (“?”) não pode ser usada.", color = TextTertiary, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        VSpace(10.dp)
        Countdown(t.deadline, ui.clockOffset)
        VSpace(10.dp)
        Timeline(
            cards = current.timeline, selectable = true, selectedSlot = ui.challengeSlot ?: t.slot,
            disabledSlots = disabled, markers = challengeMarkers(ui) + mapOf((t.slot ?: -1) to listOf(current.color)),
            onSlotSelected = a.pickChallengeSlot, cardWidth = 82.dp, cardHeight = 110.dp, modifier = Modifier.fillMaxWidth(),
            autoScrollTo = ui.challengeSlot,
        )
        VSpace(14.dp)
        NeonButton(
            if (ui.challengeSlot != null) "COLOCAR FICHA NA POSIÇÃO ${ui.challengeSlot + 1}" else "ESCOLHA UMA POSIÇÃO",
            enabled = ui.challengeSlot != null, brush = PurpleBrush, onClick = a.confirmChallenge,
        )
        VSpace(8.dp)
        GhostButton("Cancelar", color = TextSecondary, modifier = Modifier.fillMaxWidth(), onClick = a.cancelChallenge)
    }
}

@Composable
private fun VotePanel(ui: GameUi, a: GameActions, current: GamePlayer, myTurn: Boolean) {
    val t = ui.game.turn!!
    val card = t.card ?: return
    val voted = ui.myId in t.votes
    Panel {
        SectionLabel("Revelação", color = NeonYellow)
        VSpace(10.dp)
        RevealCard(card, t.result?.correct)
        VSpace(12.dp)
        Countdown(t.deadline, ui.clockOffset, label = "para votar")
        VSpace(12.dp)
        if (myTurn) {
            Text("Os outros jogadores estão confirmando se você acertou o nome e o artista…", color = TextSecondary, textAlign = TextAlign.Center)
        } else if (voted) {
            Banner("Voto registrado. Aguardando os demais…", color = TextSecondary)
        } else {
            Text("${current.name} disse o nome e o artista. Acertou?", style = MaterialTheme.typography.titleMedium, color = TextPrimary, textAlign = TextAlign.Center)
            VSpace(12.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NeonButton("ACERTOU", modifier = Modifier.weight(1f), brush = Brush.linearGradient(listOf(NeonGreen, NeonCyan))) { a.vote(true) }
                NeonButton("ERROU", modifier = Modifier.weight(1f), brush = Brush.linearGradient(listOf(Danger, NeonPink))) { a.vote(false) }
            }
        }
    }
}

@Composable
private fun ResultPanel(ui: GameUi, a: GameActions, current: GamePlayer) {
    val t = ui.game.turn!!
    val card = t.card ?: return
    val r = t.result
    val stolenBy = r?.stolenBy?.let { ui.game.player(it) }
    Panel {
        SectionLabel("Resultado", color = NeonYellow)
        VSpace(10.dp)
        RevealCard(card, r?.correct)
        VSpace(12.dp)
        val who = if (current.id == ui.myId) "Você" else current.name
        val (headline, color) = when {
            r == null -> "" to TextSecondary
            r.correct -> "$who acertou! A carta fica na linha do tempo." to NeonGreen
            stolenBy != null -> "$who errou — ${if (stolenBy.id == ui.myId) "você" else stolenBy.name} gritou HITSTER e roubou a carta!" to NeonPink
            else -> "$who errou. A carta vai para o descarte." to Danger
        }
        Text(headline, color = color, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        VSpace(6.dp)
        r?.challenges?.forEach { c ->
            val p = ui.game.player(c.playerId)
            val name = if (c.playerId == ui.myId) "Você" else p?.name
            Text(
                if (c.correct == true) "$name desafiou na posição ${c.slot + 1} e acertou." else "$name desafiou na posição ${c.slot + 1} e errou: perdeu 1 ficha.",
                color = TextSecondary, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
            )
        }
        if (r?.tokenEarned == true) {
            VSpace(6.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                HitsterToken(color = NeonYellow, size = 20.dp); Spacer(Modifier.width(6.dp))
                Text("$who ganhou 1 ficha por dizer o nome e o artista!", color = NeonYellow, style = MaterialTheme.typography.bodySmall)
            }
        } else if (t.claimsTitle && r?.tokenEarned == false) {
            Text("Os outros não confirmaram o nome/artista: sem ficha extra.", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        VSpace(14.dp)
        if (!ui.game.finished) {
            Countdown(t.deadline, ui.clockOffset, label = "para a próxima rodada")
            VSpace(10.dp)
            NeonButton("PRÓXIMA RODADA", onClick = a.continueGame)
        }
    }
}

/** The card flips from its back ("?") to the solution side the first time it is shown. */
@Composable
private fun RevealCard(card: Card, correct: Boolean?) {
    var flipped by remember(card.id) { mutableStateOf(false) }
    LaunchedEffect(card.id) { flipped = true }
    val rotation by animateFloatAsState(if (flipped) 180f else 0f, tween(650, easing = FastOutSlowInEasing), label = "flip")
    val density = LocalDensity.current.density
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.graphicsLayer { rotationY = rotation; cameraDistance = 14f * density }) {
            if (rotation <= 90f) {
                HiddenCard(width = 120.dp, height = 160.dp)
            } else {
                Box(Modifier.graphicsLayer { rotationY = 180f }) {
                    YearCard(card, width = 120.dp, height = 160.dp)
                    if (correct != null) {
                        Box(
                            Modifier.align(Alignment.TopEnd).padding(4.dp).size(28.dp).clip(CircleShape).background(if (correct) NeonGreen else Danger),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (correct) Icons.Default.Check else Icons.Default.Close,
                                contentDescription = if (correct) "Posição certa" else "Posição errada",
                                tint = Color.White, modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
        AnimatedVisibility(visible = rotation > 90f, enter = fadeIn(tween(250)) + slideInVertically { it / 6 }, modifier = Modifier.weight(1f)) {
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
 * My timeline. Portrait: label row, cards, action row. Landscape (`compact`): the confirm button and the
 * 3‑token trade sit in the label row so the cards get the full width and the panel keeps its height.
 */
@Composable
private fun MyTimeline(ui: GameUi, a: GameActions, me: GamePlayer, phase: String, myTurn: Boolean, compact: Boolean) {
    val t = ui.game.turn!!
    val placing = myTurn && phase == Phase.LISTEN
    val revealedCardId = if (phase == Phase.RESULT || phase == Phase.VOTE) t.card?.id else null
    val markers = if (myTurn && phase == Phase.CHALLENGE) challengeMarkers(ui) else emptyMap()
    val confirmLabel = ui.selectedSlot?.let { if (compact) "CONFIRMAR (${it + 1}ª)" else "CONFIRMAR NA ${it + 1}ª POSIÇÃO" } ?: (if (compact) "ESCOLHA O +" else "ESCOLHA A POSIÇÃO")
    // keep the interesting item in view: the chosen slot while placing, otherwise the card just revealed/inserted
    val scrollTarget = when {
        placing -> ui.selectedSlot
        myTurn && phase == Phase.CHALLENGE -> t.slot
        revealedCardId != null -> me.timeline.indexOfFirst { it.id == revealedCardId }.takeIf { it >= 0 }
        else -> null
    }
    val timeline: @Composable () -> Unit = {
        Timeline(
            cards = me.timeline,
            selectable = placing,
            selectedSlot = if (placing) ui.selectedSlot else if (myTurn && phase == Phase.CHALLENGE) t.slot else null,
            markers = markers,
            highlightCardId = revealedCardId,
            onSlotSelected = a.selectSlot,
            autoScrollTo = scrollTarget,
            cardWidth = if (compact) 88.dp else CardWidth,
            cardHeight = if (compact) 112.dp else CardHeight,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Column(Modifier.fillMaxWidth().background(Ink)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel(if (compact) "Linha do tempo" else "Sua linha do tempo")
            Pill("${me.timeline.size}/${ui.game.options.cardsToWin}", color = NeonYellow)
            TokenRow(me.tokens, color = parseHex(me.color), size = if (compact) 12.dp else 16.dp)
            Spacer(Modifier.weight(1f))
            if (compact) {
                BuyButton(tokens = me.tokens, enabled = !ui.game.finished && ui.game.deckCount > 0, compact = true, onClick = a.buyCard)
                AnimatedVisibility(visible = placing) {
                    NeonButton(confirmLabel, modifier = Modifier.widthIn(min = 150.dp, max = 220.dp), enabled = ui.selectedSlot != null, height = 44.dp, onClick = a.confirmPlacement)
                }
            }
        }
        timeline()
        if (!compact) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                AnimatedVisibility(visible = placing, modifier = Modifier.weight(1f)) {
                    NeonButton(confirmLabel, enabled = ui.selectedSlot != null, height = 50.dp, onClick = a.confirmPlacement)
                }
                if (!placing) Spacer(Modifier.weight(1f))
                BuyButton(tokens = me.tokens, enabled = !ui.game.finished && ui.game.deckCount > 0, onClick = a.buyCard)
            }
        } else VSpace(6.dp)
    }
}

/** "3 fichas → carta" trade. `compact` (landscape label row) shows just the tokens and an arrow. */
@Composable
private fun BuyButton(tokens: Int, enabled: Boolean, compact: Boolean = false, onClick: () -> Unit) {
    val can = tokens >= 3 && enabled
    Row(
        Modifier
            .height(if (compact) 44.dp else 50.dp)
            .alpha(if (can) 1f else 0.55f)
            .clip(RoundedCornerShape(14.dp))
            .background(Surface2)
            .border(1.dp, if (can) NeonYellow else Outline, RoundedCornerShape(14.dp))
            .clickable(enabled = can, onClickLabel = "Trocar 3 fichas por uma carta", onClick = onClick)
            .padding(horizontal = if (compact) 8.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(3) { HitsterToken(color = NeonYellow, size = 16.dp, dim = !can) }
        Text(if (compact) "→ carta" else "3 fichas → carta", color = if (can) NeonYellow else TextSecondary, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@Composable
private fun RoundIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    size: androidx.compose.ui.unit.Dp,
    enabled: Boolean = true,
    brush: Brush = Brush.linearGradient(listOf(Surface2, Surface2)),
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
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = desc, tint = Color.White, modifier = Modifier.size(size * 0.5f))
        }
        Text(desc, color = TextTertiary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp), maxLines = 1)
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
