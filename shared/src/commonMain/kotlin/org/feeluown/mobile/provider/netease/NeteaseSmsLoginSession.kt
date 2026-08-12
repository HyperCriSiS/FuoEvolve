package org.feeluown.mobile.provider.netease

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.Parameters
import io.ktor.http.formUrlEncode
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.random.Random
import org.feeluown.mobile.provider.core.asObject
import org.feeluown.mobile.provider.core.int
import org.feeluown.mobile.provider.core.providerJson
import org.feeluown.mobile.provider.core.string
import org.feeluown.mobile.provider.core.network.createProviderHttpClient
import org.feeluown.mobile.provider.core.network.currentTimeMillis

internal class NeteaseSmsLoginRiskException(
    val code: Int?,
    message: String,
) : Exception(message)

/**
 * A short-lived NetEase SMS login session.
 *
 * It only owns the authentication handshake. Once login succeeds, the returned
 * cookie JSON is handed to the existing provider cookie login path so all
 * subsequent NetEase requests keep using the normal credential store.
 */
internal class NeteaseSmsLoginSession(
    private val httpClient: HttpClient = createProviderHttpClient(),
) {
    private val startedAt = currentTimeMillis()
    private val ntesNuid = randomHex(64)
    private val cookies = linkedMapOf(
        "os" to "pc",
        "appver" to "3.1.17.204416",
        "__remember_me" to "true",
        "ntes_kaola_ad" to "1",
        "_ntes_nuid" to ntesNuid,
        "_ntes_nnid" to "$ntesNuid,$startedAt",
        "WNMCID" to "${randomLowercase(6)}.$startedAt.01.0",
        "WEVNSM" to "1.0.0",
        "osver" to "Microsoft-Windows-10-Professional-build-19045-64bit",
        "channel" to "netease",
        "deviceId" to randomHex(52).uppercase(),
    )
    private var bootstrapped = false

    suspend fun sendCaptcha(phone: String) {
        bootstrapSession()
        cookies.getOrPut("NMTID") { randomHex(32) }
        val normalizedPhone = normalizePhone(phone)
        val response = weApiPost(
            path = "sms/captcha/sent",
            json = """{"ctcode":"86","secrete":"music_middleuser_pclogin","cellphone":"$normalizedPhone","csrf_token":"${csrfToken()}"}""",
        )
        ensureSuccess(response, "验证码发送失败")
    }

    suspend fun login(phone: String, captcha: String): String {
        bootstrapSession()
        val normalizedPhone = normalizePhone(phone)
        val normalizedCaptcha = captcha.trim().also {
            require(it.matches(CAPTCHA_REGEX)) { "请输入正确的短信验证码" }
        }
        val response = weApiPost(
            path = "w/login/cellphone",
            json = """{"type":"1","https":"true","phone":"$normalizedPhone","countrycode":"86","captcha":"$normalizedCaptcha","remember":"true","secureCaptcha":"","csrf_token":"${csrfToken()}"}""",
        )
        ensureSuccess(response, "短信验证码登录失败")
        require(!cookies["MUSIC_U"].isNullOrBlank()) {
            "网易云音乐登录成功但未返回 MUSIC_U，请改用 WebView 登录"
        }
        return JsonObject(cookies.mapValues { JsonPrimitive(it.value) }).toString()
    }

    fun close() {
        httpClient.close()
    }

    private suspend fun bootstrapSession() {
        if (bootstrapped) return
        bootstrapped = true
        runCatching {
            val response = httpClient.request(BASE) {
                method = HttpMethod.Get
                header("Referer", "$BASE/")
                header(HttpHeaders.UserAgent, USER_AGENT)
                header(HttpHeaders.Cookie, cookieHeader())
            }
            response.headers.getAll(HttpHeaders.SetCookie).orEmpty().forEach(::storeSetCookie)
            response.bodyAsText()
        }
    }

    private suspend fun weApiPost(path: String, json: String): String {
        val payload = NeteaseWeApi.encrypt(json)
        val form = Parameters.build {
            append("params", payload.params)
            append("encSecKey", payload.encSecKey)
        }
        val response = httpClient.request("$BASE/weapi/$path") {
            method = HttpMethod.Post
            header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
            header("Referer", "$BASE/")
            header(HttpHeaders.UserAgent, USER_AGENT)
            header(HttpHeaders.Cookie, cookieHeader())
            setBody(form.formUrlEncode())
        }
        val body = response.bodyAsText()
        response.headers.getAll(HttpHeaders.SetCookie).orEmpty().forEach(::storeSetCookie)
        if (response.status.value !in 200..299) {
            error("网易云音乐请求失败（HTTP ${response.status.value}）")
        }
        return body
    }

    private fun ensureSuccess(raw: String, fallbackMessage: String) {
        val root = runCatching { providerJson.parseToJsonElement(raw).asObject() }
            .getOrElse { error(fallbackMessage) }
        val code = root.int("code")
        if (code == 200) return
        val detail = root.string("message").ifBlank { root.string("msg") }
        if (code in RISK_CODES || detail.contains("安全风险")) {
            throw NeteaseSmsLoginRiskException(
                code = code,
                message = detail.ifBlank { "当前登录被网易云安全风控拦截" },
            )
        }
        error(detail.ifBlank { fallbackMessage })
    }

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

    private fun cookieHeader(): String = cookies.entries.joinToString("; ") { (name, value) -> "$name=$value" }

    private fun csrfToken(): String = cookies["__csrf"].orEmpty()

    private fun normalizePhone(phone: String): String = phone
        .filter(Char::isDigit)
        .also { require(it.matches(MAINLAND_PHONE_REGEX)) { "请输入正确的中国大陆手机号" } }

    private companion object {
        const val BASE = "https://music.163.com"
        const val USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        val MAINLAND_PHONE_REGEX = Regex("1\\d{10}")
        val CAPTCHA_REGEX = Regex("\\d{4,8}")
        val RISK_CODES = setOf(10003, 10004)
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
