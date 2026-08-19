package org.feeluown.mobile

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SearchControllerTest {
    @Test
    fun narrowControllerOwnsUiStateAndDispatchesSearchActions() = runTest {
        val persistedPreferences = mutableListOf<Pair<SearchScope, String?>>()
        val controller = SearchController(
            providerRepository = EmptyProviderSearchRepository,
            localRepository = EmptyLocalMusicRepository,
            scope = this,
            providerIdsForSearch = { listOf("netease") },
            providerExists = { it == "netease" },
            openSearch = {},
            onPreferencesChanged = { searchScope, providerId ->
                persistedPreferences += searchScope to providerId
            },
        )

        controller.dispatch(SearchAction.ScopeChanged(SearchScope.Local))
        controller.dispatch(SearchAction.ProviderChanged("netease"))
        controller.dispatch(SearchAction.QueryChanged("hello"))
        controller.dispatch(SearchAction.ProviderTabChanged(ProviderSearchTab.Albums))

        assertEquals("hello", controller.uiState.value.query)
        assertEquals(SearchScope.Provider, controller.uiState.value.searchScope)
        assertEquals("netease", controller.uiState.value.selectedSearchProviderId)
        assertEquals(ProviderSearchTab.Albums, controller.uiState.value.providerSearchTab)
        assertEquals(
            listOf(
                SearchScope.Local to null,
                SearchScope.Provider to "netease",
            ),
            persistedPreferences,
        )
    }

    @Test
    fun applyingPersistedPreferencesDoesNotWriteThemBack() = runTest {
        var writes = 0
        val controller = SearchController(
            providerRepository = EmptyProviderSearchRepository,
            localRepository = EmptyLocalMusicRepository,
            scope = this,
            providerIdsForSearch = { listOf("netease") },
            providerExists = { it == "netease" },
            openSearch = {},
            onPreferencesChanged = { _, _ -> writes += 1 },
        )

        controller.applyPreferences(SearchScope.Provider, "netease")

        assertEquals(SearchScope.Provider, controller.uiState.value.searchScope)
        assertEquals("netease", controller.uiState.value.selectedSearchProviderId)
        assertEquals(0, writes)
    }
}

private object EmptyProviderSearchRepository : ProviderSearchRepository {
    override suspend fun search(keyword: String, providerId: String?): List<MusicTrack> = emptyList()

    override suspend fun searchAll(keyword: String, providerId: String?): ProviderSearchResults = ProviderSearchResults()
}

private object EmptyLocalMusicRepository : LocalMusicRepository {
    override suspend fun updateScanSettings(settings: LocalMusicScanSettings) = Unit

    override suspend fun directories(): List<LocalMusicDirectory> = emptyList()

    override suspend fun tracks(): List<MusicTrack> = emptyList()

    override suspend fun refreshDatabase(): List<MusicTrack> = emptyList()

    override suspend fun search(keyword: String): List<MusicTrack> = emptyList()
}
