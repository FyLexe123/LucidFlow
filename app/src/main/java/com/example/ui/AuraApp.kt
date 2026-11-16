package com.example.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.auth.attemptAutoSignIn
import com.example.auth.authStateFlow
import com.example.auth.onGoogleSignInClicked
import com.example.auth.signOutUser
import com.example.data.local.UserPreferencesStore
import com.example.ui.components.LiquidGlassBackdrop
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.stringsFor
import com.example.ui.screens.AnalyticsScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.MapsScreen
import com.example.ui.screens.SettingsBottomSheet
import com.example.ui.screens.SignInScreen
import com.example.ui.screens.StudioScreen
import com.example.ui.screens.VaultScreen
import com.example.ui.theme.AuraGlassTheme
import com.example.ui.theme.LocalLiquidGlass
import com.example.ui.viewmodel.AuraViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
sealed interface AuraRoute {
    @Serializable data object Vault : AuraRoute
    @Serializable data object Chat : AuraRoute
    @Serializable data object Studio : AuraRoute
    @Serializable data object Maps : AuraRoute
    @Serializable data object Analytics : AuraRoute
}

@Composable
fun AuraApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val prefsStore = remember(context) { UserPreferencesStore(context) }
    val localPrefs by prefsStore.preferences.collectAsStateWithLifecycle(
        initialValue = com.example.data.local.LocalAppPreferences()
    )

    val language = AppLanguage.fromCode(localPrefs.languageCode)
    val strings = remember(language) { stringsFor(language) }

    val authUser by remember { Firebase.auth.authStateFlow() }
        .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

    var isSigningIn by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (Firebase.auth.currentUser == null) {
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = { authError = null },
                onUnauthenticated = {},
                scope = scope
            )
        }
    }

    AuraGlassTheme(darkModeSetting = localPrefs.darkMode) {
        val user = authUser
        if (user == null) {
            SignInScreen(
                strings = strings,
                currentLanguage = language,
                currentDarkMode = localPrefs.darkMode,
                isSigningIn = isSigningIn,
                errorMessage = authError,
                onSelectLanguage = { lang ->
                    scope.launch { prefsStore.setLanguage(lang.code) }
                },
                onToggleTheme = {
                    val next = if (localPrefs.darkMode == "dark") "light" else "dark"
                    scope.launch { prefsStore.setDarkMode(next) }
                },
                onSignInClicked = {
                    isSigningIn = true
                    authError = null
                    onGoogleSignInClicked(
                        context = context,
                        credentialManager = credentialManager,
                        onAuthSuccess = {
                            isSigningIn = false
                            authError = null
                        },
                        onAuthError = { err ->
                            isSigningIn = false
                            authError = err
                        },
                        onAuthCancelled = {
                            isSigningIn = false
                        },
                        scope = scope
                    )
                }
            )
        } else {
            key(user.uid) {
                val auraViewModel: AuraViewModel = viewModel(
                    key = user.uid,
                    factory = AuraViewModel.provideFactory(
                        currentUserId = user.uid,
                        initialDisplayName = user.displayName ?: user.email ?: "Aura User"
                    )
                )
                AuthenticatedWorkspace(
                    viewModel = auraViewModel,
                    userDisplayName = user.displayName ?: "Aura User",
                    userEmail = user.email ?: "",
                    onSignOut = {
                        signOutUser(
                            credentialManager = credentialManager,
                            scope = scope
                        )
                    }
                )
            }
        }
    }
}

private data class NavDestinationItem(
    val route: AuraRoute,
    val label: String,
    val icon: ImageVector,
    val testTag: String
)

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
private fun AuthenticatedWorkspace(
    viewModel: AuraViewModel,
    userDisplayName: String,
    userEmail: String,
    onSignOut: () -> Unit
) {
    val prefs by viewModel.localPrefs.collectAsStateWithLifecycle()
    val language = AppLanguage.fromCode(prefs.languageCode)
    val strings = remember(language) { stringsFor(language) }

    val isEffectiveOffline by viewModel.isEffectiveOffline.collectAsStateWithLifecycle()
    val vaultItems by viewModel.vaultItems.collectAsStateWithLifecycle()
    val offlineCached by viewModel.offlineCachedVault.collectAsStateWithLifecycle()
    val decryptedMap by viewModel.decryptedMap.collectAsStateWithLifecycle()
    val keyFingerprint by viewModel.keyFingerprint.collectAsStateWithLifecycle()

    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val streamState by viewModel.chatStreamState.collectAsStateWithLifecycle()
    val selectedModel by viewModel.selectedModel.collectAsStateWithLifecycle()
    val searchGrounding by viewModel.searchGrounding.collectAsStateWithLifecycle()
    val mapsGrounding by viewModel.mapsGrounding.collectAsStateWithLifecycle()

    val studioState by viewModel.studioState.collectAsStateWithLifecycle()
    val isRecordingAudio by viewModel.isRecordingAudio.collectAsStateWithLifecycle()
    val isLiveCallActive by viewModel.isLiveCallActive.collectAsStateWithLifecycle()

    val mapsState by viewModel.mapsState.collectAsStateWithLifecycle()
    val analyticsEvents by viewModel.analyticsEvents.collectAsStateWithLifecycle()
    val analyticsReportState by viewModel.analyticsReportState.collectAsStateWithLifecycle()
    val bannerMessage by viewModel.bannerMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(bannerMessage) {
        val msg = bannerMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearBanner()
        }
    }

    var showSettingsSheet by rememberSaveable { mutableStateOf(false) }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val destinations = remember(strings) {
        listOf(
            NavDestinationItem(AuraRoute.Vault, strings.navHome, Icons.Rounded.Lock, "nav_vault"),
            NavDestinationItem(AuraRoute.Chat, strings.navChat, Icons.Rounded.ChatBubble, "nav_chat"),
            NavDestinationItem(AuraRoute.Studio, strings.navStudio, Icons.Rounded.AutoAwesome, "nav_studio"),
            NavDestinationItem(AuraRoute.Maps, strings.navMaps, Icons.Rounded.Map, "nav_maps"),
            NavDestinationItem(AuraRoute.Analytics, strings.navAnalytics, Icons.Rounded.Analytics, "nav_analytics")
        )
    }

    fun navigateTo(route: AuraRoute) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val glass = LocalLiquidGlass.current

    LiquidGlassBackdrop {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavigationRail = maxWidth >= 600.dp

            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    if (!useNavigationRail) {
                        NavigationBar(
                            containerColor = glass.glassFillTop,
                            windowInsets = WindowInsets.navigationBars
                        ) {
                            destinations.forEach { dest ->
                                val selected = currentDestination?.hasRoute(dest.route::class) == true
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateTo(dest.route) },
                                    icon = { Icon(dest.icon, contentDescription = dest.label) },
                                    label = { Text(dest.label, maxLines = 1) },
                                    modifier = Modifier.testTag(dest.testTag)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useNavigationRail) {
                        NavigationRail(
                            containerColor = glass.glassFillTop,
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            Spacer(Modifier.weight(0.2f))
                            destinations.forEach { dest ->
                                val selected = currentDestination?.hasRoute(dest.route::class) == true
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { navigateTo(dest.route) },
                                    icon = { Icon(dest.icon, contentDescription = dest.label) },
                                    label = { Text(dest.label, maxLines = 1) },
                                    modifier = Modifier.testTag(dest.testTag)
                                )
                            }
                            Spacer(Modifier.weight(1f))
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        NavHost(
                            navController = navController,
                            startDestination = AuraRoute.Vault,
                            modifier = Modifier
                                .fillMaxSize()
                                .widthIn(max = 840.dp)
                        ) {
                            composable<AuraRoute.Vault> {
                                VaultScreen(
                                    strings = strings,
                                    vaultItems = vaultItems,
                                    offlineCached = offlineCached,
                                    decryptedMap = decryptedMap,
                                    isOffline = isEffectiveOffline,
                                    keyFingerprint = keyFingerprint,
                                    onToggleOffline = viewModel::setOfflineModeManual,
                                    onSyncPending = viewModel::syncPendingOfflineItems,
                                    onCreateVaultItem = viewModel::createEncryptedVaultItem,
                                    onToggleDecrypt = viewModel::toggleDecryptVaultItem,
                                    onTogglePin = viewModel::togglePinItem,
                                    onDeleteItem = viewModel::deleteVaultItem,
                                    onApplyPassphrase = viewModel::applyCustomPassphrase,
                                    onOpenSettings = { showSettingsSheet = true }
                                )
                            }
                            composable<AuraRoute.Chat> {
                                ChatScreen(
                                    strings = strings,
                                    messages = chatMessages,
                                    streamState = streamState,
                                    selectedTier = selectedModel,
                                    searchGrounding = searchGrounding,
                                    mapsGrounding = mapsGrounding,
                                    onSelectTier = viewModel::selectModel,
                                    onToggleSearch = viewModel::toggleSearchGrounding,
                                    onToggleMaps = viewModel::toggleMapsGrounding,
                                    onSendMessage = viewModel::sendChatMessage,
                                    onClearChat = viewModel::clearChatHistory
                                )
                            }
                            composable<AuraRoute.Studio> {
                                StudioScreen(
                                    strings = strings,
                                    studioState = studioState,
                                    isRecordingAudio = isRecordingAudio,
                                    isLiveCallActive = isLiveCallActive,
                                    onGenerateOrEditImage = viewModel::generateOrEditImage,
                                    onSynthesizeSpeech = viewModel::synthesizeSpeech,
                                    onGenerateMusic = viewModel::generateMusic,
                                    onGenerateVideo = viewModel::generateVideo,
                                    onAnalyzeImage = viewModel::analyzeSelectedImage,
                                    onAnalyzeVideo = viewModel::analyzeSelectedVideo,
                                    onToggleRecordAudio = viewModel::toggleAudioRecordingForTranscription,
                                    onToggleLiveCall = viewModel::toggleLiveVoiceCall,
                                    onSaveTextToVault = { title, text ->
                                        viewModel.createEncryptedVaultItem(title, text, "note")
                                    }
                                )
                            }
                            composable<AuraRoute.Maps> {
                                MapsScreen(
                                    strings = strings,
                                    mapsState = mapsState,
                                    onUpdateCoordinates = viewModel::updateUserCoordinates,
                                    onSearchPlaces = viewModel::searchMapsWithGrounding,
                                    onSavePlaceEncrypted = { placeTitle, uri ->
                                        viewModel.createEncryptedVaultItem(placeTitle, uri, "place")
                                    }
                                )
                            }
                            composable<AuraRoute.Analytics> {
                                AnalyticsScreen(
                                    strings = strings,
                                    events = analyticsEvents,
                                    vaultItems = vaultItems,
                                    offlineCached = offlineCached,
                                    reportState = analyticsReportState,
                                    onGenerateAiReport = viewModel::generateAiAnalyticsReport,
                                    onSaveReportToVault = { title, report ->
                                        viewModel.createEncryptedVaultItem(title, report, "report")
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (showSettingsSheet) {
                SettingsBottomSheet(
                    strings = strings,
                    prefs = prefs,
                    userDisplayName = userDisplayName,
                    userEmail = userEmail,
                    keyFingerprint = keyFingerprint,
                    onDismiss = { showSettingsSheet = false },
                    onSelectLanguage = viewModel::setLanguage,
                    onSelectDarkMode = viewModel::setDarkMode,
                    onToggleOfflineManual = viewModel::setOfflineModeManual,
                    onUpdateNotifications = viewModel::updateNotifications,
                    onSendTestNotification = viewModel::sendTestNotification,
                    onSignOut = {
                        showSettingsSheet = false
                        onSignOut()
                    }
                )
            }
        }
    }
}
