package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import com.example.core.auth.SubhamVoiceEnrollmentManager
import com.example.core.database.SubhamEnrollmentSampleEntity
import com.example.ui.AninViewModel

@Composable
fun SubhamEnrollmentScreen(
    viewModel: AninViewModel,
    modifier: Modifier = Modifier
) {
    val isEnrolled by viewModel.isSubhamEnrolled.collectAsState()
    val metadata by viewModel.subhamMetadata.collectAsState()
    val completedSamples by viewModel.subhamEnrollmentSamples.collectAsState()
    val isRecordingEnrollment by viewModel.isRecordingEnrollment.collectAsState()
    val recordingSentenceIndex by viewModel.recordingSentenceIndex.collectAsState()
    val recordingProgress by viewModel.enrollmentRecordingProgress.collectAsState()
    val liveDbfs by viewModel.enrollmentLiveDbfs.collectAsState()

    val sentences = SubhamVoiceEnrollmentManager.ENROLLMENT_SENTENCES
    val completedIndices = completedSamples.map { it.sampleIndex }.toSet()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Security Banner
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Fingerprint,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "SYSTEM A — SUBHAM AUTHENTICATION",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Strict Fail-Closed Policy: You must actually speak the guided sentences aloud into your microphone. Silent recordings are immediately rejected.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        // Status & Progress Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Biometric Enrollment Status",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = if (isEnrolled) "Subham Enrolled & Verified" else "Enrollment Pending",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        AssistChip(
                            onClick = { },
                            label = { Text("${completedSamples.size} / 12 Samples") },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (isEnrolled) Icons.Default.CheckCircle else Icons.Default.Pending,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (isEnrolled) Color(0xFF10B981) else MaterialTheme.colorScheme.primary
                                )
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    LinearProgressIndicator(
                        progress = { (completedSamples.size.toFloat() / 12f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "• Hardware Encryption: AndroidKeyStore AES-256-GCM\n• Fail-Closed Policy: Silent or unauthorized speakers are rejected silently\n• Multi-Language Coverage: English, Bengali, Hindi phonetics",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.finalizeSubhamMultiSampleEnrollment() },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("finalize_subham_enrollment_btn"),
                            enabled = completedSamples.size >= 3
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Finalize Enrollment")
                        }

                        if (isEnrolled || completedSamples.isNotEmpty()) {
                            OutlinedButton(
                                onClick = { viewModel.clearSubhamBiometrics() },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Reset")
                            }
                        }
                    }
                }
            }
        }

        // Section Title: Guided Natural Sentences
        item {
            Text(
                text = "Guided Natural Sentences (Bengali, Hindi, English)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        items(sentences, key = { it.index }) { sentence ->
            val isDone = completedIndices.contains(sentence.index)
            val sampleEntity = completedSamples.find { it.sampleIndex == sentence.index }
            val isRecordingThis = isRecordingEnrollment && recordingSentenceIndex == sentence.index

            SentenceEnrollmentCard(
                sentence = sentence,
                isCompleted = isDone,
                sample = sampleEntity,
                isRecording = isRecordingThis,
                recordingProgress = recordingProgress,
                liveDbfs = liveDbfs,
                onRecord = { viewModel.enrollSubhamSentenceSample(sentence) }
            )
        }
    }
}

@Composable
fun SentenceEnrollmentCard(
    sentence: com.example.core.auth.EnrollmentSentence,
    isCompleted: Boolean,
    sample: SubhamEnrollmentSampleEntity?,
    isRecording: Boolean,
    recordingProgress: Float,
    liveDbfs: Float,
    onRecord: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = when {
                isRecording -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                isCompleted -> Color(0xFFF0FDF4)
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                when {
                    isRecording -> MaterialTheme.colorScheme.primary
                    isCompleted -> Color(0xFF10B981)
                    else -> MaterialTheme.colorScheme.outlineVariant
                },
                RoundedCornerShape(12.dp)
            )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isRecording -> MaterialTheme.colorScheme.error
                                    isCompleted -> Color(0xFF10B981)
                                    else -> MaterialTheme.colorScheme.secondary
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${sentence.index}",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = sentence.language.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (isCompleted && sample != null && !isRecording) {
                    Badge(containerColor = Color(0xFF10B981)) {
                        Text("Quality: ${(sample.qualityScore * 100).toInt()}%", modifier = Modifier.padding(horizontal = 4.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "\"${sentence.promptText}\"",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Hint: ${sentence.speakingSpeedHint} • Focus: ${sentence.phoneticFocus}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Recording live progress indicator
            AnimatedVisibility(visible = isRecording) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Recording real audio... Speak aloud!",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${liveDbfs.toInt()} dBFS",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { recordingProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = onRecord,
                    enabled = !isRecording,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when {
                            isRecording -> MaterialTheme.colorScheme.error
                            isCompleted -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.primary
                        }
                    ),
                    modifier = Modifier.testTag("record_subham_sample_${sentence.index}")
                ) {
                    Icon(
                        imageVector = if (isRecording) Icons.Default.GraphicEq else Icons.Default.Mic,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        when {
                            isRecording -> "Recording (3s)..."
                            isCompleted -> "Re-record (Speak aloud)"
                            else -> "Record Sample (Speak aloud)"
                        }
                    )
                }
            }
        }
    }
}
