package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.example.core.assistant.AssistantInteraction
import com.example.core.audio.SpeechRecognitionState
import com.example.ui.AninViewModel
import com.example.ui.components.VoiceWaveformVisualizer
import kotlinx.coroutines.launch

@Composable
fun AssistantLiveScreen(
    viewModel: AninViewModel,
    modifier: Modifier = Modifier
) {
    val interactions by viewModel.interactions.collectAsState()
    val audioMetrics by viewModel.audioMetrics.collectAsState()
    val audioCapabilities by viewModel.audioCapabilities.collectAsState()
    val activeProfile by viewModel.activeProfile.collectAsState()
    val isSpeaking by viewModel.audioPlaybackManager.isAssistantSpeaking.collectAsState()
    val isSubhamEnrolled by viewModel.isSubhamEnrolled.collectAsState()
    val speechStatus by viewModel.speechStatus.collectAsState()
    val isMicPermissionGranted by viewModel.isMicrophonePermissionGranted.collectAsState()

    var inputText by remember { mutableStateOf("") }
    var simulateAsSubham by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(interactions.size) {
        if (interactions.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(0)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(bottom = 8.dp)
    ) {
        // Top Card: Audio Hardware Status & Wake-Word Listening
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (audioMetrics.isCapturing) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                } else {
                    MaterialTheme.colorScheme.surface
                }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .border(
                    1.dp,
                    if (audioMetrics.isCapturing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    RoundedCornerShape(12.dp)
                )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isSpeaking -> MaterialTheme.colorScheme.error
                                        speechStatus.state == SpeechRecognitionState.LISTENING -> Color(0xFF3B82F6)
                                        audioMetrics.isCapturing -> Color(0xFF10B981)
                                        else -> Color.Gray
                                    }
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = when {
                                    isSpeaking -> "Anin Speaking (${activeProfile?.displayName ?: "Default"})"
                                    speechStatus.state == SpeechRecognitionState.LISTENING -> "Listening for Spoken Command..."
                                    speechStatus.state == SpeechRecognitionState.PROCESSING -> "Recognizing Speech..."
                                    audioMetrics.isCapturing -> "Mic Active: Listening for 'Hey Anin'"
                                    !isMicPermissionGranted -> "Mic Permission Denied"
                                    else -> "Anin Ready (Mic Paused)"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "AEC: ${if (audioCapabilities.hasAEC) "Active" else "N/A"} • dBFS: ${audioMetrics.dBFS.toInt()} dB • Subham: ${if (isSubhamEnrolled) "Enrolled" else "Not Enrolled"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = audioMetrics.isCapturing,
                            onCheckedChange = { viewModel.toggleAudioCapture(it) },
                            modifier = Modifier.testTag("mic_capture_switch")
                        )
                    }
                }

                // Speech Recognizer Live Banner
                AnimatedVisibility(visible = speechStatus.state == SpeechRecognitionState.LISTENING) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Listening... Speak in English, বাংলা, or हिन्दी!",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = { viewModel.stopListeningForVoiceCommand() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel", modifier = Modifier.size(16.dp))
                            }
                        }
                        if (!speechStatus.lastRecognizedText.isNullOrBlank()) {
                            Text(
                                text = "Heard: \"${speechStatus.lastRecognizedText}\"",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Barge-in Emergency Stop button
                if (isSpeaking) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.stopSpeaking() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("barge_in_stop_button")
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = "Barge-in Stop", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Subham Barge-In: STOP Speech Immediately")
                    }
                }
            }
        }

        // Live Audio Waveform
        VoiceWaveformVisualizer(
            isActive = isSpeaking || audioMetrics.isSpeechDetected || speechStatus.state == SpeechRecognitionState.LISTENING,
            modifier = Modifier.padding(vertical = 4.dp),
            waveColor = if (isSpeaking) MaterialTheme.colorScheme.primary else Color(0xFF10B981)
        )

        // Quick Command Chips (Unified with Real Actions)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AssistChip(
                onClick = { viewModel.sendAssistantMessage("open YouTube", simulateAsSubham) },
                label = { Text("Open YouTube", style = MaterialTheme.typography.labelSmall) },
                leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp)) }
            )
            AssistChip(
                onClick = { viewModel.sendAssistantMessage("Hey Anin, battery status", simulateAsSubham) },
                label = { Text("Battery", style = MaterialTheme.typography.labelSmall) },
                leadingIcon = { Icon(Icons.Default.BatteryFull, contentDescription = null, modifier = Modifier.size(14.dp)) }
            )
            AssistChip(
                onClick = { viewModel.sendAssistantMessage("আজকের আবহাওয়া কেমন?", simulateAsSubham) },
                label = { Text("বাংলা আবহাওয়া", style = MaterialTheme.typography.labelSmall) }
            )
            AssistChip(
                onClick = { viewModel.sendAssistantMessage("Hey Anin, who am I?", simulateAsSubham) },
                label = { Text("Who am I?", style = MaterialTheme.typography.labelSmall) }
            )
        }

        // Interaction Messages History
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (interactions.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isSubhamEnrolled) {
                                "Say 'Hey Anin' or tap the microphone to give a command."
                            } else {
                                "Anin is ready. Complete Subham Voice Enrollment to activate authenticated voice control."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            items(interactions) { item ->
                InteractionMessageCard(item)
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Input Field & Action Buttons (Text + Real Microphone Speech Recognition)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Push-to-Talk Real Microphone Button
            IconButton(
                onClick = {
                    if (speechStatus.state == SpeechRecognitionState.LISTENING) {
                        viewModel.stopListeningForVoiceCommand()
                    } else {
                        viewModel.startListeningForVoiceCommand()
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (speechStatus.state == SpeechRecognitionState.LISTENING) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        }
                    )
                    .testTag("push_to_talk_btn")
            ) {
                Icon(
                    imageVector = if (speechStatus.state == SpeechRecognitionState.LISTENING) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = "Speak Command",
                    tint = if (speechStatus.state == SpeechRecognitionState.LISTENING) Color.White else MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text("Or type command: 'open YouTube', etc.") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_input_field"),
                singleLine = true
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = {
                    if (inputText.isNotBlank()) {
                        viewModel.sendAssistantMessage(inputText, simulateAsSubham)
                        inputText = ""
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .testTag("send_command_btn")
            ) {
                Icon(
                    Icons.Default.Send,
                    contentDescription = "Send",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
fun InteractionMessageCard(item: AssistantInteraction) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // User Query Bubble
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp),
                modifier = Modifier.widthIn(max = 280.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = item.query,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Lang: ${item.detectedLanguage.displayName} • Subham: ${if (item.isSubhamAuthorized) "Authorized" else "Unverified"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Assistant Response Bubble
        if (!item.isSilentlyIgnored) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
                    modifier = Modifier.widthIn(max = 300.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.SmartToy,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Anin",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = item.response,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        } else {
            // Fail-closed silent rejection log indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.VolumeOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Fail-Closed: Anin Remained Silent",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "Unauthorized speaker. Zero speech output & zero command execution.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }
    }
}
