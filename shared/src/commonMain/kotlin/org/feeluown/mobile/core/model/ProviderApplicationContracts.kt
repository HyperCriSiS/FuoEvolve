package org.feeluown.mobile

/**
 * Application compatibility aggregate retained while callers finish migrating to narrow provider
 * capabilities. Provider-neutral contracts live in :provider:api; playback policy coupling and the
 * legacy YTMusic OAuth facade remain application-owned until the aggregate is retired.
 */
interface ProviderMusicRepository :
    ProviderRegistryRepository,
    ProviderSearchRepository,
    ProviderAuthRepository,
    ProviderCatalogRepository,
    ProviderLibraryRepository,
    ProviderPlaybackRepository {
    suspend fun updateAudioQualityPolicies(
        wifiPolicy: AudioQualityPolicy,
        cellularPolicy: AudioQualityPolicy,
    )

    override suspend fun loginWithHeaderFile(providerId: String, headerFileJson: String): ProviderAuthState =
        when (providerId) {
            "ytmusic" -> loginWithYtmusicHeaderFile(headerFileJson)
            else -> throw UnsupportedOperationException("provider does not support header file login: $providerId")
        }

    override suspend fun beginDeviceAuthorization(
        providerId: String,
        clientId: String,
        clientSecret: String,
    ): ProviderDeviceAuthorization = when (providerId) {
        "ytmusic" -> beginYtmusicOAuth(clientId, clientSecret).let { result ->
            ProviderDeviceAuthorization(
                providerId = providerId,
                deviceCode = result.deviceCode,
                userCode = result.userCode,
                verificationUrl = result.verificationUrl,
                expiresInSeconds = result.expiresInSeconds,
                intervalSeconds = result.intervalSeconds,
            )
        }
        else -> throw UnsupportedOperationException("provider does not support device authorization: $providerId")
    }

    override suspend fun pollDeviceAuthorization(
        providerId: String,
        deviceCode: String,
        clientId: String,
        clientSecret: String,
    ): ProviderDeviceAuthorizationPollResult = when (providerId) {
        "ytmusic" -> when (val result = pollYtmusicOAuth(deviceCode, clientId, clientSecret)) {
            is org.feeluown.mobile.provider.ytmusic.YtMusicOAuthPollResult.Authorized ->
                ProviderDeviceAuthorizationPollResult.Authorized(
                    ProviderOAuthToken(
                        accessToken = result.token.accessToken,
                        refreshToken = result.token.refreshToken,
                        scope = result.token.scope,
                        expiresAtMillis = result.token.expiresAtEpochSeconds * 1_000,
                    )
                )
            org.feeluown.mobile.provider.ytmusic.YtMusicOAuthPollResult.Pending ->
                ProviderDeviceAuthorizationPollResult.Pending
            org.feeluown.mobile.provider.ytmusic.YtMusicOAuthPollResult.SlowDown ->
                ProviderDeviceAuthorizationPollResult.SlowDown
            is org.feeluown.mobile.provider.ytmusic.YtMusicOAuthPollResult.Denied ->
                ProviderDeviceAuthorizationPollResult.Denied(result.message)
        }
        else -> throw UnsupportedOperationException("provider does not support device authorization: $providerId")
    }

    override suspend fun loginWithOAuth(
        providerId: String,
        accessToken: String,
        refreshToken: String,
        expiresAtMillis: Long?,
        scope: String?,
        clientId: String,
        clientSecret: String,
    ): ProviderAuthState = when (providerId) {
        "ytmusic" -> loginWithYtmusicOAuth(
            accessToken,
            refreshToken,
            expiresAtMillis,
            scope,
            clientId,
            clientSecret,
        )
        else -> throw UnsupportedOperationException("provider does not support OAuth login: $providerId")
    }

    override suspend fun loginWithOAuthJson(
        providerId: String,
        oauthJson: String,
        clientId: String,
        clientSecret: String,
    ): ProviderAuthState = when (providerId) {
        "ytmusic" -> loginWithYtmusicOAuthJson(oauthJson, clientId, clientSecret)
        else -> throw UnsupportedOperationException("provider does not support OAuth JSON login: $providerId")
    }

    suspend fun loginWithYtmusicHeaderFile(headerFileJson: String): ProviderAuthState {
        throw UnsupportedOperationException("provider does not support YTMusic header file login")
    }

    suspend fun beginYtmusicOAuth(
        clientId: String,
        clientSecret: String,
    ): org.feeluown.mobile.provider.ytmusic.YtMusicDeviceAuthCode {
        throw UnsupportedOperationException("provider does not support YTMusic OAuth")
    }

    suspend fun pollYtmusicOAuth(
        deviceCode: String,
        clientId: String,
        clientSecret: String,
    ): org.feeluown.mobile.provider.ytmusic.YtMusicOAuthPollResult {
        throw UnsupportedOperationException("provider does not support YTMusic OAuth")
    }

    suspend fun loginWithYtmusicOAuth(
        accessToken: String,
        refreshToken: String,
        expiresAtMillis: Long?,
        scope: String?,
        clientId: String,
        clientSecret: String,
    ): ProviderAuthState {
        throw UnsupportedOperationException("provider does not support YTMusic OAuth")
    }

    suspend fun loginWithYtmusicOAuthJson(
        oauthJson: String,
        clientId: String,
        clientSecret: String,
    ): ProviderAuthState {
        throw UnsupportedOperationException("provider does not support YTMusic OAuth")
    }
}
