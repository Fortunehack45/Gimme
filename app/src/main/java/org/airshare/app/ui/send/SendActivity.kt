package org.airshare.app.ui.send

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.airshare.app.R
import org.airshare.app.data.model.MediaCategory
import org.airshare.app.data.model.TransferFile
import org.airshare.app.databinding.ActivitySendBinding
import org.airshare.app.ui.theme.ThemeManager
import org.airshare.app.ui.transfer.TransferProgressActivity

class SendActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySendBinding
    private val viewModel: SendViewModel by viewModels()
    private var isPickerMode = false

    private val categories = listOf(
        MediaCategory.APPS,
        MediaCategory.PHOTOS,
        MediaCategory.VIDEOS,
        MediaCategory.MUSIC,
        MediaCategory.DOCS
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySendBinding.inflate(layoutInflater)
        setContentView(binding.root)

        isPickerMode = intent.getBooleanExtra(EXTRA_IS_PICKER_MODE, false) || callingActivity != null

        binding.btnBack.setOnClickListener {
            finish()
        }

        val adapter = CategoryPagerAdapter(this, categories)
        binding.viewPagerCategories.adapter = adapter

        TabLayoutMediator(binding.tabLayoutCategories, binding.viewPagerCategories) { tab, position ->
            tab.text = when (categories[position]) {
                MediaCategory.APPS -> "📱 Apps"
                MediaCategory.PHOTOS -> "🖼️ Photos"
                MediaCategory.VIDEOS -> "🎥 Videos"
                MediaCategory.MUSIC -> "🎵 Music"
                MediaCategory.DOCS -> "📄 Docs"
                else -> "📁 Files"
            }
        }.attach()

        // Real-time search query listener
        binding.etSearchQuery.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString() ?: ""
                viewModel.setSearchQuery(query)
                binding.btnClearSearch.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnClearSearch.setOnClickListener {
            binding.etSearchQuery.setText("")
            viewModel.setSearchQuery("")
        }

        binding.btnSelectAll.setOnClickListener {
            val currentPos = binding.viewPagerCategories.currentItem
            val currentCat = categories[currentPos]
            viewModel.selectAllForCategory(currentCat)
        }

        lifecycleScope.launch {
            viewModel.selectedFiles.collectLatest { selectedSet ->
                val count = selectedSet.size
                val totalBytes = selectedSet.sumOf { it.size }

                binding.tvSelectionCount.text = "$count item${if (count == 1) "" else "s"} selected"
                binding.tvSelectionSize.text = "${TransferFile.formatByteSize(totalBytes)} total"
                binding.btnProceedSend.isEnabled = count > 0

                if (isPickerMode) {
                    binding.btnProceedSend.text = "Confirm ($count)"
                } else {
                    binding.btnProceedSend.text = "Send"
                }
            }
        }

        binding.btnProceedSend.setOnClickListener {
            val selected = viewModel.selectedFiles.value.toList()
            if (isPickerMode) {
                val resultIntent = Intent().apply {
                    putExtra(TransferProgressActivity.EXTRA_FILES_LIST, ArrayList(selected))
                }
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            } else {
                val dialog = DeviceDiscoveryDialogFragment()
                dialog.show(supportFragmentManager, "DeviceDiscoveryDialog")
            }
        }

        lifecycleScope.launch {
            ThemeManager.activePresetFlow.collectLatest {
                applyDynamicSendTheme()
            }
        }
    }

    private fun applyDynamicSendTheme() {
        val activeColor = ThemeManager.getActiveColorInt(this)
        binding.btnSelectAll.setTextColor(activeColor)
        binding.tabLayoutCategories.setSelectedTabIndicatorColor(activeColor)
        binding.tabLayoutCategories.setTabTextColors(
            getColor(R.color.text_dark_secondary),
            activeColor
        )
        binding.btnProceedSend.backgroundTintList = ColorStateList.valueOf(activeColor)
    }

    companion object {
        const val EXTRA_IS_PICKER_MODE = "extra_is_picker_mode"
    }

    class CategoryPagerAdapter(
        activity: AppCompatActivity,
        private val list: List<MediaCategory>
    ) : FragmentStateAdapter(activity) {
        override fun getItemCount(): Int = list.size
        override fun createFragment(position: Int): Fragment {
            return FileCategoryFragment.newInstance(list[position])
        }
    }
}
