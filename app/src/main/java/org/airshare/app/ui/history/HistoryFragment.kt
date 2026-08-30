package org.airshare.app.ui.history

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.airshare.app.AirShareApplication
import org.airshare.app.R
import org.airshare.app.data.local.TransferEntity
import org.airshare.app.data.model.MediaCategory
import org.airshare.app.data.model.TransferFile
import org.airshare.app.databinding.FragmentHistoryBinding
import org.airshare.app.ui.send.SendActivity
import org.airshare.app.ui.theme.ThemeManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: HistoryAdapter
    private var allHistoryItems: List<TransferEntity> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = HistoryAdapter(
            onItemClicked = { entity -> openFile(entity) },
            onResendClicked = { entity -> resendFile(entity) }
        )

        binding.rvHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.rvHistory.adapter = adapter

        binding.tabLayoutHistory.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                filterHistory(tab?.position ?: 0)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        binding.btnClearHistory.setOnClickListener {
            lifecycleScope.launch {
                AirShareApplication.instance.transferRepository.clearHistory()
                Toast.makeText(requireContext(), "Transfer history cleared", Toast.LENGTH_SHORT).show()
            }
        }

        lifecycleScope.launch {
            AirShareApplication.instance.transferRepository.allTransfers.collectLatest { list ->
                allHistoryItems = list
                filterHistory(binding.tabLayoutHistory.selectedTabPosition)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            ThemeManager.activePresetFlow.collectLatest {
                applyDynamicTheme()
            }
        }
    }

    private fun applyDynamicTheme() {
        if (_binding == null) return
        val activeColor = ThemeManager.getActiveColorInt(requireContext())
        binding.btnClearHistory.setTextColor(activeColor)
        binding.tabLayoutHistory.setTabTextColors(
            requireContext().getColor(R.color.text_dark_secondary),
            activeColor
        )
        adapter.notifyDataSetChanged()
    }

    private fun filterHistory(tabIndex: Int) {
        val filtered = when (tabIndex) {
            1 -> allHistoryItems.filter { it.isIncoming }
            2 -> allHistoryItems.filter { !it.isIncoming }
            else -> allHistoryItems
        }
        adapter.submitList(filtered)
        binding.layoutEmptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        binding.rvHistory.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun openFile(entity: TransferEntity) {
        val path = entity.localFilePath
        if (path == null) {
            Toast.makeText(requireContext(), "File path not available", Toast.LENGTH_SHORT).show()
            return
        }

        val file = File(path)
        if (!file.exists()) {
            Toast.makeText(requireContext(), "File does not exist on disk", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, entity.mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Open file with"))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Cannot open file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun resendFile(entity: TransferEntity) {
        val path = entity.localFilePath
        if (path != null && File(path).exists()) {
            val intent = Intent(requireContext(), SendActivity::class.java)
            startActivity(intent)
        } else {
            Toast.makeText(requireContext(), "File not found to resend", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    class HistoryAdapter(
        private val onItemClicked: (TransferEntity) -> Unit,
        private val onResendClicked: (TransferEntity) -> Unit
    ) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

        private var items = listOf<TransferEntity>()

        fun submitList(newItems: List<TransferEntity>) {
            items = newItems
            notifyDataSetChanged()
        }

        inner class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val icon: ImageView = itemView.findViewById(R.id.ivCategoryIcon)
            val fileName: TextView = itemView.findViewById(R.id.tvFileName)
            val meta: TextView = itemView.findViewById(R.id.tvMetaDetails)
            val badge: TextView = itemView.findViewById(R.id.tvDirectionBadge)
            val btnResend: ImageView = itemView.findViewById(R.id.btnActionResend)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_transfer_history, parent, false)
            return HistoryViewHolder(view)
        }

        override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
            val item = items[position]
            val activeColor = ThemeManager.getActiveColorInt(holder.itemView.context)

            holder.fileName.text = item.fileName
            val dateStr = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(item.timestamp))
            val sizeStr = TransferFile.formatByteSize(item.fileSize)
            holder.meta.text = "$sizeStr • ${item.peerName} • $dateStr"

            if (item.isIncoming) {
                holder.badge.text = "RECEIVED"
                holder.badge.setTextColor(activeColor)
                ThemeManager.applySubtlePillBackground(holder.badge)
                holder.btnResend.visibility = View.VISIBLE
                holder.btnResend.setColorFilter(activeColor)
            } else {
                holder.badge.text = "SENT"
                holder.badge.setTextColor(holder.itemView.context.getColor(R.color.text_dark_secondary))
                holder.btnResend.visibility = View.GONE
            }

            val iconRes = when (item.category) {
                MediaCategory.PHOTOS -> R.drawable.ic_category_photo
                MediaCategory.VIDEOS -> R.drawable.ic_category_video
                MediaCategory.MUSIC -> R.drawable.ic_category_music
                MediaCategory.APPS -> R.drawable.ic_category_apk
                MediaCategory.DOCS -> R.drawable.ic_category_doc
                else -> R.drawable.ic_category_file
            }
            holder.icon.setImageResource(iconRes)

            holder.itemView.setOnClickListener { onItemClicked(item) }
            holder.btnResend.setOnClickListener { onResendClicked(item) }
        }

        override fun getItemCount(): Int = items.size
    }
}
