package com.hitster.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hitster.mobile.data.Prefs
import com.hitster.mobile.host.FoundSession
import com.hitster.mobile.ui.components.Avatar
import com.hitster.mobile.ui.components.NeonButton
import com.hitster.mobile.ui.components.NeonTitle
import com.hitster.mobile.ui.components.Pill
import com.hitster.mobile.ui.components.SectionLabel
import com.hitster.mobile.ui.components.VSpace
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    prefs: Prefs,
    busy: Boolean,
    sessions: List<FoundSession>,
    onStartDiscovery: () -> Unit,
    onStopDiscovery: () -> Unit,
    onCreate: (name: String, color: String) -> Unit,
    onJoin: (address: String, code: String, name: String, color: String) -> Unit,
) {
    var name by remember { mutableStateOf(prefs.name) }
    var color by remember { mutableStateOf(prefs.color) }
    var manual by remember { mutableStateOf(false) }
    var address by remember { mutableStateOf(prefs.lastAddress ?: "") }
    var code by remember { mutableStateOf(prefs.lastRoom ?: "") }
    val canPlay = name.trim().length >= 2

    // Browse for hosts on the local network while this screen is visible.
    DisposableEffect(Unit) {
        onStartDiscovery()
        onDispose { onStopDiscovery() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .wrapContentWidth()
            .widthIn(max = 520.dp),   // landscape / tablets: keep the form readable instead of edge‑to‑edge
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        VSpace(40.dp)
        NeonTitle(size = 52)
        Text("O JOGO DE MÚSICAS DO SEU TEMPO", color = TextSecondary, style = MaterialTheme.typography.labelMedium, letterSpacing = 2.sp)
        VSpace(6.dp)
        Text(
            "Seja o primeiro a completar a sua linha do tempo musical.",
            color = TextTertiary, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
        )
        VSpace(28.dp)

        SectionLabel("Seu nome", Modifier.fillMaxWidth())
        VSpace(6.dp)
        Field(name, { if (it.length <= 18) name = it }, "Ex.: Ana", capitalize = KeyboardCapitalization.Words)
        VSpace(14.dp)
        SectionLabel("Sua cor", Modifier.fillMaxWidth())
        VSpace(8.dp)
        // FlowRow, not Row: 10 × 44dp = 440dp never fit a phone, and the last colours fell off‑screen.
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Prefs.PALETTE.forEach { hex ->
                val selected = hex == color
                // 44dp tap target around a 26dp swatch
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(onClickLabel = "Escolher cor") { color = hex },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(if (selected) 32.dp else 26.dp)
                            .clip(CircleShape)
                            .background(parseHex(hex))
                            .border(if (selected) 3.dp else 0.dp, if (selected) Color.White else Color.Transparent, CircleShape),
                    )
                }
            }
        }
        VSpace(28.dp)

        if (busy) {
            CircularProgressIndicator(color = NeonPink)
            VSpace(8.dp)
            Text("Conectando…", color = TextSecondary)
            VSpace(28.dp)
            return@Column
        }

        NeonButton("CRIAR SESSÃO", enabled = canPlay) { onCreate(name.trim(), color) }
        VSpace(6.dp)
        Text("Seu celular vira o anfitrião. Os outros entram pela mesma rede Wi‑Fi (ou pelo seu hotspot).", color = TextTertiary, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        VSpace(22.dp)

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).height(1.dp).background(Outline))
            Text("  ou entre em uma sessão  ", color = TextTertiary, style = MaterialTheme.typography.labelSmall)
            Box(Modifier.weight(1f).height(1.dp).background(Outline))
        }
        VSpace(14.dp)

        // ---- sessions found on the network
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Sessões por perto")
            Spacer(Modifier.width(8.dp))
            if (sessions.isEmpty()) CircularProgressIndicator(color = NeonCyan, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
            else Pill("${sessions.size}", color = NeonGreen)
        }
        VSpace(8.dp)
        if (sessions.isEmpty()) {
            Text("Procurando anfitriões na rede… Os dois celulares precisam estar na mesma rede Wi‑Fi.", color = TextTertiary, style = MaterialTheme.typography.bodySmall)
        } else if (!canPlay) {
            Text("Digite seu nome acima para entrar.", color = NeonYellow, style = MaterialTheme.typography.bodySmall)
        }
        sessions.forEach { s ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .alpha(if (canPlay) 1f else 0.5f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Surface1)
                    .border(1.dp, Outline, RoundedCornerShape(14.dp))
                    .clickable(enabled = canPlay) { onJoin(s.url, s.code, name.trim(), color) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Sessão ${s.code}", color = TextPrimary, fontWeight = FontWeight.Bold)
                    Text("Anfitrião: ${s.hostName} · ${s.address}", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
                Text("ENTRAR", color = NeonCyan, style = MaterialTheme.typography.labelLarge)
            }
        }
        VSpace(12.dp)

        // ---- manual fallback
        Box(Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(8.dp)).clickable { manual = !manual }.padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
            Text(if (manual) "Ocultar entrada manual" else "Não apareceu? Entrar pelo endereço", color = NeonCyan, style = MaterialTheme.typography.labelSmall)
        }
        if (manual) {
            VSpace(10.dp)
            Text("O anfitrião vê o endereço e o código na tela da sessão.", color = TextTertiary, style = MaterialTheme.typography.bodySmall)
            VSpace(8.dp)
            Field(address, { address = it.trim() }, "192.168.0.10:8080", keyboard = KeyboardType.Uri)
            VSpace(8.dp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    Field(code, { if (it.length <= 4) code = it.uppercase() }, "CÓDIGO", capitalize = KeyboardCapitalization.Characters, mono = true)
                }
                NeonButton(
                    "ENTRAR", modifier = Modifier.weight(1f), enabled = canPlay && code.length == 4 && address.isNotBlank(), brush = PurpleBrush,
                ) { onJoin(address, code, name.trim(), color) }
            }
        }

        VSpace(32.dp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Avatar(name.ifBlank { "?" }, parseHex(color), size = 28.dp)
            Text("Cada jogador usa o próprio celular. A música toca dentro do app, só no celular de quem está jogando.", color = TextTertiary, style = MaterialTheme.typography.bodySmall)
        }
        VSpace(24.dp)
    }
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    keyboard: KeyboardType = KeyboardType.Text,
    capitalize: KeyboardCapitalization = KeyboardCapitalization.None,
    mono: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(placeholder, color = TextTertiary) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, capitalization = capitalize),
        textStyle = if (mono) MaterialTheme.typography.headlineSmall.copy(letterSpacing = 6.sp) else MaterialTheme.typography.bodyLarge,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Surface1, unfocusedContainerColor = Surface1,
            focusedBorderColor = NeonCyan, unfocusedBorderColor = Outline,
            cursorColor = NeonCyan,
        ),
    )
}
