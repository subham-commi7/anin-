package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import com.example.core.database.OutputVoiceProfileEntity
import com.example.core.model.VoiceLanguage
import com.example.core.model.VoiceSourceType
import com.example.ui.AninViewModel
import com.example.ui.components.VoiceWaveformVisualizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun OutputVoiceStudioScreen(
    viewModel: AninViewModel,
    modifier: Modifier = Modifier
) {
    val activeProfile by viewModel.activeProfile.collectAsState()
    val allProfiles by viewModel.allProfiles.collectAsState()
    val isSpeaking by viewModel.audioPlaybackManager.isAssistantSpeaking.collectAsState()
    val latestResult by viewModel.outputVoiceManager.latestSynthesisResult.collectAsState()
    val isFallbackActive by viewModel.outputVoiceManager.isFallbackActive.collectAsState()

    var showEnrollmentWizard by remember { mutableStateOf(false) }
    var customTestText by remember { mutableStateOf("Hello Subham, I am Anin. Your custom output voice is active.") }
    var selectedLanguage by remember { mutableStateOf(VoiceLanguage.ENGLISH) }

    if (showEnrollmentWizard) {
        VoiceEnrollmentWizard(
            viewModel = viewModel,
            onDismiss = { showEnrollmentWizard = false }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Architecture Separation Warning Banner
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "PROFILE B — OUTPUT VOICE SYSTEM",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = "Controls the voice Anin uses to speak responses. Strictly separated from Subham's authentication voice.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        // Active Output Voice Hero Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Active Output Voice",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = activeProfile?.displayName ?: "Anin Crystal (Built-in)",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        AssistChip(
                            onClick = {},
                            label = {
                                Text(
                                    if (activeProfile?.sourceType == VoiceSourceType.BUILT_IN) "Built-in" else "Custom Profile"
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    if (activeProfile?.sourceType == VoiceSourceType.BUILT_IN) Icons.Default.VolumeUp else Icons.Default.Tune,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Fallback Status Banner if applicable
                    if (isFallbackActive) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Fallback voice active (Primary voice temporarily unavailable).",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }

                    // Tech metadata chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = "Languages: EN, বাংলা (BN), हिन्दी (HI)",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = "Engine: Local Neural",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Waveform Visualizer
                    VoiceWaveformVisualizer(
                        isActive = isSpeaking,
                        waveColor = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Play / Stop Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                activeProfile?.let { prof ->
                                    viewModel.previewVoice(prof, selectedLanguage)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("preview_active_voice_btn"),
                            enabled = !isSpeaking
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Preview Active Voice")
                        }

                        if (isSpeaking) {
                            Button(
                                onClick = { viewModel.stopSpeaking() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.testTag("stop_speech_btn")
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Stop")
                            }
                        }
                    }

                    latestResult?.let { res ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Latest: Latency ${res.latencyMs}ms • Engine: ${if (res.isLocal) "On-Device" else "Remote"} • Lang: ${res.language.displayName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }

        // Action: Create New Custom Voice Profile
        item {
            Button(
                onClick = {
                    viewModel.startNewProfileEnrollment()
                    showEnrollmentWizard = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("create_output_voice_btn")
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Enroll Custom Output Voice Profile")
            }
        }

        // Live Voice Synthesis Test Sandbox
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Voice Synthesis Playground",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Test pronunciation and acoustic style across supported languages:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        VoiceLanguage.entries.forEach { lang ->
                            FilterChip(
                                selected = selectedLanguage == lang,
                                onClick = {
                                    selectedLanguage = lang
                                    customTestText = lang.testSentence
                                },
                                label = { Text(lang.displayName) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = customTestText,
                        onValueChange = { customTestText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Speech Text") },
                        maxLines = 3
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(
                            onClick = {
                                viewModel.speakCustomText(customTestText, selectedLanguage)
                            },
                            enabled = !isSpeaking
                        ) {
                            Icon(Icons.Default.RecordVoiceOver, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Synthesize & Speak")
                        }
                    }
                }
            }
        }

        // Profiles Library
        item {
            Text(
                text = "Voice Profiles Library (${allProfiles.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        items(allProfiles, key = { it.id }) { profile ->
            VoiceProfileCard(
                profile = profile,
                isActive = profile.isActive,
                onSelect = { viewModel.selectOutputVoice(profile.id) },
                onPreview = { lang -> viewModel.previewVoice(profile, lang) },
                onDelete = { viewModel.deleteProfile(profile.id) }
            )
        }
    }
}

@Composable
fun VoiceProfileCard(
    profile: OutputVoiceProfileEntity,
    isActive: Boolean,
    onSelect: () -> Unit,
    onPreview: (VoiceLanguage) -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isActive) 2.dp else 1.dp,
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = profile.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (isActive) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                Text("ACTIVE", modifier = Modifier.padding(horizontal = 4.dp))
                            }
                        }
                    }
                    Text(
                        text = "Source: ${profile.sourceType.name} • Quality: ${(profile.qualityScore * 100).toInt()}% • Pitch x${"%.2f".format(profile.pitchMultiplier)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Expand"
                    )
                }
            }

            // 10-day retention notice for custom recorded profiles
            if (profile.rawSamplePath != null && profile.rawSampleExpiresAt < Long.MAX_VALUE) {
                val remainingMs = profile.rawSampleExpiresAt - System.currentTimeMillis()
                val remainingDays = (remainingMs / (1000 * 60 * 60 * 24)).coerceAtLeast(0)
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Raw audio retention: $remainingDays days remaining until auto-purge",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Text(
                        text = "Test Pronunciation:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onPreview(VoiceLanguage.ENGLISH) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("English", style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = { onPreview(VoiceLanguage.BENGALI) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("বাংলা", style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = { onPreview(VoiceLanguage.HINDI) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("हिन्दी", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (profile.sourceType != VoiceSourceType.BUILT_IN) {
                            TextButton(
                                onClick = onDelete,
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Delete Profile")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        if (!isActive) {
                            FilledTonalButton(onClick = onSelect) {
                                Text("Set as Active")
                            }
                        }
                    }
                }
            }
        }
    }
}
