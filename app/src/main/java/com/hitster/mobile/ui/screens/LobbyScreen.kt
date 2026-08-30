package com.hitster.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hitster.mobile.net.ConnState
import com.hitster.mobile.net.GameOptions
import com.hitster.mobile.net.Room
import com.hitster.mobile.ui.components.Avatar
import com.hitster.mobile.ui.components.GhostButton
import com.hitster.mobile.ui.components.NeonButton
import com.hitster.mobile.ui.components.NeonTitle
import com.hitster.mobile.ui.components.Pill
import com.hitster.mobile.ui.components.SectionLabel
import com.hitster.mobile.ui.components.VSpace
import com.hitster.mobile.ui.theme.Danger
import com.hitster.mobile.ui.theme.NeonCyan
import com.hitster.mobile.ui.theme.NeonGreen
import com.hitster.mobile.ui.theme.NeonPink
import com.hitster.mobile.ui.theme.NeonYellow
import com.hitster.mobile.ui.theme.Outline
import com.hitster.mobile.ui.theme.Surface1
import com.hitster.mobile.ui.theme.Surface2
import com.hitster.mobile.ui.theme.TextPrimary
import com.hitster.mobile.ui.theme.TextSecondary
import com.hitster.mobile.ui.theme.TextTertiary
import com.hitster.mobile.ui.theme.parseHex

@Composable
fun LobbyScreen(
    room: Room,
    myId: String,
    isHost: Boolean,
    connection: ConnState,
    hostAddress: String?,
    onSetDecks: (List<String>) -> Unit,
    onSetOptions: (GameOptions) -> Unit,
    onKick: (String) -> Unit,
    onStart: () -> Unit,
    onLeave: () -> Unit,
) {
    val ctx = LocalContext.current
    var showOptions by remember { mutableStateOf(false) }
    val canStart = room.players.size >= 2 && room.decks.isNotEmpty()

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .wrapContentWidth()
            .widthIn(max = 560.dp),   // landscape / tablets
    ) {
        VSpace(8.dp)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            NeonTitle(size = 26)
            Spacer(Modifier.weight(1f))
            if (connection != ConnState.CONNECTED) Pill("reconectando…", color = NeonYellow)
            GhostButton("Sair", color = TextSecondary, height = 36.dp, onClick = onLeave)
        }
        VSpace(20.dp)

        // ---- room code
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Surface1)
                .border(1.dp, Outline, RoundedCornerShape(20.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SectionLabel("Código da sessão")
            VSpace(6.dp)
            Text(room.code, style = MaterialTheme.typography.displayLarge, color = TextPrimary, letterSpacing = 10.sp)
            VSpace(6.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (hostAddress != null) "Os outros abrem o app na mesma rede Wi‑Fi e tocam na sua sessão.\nEntrada manual: $hostAddress"
                    else "Conectado ao anfitrião. Aguarde o início da partida.",
                    color = TextTertiary, style = MaterialTheme.typography.bodySmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                if (hostAddress != null) IconButton(onClick = {
                    val i = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, "Bora jogar Hitster! Sessão ${room.code} · endereço $hostAddress (mesma rede Wi‑Fi)")
                    }
                    ctx.startActivity(android.content.Intent.createChooser(i, "Compartilhar sessão"))
                }) { Icon(Icons.Default.Share, contentDescription = "Compartilhar", tint = NeonCyan) }
            }
        }
        VSpace(20.dp)

        // ---- players
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Jogadores")
            Spacer(Modifier.width(8.dp))
            Pill("${room.players.size}/10", color = TextSecondary)
        }
        VSpace(8.dp)
        room.players.forEach { p ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Surface1)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Avatar(p.name, parseHex(p.color), dim = !p.connected)
                Column(Modifier.weight(1f)) {
                    Text(p.name + if (p.id == myId) " (você)" else "", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text(
                        when {
                            p.id == room.hostId -> "Criou a sessão"
                            !p.connected -> "Desconectado"
                            else -> "Na sala"
                        },
                        color = if (p.connected) TextTertiary else Danger, style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (isHost && p.id != myId) {
                    IconButton(onClick = { onKick(p.id) }) { Icon(Icons.Default.Close, contentDescription = "Remover", tint = TextTertiary) }
                }
            }
        }
        if (room.players.size < 2) {
            Text("Aguardando mais jogadores… (mínimo 2)", color = TextTertiary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
        }
        VSpace(20.dp)

        // ---- decks
        SectionLabel("Baralhos")
        VSpace(8.dp)
        room.availableDecks.forEach { d ->
            val selected = d.sku in room.decks
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selected) Surface2 else Surface1)
                    .border(1.dp, if (selected) NeonPink else Outline, RoundedCornerShape(14.dp))
                    .clickable(enabled = isHost) {
                        val next = if (selected) room.decks - d.sku else room.decks + d.sku
                        if (next.isNotEmpty()) onSetDecks(next)
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(d.name, color = TextPrimary, fontWeight = FontWeight.Bold)
                    Text("${d.subtitle ?: ""} · ${d.count} cartas", color = TextTertiary, style = MaterialTheme.typography.bodySmall)
                }
                Box(
                    Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(if (selected) NeonPink else Surface2).border(1.dp, if (selected) NeonPink else Outline, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) { if (selected) Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp)) }
            }
        }
        if (!isHost) Text("Só o anfitrião escolhe os baralhos e as opções.", color = TextTertiary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
        VSpace(20.dp)

        // ---- options
        Row(Modifier.fillMaxWidth().clickable { showOptions = !showOptions }, verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Opções da partida")
            Spacer(Modifier.weight(1f))
            Text(if (showOptions) "ocultar" else "mostrar", color = NeonCyan, style = MaterialTheme.typography.labelSmall)
        }
        VSpace(6.dp)
        val o = room.options
        if (showOptions) {
            OptionSlider("Cartas para vencer", o.cardsToWin, 5, 20, isHost) { onSetOptions(o.copy(cardsToWin = it)) }
            OptionSlider("Janela para gritar HITSTER (s)", o.challengeSeconds, 5, 45, isHost) { onSetOptions(o.copy(challengeSeconds = it)) }
            OptionSlider("Tempo de votação (s)", o.voteSeconds, 10, 60, isHost) { onSetOptions(o.copy(voteSeconds = it)) }
            OptionSlider("Pausa entre rodadas (s)", o.resultSeconds, 5, 60, isHost) { onSetOptions(o.copy(resultSeconds = it)) }
        } else {
            Text("${o.cardsToWin} cartas para vencer · ${o.challengeSeconds}s para desafiar", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        VSpace(28.dp)

        if (isHost) {
            NeonButton("INICIAR PARTIDA", enabled = canStart, onClick = onStart)
        } else {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface1).padding(16.dp), contentAlignment = Alignment.Center) {
                Text("Aguardando o anfitrião iniciar…", color = NeonGreen, fontWeight = FontWeight.Bold)
            }
        }
        VSpace(28.dp)
    }
}

@Composable
private fun OptionSlider(label: String, value: Int, min: Int, max: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    var local by remember(value) { mutableStateOf(value.toFloat()) }
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row { Text(label, color = TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f)); Text(local.toInt().toString(), color = NeonYellow, fontWeight = FontWeight.Bold) }
        Slider(
            value = local, onValueChange = { local = it }, onValueChangeFinished = { onChange(local.toInt()) },
            valueRange = min.toFloat()..max.toFloat(), steps = max - min - 1, enabled = enabled,
            colors = SliderDefaults.colors(thumbColor = NeonPink, activeTrackColor = NeonPink, inactiveTrackColor = Surface2, activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent),
        )
    }
}
