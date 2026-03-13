package com.k2fsa.sherpa.tts.ui.history

import android.net.Uri
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.k2fsa.sherpa.tts.R
import com.k2fsa.sherpa.tts.databinding.ActivityHistoryBinding
import com.k2fsa.sherpa.tts.util.AudioHistoryItem
import com.k2fsa.sherpa.tts.util.AudioFileActions
import com.k2fsa.sherpa.tts.util.AudioHistoryStore
import com.k2fsa.sherpa.tts.util.AudioMetadataReader
import com.k2fsa.sherpa.tts.util.AudioPlaybackController
import com.k2fsa.sherpa.tts.util.TtsPreferences
import java.io.File

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private lateinit var adapter: HistoryAdapter
    private var pendingExportPath: String? = null
    private val playbackController = AudioPlaybackController()
    private val handler = Handler(Looper.getMainLooper())
    private var playbackSpeed: Float = 1.0f
    private val ttsPrefs by lazy { TtsPreferences(this) }
    private val audioFileActions by lazy { AudioFileActions(this) }
    private val audioMetadataReader by lazy { AudioMetadataReader() }
    private val progressUpdater = object : Runnable {
        override fun run() {
            val path = playbackController.playingPath ?: return
            if (playbackController.isPlaying) {
                if (!File(path).exists()) {
                    stopPlayback()
                    return
                }
                adapter.updatePlayback(path, playbackController.currentPosition, true)
                handler.postDelayed(this, 500)
            }
        }
    }

    private val exportAudio = registerForActivityResult(
        ActivityResultContracts.CreateDocument("audio/wav")
    ) { uri: Uri? ->
        val srcPath = pendingExportPath ?: return@registerForActivityResult
        pendingExportPath = null
        if (uri == null) return@registerForActivityResult
        audioFileActions.export(srcPath, uri) {
            AudioHistoryStore.removeItem(this, srcPath)
            loadHistory()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        playbackSpeed = ttsPrefs.playbackSpeed

        adapter = HistoryAdapter(
            onPlay = { togglePlay(it) },
            onShare = { shareAudio(it) },
            onExport = { path ->
                pendingExportPath = path
                exportAudio.launch(File(path).name)
            },
            onDelete = { deleteItem(it) },
            onRename = { renameItem(it) },
            onToggleFavorite = { toggleFavorite(it) },
            onSeekTo = { path, pos -> seekTo(path, pos) }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        loadHistory()
        binding.backButton.setOnClickListener { finish() }
        binding.clearButton.setOnClickListener { clearHistory() }
        binding.speedSlider.value = playbackSpeed
        binding.speedSlider.addOnChangeListener { _, value, _ ->
            playbackSpeed = value
            binding.speedText.text = String.format("%.1fx", value)
            playbackController.updatePlaybackSpeed(value)
            ttsPrefs.playbackSpeed = value
        }
        binding.speedText.text = String.format("%.1fx", binding.speedSlider.value)
    }

    private fun loadHistory() {
        val items = AudioHistoryStore.getItems(this).toMutableList()
        val valid = ArrayList<AudioHistoryItem>()
        items.forEach { item ->
            if (File(item.path).exists()) {
                valid.add(item)
            } else {
                AudioHistoryStore.removeItem(this, item.path)
            }
        }
        val rows = valid.map { item ->
            HistoryRow(item, audioMetadataReader.getDurationMs(item.path), audioMetadataReader.getSizeBytes(item.path))
        }
        adapter.submitList(rows)
        binding.emptyView.visibility = if (valid.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        syncPlaybackState()
    }

    private fun shareAudio(path: String) {
        audioFileActions.share(path) {
            AudioHistoryStore.removeItem(this, path)
            loadHistory()
        }
    }

    private fun togglePlay(path: String) {
        if (!playbackController.canPlay(path)) {
            Toast.makeText(this, getString(R.string.toast_audio_missing), Toast.LENGTH_SHORT).show()
            AudioHistoryStore.removeItem(this, path)
            loadHistory()
            return
        }
        val state = playbackController.toggle(path, playbackSpeed) {
            stopPlayback()
        }
        adapter.updatePlayback(state.path, state.positionMs, state.isPlaying)
        if (state.isPlaying) {
            handler.post(progressUpdater)
        } else {
            handler.removeCallbacks(progressUpdater)
        }
    }

    private fun stopPlayback() {
        handler.removeCallbacks(progressUpdater)
        playbackController.stop()
        adapter.updatePlayback(null, 0, false)
    }

    private fun deleteItem(path: String) {
        if (playbackController.playingPath == path) {
            stopPlayback()
        }
        val deleted = File(path).delete()
        if (!deleted) {
            Toast.makeText(this, getString(R.string.toast_delete_failed), Toast.LENGTH_SHORT).show()
        }
        AudioHistoryStore.removeItem(this, path)
        loadHistory()
    }

    private fun clearHistory() {
        stopPlayback()
        AudioHistoryStore.getItems(this).forEach {
            if (!File(it.path).delete()) {
                Toast.makeText(this, getString(R.string.toast_delete_failed), Toast.LENGTH_SHORT).show()
            }
        }
        AudioHistoryStore.clear(this)
        loadHistory()
    }

    private fun seekTo(path: String, positionMs: Int) {
        if (playbackController.playingPath != path) {
            togglePlay(path)
        }
        val state = playbackController.seekTo(positionMs)
        adapter.updatePlayback(path, state.positionMs, state.isPlaying)
    }

    private fun renameItem(path: String) {
        audioFileActions.rename(
            path = path,
            onMissing = {
                AudioHistoryStore.removeItem(this, path)
                loadHistory()
            },
            onRenamed = { newPath, createdAt, favorite ->
                val item = AudioHistoryItem(newPath, createdAt, favorite)
                if (playbackController.playingPath == path) {
                    val currentPos = playbackController.currentPosition
                    val wasPlaying = playbackController.isPlaying
                    playbackController.play(newPath, playbackSpeed) {
                        stopPlayback()
                    }
                    playbackController.seekTo(currentPos)
                    if (!wasPlaying) {
                        val state = playbackController.toggle(newPath, playbackSpeed) {
                            stopPlayback()
                        }
                        adapter.updatePlayback(state.path, state.positionMs, state.isPlaying)
                    }
                }
                AudioHistoryStore.removeItem(this, path)
                AudioHistoryStore.addItem(this, item)
                loadHistory()
            }
        )
    }

    private fun toggleFavorite(path: String) {
        AudioHistoryStore.toggleFavorite(this, path)
        loadHistory()
    }

    private fun syncPlaybackState() {
        val path = playbackController.playingPath
        if (path == null) {
            adapter.updatePlayback(null, 0, false)
            return
        }
        if (!File(path).exists()) {
            stopPlayback()
            return
        }
        adapter.updatePlayback(path, playbackController.currentPosition, playbackController.isPlaying)
    }

    override fun onStop() {
        super.onStop()
        stopPlayback()
    }
}
