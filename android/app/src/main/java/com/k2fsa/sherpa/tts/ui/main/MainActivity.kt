package com.k2fsa.sherpa.tts.ui.main

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.content.Intent
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModelProvider
import com.k2fsa.sherpa.tts.R
import com.k2fsa.sherpa.tts.databinding.ActivityMainBinding
import com.k2fsa.sherpa.tts.repository.TTSRepository
import com.k2fsa.sherpa.tts.ui.history.HistoryActivity
import com.k2fsa.sherpa.tts.ui.lexicon.CustomLexiconActivity
import com.k2fsa.sherpa.tts.util.AudioHistoryItem
import com.k2fsa.sherpa.tts.util.AudioHistoryStore
import com.k2fsa.sherpa.tts.util.DocumentCopyHelper
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
    private val documentCopyHelper by lazy { DocumentCopyHelper(this) }
    private val latestAudioHandler by lazy { LatestAudioHandler(this) }
    private val modelDir by lazy { File(filesDir, "models").apply { mkdirs() } }
    private val tokensDir by lazy { File(filesDir, "tokens").apply { mkdirs() } }
    private val lexiconDir by lazy { File(filesDir, "lexicons").apply { mkdirs() } }
    private var latestAudioPath: String? = null
    private var pendingExportPath: String? = null
    private var autoPlayEnabled: Boolean = true
    private var playbackSpeed: Float = 1.0f
    private var lastErrorText: String = ""

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            initializeTTS()
        } else {
            Toast.makeText(this, "需要存储权限才能使用应用", Toast.LENGTH_LONG).show()
        }
    }

    private val pickModel = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        lifecycleScope.launch {
            documentCopyHelper.copyToAppDir(uri, modelDir)?.let { path ->
                viewModel.setModelPath(path)
                ttsPrefs.modelPath = path
                Toast.makeText(this@MainActivity, getString(R.string.toast_file_copied), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val pickTokens = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        lifecycleScope.launch {
            documentCopyHelper.copyToAppDir(uri, tokensDir)?.let { path ->
                viewModel.setTokensPath(path)
                ttsPrefs.tokensPath = path
                Toast.makeText(this@MainActivity, getString(R.string.toast_file_copied), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val pickLexicon = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        lifecycleScope.launch {
            documentCopyHelper.copyToAppDir(uri, lexiconDir)?.let { path ->
                viewModel.setLexiconPath(path)
                ttsPrefs.lexiconPath = path
                Toast.makeText(this@MainActivity, getString(R.string.toast_file_copied), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val editCustomLexicon = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data ?: return@registerForActivityResult
        val path = data.getStringExtra(CustomLexiconActivity.EXTRA_LEXICON_PATH) ?: return@registerForActivityResult
        viewModel.setLexiconPath(path)
        ttsPrefs.lexiconPath = path
        Toast.makeText(this, getString(R.string.toast_lexicon_updated), Toast.LENGTH_SHORT).show()
    }

    private val exportAudio = registerForActivityResult(
        ActivityResultContracts.CreateDocument("audio/wav")
    ) { uri: Uri? ->
        val srcPath = pendingExportPath ?: return@registerForActivityResult
        pendingExportPath = null
        if (uri == null) return@registerForActivityResult
        latestAudioHandler.export(srcPath, uri) {
            AudioHistoryStore.removeItem(this, srcPath)
            latestAudioPath = null
            setLatestEnabled(false)
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
        val history = AudioHistoryStore.getItems(this)
        val latest = history.firstOrNull { File(it.path).exists() }
        if (latest != null) {
            updateLatest(latest)
        } else if (history.isNotEmpty()) {
            history.forEach { AudioHistoryStore.removeItem(this, it.path) }
        }
    }

    private fun setupUI() {
        binding.selectModelButton.setOnClickListener { pickModel.launch(arrayOf("*/*")) }
        binding.selectTokensButton.setOnClickListener { pickTokens.launch(arrayOf("text/*", "*/*")) }
        binding.selectLexiconButton.setOnClickListener { pickLexicon.launch(arrayOf("text/*", "*/*")) }
        binding.editLexiconButton.setOnClickListener {
            editCustomLexicon.launch(Intent(this, CustomLexiconActivity::class.java))
        }
        binding.historyButton.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
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
        binding.latestExportButton.setOnClickListener {
            latestAudioPath?.let {
                pendingExportPath = it
                exportAudio.launch(File(it).name)
            }
        }
        binding.latestFavoriteButton.setOnClickListener {
            latestAudioPath?.let {
                AudioHistoryStore.toggleFavorite(this, it)
                updateLatestFromStore(it)
            }
        }
        binding.latestRenameButton.setOnClickListener {
            latestAudioPath?.let { path ->
                latestAudioHandler.rename(path,
                    onMissing = {
                        AudioHistoryStore.removeItem(this, path)
                        latestAudioPath = null
                        setLatestEnabled(false)
                    },
                    onRenamed = { newPath, createdAt, favorite ->
                        AudioHistoryStore.removeItem(this, path)
                        AudioHistoryStore.addItem(this, AudioHistoryItem(newPath, createdAt, favorite))
                        updateLatest(AudioHistoryItem(newPath, createdAt, favorite))
                    }
                )
            }
        }
        binding.autoPlaySwitch.isChecked = autoPlayEnabled
        binding.autoPlaySwitch.setOnCheckedChangeListener { _, isChecked ->
            autoPlayEnabled = isChecked
            ttsPrefs.autoPlay = isChecked
        }
        binding.playbackSpeedSlider.value = playbackSpeed
        binding.playbackSpeedText.text = String.format("%.1fx", playbackSpeed)
        binding.playbackSpeedSlider.addOnChangeListener { _, value, _ ->
            playbackSpeed = value
            ttsPrefs.playbackSpeed = value
            binding.playbackSpeedText.text = String.format("%.1fx", value)
            latestAudioHandler.updatePlaybackSpeed(value)
        }
        binding.diagnosticsButton.setOnClickListener { showDiagnostics() }
        setLatestEnabled(false)

        binding.generateButton.setOnClickListener {
            val text = binding.textInput.text.toString()
            when {
                !viewModel.isModelReady() -> Toast.makeText(
                    this,
                    getString(R.string.toast_select_model_first),
                    Toast.LENGTH_SHORT
                ).show()
                text.isBlank() -> Toast.makeText(this, getString(R.string.toast_enter_text), Toast.LENGTH_SHORT).show()
                else -> viewModel.generateSpeech(text)
            }
        }

        binding.stopButton.setOnClickListener {
            latestAudioHandler.stop()
            viewModel.stopPlayback()
        }

        binding.speedSlider.addOnChangeListener { _, value, _ ->
            binding.speedText.text = String.format("%.1fx", value)
            viewModel.setSpeed(value)
        }

        binding.volumeSlider.addOnChangeListener { _, value, _ ->
            binding.volumeText.text = String.format("%.0f%%", value)
            viewModel.setVolume(value.toInt())
        }

        // 初始显示与 ViewModel 同步
        binding.speedText.text = String.format("%.1fx", binding.speedSlider.value)
        binding.volumeText.text = String.format("%.0f%%", binding.volumeSlider.value)
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
                        binding.diagnosticsButton.visibility = android.view.View.GONE
                    }
                    is MainUiState.Loading -> {
                        binding.progressBar.visibility = android.view.View.VISIBLE
                        binding.progressBar.progress = 0
                        binding.generateButton.isEnabled = false
                        binding.statusText.text = "正在生成语音..."
                        binding.diagnosticsButton.visibility = android.view.View.GONE
                    }
                    is MainUiState.Success -> {
                        binding.progressBar.visibility = android.view.View.GONE
                        binding.generateButton.isEnabled = true
                        binding.statusText.text = "生成成功"
                        Toast.makeText(this@MainActivity, "语音生成成功", Toast.LENGTH_SHORT).show()
                        binding.diagnosticsButton.visibility = android.view.View.GONE
                    }
                    is MainUiState.Error -> {
                        binding.progressBar.visibility = android.view.View.GONE
                        binding.generateButton.isEnabled = true
                        binding.statusText.text = "生成失败: ${state.message}"
                        val toastMsg = if (state.message.contains("JNI") || state.message.contains("nativeCreate"))
                            getString(R.string.error_engine_not_ready)
                        else
                            "生成失败: ${state.message}"
                        Toast.makeText(this@MainActivity, toastMsg, Toast.LENGTH_LONG).show()
                        binding.diagnosticsButton.visibility = android.view.View.VISIBLE
                    }
                    is MainUiState.Playing -> {
                        binding.statusText.text = "正在播放"
                    }
                    is MainUiState.Stopped -> {
                        binding.statusText.text = "已停止"
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
            viewModel.modelPath.collect { path ->
                binding.modelPathText.text = if (path.isBlank()) getString(R.string.not_selected) else path.substringAfterLast('/')
            }
        }
        lifecycleScope.launch {
            viewModel.tokensPath.collect { path ->
                binding.tokensPathText.text = if (path.isBlank()) getString(R.string.not_selected) else path.substringAfterLast('/')
            }
        }
        lifecycleScope.launch {
            viewModel.lexiconPath.collect { path ->
                binding.lexiconPathText.text = if (path.isBlank()) getString(R.string.not_selected) else path.substringAfterLast('/')
            }
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
        val time = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(item.createdAt))
        binding.latestFileTime.text = time
        binding.latestFileInfo.text = buildLatestInfo(item.path)
        binding.latestFavoriteButton.text = if (item.favorite) {
            getString(R.string.favorited)
        } else {
            getString(R.string.favorite)
        }
        setLatestEnabled(true)
    }

    private fun setLatestEnabled(enabled: Boolean) {
        binding.latestShareButton.isEnabled = enabled
        binding.latestExportButton.isEnabled = enabled
        binding.latestFavoriteButton.isEnabled = enabled
        binding.latestRenameButton.isEnabled = enabled
        binding.latestFileTime.text = if (enabled) binding.latestFileTime.text else ""
        binding.latestFileInfo.text = if (enabled) binding.latestFileInfo.text else ""
    }

    private fun playWav(wavPath: String) {
        latestAudioHandler.play(wavPath, playbackSpeed) { viewModel.stopPlayback() }
        viewModel.setPlaying()
    }

    override fun onDestroy() {
        super.onDestroy()
        latestAudioHandler.release()
    }

    private fun buildLatestInfo(path: String): String {
        val duration = getDurationMs(path)
        val size = getSizeBytes(path)
        val durationText = if (duration > 0) formatDuration(duration) else ""
        val sizeText = if (size > 0) formatSize(size) else ""
        return listOf(durationText, sizeText).filter { it.isNotBlank() }.joinToString(" · ")
    }

    private fun getDurationMs(path: String): Long {
        val retriever = android.media.MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
        } catch (_: Throwable) {
            0L
        } finally {
            retriever.release()
        }
    }

    private fun getSizeBytes(path: String): Long {
        val file = File(path)
        return if (file.exists()) file.length() else 0L
    }

    private fun formatDuration(ms: Long): String {
        val totalSec = (ms / 1000).toInt()
        val m = totalSec / 60
        val s = totalSec % 60
        return String.format("%02d:%02d", m, s)
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return ""
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(java.util.Locale.getDefault(), "%.1f KB", kb)
        val mb = kb / 1024.0
        return String.format(java.util.Locale.getDefault(), "%.1f MB", mb)
    }

    private fun updateLatestFromStore(path: String) {
        val item = AudioHistoryStore.getItems(this).firstOrNull { it.path == path }
        if (item != null) updateLatest(item)
    }

    private fun showDiagnostics() {
        val message = if (lastErrorText.isBlank()) {
            "暂无诊断信息。"
        } else {
            "$lastErrorText\n\n建议：\n1. 检查 tokens.txt 与模型语言是否匹配。\n2. 确认 lexicon.txt 格式正确且词典命中。\n3. 查看 logcat -s SherpaTts 获取更详细的 native 日志。"
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.diagnostics))
            .setMessage(message)
            .setPositiveButton(getString(R.string.ok), null)
            .show()
    }

}
