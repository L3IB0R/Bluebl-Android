package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.ChatLine
import com.example.data.LineKind
import com.example.data.Peer
import com.example.ui.theme.LocalBlueBlPalette
import com.example.ui.theme.LocalHighContrast

@Composable
fun PeersPane(
    peers: List<Peer>,
    recentLines: List<ChatLine>,
    showOpenChatAction: Boolean,
    onOpenChatClick: () -> Unit,
    onRefreshDiscoveryClick: () -> Unit = {},
    meshEnabled: Boolean = true,
    onEnableMeshClick: () -> Unit = {}
) {
    val pal = LocalBlueBlPalette.current
    val highContrast = LocalHighContrast.current
    val lastUserLine = recentLines.lastOrNull { it.kind == LineKind.Peer || it.kind == LineKind.Own }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        pal.bg,
                        if (highContrast) pal.bg else pal.headerGradientEnd.copy(alpha = 0.7f),
                        pal.bg
                    )
                )
            )
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "featured_room_card") {
                val cardShape = RoundedCornerShape(24.dp)
                val cardBgBrush = if (highContrast) {
                    Brush.linearGradient(listOf(pal.peerBubbleBg, pal.peerBubbleBg))
                } else if (pal.isLight) {
                    Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = 0.94f),
                            pal.ownBubbleGradientStart.copy(alpha = 0.65f)
                        )
                    )
                } else {
                    Brush.linearGradient(
                        listOf(
                            pal.glassSurface,
                            pal.accentGradientStart.copy(alpha = 0.16f)
                        )
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(cardShape)
                        .background(cardBgBrush)
                        .border(
                            BorderStroke(1.dp, if (highContrast) pal.border else pal.glassBorder),
                            cardShape
                        )
                        .clickable(enabled = showOpenChatAction, onClick = onOpenChatClick)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (highContrast) {
                                            Brush.linearGradient(listOf(pal.accent, pal.accent))
                                        } else {
                                            Brush.linearGradient(
                                                listOf(pal.accentGradientStart, pal.accentGradientEnd)
                                            )
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ChatBubble,
                                    contentDescription = null,
                                    tint = pal.onAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Sala de chat cercana",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = pal.fg
                                )
                                Text(
                                    text = if (lastUserLine != null) {
                                        "${lastUserLine.sender}: ${lastUserLine.text}"
                                    } else if (peers.isEmpty()) {
                                        "Conversa sin internet con dispositivos cerca"
                                    } else {
                                        "${peers.size} ${if (peers.size == 1) "dispositivo listo" else "dispositivos listos"} para hablar"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = pal.dim,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (showOpenChatAction) {
                            GradientButton(
                                onClick = onOpenChatClick,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("open_chat_from_peers_button")
                            ) {
                                Text(
                                    text = "Abrir chat",
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            item(key = "peers_section_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Dispositivos disponibles",
                        style = MaterialTheme.typography.titleSmall,
                        color = pal.fg
                    )
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = (if (meshEnabled) pal.accent else pal.dim).copy(alpha = 0.12f),
                        border = BorderStroke(0.9.dp, (if (meshEnabled) pal.accent else pal.dim).copy(alpha = 0.40f))
                    ) {
                        Text(
                            text = when {
                                !meshEnabled -> "Malla apagada"
                                peers.isEmpty() -> "Buscando…"
                                else -> "${peers.size} en alcance"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (meshEnabled) pal.accent else pal.dim,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (peers.isEmpty()) {
                item(key = "peers_empty_card") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        GlassCard(
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth()
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(22.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.linearGradient(
                                                listOf(
                                                    pal.accentGradientStart.copy(alpha = 0.16f),
                                                    pal.accentGradientEnd.copy(alpha = 0.24f)
                                                )
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.People,
                                        contentDescription = null,
                                        tint = pal.accent,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                                Text(
                                    text = if (meshEnabled) "Todavía no hay dispositivos cerca" else "Malla Bluetooth apagada",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = pal.fg,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = if (meshEnabled) {
                                        "Cuando otro móvil o PC con BlueBl o BitChat esté a pocos metros aparecerá aquí por Bluetooth."
                                    } else {
                                        "Apagaste la malla Bluetooth. Vuelve a encenderla para buscar dispositivos cerca de ti."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = pal.dim,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(2.dp))
                                if (!meshEnabled) {
                                    GradientButton(
                                        onClick = onEnableMeshClick,
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("enable_mesh_button")
                                    ) {
                                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Encender malla Bluetooth", fontWeight = FontWeight.SemiBold)
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = onRefreshDiscoveryClick,
                                        shape = RoundedCornerShape(14.dp),
                                        border = BorderStroke(1.dp, pal.accent.copy(alpha = 0.7f)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("refresh_ble_discovery_button")
                                    ) {
                                        Icon(
                                            Icons.Rounded.Refresh,
                                            contentDescription = null,
                                            tint = pal.accent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text("Buscar de nuevo", color = pal.accent, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                itemsIndexed(peers, key = { _, p -> p.id }) { _, peer ->
                    GlassCard(
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("peer_card_${peer.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = peer.nickname,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = pal.fg,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(12.dp))
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF22C55E))
                            )
                        }
                    }
                }
            }
        }
    }
}
