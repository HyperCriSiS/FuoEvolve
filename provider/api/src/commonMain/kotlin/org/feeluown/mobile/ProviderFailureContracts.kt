package org.feeluown.mobile

enum class ProviderFailureKind {
    LoginExpired,
    RegionRestricted,
    CopyrightUnavailable,
    UpstreamContractChanged,
    Network,
}

data class ProviderFailure(
    val kind: ProviderFailureKind,
    val providerId: String? = null,
    val technicalMessage: String? = null,
) {
    val userMessage: String
        get() = when (kind) {
            ProviderFailureKind.LoginExpired -> "登录状态已失效，请重新登录后重试"
            ProviderFailureKind.RegionRestricted -> "当前地区暂不支持此内容"
            ProviderFailureKind.CopyrightUnavailable -> "该内容因版权或资源限制不可用"
            ProviderFailureKind.UpstreamContractChanged -> "音源接口响应已变化，请更新应用或稍后重试"
            ProviderFailureKind.Network -> "网络请求失败，请检查网络后重试"
        }
}

class ProviderOperationException(
    val failure: ProviderFailure,
    cause: Throwable? = null,
) : IllegalStateException(failure.technicalMessage ?: failure.userMessage, cause)
