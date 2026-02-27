package com.k2fsa.sherpa.tts.ui.history

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.k2fsa.sherpa.tts.databinding.ItemHistoryBinding
import com.k2fsa.sherpa.tts.util.AudioHistoryItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryAdapter(
    private val onShare: (String) -> Unit,
    private val onExport: (String) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.VH>() {

    private val items = mutableListOf<AudioHistoryItem>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    fun submit(newItems: List<AudioHistoryItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class VH(private val binding: ItemHistoryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: AudioHistoryItem) {
            val name = item.path.substringAfterLast('/')
            binding.fileName.text = name
            binding.fileTime.text = dateFormat.format(Date(item.createdAt))
            binding.shareButton.setOnClickListener { onShare(item.path) }
            binding.exportButton.setOnClickListener { onExport(item.path) }
        }
    }
}
