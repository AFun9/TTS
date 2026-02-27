package com.k2fsa.sherpa.tts.ui.history

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.k2fsa.sherpa.tts.R
import com.k2fsa.sherpa.tts.databinding.ActivityHistoryBinding
import com.k2fsa.sherpa.tts.util.AudioHistoryStore
import java.io.File

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private lateinit var adapter: HistoryAdapter
    private var pendingExportPath: String? = null

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

        adapter = HistoryAdapter(
            onShare = { shareAudio(it) },
            onExport = { path ->
                pendingExportPath = path
                exportAudio.launch(File(path).name)
            }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        loadHistory()
        binding.backButton.setOnClickListener { finish() }
    }

    private fun loadHistory() {
        val items = AudioHistoryStore.getItems(this)
        adapter.submit(items)
        binding.emptyView.visibility = if (items.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun shareAudio(path: String) {
        val file = File(path)
        if (!file.exists()) {
            Toast.makeText(this, getString(R.string.toast_audio_missing), Toast.LENGTH_SHORT).show()
            return
        }
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/wav"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_audio)))
    }

    private fun exportToUri(srcPath: String, dest: Uri) {
        val src = File(srcPath)
        if (!src.exists()) {
            Toast.makeText(this, getString(R.string.toast_audio_missing), Toast.LENGTH_SHORT).show()
            return
        }
        contentResolver.openOutputStream(dest)?.use { out ->
            src.inputStream().use { input ->
                input.copyTo(out)
            }
        }
        Toast.makeText(this, getString(R.string.toast_export_success), Toast.LENGTH_SHORT).show()
    }
}
