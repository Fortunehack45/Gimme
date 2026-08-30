package org.airshare.app.ui.send

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.checkbox.MaterialCheckBox
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.airshare.app.R
import org.airshare.app.data.model.MediaCategory
import org.airshare.app.data.model.TransferFile
import org.airshare.app.databinding.FragmentFileCategoryBinding
import org.airshare.app.ui.theme.ThemeManager

class FileCategoryFragment : Fragment() {

    private var _binding: FragmentFileCategoryBinding? = null
    private val binding get() = _binding!!

    private val sendViewModel: SendViewModel by activityViewModels()
    private lateinit var category: MediaCategory
    private lateinit var adapter: CategoryFilesAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val catName = arguments?.getString(ARG_CATEGORY) ?: MediaCategory.PHOTOS.name
        category = MediaCategory.valueOf(catName)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFileCategoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = CategoryFilesAdapter(
            onItemToggled = { file -> sendViewModel.toggleFileSelection(file) }
        )

        binding.rvCategoryFiles.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCategoryFiles.adapter = adapter

        sendViewModel.loadCategory(category)

        lifecycleScope.launch {
            sendViewModel.isLoadingCategory.collectLatest { loadingMap ->
                val isLoading = loadingMap[category] ?: false
                binding.progressBarLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
            }
        }

        // Combine category files with active search query for instant search filtering
        lifecycleScope.launch {
            combine(
                sendViewModel.categoryFilesMap,
                sendViewModel.searchQuery
            ) { filesMap, query ->
                val allCategoryFiles = filesMap[category] ?: emptyList()
                if (query.isBlank()) {
                    allCategoryFiles
                } else {
                    allCategoryFiles.filter { it.name.contains(query, ignoreCase = true) || it.packageName?.contains(query, ignoreCase = true) == true }
                }
            }.collectLatest { filteredList ->
                adapter.submitFiles(filteredList)
                binding.layoutCategoryEmpty.visibility = if (filteredList.isEmpty()) View.VISIBLE else View.GONE
                binding.rvCategoryFiles.visibility = if (filteredList.isEmpty()) View.GONE else View.VISIBLE
            }
        }

        lifecycleScope.launch {
            sendViewModel.selectedFiles.collectLatest { selectedSet ->
                adapter.setSelectedIds(selectedSet.map { it.id }.toSet())
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_CATEGORY = "arg_category"

        fun newInstance(category: MediaCategory): FileCategoryFragment {
            return FileCategoryFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_CATEGORY, category.name)
                }
            }
        }
    }

    class CategoryFilesAdapter(
        private val onItemToggled: (TransferFile) -> Unit
    ) : RecyclerView.Adapter<CategoryFilesAdapter.FileViewHolder>() {

        private var files = listOf<TransferFile>()
        private var selectedIds = setOf<String>()

        fun submitFiles(newFiles: List<TransferFile>) {
            files = newFiles
            notifyDataSetChanged()
        }

        fun setSelectedIds(newIds: Set<String>) {
            selectedIds = newIds
            notifyDataSetChanged()
        }

        inner class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val thumbnail: ImageView = itemView.findViewById(R.id.ivFileThumbnail)
            val name: TextView = itemView.findViewById(R.id.tvSelectableFileName)
            val size: TextView = itemView.findViewById(R.id.tvSelectableFileSize)
            val checkbox: MaterialCheckBox = itemView.findViewById(R.id.cbFileSelect)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_file_selectable, parent, false)
            return FileViewHolder(view)
        }

        override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
            val file = files[position]
            val context = holder.itemView.context
            val activeColor = ThemeManager.getActiveColorInt(context)

            holder.name.text = file.name
            holder.size.text = file.formattedSize
            holder.checkbox.isChecked = selectedIds.contains(file.id)
            holder.checkbox.buttonTintList = ColorStateList.valueOf(activeColor)

            if (file.category == MediaCategory.APPS) {
                // High-resolution real App Icon
                if (file.packageName != null) {
                    try {
                        val appIcon = context.packageManager.getApplicationIcon(file.packageName)
                        holder.thumbnail.setImageDrawable(appIcon)
                        holder.thumbnail.setPadding(0, 0, 0, 0)
                    } catch (e: Exception) {
                        holder.thumbnail.setImageResource(R.drawable.ic_category_apk)
                        holder.thumbnail.setPadding(8, 8, 8, 8)
                    }
                } else {
                    holder.thumbnail.setImageResource(R.drawable.ic_category_apk)
                    holder.thumbnail.setPadding(8, 8, 8, 8)
                }
            } else if (file.category == MediaCategory.PHOTOS || file.category == MediaCategory.VIDEOS) {
                holder.thumbnail.setPadding(0, 0, 0, 0)
                Glide.with(context)
                    .load(file.localFilePath ?: file.uriString)
                    .centerCrop()
                    .placeholder(R.drawable.ic_category_photo)
                    .into(holder.thumbnail)
            } else {
                holder.thumbnail.setPadding(8, 8, 8, 8)
                val iconRes = when (file.category) {
                    MediaCategory.MUSIC -> R.drawable.ic_category_music
                    MediaCategory.DOCS -> R.drawable.ic_category_doc
                    else -> R.drawable.ic_category_file
                }
                holder.thumbnail.setImageResource(iconRes)
            }

            holder.itemView.setOnClickListener { onItemToggled(file) }
        }

        override fun getItemCount(): Int = files.size
    }
}
