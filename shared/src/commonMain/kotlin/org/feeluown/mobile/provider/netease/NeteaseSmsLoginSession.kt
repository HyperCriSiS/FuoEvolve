package org.feeluown.mobile.provider.netease

import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.random.Random
import org.feeluown.mobile.provider.core.asObject
import org.feeluown.mobile.provider.core.int
import org.feeluown.mobile.provider.core.providerJson
import org.feeluown.mobile.provider.core.string
import org.feeluown.mobile.provider.core.network.ProviderHttpClient
import org.feeluown.mobile.provider.core.network.ProviderRequestKind
import org.feeluown.mobile.provider.core.network.currentTimeMillis

internal class NeteaseSmsLoginRiskException(
    val code: Int?,
    message: String,
) : Exception(message)

/**
 * A short-lived NetEase SMS authentication handshake.
 *
 * Transport is delegated to [ProviderHttpClient], response interpretation to
 * [NeteaseSmsLoginResponseParser], and login cookies to [NeteaseSmsCookieStore].
 * Once login succeeds the cookie JSON continues through the existing provider
 * credential path.
 */
internal class NeteaseSmsLoginSession(
    private val http: ProviderHttpClient = ProviderHttpClient(),
) {
    private val cookieStore = NeteaseSmsCookieStore()
    private var bootstrapped = false

    suspend fun sendCaptcha(phone: String) {
        bootstrapSession()
        cookieStore.ensureNmtid()
        val normalizedPhone = normalizePhone(phone)
        val response = weApiPost(
            path = "sms/captcha/sent",
            json = """{"ctcode":"86","secrete":"music_middleuser_pclogin","cellphone":"$normalizedPhone","csrf_token":"${cookieStore.csrfToken()}"}""",
        )
        NeteaseSmsLoginResponseParser.ensureSuccess(response, "验证码发送失败")
    }

    suspend fun login(phone: String, captcha: String): String {
        bootstrapSession()
        val normalizedPhone = normalizePhone(phone)
        val normalizedCaptcha = captcha.trim().also {
            require(it.matches(CAPTCHA_REGEX)) { "请输入正确的短信验证码" }
        }
        val response = weApiPost(
            path = "w/login/cellphone",
            json = """{"type":"1","https":"true","phone":"$normalizedPhone","countrycode":"86","captcha":"$normalizedCaptcha","remember":"true","secureCaptcha":"","csrf_token":"${cookieStore.csrfToken()}"}""",
        )
        NeteaseSmsLoginResponseParser.ensureSuccess(response, "短信验证码登录失败")
        require(cookieStore.hasMusicU()) {
            "网易云音乐登录成功但未返回 MUSIC_U，请改用 WebView 登录"
        }
        return cookieStore.exportJson()
    }

    fun close() {
        http.close()
    }

    private suspend fun bootstrapSession() {
        if (bootstrapped) return
        bootstrapped = true
        runCatching {
            http.getText(
                providerId = ID,
                url = BASE,
                headers = requestHeaders(),
                kind = ProviderRequestKind.Auth,
                cacheKey = null,
                onResponseHeaders = cookieStore::captureResponseHeaders,
            )
        }
    }

    private suspend fun weApiPost(path: String, json: String): String {
        val payload = NeteaseWeApi.encrypt(json)
        val form = Parameters.build {
            append("params", payload.params)
            append("encSecKey", payload.encSecKey)
        }
        return http.postForm(
            providerId = ID,
            url = "$BASE/weapi/$path",
            form = form,
            headers = requestHeaders(),
            kind = ProviderRequestKind.Auth,
            cacheKey = null,
            onResponseHeaders = cookieStore::captureResponseHeaders,
        ).value
    }

    private fun requestHeaders(): Map<String, String> = mapOf(
        "Referer" to "$BASE/",
        HttpHeaders.UserAgent to USER_AGENT,
        HttpHeaders.Cookie to cookieStore.cookieHeader(),
    )

    private fun normalizePhone(phone: String): String = phone
        .filter(Char::isDigit)
        .also { require(it.matches(MAINLAND_PHONE_REGEX)) { "请输入正确的中国大陆手机号" } }

    private companion object {
        const val ID = "netease"
        const val BASE = "https://music.163.com"
        const val USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        val MAINLAND_PHONE_REGEX = Regex("1\\d{10}")
        val CAPTCHA_REGEX = Regex("\\d{4,8}")
    }
}

internal object NeteaseSmsLoginResponseParser {
    private val riskCodes = setOf(10003, 10004)

    fun ensureSuccess(raw: String, fallbackMessage: String) {
        val root = runCatching { providerJson.parseToJsonElement(raw).asObject() }
            .getOrElse { error(fallbackMessage) }
        val code = root.int("code")
        if (code == 200) return
        val detail = root.string("message").ifBlank { root.string("msg") }
        if (code in riskCodes || detail.contains("安全风险")) {
            throw NeteaseSmsLoginRiskException(
                code = code,
                message = detail.ifBlank { "当前登录被网易云安全风控拦截" },
            )
        }
        error(detail.ifBlank { fallbackMessage })
    }
}

internal class NeteaseSmsCookieStore(
    startedAt: Long = currentTimeMillis(),
    ntesNuid: String = randomHex(64),
    deviceId: String = randomHex(52).uppercase(),
    wnmcid: String = "${randomLowercase(6)}.$startedAt.01.0",
) {
    private val cookies = linkedMapOf(
        "os" to "pc",
        "appver" to "3.1.17.204416",
        "__remember_me" to "true",
        "ntes_kaola_ad" to "1",
        "_ntes_nuid" to ntesNuid,
        "_ntes_nnid" to "$ntesNuid,$startedAt",
        "WNMCID" to wnmcid,
        "WEVNSM" to "1.0.0",
        "osver" to "Microsoft-Windows-10-Professional-build-19045-64bit",
        "channel" to "netease",
        "deviceId" to deviceId,
    )

    fun ensureNmtid() {
        cookies.getOrPut("NMTID") { randomHex(32) }
    }

    fun captureResponseHeaders(headers: Map<String, List<String>>) {
        headers.entries
            .filter { (name, _) -> name.equals(HttpHeaders.SetCookie, ignoreCase = true) }
            .flatMap { (_, values) -> values }
            .forEach(::storeSetCookie)
    }

    fun cookieHeader(): String = cookies.entries.joinToString("; ") { (name, value) -> "$name=$value" }

    fun csrfToken(): String = cookies["__csrf"].orEmpty()

    fun hasMusicU(): Boolean = !cookies["MUSIC_U"].isNullOrBlank()

    fun exportJson(): String = JsonObject(cookies.mapValues { JsonPrimitive(it.value) }).toString()

    private fun storeSetCookie(header: String) {
        val pair = header.substringBefore(';').trim()
        val separator = pair.indexOf('=')
        if (separator <= 0) return
        val name = pair.substring(0, separator).trim()
        val value = pair.substring(separator + 1).trim()
        if (name.isBlank()) return
        if (value.isBlank()) {
            cookies.remove(name)
        } else {
            cookies[name] = value
        }
    }
}

private fun randomHex(length: Int): String = buildString(length) {
    repeat(length) {
        append(HEX_CHARS[Random.nextInt(HEX_CHARS.length)])
    }
}

private fun randomLowercase(length: Int): String = buildString(length) {
    repeat(length) {
        append(LOWERCASE_CHARS[Random.nextInt(LOWERCASE_CHARS.length)])
    }
}

private const val HEX_CHARS = "0123456789abcdef"
private const val LOWERCASE_CHARS = "abcdefghijklmnopqrstuvwxyz"
