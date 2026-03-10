package com.k2fsa.sherpa.tts.ui.main

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.content.Intent
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModelProvider
import com.k2fsa.sherpa.tts.R
import com.k2fsa.sherpa.tts.databinding.ActivityMainBinding
import com.k2fsa.sherpa.tts.repository.TTSRepository
import com.k2fsa.sherpa.tts.ui.history.HistoryActivity
import com.k2fsa.sherpa.tts.ui.lexicon.CustomLexiconActivity
import com.k2fsa.sherpa.tts.util.AudioHistoryItem
import com.k2fsa.sherpa.tts.util.AudioHistoryStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private var mediaPlayer: MediaPlayer? = null
    private val ttsRepository by lazy {
        TTSRepository(this, File(filesDir, "tts_output").apply { mkdirs() })
    }

    private val prefs by lazy { getSharedPreferences("tts_prefs", MODE_PRIVATE) }
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
        copyToAppDir(uri, modelDir) { path ->
            viewModel.setModelPath(path)
            prefs.edit().putString(KEY_MODEL_PATH, path).apply()
            Toast.makeText(this, getString(R.string.toast_file_copied), Toast.LENGTH_SHORT).show()
        }
    }

    private val pickTokens = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        copyToAppDir(uri, tokensDir) { path ->
            viewModel.setTokensPath(path)
            prefs.edit().putString(KEY_TOKENS_PATH, path).apply()
            Toast.makeText(this, getString(R.string.toast_file_copied), Toast.LENGTH_SHORT).show()
        }
    }

    private val pickLexicon = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        copyToAppDir(uri, lexiconDir) { path ->
            viewModel.setLexiconPath(path)
            prefs.edit().putString(KEY_LEXICON_PATH, path).apply()
            Toast.makeText(this, getString(R.string.toast_file_copied), Toast.LENGTH_SHORT).show()
        }
    }

    private val editCustomLexicon = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data ?: return@registerForActivityResult
        val path = data.getStringExtra(CustomLexiconActivity.EXTRA_LEXICON_PATH) ?: return@registerForActivityResult
        viewModel.setLexiconPath(path)
        prefs.edit().putString(KEY_LEXICON_PATH, path).apply()
        Toast.makeText(this, getString(R.string.toast_lexicon_updated), Toast.LENGTH_SHORT).show()
    }

    private val exportAudio = registerForActivityResult(
        ActivityResultContracts.CreateDocument("audio/wav")
    ) { uri: Uri? ->
        val srcPath = pendingExportPath ?: return@registerForActivityResult
        pendingExportPath = null
        if (uri == null) return@registerForActivityResult
        exportToUri(srcPath, uri)
    }

    private fun copyToAppDir(uri: Uri, destDir: File, onDone: (String) -> Unit) {
        lifecycleScope.launch {
            val path = withContext(Dispatchers.IO) {
                val name = contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx) else null
                    } else null
                } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "file"
                val dest = File(destDir, name)
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(dest).use { output ->
                        input.copyTo(output)
                    }
                    dest.absolutePath
                }
            }
            path?.let { onDone(it) }
        }
    }

    companion object {
        private const val KEY_MODEL_PATH = "model_path"
        private const val KEY_TOKENS_PATH = "tokens_path"
        private const val KEY_LEXICON_PATH = "lexicon_path"
        private const val KEY_AUTO_PLAY = "auto_play"
        private const val KEY_PLAYBACK_SPEED = "playback_speed"
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
        prefs.getString(KEY_MODEL_PATH, null)?.let { viewModel.setModelPath(it) }
        prefs.getString(KEY_TOKENS_PATH, null)?.let { viewModel.setTokensPath(it) }
        prefs.getString(KEY_LEXICON_PATH, null)?.let { viewModel.setLexiconPath(it) }
        autoPlayEnabled = prefs.getBoolean(KEY_AUTO_PLAY, true)
        playbackSpeed = prefs.getFloat(KEY_PLAYBACK_SPEED, 1.0f)
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
            latestAudioPath?.let { shareAudio(it) }
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
            latestAudioPath?.let { renameLatest(it) }
        }
        binding.autoPlaySwitch.isChecked = autoPlayEnabled
        binding.autoPlaySwitch.setOnCheckedChangeListener { _, isChecked ->
            autoPlayEnabled = isChecked
            prefs.edit().putBoolean(KEY_AUTO_PLAY, isChecked).apply()
        }
        binding.playbackSpeedSlider.value = playbackSpeed
        binding.playbackSpeedText.text = String.format("%.1fx", playbackSpeed)
        binding.playbackSpeedSlider.addOnChangeListener { _, value, _ ->
            playbackSpeed = value
            prefs.edit().putFloat(KEY_PLAYBACK_SPEED, value).apply()
            binding.playbackSpeedText.text = String.format("%.1fx", value)
            applyPlaybackSpeed()
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
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
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
        val file = File(wavPath)
        if (!file.exists()) return
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            prepare()
            applyPlaybackSpeed()
            setOnCompletionListener {
                viewModel.stopPlayback()
            }
            start()
        }
        viewModel.setPlaying()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun applyPlaybackSpeed() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            try {
                val params = mediaPlayer?.playbackParams?.setSpeed(playbackSpeed)
                if (params != null) mediaPlayer?.playbackParams = params
            } catch (_: Throwable) {
                // 일부 기기에서 playbackParams 가 실패할 수 있어 무시
            }
        }
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

    private fun renameLatest(path: String) {
        val file = File(path)
        if (!file.exists()) {
            Toast.makeText(this, getString(R.string.toast_audio_missing), Toast.LENGTH_SHORT).show()
            AudioHistoryStore.removeItem(this, path)
            latestAudioPath = null
            setLatestEnabled(false)
            return
        }
        val input = android.widget.EditText(this).apply {
            setText(file.nameWithoutExtension)
        }
        val origin = AudioHistoryStore.getItems(this).firstOrNull { it.path == path }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.rename))
            .setView(input)
            .setPositiveButton(getString(R.string.ok)) { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isBlank()) return@setPositiveButton
                val target = File(file.parentFile, "$newName.wav")
                if (target.exists()) {
                    Toast.makeText(this, getString(R.string.toast_name_exists), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (file.renameTo(target)) {
                    val item = AudioHistoryItem(
                        target.absolutePath,
                        origin?.createdAt ?: System.currentTimeMillis(),
                        origin?.favorite ?: false
                    )
                    AudioHistoryStore.removeItem(this, path)
                    AudioHistoryStore.addItem(this, item)
                    updateLatest(item)
                } else {
                    Toast.makeText(this, getString(R.string.toast_rename_failed), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
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

    private fun shareAudio(path: String) {
        val file = File(path)
        if (!file.exists()) {
            Toast.makeText(this, getString(R.string.toast_audio_missing), Toast.LENGTH_SHORT).show()
            AudioHistoryStore.removeItem(this, path)
            latestAudioPath = null
            setLatestEnabled(false)
            return
        }
        try {
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/wav"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.share_audio)))
        } catch (_: Throwable) {
            Toast.makeText(this, getString(R.string.toast_share_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportToUri(srcPath: String, dest: Uri) {
        val src = File(srcPath)
        if (!src.exists()) {
            Toast.makeText(this, getString(R.string.toast_audio_missing), Toast.LENGTH_SHORT).show()
            AudioHistoryStore.removeItem(this, srcPath)
            latestAudioPath = null
            setLatestEnabled(false)
            return
        }
        try {
            val out = contentResolver.openOutputStream(dest)
            if (out == null) {
                Toast.makeText(this, getString(R.string.toast_export_failed), Toast.LENGTH_SHORT).show()
                return
            }
            out.use { output ->
                src.inputStream().use { input ->
                    input.copyTo(output)
                }
            }
            Toast.makeText(this, getString(R.string.toast_export_success), Toast.LENGTH_SHORT).show()
        } catch (_: Throwable) {
            Toast.makeText(this, getString(R.string.toast_export_failed), Toast.LENGTH_SHORT).show()
        }
    }
}
