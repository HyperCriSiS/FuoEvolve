package org.feeluown.mobile.feature.onboarding

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class OnboardingFeatureState(
    val selectedProviderIds: Set<String> = emptySet(),
    val bilibiliReplacementOnly: Boolean = false,
    val isBusy: Boolean = false,
    val feedback: String? = null,
)

data class OnboardingProviderPreferences<PlaybackPolicy>(
    val enabledProviderIds: Set<String>,
    val searchProviderIds: Set<String>,
    val recommendProviderIds: Set<String>,
    val exploreProviderIds: Set<String>,
    val mineProviderIds: Set<String>,
    val smartReplacementProviderIds: Set<String>,
    val unavailablePlaybackPolicy: PlaybackPolicy,
)

interface OnboardingPreferencesPort<PlaybackPolicy> {
    val providerPreferences: StateFlow<OnboardingProviderPreferences<PlaybackPolicy>>
    suspend fun updateProviderPreferences(value: OnboardingProviderPreferences<PlaybackPolicy>)
    suspend fun markCompleted()
}

interface OnboardingProviderRuntimePort {
    suspend fun updateEnabledProviders(providerIds: Set<String>)
    fun refreshCatalog()
}

interface OnboardingFeatureOwner {
    val state: StateFlow<OnboardingFeatureState>
    fun initialize(availableProviderIds: List<String>)
    fun setProviderSelected(providerId: String, selected: Boolean)
    fun setBilibiliReplacementOnly(enabled: Boolean)
    fun applyProviderSelection(availableProviderIds: Set<String>, onComplete: (Boolean) -> Unit)
    fun complete()
    fun dismissFeedback(feedback: String)
}

fun <PlaybackPolicy> createOnboardingFeatureOwner(
    preferences: OnboardingPreferencesPort<PlaybackPolicy>,
    providerRuntime: OnboardingProviderRuntimePort,
    smartReplacePolicy: PlaybackPolicy,
    scope: CoroutineScope,
): OnboardingFeatureOwner = DefaultOnboardingFeatureOwner(
    preferences = preferences,
    providerRuntime = providerRuntime,
    smartReplacePolicy = smartReplacePolicy,
    scope = scope,
)

private class DefaultOnboardingFeatureOwner<PlaybackPolicy>(
    private val preferences: OnboardingPreferencesPort<PlaybackPolicy>,
    private val providerRuntime: OnboardingProviderRuntimePort,
    private val smartReplacePolicy: PlaybackPolicy,
    private val scope: CoroutineScope,
) : OnboardingFeatureOwner {
    private val mutableState = MutableStateFlow(OnboardingFeatureState())
    override val state: StateFlow<OnboardingFeatureState> = mutableState.asStateFlow()
    private var initialized = false

    override fun initialize(availableProviderIds: List<String>) {
        if (initialized || availableProviderIds.isEmpty()) return
        initialized = true
        val availableIds = availableProviderIds.toSet()
        val current = preferences.providerPreferences.value
        val selected = current.enabledProviderIds.intersect(availableIds)
            .ifEmpty { setOf(availableProviderIds.first()) }
        val replacementOnly = "bilibili" in selected &&
            current.smartReplacementProviderIds == setOf("bilibili") &&
            listOf(
                current.searchProviderIds,
                current.recommendProviderIds,
                current.exploreProviderIds,
                current.mineProviderIds,
            ).none { "bilibili" in it }
        mutableState.value = mutableState.value.copy(
            selectedProviderIds = selected,
            bilibiliReplacementOnly = replacementOnly,
        )
    }

    override fun setProviderSelected(providerId: String, selected: Boolean) {
        val current = state.value
        val next = if (selected) {
            current.selectedProviderIds + providerId
        } else {
            current.selectedProviderIds - providerId
        }
        mutableState.value = current.copy(
            selectedProviderIds = next,
            bilibiliReplacementOnly = current.bilibiliReplacementOnly && "bilibili" in next,
            feedback = null,
        )
    }

    override fun setBilibiliReplacementOnly(enabled: Boolean) {
        mutableState.value = state.value.copy(
            bilibiliReplacementOnly = enabled && "bilibili" in state.value.selectedProviderIds,
            feedback = null,
        )
    }

    override fun applyProviderSelection(
        availableProviderIds: Set<String>,
        onComplete: (Boolean) -> Unit,
    ) {
        val currentState = state.value
        val selected = currentState.selectedProviderIds.intersect(availableProviderIds)
        when {
            selected.isEmpty() -> {
                mutableState.value = currentState.copy(feedback = "请至少选择一个音源")
                onComplete(false)
                return
            }

            currentState.bilibiliReplacementOnly && selected == setOf("bilibili") -> {
                mutableState.value = currentState.copy(feedback = "Bilibili 仅作为替换音源时，请再选择一个常规音源")
                onComplete(false)
                return
            }
        }

        scope.launch {
            val previous = preferences.providerPreferences.value
            val next = onboardingProviderPreferences(
                current = previous,
                selectedProviderIds = selected,
                bilibiliReplacementOnly = currentState.bilibiliReplacementOnly,
                smartReplacePolicy = smartReplacePolicy,
            )
            mutableState.value = state.value.copy(isBusy = true, feedback = "正在初始化音源")
            val result = runCatching {
                providerRuntime.updateEnabledProviders(selected)
                preferences.updateProviderPreferences(next)
            }
            if (result.isFailure) {
                runCatching { providerRuntime.updateEnabledProviders(previous.enabledProviderIds) }
                runCatching { preferences.updateProviderPreferences(previous) }
                providerRuntime.refreshCatalog()
                mutableState.value = state.value.copy(
                    isBusy = false,
                    feedback = result.exceptionOrNull()?.message ?: "音源初始化失败",
                )
                onComplete(false)
                return@launch
            }

            providerRuntime.refreshCatalog()
            mutableState.value = state.value.copy(isBusy = false, feedback = "音源初始化完成")
            onComplete(true)
        }
    }

    override fun complete() {
        scope.launch {
            preferences.markCompleted()
        }
    }

    override fun dismissFeedback(feedback: String) {
        if (state.value.feedback == feedback) {
            mutableState.value = state.value.copy(feedback = null)
        }
    }
}

fun <PlaybackPolicy> onboardingProviderPreferences(
    current: OnboardingProviderPreferences<PlaybackPolicy>,
    selectedProviderIds: Set<String>,
    bilibiliReplacementOnly: Boolean,
    smartReplacePolicy: PlaybackPolicy,
): OnboardingProviderPreferences<PlaybackPolicy> {
    if (bilibiliReplacementOnly && "bilibili" in selectedProviderIds) {
        val regularProviderIds = selectedProviderIds - "bilibili"
        return current.copy(
            enabledProviderIds = selectedProviderIds,
            searchProviderIds = regularProviderIds,
            recommendProviderIds = regularProviderIds,
            exploreProviderIds = regularProviderIds,
            mineProviderIds = regularProviderIds,
            smartReplacementProviderIds = setOf("bilibili"),
            unavailablePlaybackPolicy = smartReplacePolicy,
        )
    }
    return current.copy(
        enabledProviderIds = selectedProviderIds,
        searchProviderIds = emptySet(),
        recommendProviderIds = emptySet(),
        exploreProviderIds = emptySet(),
        mineProviderIds = emptySet(),
        smartReplacementProviderIds = emptySet(),
    )
}
