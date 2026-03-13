package com.k2fsa.sherpa.tts.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.k2fsa.sherpa.tts.R
import com.k2fsa.sherpa.tts.databinding.ActivitySettingsBinding
import com.k2fsa.sherpa.tts.ui.lexicon.CustomLexiconActivity
import com.k2fsa.sherpa.tts.util.DocumentCopyHelper
import com.k2fsa.sherpa.tts.util.TtsPreferences
import kotlinx.coroutines.launch
import java.io.File

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var ttsPrefs: TtsPreferences
    private lateinit var documentCopyHelper: DocumentCopyHelper
    private val modelDir by lazy { File(filesDir, "models").apply { mkdirs() } }
    private val tokensDir by lazy { File(filesDir, "tokens").apply { mkdirs() } }
    private val lexiconDir by lazy { File(filesDir, "lexicons").apply { mkdirs() } }

    private val pickModel = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        lifecycleScope.launch {
            documentCopyHelper.copyToAppDir(uri, modelDir)?.let { path ->
                ttsPrefs.modelPath = path
                refreshPathViews()
                Toast.makeText(this@SettingsActivity, getString(R.string.toast_file_copied), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val pickTokens = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        lifecycleScope.launch {
            documentCopyHelper.copyToAppDir(uri, tokensDir)?.let { path ->
                ttsPrefs.tokensPath = path
                refreshPathViews()
                Toast.makeText(this@SettingsActivity, getString(R.string.toast_file_copied), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val pickLexicon = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        lifecycleScope.launch {
            documentCopyHelper.copyToAppDir(uri, lexiconDir)?.let { path ->
                ttsPrefs.lexiconPath = path
                refreshPathViews()
                Toast.makeText(this@SettingsActivity, getString(R.string.toast_file_copied), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val editLexicon = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data ?: return@registerForActivityResult
        val path = data.getStringExtra(CustomLexiconActivity.EXTRA_LEXICON_PATH) ?: return@registerForActivityResult
        ttsPrefs.lexiconPath = path
        refreshPathViews()
        Toast.makeText(this, getString(R.string.toast_lexicon_updated), Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ttsPrefs = TtsPreferences(this)
        documentCopyHelper = DocumentCopyHelper(this)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        refreshPathViews()
        binding.settingsSelectModel.setOnClickListener { pickModel.launch(arrayOf("*/*")) }
        binding.settingsSelectTokens.setOnClickListener { pickTokens.launch(arrayOf("text/*", "*/*")) }
        binding.settingsSelectLexicon.setOnClickListener { pickLexicon.launch(arrayOf("text/*", "*/*")) }
        binding.settingsEditLexicon.setOnClickListener {
            editLexicon.launch(Intent(this, CustomLexiconActivity::class.java))
        }

        binding.settingsAutoPlay.isChecked = ttsPrefs.autoPlay
        binding.settingsAutoPlay.setOnCheckedChangeListener { _, isChecked ->
            ttsPrefs.autoPlay = isChecked
        }

        binding.settingsPlaybackSpeed.value = ttsPrefs.playbackSpeed
        binding.settingsPlaybackSpeedText.text = String.format("%.1fx", ttsPrefs.playbackSpeed)
        binding.settingsPlaybackSpeed.addOnChangeListener { _, value, _ ->
            ttsPrefs.playbackSpeed = value
            binding.settingsPlaybackSpeedText.text = String.format("%.1fx", value)
        }

        binding.settingsTtsSpeed.value = ttsPrefs.ttsSpeed
        binding.settingsTtsSpeedText.text = String.format("%.1fx", ttsPrefs.ttsSpeed)
        binding.settingsTtsSpeed.addOnChangeListener { _, value, _ ->
            ttsPrefs.ttsSpeed = value
            binding.settingsTtsSpeedText.text = String.format("%.1fx", value)
        }

        binding.settingsVolume.value = ttsPrefs.volume.toFloat()
        binding.settingsVolumeText.text = String.format("%.0f%%", ttsPrefs.volume.toFloat())
        binding.settingsVolume.addOnChangeListener { _, value, _ ->
            ttsPrefs.volume = value.toInt()
            binding.settingsVolumeText.text = String.format("%.0f%%", value)
        }

        val lastError = intent.getStringExtra(EXTRA_LAST_ERROR).orEmpty()
        binding.settingsDiagnostics.setOnClickListener {
            val message = if (lastError.isBlank()) {
                getString(R.string.diagnostics_empty)
            } else {
                "$lastError\n\n${getString(R.string.diagnostics_suggestions)}"
            }
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(getString(R.string.diagnostics))
                .setMessage(message)
                .setPositiveButton(getString(R.string.ok), null)
                .show()
        }
    }

    private fun refreshPathViews() {
        binding.settingsModelPath.text = ttsPrefs.modelPath.let { if (it.isBlank()) getString(R.string.not_selected) else it.substringAfterLast('/') }
        binding.settingsTokensPath.text = ttsPrefs.tokensPath.let { if (it.isBlank()) getString(R.string.not_selected) else it.substringAfterLast('/') }
        binding.settingsLexiconPath.text = ttsPrefs.lexiconPath.let { if (it.isBlank()) getString(R.string.not_selected) else it.substringAfterLast('/') }
    }

    companion object {
        const val EXTRA_LAST_ERROR = "last_error"
    }
}
