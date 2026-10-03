package com.example.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.screens.AssistantLiveScreen
import com.example.ui.screens.AuthenticationVoiceScreen
import com.example.ui.screens.OutputVoiceStudioScreen
import com.example.ui.screens.PersonalMemoryScreen
import com.example.ui.screens.PrivacySettingsScreen
import com.example.ui.screens.SubhamEnrollmentScreen

enum class AppNavDestination(
    val title: String,
    val icon: ImageVector,
    val testTag: String
) {
    ASSISTANT_LIVE("Anin Live", Icons.Default.Chat, "nav_assistant_live"),
    SUBHAM_ENROLL("Enroll Subham", Icons.Default.Fingerprint, "nav_subham_enroll"),
    AUTH_TEST("Security Lab", Icons.Default.Security, "nav_auth_voice"),
    OUTPUT_VOICE("Voice Studio", Icons.Default.RecordVoiceOver, "nav_output_voice"),
    MEMORY("Memory", Icons.Default.Psychology, "nav_personal_memory"),
    DIAGNOSTICS("Diagnostics", Icons.Default.Shield, "nav_diagnostics")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainVoiceApp(
    viewModel: AninViewModel,
    modifier: Modifier = Modifier
) {
    var currentDestination by remember { mutableStateOf(AppNavDestination.ASSISTANT_LIVE) }
    val statusMessage by viewModel.statusMessage.collectAsState()
    val isSubhamEnrolled by viewModel.isSubhamEnrolled.collectAsState()
    val activeProfile by viewModel.activeProfile.collectAsState()
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
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Anin Voice Assistant",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    AssistChip(
                        onClick = { currentDestination = AppNavDestination.SUBHAM_ENROLL },
                        label = {
                            Text(if (isSubhamEnrolled) "Subham: Enrolled" else "Subham: Enroll")
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (isSubhamEnrolled) Icons.Default.Verified else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (isSubhamEnrolled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.testTag("main_bottom_nav")
            ) {
                AppNavDestination.entries.forEach { destination ->
                    val selected = currentDestination == destination
                    NavigationBarItem(
                        selected = selected,
                        onClick = { currentDestination = destination },
                        icon = {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = destination.title
                            )
                        },
                        label = { Text(destination.title, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.testTag(destination.testTag)
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentDestination) {
                AppNavDestination.ASSISTANT_LIVE -> {
                    AssistantLiveScreen(viewModel = viewModel)
                }
                AppNavDestination.SUBHAM_ENROLL -> {
                    SubhamEnrollmentScreen(viewModel = viewModel)
                }
                AppNavDestination.AUTH_TEST -> {
                    AuthenticationVoiceScreen(viewModel = viewModel)
                }
                AppNavDestination.OUTPUT_VOICE -> {
                    OutputVoiceStudioScreen(viewModel = viewModel)
                }
                AppNavDestination.MEMORY -> {
                    PersonalMemoryScreen(viewModel = viewModel)
                }
                AppNavDestination.DIAGNOSTICS -> {
                    PrivacySettingsScreen(viewModel = viewModel)
                }
            }
        }
    }
}
