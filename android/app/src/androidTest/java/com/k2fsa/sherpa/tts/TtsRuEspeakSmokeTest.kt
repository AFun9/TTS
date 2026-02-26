package com.k2fsa.sherpa.tts

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.k2fsa.sherpa.tts.data.FrontendMode
import com.k2fsa.sherpa.tts.data.TTSConfig
import com.k2fsa.sherpa.tts.repository.TTSRepository
import com.k2fsa.sherpa.tts.util.EspeakDataHelper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class TtsRuEspeakSmokeTest {

    @Test
    fun ruWithoutLexiconUsesEspeakAndProducesWav() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val filesDir = context.filesDir
        val modelPath = copyAssetToFile(context, "russian_vits.onnx", File(filesDir, "models"))
        val tokensPath = copyAssetToFile(context, "ru_tokens.txt", File(filesDir, "models"))
        val dataDir = EspeakDataHelper.ensure(context)
        val outputDir = File(filesDir, "tts_smoke_output").apply { mkdirs() }

        val repo = TTSRepository(context, outputDir)
        val cfg = TTSConfig(
            modelPath = modelPath,
            tokensPath = tokensPath,
            dataDir = dataDir,
            frontendMode = FrontendMode.EspeakOnly,
            voice = "ru",
            speed = 1.0f
        )
        val result = repo.generateSpeech(cfg, "Привет, как дела?", 1.0f)
        repo.release()

        assertTrue("TTS should succeed in ru espeak smoke test", result.isSuccess)
        val wavPath = result.getOrThrow().wavFilePath
        val wav = File(wavPath)
        assertTrue("Generated wav should exist", wav.exists())
        assertTrue("Generated wav should not be empty", wav.length() > 44)
    }

    private fun copyAssetToFile(context: android.content.Context, assetName: String, dir: File): String {
        dir.mkdirs()
        val target = File(dir, assetName)
        context.assets.open(assetName).use { input ->
            FileOutputStream(target).use { output -> input.copyTo(output) }
        }
        return target.absolutePath
    }
}
