package com.k2fsa.sherpa.tts.util

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioFormatUtilsTest {

    @Test
    fun formatDuration_formatsMinutesAndSeconds() {
        assertEquals("00:00", AudioFormatUtils.formatDuration(0))
        assertEquals("01:05", AudioFormatUtils.formatDuration(65_000))
    }

    @Test
    fun formatSize_formatsKilobytesAndMegabytes() {
        assertEquals("", AudioFormatUtils.formatSize(0))
        assertEquals("1.5 KB", AudioFormatUtils.formatSize(1536))
        assertEquals("1.0 MB", AudioFormatUtils.formatSize(1024 * 1024))
    }

    @Test
    fun formatDate_usesExpectedPattern() {
        assertEquals("1970-01-01 00:00", AudioFormatUtils.formatDate(0))
    }
}
