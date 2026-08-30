package org.airshare.app.ui.send

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
import kotlinx.coroutines.launch
import org.airshare.app.R
import org.airshare.app.data.model.MediaCategory
import org.airshare.app.data.model.TransferFile
import org.airshare.app.databinding.FragmentFileCategoryBinding

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

        lifecycleScope.launch {
            sendViewModel.categoryFilesMap.collectLatest { filesMap ->
                val list = filesMap[category] ?: emptyList()
                adapter.submitFiles(list)
                binding.layoutCategoryEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                binding.rvCategoryFiles.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
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
            holder.name.text = file.name
            holder.size.text = file.formattedSize
            holder.checkbox.isChecked = selectedIds.contains(file.id)

            if (file.category == MediaCategory.PHOTOS || file.category == MediaCategory.VIDEOS) {
                Glide.with(holder.itemView)
                    .load(file.localFilePath ?: file.uriString)
                    .centerCrop()
                    .placeholder(R.drawable.ic_category_photo)
                    .into(holder.thumbnail)
            } else {
                val iconRes = when (file.category) {
                    MediaCategory.APPS -> R.drawable.ic_category_apk
                    MediaCategory.MUSIC -> R.drawable.ic_category_music
                    MediaCategory.DOCS -> R.drawable.ic_category_doc
                    else -> R.drawable.ic_category_file
                }
                holder.thumbnail.setImageResource(iconRes)
            }

            holder.itemView.setOnClickListener {
                onItemToggled(file)
            }
        }

        override fun getItemCount(): Int = files.size
    }
}
