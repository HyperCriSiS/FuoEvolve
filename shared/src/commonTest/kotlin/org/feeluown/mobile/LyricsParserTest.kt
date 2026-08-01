package org.feeluown.mobile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LyricsParserTest {
    @Test
    fun parseLrcPairsTranslatedLinesByTimestamp() {
        val lines = parseLrc(
            """
            [by:lyrics.example]
            [00:06.220]Hello, it's me
            [00:11.320]I was wondering if after all these years you'd like to meet
            [00:06.220]你好 是我
            [00:11.320]我犹豫着要不要给你来电
            """.trimIndent(),
        )

        assertEquals(2, lines.size)
        assertEquals(6_220, lines[0].timeMs)
        assertEquals("Hello, it's me", lines[0].text)
        assertEquals("你好 是我", lines[0].translation)
        assertEquals("我犹豫着要不要给你来电", lines[1].translation)
    }

    @Test
    fun parseLrcKeepsLinesWithoutTranslationAndSkipsMetadata() {
        val lines = parseLrc(
            """
            [ar:Adele]
            [offset:0]
            [00:00.000]Hello
            [00:01.500]world
            """.trimIndent(),
        )

        assertEquals(2, lines.size)
        assertEquals("Hello", lines[0].text)
        assertNull(lines[0].translation)
        assertEquals(1_500, lines[1].timeMs)
    }
}
