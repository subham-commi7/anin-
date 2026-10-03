package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.AninViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AuthenticationVoiceScreen(
    viewModel: AninViewModel,
    modifier: Modifier = Modifier
) {
    val isEnrolled by viewModel.isSubhamEnrolled.collectAsState()
    val metadata by viewModel.subhamMetadata.collectAsState()
    val testResult by viewModel.verificationTestResult.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Architecture Guard Banner
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "PROFILE A — AUTHENTICATION VOICE",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        Text(
                            text = "Used exclusively to identify and authorize Subham. Strictly isolated from Output Voice.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        // Subham Enrollment Status Card
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isEnrolled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        if (isEnrolled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        RoundedCornerShape(16.dp)
                    )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Authorized Speaker",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Subham",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        AssistChip(
                            onClick = {},
                            label = { Text(if (isEnrolled) "Enrolled & Protected" else "Not Enrolled") },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (isEnrolled) Icons.Default.CheckCircle else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = if (isEnrolled) Color(0xFF10B981) else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (isEnrolled && metadata != null) {
                        val dateStr = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())
                            .format(Date(metadata!!.enrolledAt))
                        Text(
                            text = "• Enrolled On: $dateStr\n• Biometric Samples: ${metadata!!.sampleCount} acoustic frames\n• Vocal Baseline: ${metadata!!.dominantFrequency.toInt()} Hz (Subham Resonance)\n• Biometric Confidence Score: ${(metadata!!.qualityScore * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "Subham's biometric voice signature is required before Anin will execute privileged voice commands or barge-in requests.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.enrollSubhamVoiceBiometrics() },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("enroll_subham_btn")
                        ) {
                            Icon(Icons.Default.Fingerprint, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isEnrolled) "Re-enroll Voice" else "Enroll Subham Voice")
                        }

                        if (isEnrolled) {
                            OutlinedButton(
                                onClick = { viewModel.clearSubhamBiometrics() },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.DeleteForever, contentDescription = null)
                            }
                        }
                    }
                }
            }
        }

        // Live Anti-Self-Authentication Testing Lab
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Anti-Self-Authentication Proof Lab",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "MANDATORY RULE: Anin's generated output voice must NEVER authenticate itself as Subham. Test verification responses below:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 3 Test Simulation Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = { viewModel.testSpeakerVerification("subham") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("test_subham_voice_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488))
                        ) {
                            Text("1. Subham", style = MaterialTheme.typography.labelSmall)
                        }

                        Button(
                            onClick = { viewModel.testSpeakerVerification("stranger") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("test_stranger_voice_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706))
                        ) {
                            Text("2. Stranger", style = MaterialTheme.typography.labelSmall)
                        }

                        Button(
                            onClick = { viewModel.testSpeakerVerification("anin_voice") },
                            modifier = Modifier
                                .weight(1.2f)
                                .testTag("test_anin_self_auth_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("3. Anin Playback", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Verification Output Result Card
                    testResult?.let { result ->
                        val isSuccess = result.isSubham
                        val cardBg = if (isSuccess) Color(0xFFECFDF5) else Color(0xFFFEF2F2)
                        val textBorder = if (isSuccess) Color(0xFF10B981) else Color(0xFFEF4444)

                        Card(
                            colors = CardDefaults.cardColors(containerColor = cardBg),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, textBorder, RoundedCornerShape(10.dp))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                            contentDescription = null,
                                            tint = textBorder
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isSuccess) "ACCESS GRANTED: SUBHAM" else "ACCESS DENIED",
                                            fontWeight = FontWeight.Bold,
                                            color = textBorder,
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                    }

                                    Badge(containerColor = textBorder) {
                                        Text(result.diagnosticCode, modifier = Modifier.padding(horizontal = 4.dp))
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = result.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.Black
                                )

                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Confidence Match: ${(result.confidence * 100).toInt()}% (Threshold: 72%)\nInternal Playback Detected: ${result.isAninInternalVoice}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.DarkGray
                                )
                            }
                        }
                    }
                }
            }
        }

        // Explanation of Architectural Isolation
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Why Authentication & Output Voice Never Mix",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "1. Speaker Verification Model: Trained exclusively on Subham's voice features (16-band MFCC spectral centroid, zero-crossing rate, fundamental vocal chord resonance).\n\n2. Output Voice Model: Generates synthetic acoustic waves for Anin using calibrated pitch multipliers, speech cadence, and language phonemes.\n\n3. Echo Cancellation & Acoustic Gating: When Anin's speakers play audio, the internal microphone loop detects and flags the signal as 'ANIN_INTERNAL_PLAYBACK', instantly rejecting self-authentication.\n\n4. Zero Self-Authorization: Anin can never trigger its own wake-word or verify its own voice.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
