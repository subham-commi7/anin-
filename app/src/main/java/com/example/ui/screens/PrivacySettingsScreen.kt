package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.database.SecurityAuditLogEntity
import com.example.core.model.VoiceProcessingMode
import com.example.ui.AninViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PrivacySettingsScreen(
    viewModel: AninViewModel,
    modifier: Modifier = Modifier
) {
    val processingMode by viewModel.outputVoiceManager.processingMode.collectAsState()
    val auditLogs by viewModel.auditLogs.collectAsState()
    val deviceDiagnostics by viewModel.deviceDiagnostics.collectAsState()
    val audioCaps by viewModel.audioCapabilities.collectAsState()

    var showClearSamplesConfirm by remember { mutableStateOf(false) }
    var showDeleteAllProfilesConfirm by remember { mutableStateOf(false) }

    if (showClearSamplesConfirm) {
        AlertDialog(
            onDismissRequest = { showClearSamplesConfirm = false },
            title = { Text("Delete All Raw Voice Samples?") },
            text = { Text("This will immediately delete all raw audio recordings stored on the device. Derived voice profile synthesis parameters will be preserved.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAllVoiceSamples()
                        showClearSamplesConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Samples")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearSamplesConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeleteAllProfilesConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAllProfilesConfirm = false },
            title = { Text("Delete All Custom Profiles?") },
            text = { Text("This will permanently delete all custom output voice profiles, biometric models, and raw audio files, resetting Anin to built-in voices.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAllCustomProfiles()
                        showDeleteAllProfilesConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete All Profiles")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllProfilesConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section: Device & Hardware Diagnostics
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Target Device Diagnostics",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { viewModel.refreshDeviceDiagnostics() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh Diagnostics")
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Device: ${deviceDiagnostics.model} • ${deviceDiagnostics.androidVersion} • ${deviceDiagnostics.memoryInfo}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    val battery = deviceDiagnostics.battery
                    val network = deviceDiagnostics.network

                    MetricRow("Battery Level", "${battery.percentage}% (${battery.chargePlug})", battery.percentage > 20)
                    MetricRow("Battery Temperature", "${battery.temperatureCelsius}°C (Health: ${battery.health})", battery.temperatureCelsius < 42f)
                    MetricRow("Network Status", "${network.networkType} (${if (network.isConnected) "Connected" else "Offline"})", network.isConnected)
                    MetricRow("Acoustic Echo Canceler (AEC)", if (audioCaps.hasAEC) "Hardware Supported" else "Software Fallback", audioCaps.hasAEC)
                    MetricRow("Noise Suppressor (NS)", if (audioCaps.hasNoiseSuppressor) "Hardware Supported" else "Software Fallback", audioCaps.hasNoiseSuppressor)
                    MetricRow("Automatic Gain Control (AGC)", if (audioCaps.hasAGC) "Hardware Supported" else "Software Fallback", audioCaps.hasAGC)
                }
            }
        }

        // Section: Voice Processing Mode
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Voice Processing Mode",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Control how voice synthesis is processed on your device:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    VoiceProcessingMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = processingMode == mode,
                                onClick = { viewModel.setProcessingMode(mode) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = mode.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = mode.description,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section: Voice Data Privacy & Retention Policy
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Voice Data Privacy & 10-Day Retention",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "• Raw Voice Samples: Encrypted in private local app sandbox.\n• 10-Day Strict Retention: Audio files older than 10 days are permanently purged.\n• Zero Automatic Cloud Upload: Voice data is never transmitted to Firebase or Gemini API.\n• Independent Biometrics: Authentication biometrics and output voices are never mixed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.runRetentionCleanup() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.AutoDelete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Run Retention Check", style = MaterialTheme.typography.labelSmall)
                        }

                        OutlinedButton(
                            onClick = { viewModel.clearSynthesisCache() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear Audio Cache", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showClearSamplesConfirm = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Delete All Samples", style = MaterialTheme.typography.labelSmall)
                        }

                        Button(
                            onClick = { showDeleteAllProfilesConfirm = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Delete All Profiles", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        // Section: Security & Audit Events Stream
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Security Audit Events (${auditLogs.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Zero sensitive audio stored in logs",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }

        items(auditLogs) { log ->
            AuditLogCard(log)
        }
    }
}

@Composable
fun AuditLogCard(log: SecurityAuditLogEntity) {
    val dateStr = SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp))

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = log.eventType,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = log.details,
                style = MaterialTheme.typography.bodySmall
            )
            log.diagnosticCode?.let { code ->
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Code: $code",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}
