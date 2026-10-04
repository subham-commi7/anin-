package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.screens.*

enum class AppNavDestination(
    val route: String,
    val title: String,
    val icon: ImageVector,
    val testTag: String
) {
    LIVE("live", "Live", Icons.Default.Mic, "nav_live"),
    AUTH("auth", "Security", Icons.Default.Security, "nav_auth"),
    ENROLLMENT("enrollment", "Enroll", Icons.Default.Fingerprint, "nav_enroll"),
    VOICE_STUDIO("studio", "Studio", Icons.Default.RecordVoiceOver, "nav_studio"),
    MEMORY("memory", "Memory", Icons.Default.Psychology, "nav_memory"),
    PERMISSIONS("permissions", "Permissions", Icons.Default.Key, "nav_permissions"),
    SETTINGS("settings", "Diagnostics", Icons.Default.Settings, "nav_settings")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainVoiceApp(
    viewModel: AninViewModel,
    modifier: Modifier = Modifier
) {
    var currentDestination by remember { mutableStateOf(AppNavDestination.LIVE) }
    val statusMessage by viewModel.statusMessage.collectAsState()
    val isSubhamEnrolled by viewModel.isSubhamEnrolled.collectAsState()
    val isSpeaking by viewModel.audioPlaybackManager.isAssistantSpeaking.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusMessage) {
        statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(
                message = msg,
                duration = SnackbarDuration.Short
            )
            viewModel.clearStatusMessage()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Anin",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Personal Private Voice Assistant • Step 1+2+3",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    if (isSpeaking) {
                        FilledTonalButton(
                            onClick = { viewModel.audioPlaybackManager.bargeInEmergencyStop() },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .testTag("top_bar_stop_button")
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = "Emergency Stop", modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Stop", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    AssistChip(
                        onClick = { currentDestination = AppNavDestination.ENROLLMENT },
                        label = {
                            Text(
                                if (isSubhamEnrolled) "Subham: Enrolled" else "Subham: Enroll",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        leadingIcon = {
                            Icon(
                                if (isSubhamEnrolled) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (isSubhamEnrolled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("auth_status_chip")
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.testTag("bottom_navigation_bar")
            ) {
                AppNavDestination.values().forEach { destination ->
                    val selected = currentDestination == destination
                    NavigationBarItem(
                        selected = selected,
                        onClick = { currentDestination = destination },
                        icon = {
                            Icon(
                                destination.icon,
                                contentDescription = destination.title,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        label = {
                            Text(
                                destination.title,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        modifier = Modifier.testTag(destination.testTag)
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (currentDestination) {
                AppNavDestination.LIVE -> AssistantLiveScreen(viewModel)
                AppNavDestination.AUTH -> AuthenticationVoiceScreen(viewModel)
                AppNavDestination.ENROLLMENT -> SubhamEnrollmentScreen(viewModel)
                AppNavDestination.VOICE_STUDIO -> OutputVoiceStudioScreen(viewModel)
                AppNavDestination.MEMORY -> PersonalMemoryScreen(viewModel)
                AppNavDestination.PERMISSIONS -> PrivacySettingsScreen(viewModel)
                AppNavDestination.SETTINGS -> PrivacySettingsScreen(viewModel)
            }
        }
    }
}
