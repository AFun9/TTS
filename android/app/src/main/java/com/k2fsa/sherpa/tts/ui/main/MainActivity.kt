package com.k2fsa.sherpa.tts.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModelProvider
import com.k2fsa.sherpa.tts.R
import com.k2fsa.sherpa.tts.databinding.ActivityMainBinding
import com.k2fsa.sherpa.tts.repository.TTSRepository
import com.k2fsa.sherpa.tts.ui.history.HistoryActivity
import com.k2fsa.sherpa.tts.ui.settings.SettingsActivity
import com.k2fsa.sherpa.tts.util.AudioFormatUtils
import com.k2fsa.sherpa.tts.util.AudioHistoryItem
import com.k2fsa.sherpa.tts.util.AudioHistoryStore
import com.k2fsa.sherpa.tts.util.TtsPreferences
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private val ttsRepository by lazy {
        TTSRepository(this, File(filesDir, "tts_output").apply { mkdirs() })
    }

    private val ttsPrefs by lazy { TtsPreferences(this) }
    private val latestAudioHandler by lazy { LatestAudioHandler(this) }
    private var latestAudioPath: String? = null
    private var autoPlayEnabled: Boolean = true
    private var playbackSpeed: Float = 1.0f
    private var lastErrorText: String = ""

    private val requestPermissions = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            initializeTTS()
        } else {
            Toast.makeText(this, getString(R.string.toast_storage_required), Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this, MainViewModelFactory(ttsRepository))[MainViewModel::class.java]
        loadSavedPaths()
        setupUI()
        checkPermissions()
        observeViewModel()
    }

    private fun loadSavedPaths() {
        ttsPrefs.modelPath.takeIf { it.isNotBlank() }?.let { viewModel.setModelPath(it) }
        ttsPrefs.tokensPath.takeIf { it.isNotBlank() }?.let { viewModel.setTokensPath(it) }
        ttsPrefs.lexiconPath.takeIf { it.isNotBlank() }?.let { viewModel.setLexiconPath(it) }
        autoPlayEnabled = ttsPrefs.autoPlay
        playbackSpeed = ttsPrefs.playbackSpeed
        viewModel.setSpeed(ttsPrefs.ttsSpeed)
        viewModel.setVolume(ttsPrefs.volume)
        latestAudioHandler.updatePlaybackSpeed(playbackSpeed)
        val history = AudioHistoryStore.getItems(this)
        val latest = history.firstOrNull { File(it.path).exists() }
        if (latest != null) {
            updateLatest(latest)
        } else if (history.isNotEmpty()) {
            history.forEach { AudioHistoryStore.removeItem(this, it.path) }
        }
    }

    override fun onResume() {
        super.onResume()
        loadSavedPaths()
        updateModelStatusText()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_settings) {
            startActivity(Intent(this, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_LAST_ERROR, lastErrorText))
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_LAST_ERROR, lastErrorText))
    }

    private fun updateModelStatusText() {
        binding.modelStatusText.text = if (viewModel.isModelReady()) getString(R.string.model_ready) else getString(R.string.model_not_ready)
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)

        binding.modelStatusCard.setOnClickListener { openSettings() }
        binding.historyButton.setOnClickListener { startActivity(Intent(this, HistoryActivity::class.java)) }
        binding.latestPlayButton.setOnClickListener {
            latestAudioPath?.let { playWav(it) }
        }
        binding.latestShareButton.setOnClickListener {
            latestAudioPath?.let { path ->
                latestAudioHandler.share(path) {
                    AudioHistoryStore.removeItem(this, path)
                    latestAudioPath = null
                    setLatestEnabled(false)
                }
            }
        }
        setLatestEnabled(false)

        binding.generateButton.setOnClickListener {
            val text = binding.textInput.text.toString()
            when {
                !viewModel.isModelReady() -> {
                    Toast.makeText(this, getString(R.string.toast_select_model_first), Toast.LENGTH_SHORT).show()
                    openSettings()
                }
                text.isBlank() -> Toast.makeText(this, getString(R.string.toast_enter_text), Toast.LENGTH_SHORT).show()
                else -> viewModel.generateSpeech(text)
            }
        }

        binding.stopButton.setOnClickListener {
            latestAudioHandler.stop()
            viewModel.stopPlayback()
        }
    }

    private fun checkPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissions.clear()
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
        }

        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            initializeTTS()
        } else {
            requestPermissions.launch(permissions.toTypedArray())
        }
    }

    private fun initializeTTS() {
        lifecycleScope.launch {
            viewModel.initializeTTS()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                when (state) {
                    is MainUiState.Idle -> {
                        binding.progressBar.visibility = android.view.View.GONE
                        binding.generateButton.isEnabled = true
                        binding.statusText.text = getString(R.string.status_idle_hint)
                    }
                    is MainUiState.Loading -> {
                        binding.progressBar.visibility = android.view.View.VISIBLE
                        binding.progressBar.progress = 0
                        binding.generateButton.isEnabled = false
                        binding.statusText.text = getString(R.string.status_loading)
                    }
                    is MainUiState.Success -> {
                        binding.progressBar.visibility = android.view.View.GONE
                        binding.generateButton.isEnabled = true
                        binding.statusText.text = getString(R.string.status_success)
                        Toast.makeText(this@MainActivity, getString(R.string.toast_generate_success), Toast.LENGTH_SHORT).show()
                    }
                    is MainUiState.Error -> {
                        binding.progressBar.visibility = android.view.View.GONE
                        binding.generateButton.isEnabled = true
                        binding.statusText.text = getString(R.string.status_error_fmt, state.message)
                        val toastMsg = if (state.message.contains("JNI") || state.message.contains("nativeCreate"))
                            getString(R.string.error_engine_not_ready)
                        else
                            getString(R.string.status_error_fmt, state.message)
                        Toast.makeText(this@MainActivity, toastMsg, Toast.LENGTH_LONG).show()
                    }
                    is MainUiState.Playing -> {
                        binding.statusText.text = getString(R.string.status_playing)
                    }
                    is MainUiState.Stopped -> {
                        binding.statusText.text = getString(R.string.status_stopped)
                    }
                }
            }
        }

        lifecycleScope.launch {
            viewModel.progress.collect { progress ->
                binding.progressBar.progress = progress
            }
        }

        lifecycleScope.launch {
            viewModel.lastError.collect { msg ->
                lastErrorText = msg
            }
        }

        lifecycleScope.launch {
            viewModel.modelPath.collect { updateModelStatusText() }
        }
        lifecycleScope.launch {
            viewModel.tokensPath.collect { updateModelStatusText() }
        }

        viewModel.generatedWavPath
            .onEach { wavPath ->
                val item = AudioHistoryItem(wavPath, System.currentTimeMillis())
                AudioHistoryStore.addItem(this, item)
                updateLatest(item)
                if (autoPlayEnabled) {
                    playWav(wavPath)
                }
            }
            .launchIn(lifecycleScope)
    }

    private fun updateLatest(item: AudioHistoryItem) {
        latestAudioPath = item.path
        binding.latestFileName.text = item.path.substringAfterLast('/')
        binding.latestFileTime.text = AudioFormatUtils.formatDate(item.createdAt)
        setLatestEnabled(true)
    }

    private fun setLatestEnabled(enabled: Boolean) {
        binding.latestPlayButton.isEnabled = enabled
        binding.latestShareButton.isEnabled = enabled
        if (!enabled) binding.latestFileTime.text = ""
    }

    private fun playWav(wavPath: String) {
        latestAudioHandler.play(wavPath, playbackSpeed) { viewModel.stopPlayback() }
        viewModel.setPlaying()
    }

    override fun onDestroy() {
        super.onDestroy()
        latestAudioHandler.release()
    }

}
