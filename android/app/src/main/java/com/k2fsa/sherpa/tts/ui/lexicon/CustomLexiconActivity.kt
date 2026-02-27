package com.k2fsa.sherpa.tts.ui.lexicon

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.k2fsa.sherpa.tts.R
import com.k2fsa.sherpa.tts.databinding.ActivityCustomLexiconBinding
import java.io.File

class CustomLexiconActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCustomLexiconBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCustomLexiconBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val lexiconFile = File(filesDir, "lexicons").apply { mkdirs() }
            .resolve("custom_lexicon.txt")

        if (lexiconFile.exists()) {
            binding.lexiconInput.setText(lexiconFile.readText())
        }

        binding.saveButton.setOnClickListener {
            val text = binding.lexiconInput.text?.toString().orEmpty()
            val invalidLine = findInvalidLine(text)
            if (invalidLine != null) {
                Toast.makeText(
                    this,
                    getString(R.string.lexicon_invalid_line, invalidLine),
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }
            lexiconFile.writeText(text.trimEnd())
            val result = Intent().putExtra(EXTRA_LEXICON_PATH, lexiconFile.absolutePath)
            setResult(RESULT_OK, result)
            finish()
        }

        binding.cancelButton.setOnClickListener { finish() }
    }

    private fun findInvalidLine(text: String): String? {
        val lines = text.split("\n")
        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val parts = line.split(Regex("\\s+"))
            if (parts.size < 2) return raw
        }
        return null
    }

    companion object {
        const val EXTRA_LEXICON_PATH = "lexicon_path"
    }
}
