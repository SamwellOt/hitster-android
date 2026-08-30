package com.hitster.mobile.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.hitster.mobile.ui.theme.NeonYellow
import com.hitster.mobile.ui.theme.Surface3
import com.hitster.mobile.ui.theme.TextPrimary
import com.hitster.mobile.ui.theme.TextSecondary
import kotlinx.coroutines.delay

/**
 * Top banner shown while the socket to the host is down for more than a couple of seconds.
 * The client keeps reconnecting on its own; the banner only makes the state visible and offers an exit.
 */
@Composable
fun ConnectionBanner(visible: Boolean, onLeave: () -> Unit, graceMs: Long = 3_000) {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible) { delay(graceMs); show = true } else show = false
    }
    Box(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
        AnimatedVisibility(visible = show, enter = slideInVertically { -it } + fadeIn(), exit = slideOutVertically { -it } + fadeOut()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Surface3)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CircularProgressIndicator(color = NeonYellow, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                Column(Modifier.weight(1f)) {
                    Text("Conexão com o anfitrião perdida", color = TextPrimary, style = MaterialTheme.typography.titleSmall)
                    Text("Tentando reconectar… Confira o Wi‑Fi e se o anfitrião ainda está com o app aberto.", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
                Box(Modifier.width(4.dp))
                GhostButton("Sair", color = TextSecondary, height = 40.dp, onClick = onLeave)
            }
        }
    }
}
