package org.feeluown.mobile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingFeatureScreen(
    onboarding: OnboardingFeatureController,
    settings: SettingsFeatureController,
    providerCatalog: ProviderCatalogFeatureController,
    providerAuth: ProviderAuthFeatureController,
    onOpenProviderWebLogin: (ProviderInfo) -> Unit,
    onLogoutProvider: (ProviderInfo) -> Unit,
    onImportYtmusicHeaderFile: (() -> Unit)? = null,
    onImportYtmusicOAuthFile: (() -> Unit)? = null,
    onStartYtmusicOAuth: (() -> Unit)? = null,
) {
    val onboardingState by onboarding.uiState.collectAsStateWithLifecycle()
    val settingsState by settings.uiState.collectAsStateWithLifecycle()
    val catalogState by providerCatalog.uiState.collectAsStateWithLifecycle()
    val authState by providerAuth.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    LaunchedEffect(catalogState.availableProviders) {
        onboarding.initialize(catalogState)
    }
    val availableProviders = remember(catalogState.availableProviders, catalogState.providerOrderIds) {
        val order = catalogState.providerOrderIds.withIndex().associate { it.value to it.index }
        catalogState.availableProviders.sortedBy { order[it.providerId] ?: Int.MAX_VALUE }
    }
    val selectedProviders = remember(availableProviders, onboardingState.selectedProviderIds) {
        availableProviders.filter { it.providerId in onboardingState.selectedProviderIds }
    }
    val pageCount = selectedProviders.size + 3
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val sourcePage = pagerState.currentPage == 0
    val themePage = pagerState.currentPage == pageCount - 2
    val qualityPage = pagerState.currentPage == pageCount - 1
    val busy = onboardingState.isBusy || catalogState.isLoading

    LaunchedEffect(pageCount) {
        if (pagerState.currentPage >= pageCount) {
            pagerState.scrollToPage((pageCount - 1).coerceAtLeast(0))
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Ersteinrichtung") },
                navigationIcon = {
                    if (!sourcePage) {
                        IconButton(
                            enabled = !busy,
                            onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                        }
                    }
                },
            )
        },
        bottomBar = {
            OnboardingFeatureFooter(
                currentPage = pagerState.currentPage,
                pageCount = pageCount,
                isBusy = busy,
                actionLabel = when {
                    sourcePage || themePage -> "Weiter"
                    qualityPage -> "Fertig"
                    else -> {
                        val provider = selectedProviders.getOrNull(pagerState.currentPage - 1)
                        if (provider != null && providerAuth.authStateFor(provider).isLoggedIn) "Weiter" else "Überspringen"
                    }
                },
                onAction = {
                    when {
                        sourcePage -> onboarding.applyProviderSelection { success ->
                            if (success) scope.launch { pagerState.animateScrollToPage(1) }
                        }
                        qualityPage -> onboarding.complete()
                        else -> scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                },
            )
        },
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().padding(padding),
            userScrollEnabled = !busy && !sourcePage,
            verticalAlignment = Alignment.Top,
        ) { page ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                when {
                    page == 0 -> OnboardingProviderSelectionPage(
                        providers = availableProviders,
                        state = onboardingState,
                        enabled = !busy,
                        onProviderSelected = onboarding::setProviderSelected,
                        onReplacementOnlyChange = onboarding::setBilibiliReplacementOnly,
                    )
                    page == pageCount - 2 -> OnboardingThemePage(
                        settingsState = settingsState,
                        settingsController = settings,
                    )
                    page == pageCount - 1 -> OnboardingQualityPage(
                        settingsState = settingsState,
                        settingsController = settings,
                    )
                    else -> selectedProviders.getOrNull(page - 1)?.let { provider ->
                        OnboardingProviderLoginPage(
                            provider = provider,
                            authController = providerAuth,
                            authState = authState,
                            onOpenProviderWebLogin = onOpenProviderWebLogin,
                            onLogoutProvider = onLogoutProvider,
                            onImportYtmusicHeaderFile = onImportYtmusicHeaderFile,
                            onImportYtmusicOAuthFile = onImportYtmusicOAuthFile,
                            onStartYtmusicOAuth = onStartYtmusicOAuth,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingProviderSelectionPage(
    providers: List<ProviderInfo>,
    state: OnboardingUiState,
    enabled: Boolean,
    onProviderSelected: (String, Boolean) -> Unit,
    onReplacementOnlyChange: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(Icons.Filled.MusicNote, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Text("Musikquellen auswählen", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Wähle mindestens eine Musikquelle. Du kannst dich anschließend einzeln anmelden und die Auswahl später in den Einstellungen ändern.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (providers.isEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Text("Musikquelle wird initialisiert")
            }
        } else {
            providers.forEach { provider ->
                val selected = provider.providerId in state.selectedProviderIds
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Checkbox) {
                        onProviderSelected(provider.providerId, !selected)
                    },
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = selected,
                            enabled = enabled,
                            onCheckedChange = { onProviderSelected(provider.providerId, it) },
                        )
                        Column(Modifier.weight(1f)) {
                            Text(provider.providerName, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (selected) "Diese Musikquelle wird aktiviert" else "Diese Musikquelle wird nicht geladen",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (selected) Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        if ("bilibili" in state.selectedProviderIds) {
            Surface(
                modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Checkbox) {
                    onReplacementOnlyChange(!state.bilibiliReplacementOnly)
                },
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(16.dp),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = state.bilibiliReplacementOnly,
                        enabled = enabled,
                        onCheckedChange = onReplacementOnlyChange,
                    )
                    Column(Modifier.weight(1f)) {
                        Text("Bilibili nur als Ersatzquelle verwenden", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Nicht in Suche und Startseite anzeigen; nur für intelligenten Ersatz verwenden, wenn die ursprüngliche Quelle nicht verfügbar ist.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
        state.feedback?.let { feedback ->
            Text(
                feedback,
                color = if (feedback.contains("Fehlgeschlagen") || feedback.startsWith("Bitte mindestens") || feedback.startsWith("Bilibili")) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun OnboardingThemePage(
    settingsState: SettingsFeatureUiState,
    settingsController: SettingsFeatureController,
) {
    val appSettings = settingsState.settings
    Column(
        modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("App-Design auswählen", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Kann später in den Einstellungen geändert werden.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Darstellung", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { mode ->
                FilterChip(
                    selected = appSettings.themeMode == mode,
                    onClick = { settingsController.update { it.copy(themeMode = mode) } },
                    label = { Text(mode.label) },
                )
            }
        }
        Text("Farbschema", style = MaterialTheme.typography.titleMedium)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeColorScheme.entries.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { scheme ->
                        FilterChip(
                            selected = appSettings.themeColorScheme == scheme,
                            onClick = { settingsController.update { it.copy(themeColorScheme = scheme) } },
                            label = { Text(scheme.label) },
                        )
                    }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Dynamische Farben aus dem Cover")
                Text("Player-Farben aus dem aktuell abgespielten Cover erzeugen", style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = appSettings.dynamicCoverColorEnabled,
                onCheckedChange = { enabled -> settingsController.update { it.copy(dynamicCoverColorEnabled = enabled) } },
            )
        }
    }
}

@Composable
private fun OnboardingQualityPage(
    settingsState: SettingsFeatureUiState,
    settingsController: SettingsFeatureController,
) {
    val appSettings = settingsState.settings
    Column(
        modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Standard-Audioqualität auswählen", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Die Audioqualität kann für WLAN und Mobilfunk getrennt eingestellt werden.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Wi‑Fi", style = MaterialTheme.typography.titleMedium)
        OnboardingQualityChoices(
            selected = appSettings.wifiAudioQualityPolicy,
            onSelect = settingsController::setWifiAudioQualityPolicy,
        )
        Text("Mobilfunk", style = MaterialTheme.typography.titleMedium)
        OnboardingQualityChoices(
            selected = appSettings.cellularAudioQualityPolicy,
            onSelect = settingsController::setCellularAudioQualityPolicy,
        )
    }
}

@Composable
private fun OnboardingQualityChoices(
    selected: AudioQualityPolicy,
    onSelect: (AudioQualityPolicy) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AudioQualityPolicy.entries.forEach { policy ->
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { onSelect(policy) },
                color = if (selected == policy) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(12.dp),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(policy.label, modifier = Modifier.weight(1f))
                    if (selected == policy) Icon(Icons.Filled.CheckCircle, contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun OnboardingProviderLoginPage(
    provider: ProviderInfo,
    authController: ProviderAuthFeatureController,
    authState: ProviderAuthUiState,
    onOpenProviderWebLogin: (ProviderInfo) -> Unit,
    onLogoutProvider: (ProviderInfo) -> Unit,
    onImportYtmusicHeaderFile: (() -> Unit)?,
    onImportYtmusicOAuthFile: (() -> Unit)?,
    onStartYtmusicOAuth: (() -> Unit)?,
) {
    val uriHandler = LocalUriHandler.current
    val currentAuth = authController.authStateFor(provider)
    val busy = authController.isBusy(provider.providerId)
    val modes = provider.supportedLoginModes.toList().ifEmpty { listOf(ProviderLoginMode.Cookie) }
    var selectedMode by rememberSaveable(provider.providerId) { mutableStateOf(modes.first()) }
    val header = authController.headerInput(provider.providerId)
    val oauth = authController.oauthInput(provider.providerId)
    val oauthFlow = authState.ytmusicOAuthFlow.takeIf { provider.providerId == "ytmusic" }

    Column(
        modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(provider.providerName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            if (currentAuth.isLoggedIn) {
                currentAuth.userName?.takeIf { it.isNotBlank() }?.let { "Angemeldet: $it" } ?: "Angemeldet"
            } else {
                "Mit Anmeldung stehen personalisierte Empfehlungen, eigene Playlists und weitere Funktionen zur Verfügung. Du kannst dies auch überspringen."
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (currentAuth.isLoggedIn) {
            OutlinedButton(onClick = { onLogoutProvider(provider) }, enabled = !busy) { Text("Abmelden") }
            return@Column
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            modes.forEach { mode ->
                FilterChip(
                    selected = selectedMode == mode,
                    enabled = !busy,
                    onClick = { selectedMode = mode },
                    label = { Text(onboardingLoginModeLabel(mode)) },
                )
            }
        }
        when (selectedMode) {
            ProviderLoginMode.WebView -> Button(
                onClick = { onOpenProviderWebLogin(provider) },
                enabled = provider.loginConfig != null && !busy,
            ) { Text("Web-Anmeldung") }
            ProviderLoginMode.Cookie -> {
                OutlinedTextField(
                    value = authController.cookieInput(provider.providerId),
                    onValueChange = { authController.onCookiesChange(provider.providerId, it) },
                    label = { Text("Cookie / Cookie JSON") },
                    minLines = 3,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { authController.loginWithCookies(provider.providerId, authController.cookieInput(provider.providerId)) },
                    enabled = !busy,
                ) { Text("Mit Cookie anmelden") }
            }
            ProviderLoginMode.Headers -> {
                OutlinedTextField(
                    value = header.authorization,
                    onValueChange = { authController.onHeaderAuthorizationChange(provider.providerId, it) },
                    label = { Text("Authorization") },
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = header.cookie,
                    onValueChange = { authController.onHeaderCookieChange(provider.providerId, it) },
                    label = { Text("Cookie") },
                    minLines = 2,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = { authController.loginWithHeaders(provider.providerId) }, enabled = !busy) {
                    Text("Mit Headern anmelden")
                }
                if (provider.providerId == "ytmusic") {
                    onImportYtmusicHeaderFile?.let { action ->
                        TextButton(onClick = action, enabled = !busy) { Text("ytmusic_header.json importieren") }
                    }
                }
            }
            ProviderLoginMode.OAuth -> {
                Text(
                    "Google-Cloud-OAuth-Client vom Typ „TVs and Limited Input devices“ verwenden.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = oauth.clientId,
                    onValueChange = { authController.onOAuthClientIdChange(provider.providerId, it) },
                    label = { Text("client_id") },
                    enabled = !busy && oauthFlow == null,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = oauth.clientSecret,
                    onValueChange = { authController.onOAuthClientSecretChange(provider.providerId, it) },
                    label = { Text("client_secret") },
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = !busy && oauthFlow == null,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (oauthFlow == null) {
                    val startAction = onStartYtmusicOAuth ?: authController::startYtmusicTvOAuthLogin
                    Button(onClick = startAction, enabled = !busy) { Text("Mit Google anmelden (TV)") }
                    onImportYtmusicOAuthFile?.let { action ->
                        TextButton(onClick = action, enabled = !busy) { Text("client_secret.json / oauth.json importieren") }
                    }
                } else {
                    val verificationUrl = oauthFlow.verificationUrlWithCode.ifBlank { oauthFlow.verificationUrl }
                    LaunchedEffect(oauthFlow.userCode, verificationUrl) {
                        if (!oauthFlow.browserOpened && verificationUrl.isNotBlank()) {
                            runCatching { uriHandler.openUri(verificationUrl) }
                                .onSuccess { authController.markYtmusicOAuthBrowserOpened() }
                        }
                    }
                    Text(
                        if (oauthFlow.browserOpened) "Der Browser wurde geöffnet. Gib dort den folgenden Code ein." else oauthFlow.statusMessage,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Gerätecode", style = MaterialTheme.typography.labelMedium)
                            Text(oauthFlow.userCode, style = MaterialTheme.typography.headlineMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = authController::copyYtmusicOAuthUserCode) { Text("Code kopieren") }
                                OutlinedButton(
                                    enabled = verificationUrl.isNotBlank(),
                                    onClick = {
                                        runCatching { uriHandler.openUri(verificationUrl) }
                                            .onSuccess { authController.markYtmusicOAuthBrowserOpened() }
                                    },
                                ) { Text("Browser erneut öffnen") }
                            }
                        }
                    }
                    Text(verificationUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = authController::cancelYtmusicTvOAuthLogin) { Text("Autorisierung abbrechen") }
                }
            }
        }
        authController.authError(provider.providerId)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        authState.feedback?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun OnboardingFeatureFooter(
    currentPage: Int,
    pageCount: Int,
    isBusy: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Surface(tonalElevation = 2.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${currentPage + 1} / $pageCount",
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Start,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onAction, enabled = !isBusy) {
                if (isBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(8.dp))
                }
                Text(actionLabel)
            }
        }
    }
}

private fun onboardingLoginModeLabel(mode: ProviderLoginMode): String = when (mode) {
    ProviderLoginMode.WebView -> "Web"
    ProviderLoginMode.Cookie -> "Cookie"
    ProviderLoginMode.Headers -> "Headers"
    ProviderLoginMode.OAuth -> "OAuth"
}
