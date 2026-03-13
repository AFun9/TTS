package com.k2fsa.sherpa.tts

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.k2fsa.sherpa.tts.util.AudioMetadataReader
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AudioMetadataReaderTest {

    @Test
    fun missingFileReturnsZeroDurationAndSize() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val reader = AudioMetadataReader()
        val missing = File(context.cacheDir, "missing-audio.wav").absolutePath

        assertEquals(0L, reader.getDurationMs(missing))
        assertEquals(0L, reader.getSizeBytes(missing))
    }
}
