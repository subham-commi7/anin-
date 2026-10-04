package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.example.core.model.VoiceLanguage
import com.example.ui.AninViewModel
import com.example.ui.components.VoiceWaveformVisualizer

@Composable
fun VoiceEnrollmentWizard(
    viewModel: AninViewModel,
    onDismiss: () -> Unit
) {
    val state by viewModel.enrollmentState.collectAsState()
    var setAsActiveVoice by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .testTag("voice_enrollment_dialog"),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.RecordVoiceOver,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = "Custom Output Voice Setup",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Step Progress Indicator
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (stepIndex in 1..5) {
                        val isCompleted = stepIndex < state.step
                        val isCurrent = stepIndex == state.step
                        val stepColor = when {
                            isCompleted -> MaterialTheme.colorScheme.primary
                            isCurrent -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.outlineVariant
                        }

                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(stepColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$stepIndex",
                                color = if (isCompleted || isCurrent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (stepIndex < 5) {
                            HorizontalDivider(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 4.dp),
                                color = if (isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                thickness = 2.dp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                state.errorMessage?.let { error ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = "Error",
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                if (state.step == 1) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "STEP 1: Profile Purpose",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "\"This voice will be used only for Anin's spoken responses.\"",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Important Security Boundary:\n• Output Voice is Profile B: Controls how Anin speaks to you.\n• Authentication Voice is Profile A: Used only to verify Subham.\n• The Output Voice can NEVER authenticate commands or authorize actions as Subham.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (state.step == 2) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "STEP 2: Consent & Legal Authorization",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "The custom voice feature must only be used when you have the legal right or explicit permission to use the supplied voice.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = state.hasConfirmedConsent,
                                    onCheckedChange = { viewModel.setConsentConfirmed(it) },
                                    modifier = Modifier.testTag("consent_checkbox")
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "I confirm that I own this voice sample or have permission from the voice owner to use it for speech synthesis.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Note: Unauthorized impersonation is strictly prohibited. Raw audio samples are stored in encrypted storage and governed by a 10-day automatic deletion policy.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }

                if (state.step == 3) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "STEP 3: Multi-Sample Recording / Import",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Select a prompt language to speak naturally for 2 to 4 seconds:",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            VoiceLanguage.entries.forEach { lang ->
                                FilterChip(
                                    selected = state.selectedLanguagePrompt == lang,
                                    onClick = { viewModel.setEnrollmentLanguagePrompt(lang) },
                                    label = { Text(lang.displayName) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Read aloud naturally:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "\"${state.selectedLanguagePrompt.samplePrompt}\"",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        VoiceWaveformVisualizer(
                            isActive = state.isRecording || state.recordedAudioSamples != null,
                            waveColor = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.recordVoiceSample(isSimulationClean = true) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("record_sample_btn"),
                                enabled = !state.isRecording
                            ) {
                                Icon(Icons.Default.Mic, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (state.recordedAudioSamples == null) "Record Sample" else "Retake Sample")
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Quality Testing Lab (Simulate Edge Cases):",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.recordVoiceSample(simulateFlaw = "clipping") },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text("Test Clipping", style = MaterialTheme.typography.labelSmall)
                            }
                            OutlinedButton(
                                onClick = { viewModel.recordVoiceSample(simulateFlaw = "too_short") },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text("Test Short", style = MaterialTheme.typography.labelSmall)
                            }
                            OutlinedButton(
                                onClick = { viewModel.recordVoiceSample(simulateFlaw = "high_noise") },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text("Test Noise", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                if (state.step == 4) {
                    val quality = state.qualityCheck
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "STEP 4: Audio Quality Validation",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        if (quality != null) {
                            val statusColor = if (quality.isValid) Color(0xFF10B981) else MaterialTheme.colorScheme.error

                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, statusColor, RoundedCornerShape(12.dp))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (quality.isValid) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                            contentDescription = null,
                                            tint = statusColor
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (quality.isValid) "Audio Quality Validated" else "Quality Insufficient",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = statusColor
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))
                                    MetricRow("Duration", "${quality.durationMs / 1000.0}s (Min 1.5s)", quality.durationMs >= 1500L)
                                    MetricRow("Volume RMS", "${quality.rmsLevelDb.toInt()} dB (Target > -42 dB)", quality.rmsLevelDb > -42f)
                                    MetricRow("Clipping", if (quality.clippingDetected) "Clipping detected" else "No clipping", !quality.clippingDetected)
                                    MetricRow("Silence Ratio", "${(quality.silenceRatio * 100).toInt()}% (Max 45%)", quality.silenceRatio <= 0.45f)
                                    MetricRow("Estimated SNR", "${quality.estimatedSnrDb.toInt()} dB (Min 10 dB)", quality.estimatedSnrDb >= 10f)
                                    MetricRow("Speech Detected", if (quality.speechDetected) "Clear speech verified" else "No clear speech", quality.speechDetected)

                                    if (!quality.isValid) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = "Specific Failure Reason:",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                        quality.failureReasons.forEach { reason ->
                                            Text(
                                                text = "• $reason",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (state.step == 5) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "STEP 5: Create & Activate Voice Profile",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = state.profileName,
                            onValueChange = { viewModel.updateProfileName(it) },
                            label = { Text("Profile Display Name") },
                            placeholder = { Text("e.g., Subham Custom Voice (Studio)") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profile_name_input"),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Use this voice as Anin's speaking voice?",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "If enabled, Anin will immediately speak responses with this voice profile.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = setAsActiveVoice,
                                        onCheckedChange = { setAsActiveVoice = it },
                                        modifier = Modifier.testTag("set_active_switch")
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "• Raw sample will be scheduled for 10-day retention deletion.\n• Profile is saved locally in private sandbox.\n• Not uploaded to cloud or Firebase.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (state.step == 5) {
                Button(
                    onClick = {
                        viewModel.completeProfileCreation(setAsActive = setAsActiveVoice)
                        onDismiss()
                    },
                    modifier = Modifier.testTag("save_profile_btn")
                ) {
                    Text("Save Voice Profile")
                }
            } else {
                Button(
                    onClick = { viewModel.nextEnrollmentStep() },
                    modifier = Modifier.testTag("next_step_btn")
                ) {
                    Text(if (state.step == 4) "Continue to Save" else "Next")
                }
            }
        },
        dismissButton = {
            if (state.step > 1) {
                OutlinedButton(onClick = { viewModel.previousEnrollmentStep() }) {
                    Text("Back")
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

@Composable
fun MetricRow(label: String, value: String, isPass: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = if (isPass) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
        )
    }
}
