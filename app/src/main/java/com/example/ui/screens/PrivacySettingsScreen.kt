package com.example.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.database.SecurityAuditLogEntity
import com.example.core.model.VoiceProcessingMode
import com.example.core.service.AninVoiceInteractionService
import com.example.ui.AninViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PrivacySettingsScreen(
    viewModel: AninViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val processingMode by viewModel.outputVoiceManager.processingMode.collectAsState()
    val auditLogs by viewModel.auditLogs.collectAsState()
    val deviceDiagnostics by viewModel.deviceDiagnostics.collectAsState()
    val audioCaps by viewModel.audioCapabilities.collectAsState()
    val micMetrics by viewModel.audioMetrics.collectAsState()
    val isMicGranted by viewModel.isMicrophonePermissionGranted.collectAsState()
    val isTestingMic by viewModel.isTestingMicrophone.collectAsState()
    val micTestResult by viewModel.microphoneTestResult.collectAsState()

    var showClearSamplesConfirm by remember { mutableStateOf(false) }
    var showDeleteAllProfilesConfirm by remember { mutableStateOf(false) }

    val isDefaultAssistant = remember(context) {
        AninVoiceInteractionService.isAninActiveAssistant(context)
    }

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
        // SECTION 1: HYBRID INTELLIGENCE & GEMINI OBSERVABILITY
        item {
            val operatingMode by viewModel.operatingMode.collectAsState()
            val geminiDiag by viewModel.geminiDiagnostics.collectAsState()
            val lastTrace by viewModel.lastTrace.collectAsState()

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Psychology, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Hybrid Intelligence Orchestrator",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Unified coordination of Local AI (Battery, YouTube, Maps, Settings, Reminders) + Gemini Intelligence (Reasoning, Routine, Q&A) through a single Safety Gate.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Operating Mode:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        com.example.core.assistant.AssistantOperatingMode.values().forEach { mode ->
                            val isSelected = operatingMode == mode
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.setOperatingMode(mode) },
                                label = { Text(mode.name, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Gemini AI Diagnostics:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "• Model: ${geminiDiag.modelName}\n" +
                               "• Key Configured: ${if (geminiDiag.isConfigured) "YES (Masked in Secrets)" else "NOT CONFIGURED (.env)"}\n" +
                               "• Last Status: ${geminiDiag.lastStatusCode ?: "No request yet"}\n" +
                               "• Latency: ${geminiDiag.lastLatencyMs}ms\n" +
                               "• Requests: ${geminiDiag.totalRequests} (Success: ${geminiDiag.successfulRequests}, Failed: ${geminiDiag.failedRequests})" +
                               (if (geminiDiag.lastErrorMessage != null) "\n• Last Error: ${geminiDiag.lastErrorMessage}" else ""),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    AnimatedVisibility(visible = lastTrace != null) {
                        lastTrace?.let { tr ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "LAST ORCHESTRATOR TRACE",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Input: \"${tr.transcript}\"\n" +
                                               "Routed To: ${tr.routingTarget} (${tr.routingReason})\n" +
                                               "Gemini Used: ${if (tr.geminiUsed) "YES (${tr.geminiLatencyMs ?: 0}ms)" else "NO (0 calls - Local Fast Path)"}\n" +
                                               "Action: ${tr.actionExecuted ?: "None"} -> ${tr.actionResult ?: "N/A"}\n" +
                                               "Duration: ${tr.totalDurationMs}ms",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Security Notice: Old API keys shown in screenshots should be revoked/rotated in Google Cloud Console. Secrets are never hardcoded.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // SECTION 2: PHYSICAL HARDWARE MICROPHONE TEST (SECTION P)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "iQOO Neo 10R Physical Microphone Test",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Tests physical AudioRecord PCM capture, dBFS levels, and speech presence on your device. Fails closed if silent.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { viewModel.runMicrophoneHardwareTest() },
                        enabled = !isTestingMic && isMicGranted,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isTestingMic) "Recording 3s (Speak into mic!)..." else "Run Microphone Test (3s)")
                    }

                    AnimatedVisibility(visible = micTestResult != null) {
                        micTestResult?.let { res ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (res.passed) Color(0xFFF0FDF4) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp)
                                    .border(1.dp, if (res.passed) Color(0xFF10B981) else MaterialTheme.colorScheme.error, RoundedCornerShape(8.dp))
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = if (res.passed) "TEST PASSED" else "TEST FAILED",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (res.passed) Color(0xFF047857) else MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = res.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (res.passed) Color(0xFF065F46) else MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // SECTION 2: SYSTEM VOICE ASSISTANT SETTINGS (SECTION E)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SettingsVoice, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Default Voice Assistant Service",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (isDefaultAssistant) {
                            "Anin is selected as your device's default Voice Assistant."
                        } else {
                            "Background 'Hey Anin' when screen is off or app is closed requires Anin to be selected as the device's default Assistant in Android Settings."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = {
                            try {
                                val intent = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Launch, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open Device Voice Assistant Settings")
                    }
                }
            }
        }

        // SECTION 3: DEVICE & HARDWARE DIAGNOSTICS
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
                            text = "Hardware Diagnostics",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { viewModel.refreshDeviceDiagnostics() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Device: ${deviceDiagnostics.deviceModel} • OS: ${deviceDiagnostics.osVersion} (API 36)\n" +
                               "CPU: ${deviceDiagnostics.cpuArchitecture} • RAM: ${deviceDiagnostics.availableRamMb} MB Free\n" +
                               "Battery: ${deviceDiagnostics.batteryPercentage}% • Thermal: ${deviceDiagnostics.thermalState}\n" +
                               "Microphone Permission: ${if (isMicGranted) "GRANTED" else "DENIED"}\n" +
                               "AudioRecord Initialized: ${if (micMetrics.audioRecordInitialized) "YES" else "NO"}\n" +
                               "Live dBFS: ${micMetrics.dBFS.toInt()} dBFS • Speech Detected: ${if (micMetrics.isSpeechDetected) "YES" else "NO"}\n" +
                               "Hardware AEC: ${if (audioCaps.hasAEC) "Supported" else "N/A"} • NS: ${if (audioCaps.hasNoiseSuppressor) "Supported" else "N/A"}\n" +
                               "YouTube App Launch: Available (Native App + Web Fallback)",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // SECTION 4: PRIVACY & RETENTION
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Voice Data Privacy & 10-Day Retention",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "• Raw Voice Samples: Encrypted in private local app sandbox.\n• 10-Day Strict Retention: Audio files older than 10 days are permanently purged.\n• Zero Automatic Cloud Upload: Voice data is never transmitted to Firebase or external servers.\n• Independent Biometrics: Authentication biometrics and output voices are never mixed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

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
    }
}
