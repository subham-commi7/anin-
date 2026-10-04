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
import com.example.ui.AninViewModel
import com.example.ui.components.VoiceWaveformVisualizer

@Composable
fun AssistantLiveScreen(
    viewModel: AninViewModel,
    modifier: Modifier = Modifier
) {
    val interactions by viewModel.interactions.collectAsState()
    val isSpeaking by viewModel.audioPlaybackManager.isAssistantSpeaking.collectAsState()
    val activeProfile by viewModel.activeProfile.collectAsState()
    val isSubhamEnrolled by viewModel.isSubhamEnrolled.collectAsState()
    val audioMetrics by viewModel.audioMetrics.collectAsState()
    val audioCapabilities by viewModel.audioCapabilities.collectAsState()

    var inputText by remember { mutableStateOf("") }
    var simulateAsSubham by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()

    LaunchedEffect(interactions.size) {
        if (interactions.isNotEmpty()) {
            listState.animateScrollToItem(interactions.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Microphone & Wake-Word Pipeline Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
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
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isSpeaking -> MaterialTheme.colorScheme.primary
                                        audioMetrics.isCapturing -> Color(0xFF10B981)
                                        else -> Color.Gray
                                    }
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = when {
                                    isSpeaking -> "Anin Speaking (${activeProfile?.displayName})"
                                    audioMetrics.isCapturing -> "Mic Active: Listening for 'Hey Anin'"
                                    else -> "Anin Ready (Mic Paused)"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "AEC: ${if (audioCapabilities.hasAEC) "Active" else "N/A"} • NS: ${if (audioCapabilities.hasNoiseSuppressor) "Active" else "N/A"} • dBFS: ${audioMetrics.dBFS.toInt()} dB",
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
            isActive = isSpeaking || audioMetrics.isSpeechDetected,
            modifier = Modifier.padding(vertical = 4.dp),
            waveColor = if (isSpeaking) MaterialTheme.colorScheme.primary else Color(0xFF10B981)
        )

        // Quick Command Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AssistChip(
                onClick = { viewModel.sendAssistantMessage("Hey Anin, battery status", simulateAsSubham) },
                label = { Text("Battery", style = MaterialTheme.typography.labelSmall) }
            )
            AssistChip(
                onClick = { viewModel.sendAssistantMessage("Hey Anin, who am I?", simulateAsSubham) },
                label = { Text("Who am I?", style = MaterialTheme.typography.labelSmall) }
            )
            AssistChip(
                onClick = { viewModel.sendAssistantMessage("আজকের আবহাওয়া ও সময় কত?", simulateAsSubham) },
                label = { Text("বাংলা সময়", style = MaterialTheme.typography.labelSmall) }
            )
            AssistChip(
                onClick = { viewModel.sendAssistantMessage("अनিন, यूट्यूब खोलो", simulateAsSubham) },
                label = { Text("यूट्यूब", style = MaterialTheme.typography.labelSmall) }
            )
            AssistChip(
                onClick = { viewModel.sendAssistantMessage("Hey Anin, remind me to call Shubhrata", simulateAsSubham) },
                label = { Text("Reminder", style = MaterialTheme.typography.labelSmall) }
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
                            .padding(top = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Hearing,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Say \"Hey Anin\" or enter a command below",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Text(
                                text = "Commands are executed ONLY when Subham is verified.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }

            items(interactions) { item ->
                InteractionMessageCard(item)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Speaker Verification Security Simulation Switch
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (simulateAsSubham) Icons.Default.VerifiedUser else Icons.Default.PersonOff,
                    contentDescription = null,
                    tint = if (simulateAsSubham) Color(0xFF10B981) else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = if (simulateAsSubham) "Current Speaker: Subham (Authorized)" else "Current Speaker: Stranger (Unauthorized)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (simulateAsSubham) Color(0xFF10B981) else MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = if (simulateAsSubham) "Verified Subham voice signature" else "Rule: Fails closed and remains 100% silent",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Switch(
                checked = simulateAsSubham,
                onCheckedChange = { simulateAsSubham = it },
                modifier = Modifier.testTag("speaker_auth_toggle")
            )
        }

        // Input Field & Send Action
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text("Command in English, বাংলা, हिन्दी...") },
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
            Surface(
                shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.widthIn(max = 300.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (item.isSubhamAuthorized) Icons.Default.Verified else Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = if (item.isSubhamAuthorized) Color(0xFF10B981) else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (item.isSubhamAuthorized) "Subham" else "Unknown / Unauthorized Speaker",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (item.isSubhamAuthorized) Color(0xFF10B981) else MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = item.query, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Assistant Response Bubble or Silent Rejection Notice
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
                color = if (item.isSilentlyIgnored) Color(0xFFFEF2F2) else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.widthIn(max = 300.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (item.isSilentlyIgnored) Icons.Default.VolumeMute else Icons.Default.RecordVoiceOver,
                            contentDescription = null,
                            tint = if (item.isSilentlyIgnored) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (item.isSilentlyIgnored) "Anin: Completely Silent" else "Anin • ${item.detectedLanguage.displayName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (item.isSilentlyIgnored) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (item.isSilentlyIgnored)
                            "• External Audio: 100% Silent (No speech generated)\n• Reason: ${item.diagnosticReason}\n• Log: Non-sensitive AUTHENTICATION_FAILED recorded."
                        else item.response,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (item.isSilentlyIgnored) Color(0xFF991B1B) else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
