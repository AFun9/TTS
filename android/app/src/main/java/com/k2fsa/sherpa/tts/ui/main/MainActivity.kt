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
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModelProvider
import com.k2fsa.sherpa.tts.R
import com.k2fsa.sherpa.tts.databinding.ActivityMainBinding
import com.k2fsa.sherpa.tts.repository.TTSRepository
import com.k2fsa.sherpa.tts.ui.lexicon.CustomLexiconActivity
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
    }

    private fun setupUI() {
        binding.selectModelButton.setOnClickListener { pickModel.launch(arrayOf("*/*")) }
        binding.selectTokensButton.setOnClickListener { pickTokens.launch(arrayOf("text/*", "*/*")) }
        binding.selectLexiconButton.setOnClickListener { pickLexicon.launch(arrayOf("text/*", "*/*")) }
        binding.editLexiconButton.setOnClickListener {
            editCustomLexicon.launch(Intent(this, CustomLexiconActivity::class.java))
        }

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
                    }
                    is MainUiState.Loading -> {
                        binding.progressBar.visibility = android.view.View.VISIBLE
                        binding.progressBar.progress = 0
                        binding.generateButton.isEnabled = false
                        binding.statusText.text = "正在生成语音..."
                    }
                    is MainUiState.Success -> {
                        binding.progressBar.visibility = android.view.View.GONE
                        binding.generateButton.isEnabled = true
                        binding.statusText.text = "生成成功"
                        Toast.makeText(this@MainActivity, "语音生成成功", Toast.LENGTH_SHORT).show()
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
                playWav(wavPath)
            }
            .launchIn(lifecycleScope)
    }

    private fun playWav(wavPath: String) {
        val file = File(wavPath)
        if (!file.exists()) return
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            prepare()
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
}
