package com.erdman.erdstream.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.erdman.erdstream.BuildConfig
import com.erdman.erdstream.data.TabSettingsManager
import com.erdman.erdstream.data.TabSetting
import com.erdman.erdstream.data.TranscodeBitrate
import com.erdman.erdstream.navItems

@Composable
fun SettingsScreen(
    serverUrl: String,
    username: String,
    selectedBitrate: TranscodeBitrate,
    onBitrateSelected: (TranscodeBitrate) -> Unit,
    onDisconnectClick: () -> Unit,
    hasBatteryOptimizationExemption: Boolean,
    onRequestBatteryOptimizationExemption: () -> Unit,
    isDuraSpeedAvailable: Boolean,
    duraspeedConfirmed: Boolean,
    onDuraSpeedConfirmedChange: (Boolean) -> Unit,
    onOpenDuraSpeed: () -> Unit,
    isResyncing: Boolean,
    onResyncLibraryClick: () -> Unit,
    tabSettings: List<TabSetting>,
    onTabSettingsChange: (List<TabSetting>) -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val isScrollable by remember { derivedStateOf { listState.canScrollForward || listState.canScrollBackward } }

    Row(modifier = Modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .eInkVerticalScroll(listState, scope, isScrollable),
        contentPadding = PaddingValues(16.dp),
        userScrollEnabled = false,
    ) {
        item {
            Text(text = "Connection", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = serverUrl, fontSize = 14.sp)
            Text(text = "Signed in as $username", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(onClick = onDisconnectClick) {
                Text("Disconnect")
            }
            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            Text(text = "Library", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Refresh artists, playlists, and the Home tab's lists from the server.",
                fontSize = 14.sp,
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onResyncLibraryClick,
                enabled = !isResyncing,
            ) {
                Text(if (isResyncing) "Resyncing…" else "Resync library")
            }
            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            Text(text = "Bottom navigation tabs", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Reorder tabs with the arrows and use the switches to show or hide them. Settings can't be hidden since it's the only way back to this screen.",
                fontSize = 14.sp,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        items(tabSettings.size) { index ->
            val setting = tabSettings[index]
            val label = navItems.find { it.route == setting.route }?.label ?: setting.route
            val isSettingsTab = setting.route == TabSettingsManager.TAB_ROUTE_SETTINGS

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f),
                )

                IconButton(
                    onClick = {
                        if (index > 0) {
                            val reordered = tabSettings.toMutableList()
                            reordered[index] = reordered[index - 1]
                                .also { reordered[index - 1] = reordered[index] }
                            onTabSettingsChange(reordered)
                        }
                    },
                    enabled = index > 0,
                ) {
                    Icon(imageVector = Icons.Outlined.KeyboardArrowUp, contentDescription = "Move $label up")
                }

                IconButton(
                    onClick = {
                        if (index < tabSettings.lastIndex) {
                            val reordered = tabSettings.toMutableList()
                            reordered[index] = reordered[index + 1]
                                .also { reordered[index + 1] = reordered[index] }
                            onTabSettingsChange(reordered)
                        }
                    },
                    enabled = index < tabSettings.lastIndex,
                ) {
                    Icon(imageVector = Icons.Outlined.KeyboardArrowDown, contentDescription = "Move $label down")
                }

                Switch(
                    checked = setting.visible,
                    onCheckedChange = { visible ->
                        if (!isSettingsTab) {
                            val updated = tabSettings.map {
                                if (it.route == setting.route) it.copy(visible = visible) else it
                            }
                            onTabSettingsChange(updated)
                        }
                    },
                    enabled = !isSettingsTab,
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            Text(text = "Background playback", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (hasBatteryOptimizationExemption) {
                    "Battery optimizations are currently ignoring ErdStream. Background playback is less likely to be stopped, but the system may still close the app in extreme cases."
                } else {
                    "On some devices, battery optimizations can stop ErdStream while playing in the background. You can request an exemption so the system is less likely to pause playback."
                },
                fontSize = 14.sp,
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onRequestBatteryOptimizationExemption,
                enabled = !hasBatteryOptimizationExemption,
            ) {
                Text(
                    text = if (hasBatteryOptimizationExemption) {
                        "Background optimization already allowed"
                    } else {
                        "Allow ErdStream to run in background"
                    },
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (isDuraSpeedAvailable) {
            item {
                DuraSpeedStatusRow(
                    confirmed = duraspeedConfirmed,
                    onConfirmedChange = onDuraSpeedConfirmedChange,
                    onOpenDuraSpeed = onOpenDuraSpeed,
                )
                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        item {
            Text(text = "Streaming quality", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Your library is FLAC. Choose a lower bitrate to have the server transcode on the fly and save bandwidth.",
                fontSize = 14.sp,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        items(TranscodeBitrate.entries) { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onBitrateSelected(option) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = option == selectedBitrate,
                    onClick = { onBitrateSelected(option) },
                )
                Text(text = option.label, fontSize = 16.sp)
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "About", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "ErdStream", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(text = "Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})", fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "A calm, minimal Subsonic/Navidrome client for e-ink phones.",
                fontSize = 14.sp,
            )
        }
    }
    if (isScrollable) {
        EInkScrollbar(state = listState, scope = scope)
    }
    }
}

/**
 * DuraSpeed's per-app whitelist state can't be queried from a third-party
 * app, so this is self-reported: tapping the row opens the DuraSpeed app
 * so the user can check/set it there, and the switch is a manual
 * "I've done this" toggle that's persisted.
 */
@Composable
private fun DuraSpeedStatusRow(
    confirmed: Boolean,
    onConfirmedChange: (Boolean) -> Unit,
    onOpenDuraSpeed: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenDuraSpeed() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "DuraSpeed", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(
                text = if (confirmed) {
                    "Confirmed whitelisted (tap to open DuraSpeed)"
                } else {
                    "Ensure ErdStream is toggled ON in DuraSpeed settings, then mark it done here"
                },
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = confirmed,
            onCheckedChange = onConfirmedChange,
        )
    }
}
