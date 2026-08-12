package org.feeluown.mobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.feeluown.mobile.provider.netease.NeteaseSmsLoginRiskException
import org.feeluown.mobile.provider.netease.NeteaseSmsLoginSession

@Composable
internal fun NeteaseSmsLoginPanel(
    controller: FuoPlayerController,
    isAuthBusy: Boolean,
) {
    val scope = rememberCoroutineScope()
    val session = remember { NeteaseSmsLoginSession() }
    var phone by remember { mutableStateOf("") }
    var captcha by remember { mutableStateOf("") }
    var isSmsBusy by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var feedbackIsError by remember { mutableStateOf(false) }
    var riskCode by remember { mutableStateOf<Int?>(null) }
    var showWebFallback by remember { mutableStateOf(false) }

    DisposableEffect(session) {
        onDispose(session::close)
    }

    fun clearRiskFallback() {
        riskCode = null
        showWebFallback = false
    }

    fun handleFailure(throwable: Throwable, fallbackMessage: String) {
        if (throwable is NeteaseSmsLoginRiskException) {
            riskCode = throwable.code
            showWebFallback = true
            feedback = throwable.message ?: fallbackMessage
        } else {
            clearRiskFallback()
            feedback = throwable.message ?: fallbackMessage
        }
        feedbackIsError = true
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "短信验证码登录（推荐）",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "使用中国大陆 +86 手机号登录。验证成功后仍使用现有网易云 Cookie 会话。",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextField(
                modifier = Modifier.fillMaxWidth(),
                value = phone,
                onValueChange = { value -> phone = value.filter(Char::isDigit).take(11) },
                label = { Text("手机号") },
                prefix = { Text("+86 ") },
                singleLine = true,
                enabled = !isSmsBusy && !isAuthBusy,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextField(
                    modifier = Modifier.weight(1f),
                    value = captcha,
                    onValueChange = { value -> captcha = value.filter(Char::isDigit).take(8) },
                    label = { Text("验证码") },
                    singleLine = true,
                    enabled = !isSmsBusy && !isAuthBusy,
                )
                Button(
                    enabled = !isSmsBusy && !isAuthBusy && phone.length == 11,
                    onClick = {
                        scope.launch {
                            isSmsBusy = true
                            feedback = "正在发送验证码"
                            feedbackIsError = false
                            clearRiskFallback()
                            runCatching { session.sendCaptcha(phone) }
                                .onSuccess {
                                    feedback = "验证码已发送"
                                }
                                .onFailure { throwable ->
                                    handleFailure(throwable, "验证码发送失败")
                                }
                            isSmsBusy = false
                        }
                    },
                ) {
                    Text(if (isSmsBusy) "发送中" else "获取验证码")
                }
            }
            feedback?.let { message ->
                val displayMessage = if (showWebFallback) {
                    if (riskCode != null) {
                        "网易云风控拦截了本次短信登录（$riskCode），可稍后重试或使用下方 WebView 登录。"
                    } else {
                        "网易云风控拦截了本次短信登录，可稍后重试或使用下方 WebView 登录。"
                    }
                } else {
                    message
                }
                Text(
                    text = displayMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (feedbackIsError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    },
                )
            }
            Button(
                enabled = !isSmsBusy && !isAuthBusy && phone.length == 11 && captcha.length >= 4,
                onClick = {
                    scope.launch {
                        isSmsBusy = true
                        feedback = "正在验证并登录"
                        feedbackIsError = false
                        clearRiskFallback()
                        runCatching { session.login(phone, captcha) }
                            .onSuccess { cookiesJson ->
                                feedback = "验证成功，正在建立网易云登录会话"
                                controller.loginProviderWithCookies("netease", cookiesJson)
                            }
                            .onFailure { throwable ->
                                handleFailure(throwable, "短信验证码登录失败")
                            }
                        isSmsBusy = false
                    }
                },
            ) {
                Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(if (isSmsBusy) "登录中" else "短信验证码登录")
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                text = "其他方式：仍可使用下方 WebView 登录。",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
