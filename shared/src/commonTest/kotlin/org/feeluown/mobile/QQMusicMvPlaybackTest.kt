package org.feeluown.mobile

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.feeluown.mobile.provider.core.InMemoryProviderCredentialStore
import org.feeluown.mobile.provider.core.network.ProviderHttpClient
import org.feeluown.mobile.provider.qqmusic.QQMusicProvider

class QQMusicMvPlaybackTest {
    @Test
    fun usesCurrentMvUrlApiAndParsesCommonUrlFallback() = runTest {
        val client = ProviderHttpClient(
            HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals("/cgi-bin/musicu.fcg", request.url.encodedPath)
                        val data = request.url.parameters["data"].orEmpty()
                        assertTrue(data.contains("\"module\":\"music.stream.MvUrlProxy\""))
                        assertTrue(data.contains("\"method\":\"GetMvUrls\""))
                        assertTrue(data.contains("\"request_type\":10003"))
                        assertTrue(data.contains("\"videoformat\":1"))
                        assertTrue(data.contains("\"format\":265"))
                        assertTrue(data.contains("\"dolby\":1"))
                        assertTrue(data.contains("\"use_new_domain\":1"))
                        assertTrue(data.contains("\"use_ipv6\":1"))
                        assertTrue(Regex("\\\"guid\\\":\\\"[0-9a-f]{32}\\\"").containsMatchIn(data))
                        assertFalse(data.contains("request_typet"))
                        assertFalse(data.contains("gosrf.Stream.MvUrlProxy"))
                        respond(
                            """
                            {
                              "getMvUrl": {
                                "data": {
                                  "test-vid": {
                                    "mp4": [
                                      {
                                        "code": 1,
                                        "url": ["https://invalid.example/blocked.mp4"],
                                        "freeflow_url": [],
                                        "comm_url": [],
                                        "m3u8": ""
                                      },
                                      {
                                        "code": 0,
                                        "url": [],
                                        "freeflow_url": ["http://legacy.example/test.mp4"],
                                        "comm_url": ["https://media.example/test.mp4"],
                                        "m3u8": ""
                                      }
                                    ],
                                    "hls": []
                                  }
                                }
                              }
                            }
                            """.trimIndent(),
                        )
                    }
                }
            },
        )
        val provider = QQMusicProvider(client, InMemoryProviderCredentialStore())
        val video = ProviderVideo(
            id = "video:qqmusic:test-vid",
            title = "测试 MV",
            providerId = "qqmusic",
            providerName = "QQ 音乐",
        )

        val payload = provider.videoPlaybackPayload(video)

        assertEquals("https://media.example/test.mp4", payload.url)
        assertEquals("https://media.example/test.mp4", payload.videoUrl)
        assertEquals("https://y.qq.com/", payload.headers["Referer"])
        client.close()
    }
}
