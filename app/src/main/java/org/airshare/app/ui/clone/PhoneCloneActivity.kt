package org.airshare.app.ui.clone

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.checkbox.MaterialCheckBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.airshare.app.R
import org.airshare.app.data.model.CloneCategory
import org.airshare.app.data.model.CloneCategoryType
import org.airshare.app.data.model.MediaCategory
import org.airshare.app.data.model.TransferFile
import org.airshare.app.data.repository.FileRepository
import org.airshare.app.databinding.ActivityPhoneCloneBinding
import org.airshare.app.ui.receive.ReceiveActivity
import org.airshare.app.ui.send.DeviceDiscoveryDialogFragment
import org.airshare.app.ui.transfer.TransferProgressActivity

class PhoneCloneActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPhoneCloneBinding
    private lateinit var fileRepository: FileRepository
    private lateinit var adapter: CloneCategoriesAdapter
    private var categories = mutableListOf<CloneCategory>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPhoneCloneBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fileRepository = FileRepository(this)
        binding.btnCloneBack.setOnClickListener { finish() }

        adapter = CloneCategoriesAdapter { category ->
            category.isSelected = !category.isSelected
            adapter.notifyDataSetChanged()
            updateTotalSummary()
        }

        binding.rvCloneCategories.layoutManager = LinearLayoutManager(this)
        binding.rvCloneCategories.adapter = adapter

        setupRoleToggle()
        loadCategories()

        binding.btnStartClone.setOnClickListener {
            val isOldPhone = binding.toggleCloneRole.checkedButtonId == R.id.btnCloneOldPhone
            if (isOldPhone) {
                startOldPhoneSenderFlow()
            } else {
                startNewPhoneReceiverFlow()
            }
        }
    }

    private fun setupRoleToggle() {
        binding.toggleCloneRole.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                if (checkedId == R.id.btnCloneOldPhone) {
                    binding.tvCategoriesPrompt.visibility = View.VISIBLE
                    binding.rvCloneCategories.visibility = View.VISIBLE
                    binding.tvCloneTotalSummary.visibility = View.VISIBLE
                    binding.btnStartClone.text = "Start Phone Clone Migration (Send)"
                } else {
                    binding.tvCategoriesPrompt.visibility = View.GONE
                    binding.rvCloneCategories.visibility = View.GONE
                    binding.tvCloneTotalSummary.visibility = View.GONE
                    binding.btnStartClone.text = "Prepare New Phone to Receive Data"
                }
            }
        }
    }

    private fun loadCategories() {
        lifecycleScope.launch {
            categories = fileRepository.getCloneCategories().toMutableList()
            adapter.submitList(categories)
            updateTotalSummary()
        }
    }

    private fun updateTotalSummary() {
        val selected = categories.filter { it.isSelected }
        val totalCount = selected.sumOf { it.count }
        val totalBytes = selected.sumOf { it.totalBytes }
        binding.tvCloneTotalSummary.text = "Total selected: $totalCount items • ${TransferFile.formatByteSize(totalBytes)}"
        binding.btnStartClone.isEnabled = selected.isNotEmpty()
    }

    private fun startOldPhoneSenderFlow() {
        lifecycleScope.launch {
            val filesToClone = mutableListOf<TransferFile>()
            val selectedTypes = categories.filter { it.isSelected }.map { it.type }
            if (selectedTypes.contains(CloneCategoryType.PHOTOS)) filesToClone.addAll(fileRepository.getPhotos())
            if (selectedTypes.contains(CloneCategoryType.VIDEOS)) filesToClone.addAll(fileRepository.getVideos())
            if (selectedTypes.contains(CloneCategoryType.MUSIC)) filesToClone.addAll(fileRepository.getMusic())
            if (selectedTypes.contains(CloneCategoryType.APPS)) filesToClone.addAll(fileRepository.getInstalledApps())
            if (selectedTypes.contains(CloneCategoryType.DOCUMENTS)) filesToClone.addAll(fileRepository.getDocuments())

            if (filesToClone.isEmpty()) {
                Toast.makeText(this@PhoneCloneActivity, "No files found in selected categories", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val intent = Intent(this@PhoneCloneActivity, TransferProgressActivity::class.java).apply {
                putExtra(TransferProgressActivity.EXTRA_IS_SENDER, true)
                putExtra(TransferProgressActivity.EXTRA_FILES_LIST, ArrayList(filesToClone))
            }
            startActivity(intent)
            finish()
        }
    }

    private fun startNewPhoneReceiverFlow() {
        val intent = Intent(this, ReceiveActivity::class.java)
        startActivity(intent)
        finish()
    }

    class CloneCategoriesAdapter(
        private val onItemClicked: (CloneCategory) -> Unit
    ) : RecyclerView.Adapter<CloneCategoriesAdapter.ViewHolder>() {

        private var items = listOf<CloneCategory>()

        fun submitList(newList: List<CloneCategory>) {
            items = newList
            notifyDataSetChanged()
        }

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val icon: ImageView = itemView.findViewById(R.id.ivCloneIcon)
            val title: TextView = itemView.findViewById(R.id.tvCloneTitle)
            val subtitle: TextView = itemView.findViewById(R.id.tvCloneCountAndSize)
            val checkbox: MaterialCheckBox = itemView.findViewById(R.id.cbCloneSelect)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_clone_category, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.title.text = item.title
            holder.subtitle.text = "${item.count} items • ${item.formattedSize}"
            holder.checkbox.isChecked = item.isSelected

            val iconRes = when (item.type) {
                CloneCategoryType.PHOTOS -> R.drawable.ic_category_photo
                CloneCategoryType.VIDEOS -> R.drawable.ic_category_video
                CloneCategoryType.MUSIC -> R.drawable.ic_category_music
                CloneCategoryType.APPS -> R.drawable.ic_category_apk
                CloneCategoryType.DOCUMENTS -> R.drawable.ic_category_doc
                else -> R.drawable.ic_category_file
            }
            holder.icon.setImageResource(iconRes)

            holder.itemView.setOnClickListener { onItemClicked(item) }
        }

        override fun getItemCount(): Int = items.size
    }
}
