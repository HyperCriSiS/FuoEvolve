package org.feeluown.mobile

import org.feeluown.mobile.provider.qqmusic.QQMusicContentProvider

/**
 * Adds QQ Music discovery/search surfaces without changing the core provider repository.
 * Playback, authentication and user-library mutations continue to use the base QQ provider.
 */
internal class QQMusicContentRepository(
    private val delegate: ProviderMusicRepository,
    private val qqmusic: QQMusicContentProvider,
) : ProviderMusicRepository by delegate {
    override suspend fun features(): List<ProviderFeature> {
        val base = delegate.features()
        if (base.none { it.providerId == QQMUSIC_PROVIDER_ID }) return base
        return buildList {
            var inserted = false
            base.forEach { feature ->
                if (feature.providerId == QQMUSIC_PROVIDER_ID) {
                    if (!inserted) {
                        addAll(qqmusic.features)
                        inserted = true
                    }
                } else {
                    add(feature)
                }
            }
        }
    }

    override suspend fun search(keyword: String, providerId: String?): List<MusicTrack> =
        searchAll(keyword, providerId).tracks

    override suspend fun searchAll(keyword: String, providerId: String?): ProviderSearchResults {
        val base = delegate.searchAll(keyword, providerId)
        if (providerId != null && providerId != QQMUSIC_PROVIDER_ID) return base
        val qqEnabled = providerId == QQMUSIC_PROVIDER_ID ||
            delegate.features().any { it.providerId == QQMUSIC_PROVIDER_ID }
        if (!qqEnabled) return base
        val extras = runCatching { qqmusic.searchExtras(keyword) }.getOrElse { return base }
        return base.copy(
            playlists = replaceQQMusic(base.playlists, extras.playlists) { it.providerId },
            artists = replaceQQMusic(base.artists, extras.artists) { it.providerId },
            albums = replaceQQMusic(base.albums, extras.albums) { it.providerId },
            videos = replaceQQMusic(base.videos, extras.videos) { it.providerId },
        )
    }

    override suspend fun loadFeature(feature: ProviderFeature): ProviderContentSection =
        loadFeaturePage(feature, 0, PROVIDER_PAGE_SIZE)

    override suspend fun loadFeaturePage(
        feature: ProviderFeature,
        offset: Int,
        limit: Int,
    ): ProviderContentSection = if (feature.providerId == QQMUSIC_PROVIDER_ID) {
        qqmusic.loadFeature(feature, offset, limit)
    } else {
        delegate.loadFeaturePage(feature, offset, limit)
    }

    override suspend fun loadMoreFeatureTracks(feature: ProviderFeature): List<MusicTrack> =
        if (feature.providerId == QQMUSIC_PROVIDER_ID) {
            qqmusic.loadFeature(feature, 0, PROVIDER_PAGE_SIZE).tracks
        } else {
            delegate.loadMoreFeatureTracks(feature)
        }

    override suspend fun playlistDetail(playlist: ProviderPlaylist): ProviderPlaylistDetail =
        playlistDetailPage(playlist, 0, PROVIDER_PAGE_SIZE)

    override suspend fun playlistDetailPage(
        playlist: ProviderPlaylist,
        offset: Int,
        limit: Int,
    ): ProviderPlaylistDetail = if (playlist.providerId == QQMUSIC_PROVIDER_ID) {
        qqmusic.playlistDetail(playlist, offset, limit)
    } else {
        delegate.playlistDetailPage(playlist, offset, limit)
    }

    override suspend fun playlistTracks(playlist: ProviderPlaylist): List<MusicTrack> =
        if (playlist.providerId == QQMUSIC_PROVIDER_ID) {
            qqmusic.playlistTracks(playlist)
        } else {
            delegate.playlistTracks(playlist)
        }

    private fun <T> replaceQQMusic(
        base: List<T>,
        qqItems: List<T>,
        providerId: (T) -> String,
    ): List<T> = (base.filterNot { providerId(it) == QQMUSIC_PROVIDER_ID } + qqItems).distinct()

    private companion object {
        const val QQMUSIC_PROVIDER_ID = "qqmusic"
    }
}
