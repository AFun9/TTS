package com.k2fsa.sherpa.tts.ui.history

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.k2fsa.sherpa.tts.databinding.ItemHistoryBinding
import com.k2fsa.sherpa.tts.util.AudioFormatUtils
import com.k2fsa.sherpa.tts.util.AudioHistoryItem

data class HistoryRow(
    val item: AudioHistoryItem,
    val durationMs: Long,
    val sizeBytes: Long
)

/**
 * 历史页列表适配器。
 *
 * 除了展示文件名、时间、大小等静态信息，还负责根据当前播放状态切换按钮文案、
 * 进度条与拖拽回调，因此播放中的临时状态也保存在适配器内部。
 */
class HistoryAdapter(
    private val onPlay: (String) -> Unit,
    private val onShare: (String) -> Unit,
    private val onExport: (String) -> Unit,
    private val onDelete: (String) -> Unit,
    private val onRename: (String) -> Unit,
    private val onToggleFavorite: (String) -> Unit,
    private val onSeekTo: (String, Int) -> Unit
) : ListAdapter<HistoryRow, HistoryAdapter.VH>(Diff) {

    private var playingPath: String? = null
    private var playingPosMs: Int = 0
    private var playing: Boolean = false
    private var trackingSeek: String? = null
    private var isSeeking: Boolean = false

    fun updatePlayback(path: String?, positionMs: Int, isPlaying: Boolean) {
        playingPath = path
        playingPosMs = positionMs
        playing = isPlaying
        if (isSeeking) return
        if (path == null) {
            notifyDataSetChanged()
            return
        }
        val idx = currentList.indexOfFirst { it.item.path == path }
        if (idx >= 0) {
            notifyItemChanged(idx)
        } else {
            notifyDataSetChanged()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(private val binding: ItemHistoryBinding) : androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root) {
        fun bind(row: HistoryRow) {
            val item = row.item
            val name = item.path.substringAfterLast('/')
            binding.fileName.text = name
            binding.fileTime.text = AudioFormatUtils.formatDate(item.createdAt)
            binding.durationText.text = AudioFormatUtils.formatDuration(row.durationMs)
            binding.sizeText.text = AudioFormatUtils.formatSize(row.sizeBytes)
            binding.playButton.text = if (item.path == playingPath && playing) {
                binding.root.context.getString(com.k2fsa.sherpa.tts.R.string.pause)
            } else {
                binding.root.context.getString(com.k2fsa.sherpa.tts.R.string.play)
            }
            binding.favoriteButton.text = if (item.favorite) {
                binding.root.context.getString(com.k2fsa.sherpa.tts.R.string.favorited)
            } else {
                binding.root.context.getString(com.k2fsa.sherpa.tts.R.string.favorite)
            }
            val isCurrent = item.path == playingPath
            val pos = if (isCurrent) playingPosMs else 0
            binding.progressText.text = if (isCurrent && row.durationMs > 0) {
                "${AudioFormatUtils.formatDuration(pos.toLong())} / ${AudioFormatUtils.formatDuration(row.durationMs)}"
            } else {
                ""
            }
            binding.seekBar.max = row.durationMs.toInt().coerceAtLeast(1)
            if (trackingSeek != item.path) {
                binding.seekBar.progress = pos.coerceAtLeast(0)
            }
            binding.seekBar.visibility = if (isCurrent) android.view.View.VISIBLE else android.view.View.GONE
            binding.progressText.visibility = if (isCurrent) android.view.View.VISIBLE else android.view.View.GONE
            binding.playButton.setOnClickListener { onPlay(item.path) }
            binding.shareButton.setOnClickListener { onShare(item.path) }
            binding.exportButton.setOnClickListener { onExport(item.path) }
            binding.deleteButton.setOnClickListener { onDelete(item.path) }
            binding.renameButton.setOnClickListener { onRename(item.path) }
            binding.favoriteButton.setOnClickListener { onToggleFavorite(item.path) }
            binding.seekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        trackingSeek = item.path
                        isSeeking = true
                        binding.progressText.text =
                            "${AudioFormatUtils.formatDuration(progress.toLong())} / ${AudioFormatUtils.formatDuration(row.durationMs)}"
                    }
                }

                override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {
                    trackingSeek = item.path
                    isSeeking = true
                }

                override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {
                    val p = seekBar?.progress ?: 0
                    trackingSeek = null
                    isSeeking = false
                    onSeekTo(item.path, p)
                }
            })
        }
    }

    object Diff : DiffUtil.ItemCallback<HistoryRow>() {
        override fun areItemsTheSame(oldItem: HistoryRow, newItem: HistoryRow): Boolean {
            return oldItem.item.path == newItem.item.path
        }

        override fun areContentsTheSame(oldItem: HistoryRow, newItem: HistoryRow): Boolean {
            return oldItem == newItem
        }
    }
}
