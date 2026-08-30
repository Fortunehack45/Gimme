package org.airshare.app.ui.send

import android.content.res.ColorStateList
import android.os.Bundle
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

class SendActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySendBinding
    private val viewModel: SendViewModel by viewModels()

    private val categories = listOf(
        MediaCategory.PHOTOS,
        MediaCategory.VIDEOS,
        MediaCategory.MUSIC,
        MediaCategory.APPS,
        MediaCategory.DOCS
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySendBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener {
            finish()
        }

        val adapter = CategoryPagerAdapter(this, categories)
        binding.viewPagerCategories.adapter = adapter

        TabLayoutMediator(binding.tabLayoutCategories, binding.viewPagerCategories) { tab, position ->
            tab.text = when (categories[position]) {
                MediaCategory.PHOTOS -> "Photos"
                MediaCategory.VIDEOS -> "Videos"
                MediaCategory.MUSIC -> "Music"
                MediaCategory.APPS -> "Apps"
                MediaCategory.DOCS -> "Docs"
                else -> "Files"
            }
        }.attach()

        binding.btnSelectAll.setOnClickListener {
            val currentPos = binding.viewPagerCategories.currentItem
            val currentCat = categories[currentPos]
            viewModel.selectAllForCategory(currentCat)
        }

        lifecycleScope.launch {
            viewModel.selectedFiles.collectLatest { selectedSet ->
                val count = selectedSet.size
                val totalBytes = selectedSet.sumOf { it.size }

                binding.tvSelectionCount.text = "$count file${if (count == 1) "" else "s"} selected"
                binding.tvSelectionSize.text = "${TransferFile.formatByteSize(totalBytes)} total"
                binding.btnProceedSend.isEnabled = count > 0
            }
        }

        binding.btnProceedSend.setOnClickListener {
            val dialog = DeviceDiscoveryDialogFragment()
            dialog.show(supportFragmentManager, "DeviceDiscoveryDialog")
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
