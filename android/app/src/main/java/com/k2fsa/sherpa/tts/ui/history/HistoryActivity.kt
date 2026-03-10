package com.k2fsa.sherpa.tts.ui.history

import android.content.Intent
import android.net.Uri
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.k2fsa.sherpa.tts.R
import com.k2fsa.sherpa.tts.databinding.ActivityHistoryBinding
import com.k2fsa.sherpa.tts.util.AudioHistoryItem
import com.k2fsa.sherpa.tts.util.AudioHistoryStore
import java.io.File

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private lateinit var adapter: HistoryAdapter
    private var pendingExportPath: String? = null
    private var mediaPlayer: MediaPlayer? = null
    private var playingPath: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private var playbackSpeed: Float = 1.0f
    private val prefs by lazy { getSharedPreferences("tts_prefs", MODE_PRIVATE) }
    private val progressUpdater = object : Runnable {
        override fun run() {
            val player = mediaPlayer ?: return
            val path = playingPath ?: return
            if (player.isPlaying) {
                if (!File(path).exists()) {
                    stopPlayback()
                    return
                }
                adapter.updatePlayback(path, player.currentPosition, true)
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
        exportToUri(srcPath, uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        playbackSpeed = prefs.getFloat(KEY_PLAYBACK_SPEED, 1.0f)

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
            applyPlaybackSpeed()
            prefs.edit().putFloat(KEY_PLAYBACK_SPEED, value).apply()
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
            HistoryRow(item, getDurationMs(item.path), getSizeBytes(item.path))
        }
        adapter.submitList(rows)
        binding.emptyView.visibility = if (valid.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        syncPlaybackState()
    }

    private fun shareAudio(path: String) {
        val file = File(path)
        if (!file.exists()) {
            Toast.makeText(this, getString(R.string.toast_audio_missing), Toast.LENGTH_SHORT).show()
            AudioHistoryStore.removeItem(this, path)
            loadHistory()
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
            loadHistory()
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

    private fun togglePlay(path: String) {
        val file = File(path)
        if (!file.exists()) {
            Toast.makeText(this, getString(R.string.toast_audio_missing), Toast.LENGTH_SHORT).show()
            AudioHistoryStore.removeItem(this, path)
            loadHistory()
            return
        }
        if (playingPath == path && mediaPlayer != null) {
            val player = mediaPlayer!!
            if (player.isPlaying) {
                player.pause()
                adapter.updatePlayback(path, player.currentPosition, false)
            } else {
                player.start()
                adapter.updatePlayback(path, player.currentPosition, true)
                handler.post(progressUpdater)
            }
            return
        }
        stopPlayback()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            prepare()
            setOnCompletionListener {
                stopPlayback()
            }
            start()
        }
        playingPath = path
        applyPlaybackSpeed()
        adapter.updatePlayback(path, 0, true)
        handler.post(progressUpdater)
    }

    private fun stopPlayback() {
        handler.removeCallbacks(progressUpdater)
        try {
            mediaPlayer?.release()
        } catch (_: Throwable) {
            // ignore
        }
        mediaPlayer = null
        playingPath = null
        adapter.updatePlayback(null, 0, false)
    }

    private fun deleteItem(path: String) {
        if (playingPath == path) {
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

    private fun getDurationMs(path: String): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
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

    private fun seekTo(path: String, positionMs: Int) {
        if (playingPath != path) {
            togglePlay(path)
        }
        mediaPlayer?.seekTo(positionMs)
        adapter.updatePlayback(path, positionMs, mediaPlayer?.isPlaying == true)
    }

    private fun renameItem(path: String) {
        val file = File(path)
        if (!file.exists()) {
            Toast.makeText(this, getString(R.string.toast_audio_missing), Toast.LENGTH_SHORT).show()
            AudioHistoryStore.removeItem(this, path)
            loadHistory()
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
                    if (playingPath == path) {
                        playingPath = target.absolutePath
                    }
                    AudioHistoryStore.removeItem(this, path)
                    AudioHistoryStore.addItem(this, item)
                    loadHistory()
                } else {
                    Toast.makeText(this, getString(R.string.toast_rename_failed), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun toggleFavorite(path: String) {
        AudioHistoryStore.toggleFavorite(this, path)
        loadHistory()
    }

    private fun syncPlaybackState() {
        val path = playingPath
        if (path == null || mediaPlayer == null) {
            adapter.updatePlayback(null, 0, false)
            return
        }
        if (!File(path).exists()) {
            stopPlayback()
            return
        }
        val player = mediaPlayer!!
        adapter.updatePlayback(path, player.currentPosition, player.isPlaying)
    }

    private fun applyPlaybackSpeed() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            try {
                val params = mediaPlayer?.playbackParams?.setSpeed(playbackSpeed)
                if (params != null) mediaPlayer?.playbackParams = params
            } catch (_: Throwable) {
            }
        }
    }

    override fun onStop() {
        super.onStop()
        stopPlayback()
    }

    companion object {
        private const val KEY_PLAYBACK_SPEED = "playback_speed"
    }
}
