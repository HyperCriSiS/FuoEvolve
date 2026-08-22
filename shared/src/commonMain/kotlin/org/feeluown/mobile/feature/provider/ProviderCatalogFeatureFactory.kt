package org.feeluown.mobile

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.feeluown.mobile.feature.providercatalog.ProviderCatalogDisplaySection as CoreProviderCatalogDisplaySection
import org.feeluown.mobile.feature.providercatalog.ProviderCatalogFeatureOwner as CoreProviderCatalogFeatureOwner
import org.feeluown.mobile.feature.providercatalog.ProviderCatalogFeatureState as CoreProviderCatalogFeatureState
import org.feeluown.mobile.feature.providercatalog.ProviderCatalogPreferences
import org.feeluown.mobile.feature.providercatalog.ProviderCatalogPreferencesPort
import org.feeluown.mobile.feature.providercatalog.ProviderCatalogPreferencesState
import org.feeluown.mobile.feature.providercatalog.ProviderCatalogRepositoryPort
import org.feeluown.mobile.feature.providercatalog.ProviderCatalogSessionPort
import org.feeluown.mobile.feature.providercatalog.createProviderCatalogFeatureOwner

typealias ProviderCatalogUiState = CoreProviderCatalogFeatureState<
    ProviderInfo,
    ProviderFeature,
    ProviderCapabilities,
    ProviderSessionState,
>

interface ProviderCatalogFeatureController {
    val uiState: StateFlow<ProviderCatalogUiState>
    fun refresh()
    fun setProviderEnabled(providerId: String, enabled: Boolean)
    fun moveProvider(providerId: String, offset: Int)
    fun setDisplayProviderEnabled(section: ProviderDisplaySection, providerId: String, enabled: Boolean)
}

fun createProviderCatalogFeatureController(
    providerRepository: ProviderMusicRepository,
    sessionRepository: ProviderSessionRepository,
    settingsRepository: AppSettingsRepository,
    scope: CoroutineScope,
): ProviderCatalogFeatureController {
    val owner = createProviderCatalogFeatureOwner(
        repository = ProviderCatalogRepositoryBinding(providerRepository),
        preferences = ProviderCatalogPreferencesBinding(settingsRepository, scope),
        sessions = ProviderCatalogSessionBinding(sessionRepository),
        scope = scope,
        defaultEnabledProviderIds = DEFAULT_ENABLED_PROVIDER_IDS,
        defaultProviderOrderIds = DEFAULT_PROVIDER_ORDER_IDS,
    )
    return BoundProviderCatalogFeatureController(owner)
}

private class BoundProviderCatalogFeatureController(
    private val owner: CoreProviderCatalogFeatureOwner<
        ProviderInfo,
        ProviderFeature,
        ProviderCapabilities,
        ProviderSessionState,
    >,
) : ProviderCatalogFeatureController {
    override val uiState: StateFlow<ProviderCatalogUiState> = owner.state

    override fun refresh() = owner.refresh()

    override fun setProviderEnabled(providerId: String, enabled: Boolean) =
        owner.setProviderEnabled(providerId, enabled)

    override fun moveProvider(providerId: String, offset: Int) = owner.moveProvider(providerId, offset)

    override fun setDisplayProviderEnabled(
        section: ProviderDisplaySection,
        providerId: String,
        enabled: Boolean,
    ) = owner.setDisplayProviderEnabled(section.toCore(), providerId, enabled)
}

private class ProviderCatalogRepositoryBinding(
    private val delegate: ProviderMusicRepository,
) : ProviderCatalogRepositoryPort<ProviderInfo, ProviderFeature, ProviderCapabilities> {
    override suspend fun initialize() = delegate.initialize()
    override suspend fun availableProviders(): List<ProviderInfo> = delegate.availableProviders()
    override suspend fun providers(): List<ProviderInfo> = delegate.providers()
    override suspend fun features(): List<ProviderFeature> = delegate.features()
    override suspend fun capabilities(): List<ProviderCapabilities> = delegate.providerCapabilities()
    override suspend fun updateEnabledProviders(providerIds: Set<String>) = delegate.updateEnabledProviders(providerIds)
    override fun providerId(provider: ProviderInfo): String = provider.providerId
    override fun providerName(provider: ProviderInfo): String = provider.providerName
    override fun capabilityProviderId(capability: ProviderCapabilities): String = capability.providerId
}

private class ProviderCatalogPreferencesBinding(
    private val delegate: AppSettingsRepository,
    scope: CoroutineScope,
) : ProviderCatalogPreferencesPort {
    override val state: StateFlow<ProviderCatalogPreferencesState> = delegate.state
        .map { settings -> settings.toProviderCatalogPreferencesState() }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = delegate.state.value.toProviderCatalogPreferencesState(),
        )

    override suspend fun awaitPreferences(): ProviderCatalogPreferences = delegate.awaitSettings().toProviderCatalogPreferences()

    override suspend fun update(transform: (ProviderCatalogPreferences) -> ProviderCatalogPreferences) {
        delegate.update { current ->
            val next = transform(current.toProviderCatalogPreferences())
            current.copy(
                enabledProviderIds = next.enabledProviderIds,
                providerOrderIds = next.providerOrderIds,
                searchProviderIds = next.searchProviderIds,
                recommendProviderIds = next.recommendProviderIds,
                exploreProviderIds = next.exploreProviderIds,
                mineProviderIds = next.mineProviderIds,
                smartReplacementProviderIds = next.replacementProviderIds,
            )
        }
    }
}

private class ProviderCatalogSessionBinding(
    private val delegate: ProviderSessionRepository,
) : ProviderCatalogSessionPort<ProviderInfo, ProviderSessionState> {
    override val state: StateFlow<ProviderSessionState> = delegate.state
    override suspend fun updateProviders(providers: List<ProviderInfo>) = delegate.updateProviders(providers)
    override suspend fun refresh(providerId: String) {
        delegate.refresh(providerId)
    }
}

private fun SettingsState.toProviderCatalogPreferencesState(): ProviderCatalogPreferencesState =
    ProviderCatalogPreferencesState(
        isLoaded = isLoaded,
        settings = settings.toProviderCatalogPreferences(),
        errorMessage = errorMessage,
    )

private fun AppSettings.toProviderCatalogPreferences(): ProviderCatalogPreferences = ProviderCatalogPreferences(
    enabledProviderIds = enabledProviderIds,
    providerOrderIds = providerOrderIds,
    searchProviderIds = searchProviderIds,
    recommendProviderIds = recommendProviderIds,
    exploreProviderIds = exploreProviderIds,
    mineProviderIds = mineProviderIds,
    replacementProviderIds = smartReplacementProviderIds,
)

private fun ProviderDisplaySection.toCore(): CoreProviderCatalogDisplaySection = when (this) {
    ProviderDisplaySection.Search -> CoreProviderCatalogDisplaySection.Search
    ProviderDisplaySection.Recommend -> CoreProviderCatalogDisplaySection.Recommend
    ProviderDisplaySection.Explore -> CoreProviderCatalogDisplaySection.Explore
    ProviderDisplaySection.Mine -> CoreProviderCatalogDisplaySection.Mine
    ProviderDisplaySection.Replace -> CoreProviderCatalogDisplaySection.Replace
}
