package org.airshare.app.ui.settings

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.airshare.app.AirShareApplication
import org.airshare.app.R
import org.airshare.app.databinding.FragmentSettingsBinding
import org.airshare.app.ui.theme.ThemeManager
import org.airshare.app.ui.theme.ThemePreset

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var themeSwatchesAdapter: ThemeSwatchesAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val settingsRepo = AirShareApplication.instance.settingsRepository

        binding.etDeviceName.setText(settingsRepo.deviceName)
        binding.switchPinRequired.isChecked = settingsRepo.isPinConfirmationRequired
        binding.tvSaveLocation.text = settingsRepo.saveDirectoryPath

        themeSwatchesAdapter = ThemeSwatchesAdapter { preset ->
            ThemeManager.setPreset(preset)
            binding.tvActiveThemeName.text = preset.title
            binding.tvActiveThemeName.setTextColor(ThemeManager.getActiveColorInt(requireContext()))
            applyDynamicSettingsTheme()
            Toast.makeText(requireContext(), "Accent color set to ${preset.title}", Toast.LENGTH_SHORT).show()
        }

        binding.rvThemeSwatches.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvThemeSwatches.adapter = themeSwatchesAdapter
        themeSwatchesAdapter.setSelectedPreset(ThemeManager.activePresetFlow.value)
        binding.tvActiveThemeName.text = ThemeManager.activePresetFlow.value.title

        binding.btnSaveDeviceName.setOnClickListener {
            val newName = binding.etDeviceName.text.toString().trim()
            if (newName.isNotBlank()) {
                settingsRepo.deviceName = newName
                Toast.makeText(requireContext(), "Device name saved: '$newName'", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Device name cannot be blank", Toast.LENGTH_SHORT).show()
            }
        }

        binding.switchPinRequired.setOnCheckedChangeListener { _, isChecked ->
            settingsRepo.isPinConfirmationRequired = isChecked
            val stateText = if (isChecked) "enabled" else "disabled"
            Toast.makeText(requireContext(), "PIN confirmation $stateText", Toast.LENGTH_SHORT).show()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            ThemeManager.activePresetFlow.collectLatest { preset ->
                themeSwatchesAdapter.setSelectedPreset(preset)
                binding.tvActiveThemeName.text = preset.title
                applyDynamicSettingsTheme()
            }
        }
    }

    private fun applyDynamicSettingsTheme() {
        if (_binding == null) return
        ThemeManager.applyThemeToPrimaryButton(binding.btnSaveDeviceName)
        ThemeManager.applyThemeToTextView(binding.tvActiveThemeName)
        ThemeManager.applyThemeToTextView(binding.tvSaveLocation)
        ThemeManager.applyThemeToTextView(binding.tvEncryptionOnBadge)
        ThemeManager.applyThemeToImageView(binding.ivSettingsShield)
        ThemeManager.applySubtlePillBackground(binding.tvEncryptionOnBadge)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    class ThemeSwatchesAdapter(
        private val onPresetSelected: (ThemePreset) -> Unit
    ) : RecyclerView.Adapter<ThemeSwatchesAdapter.SwatchViewHolder>() {

        private val presets = ThemePreset.values().toList()
        private var selectedPreset: ThemePreset = ThemePreset.RED

        fun setSelectedPreset(preset: ThemePreset) {
            selectedPreset = preset
            notifyDataSetChanged()
        }

        inner class SwatchViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val circle: View = itemView.findViewById(R.id.viewColorCircle)
            val checkmark: ImageView = itemView.findViewById(R.id.ivCheckmark)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SwatchViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_theme_color_swatch, parent, false)
            return SwatchViewHolder(view)
        }

        override fun onBindViewHolder(holder: SwatchViewHolder, position: Int) {
            val preset = presets[position]
            val isDark = ThemeManager.isDarkMode(holder.itemView.context)
            val hex = if (isDark) preset.hexDarkColor else preset.hexColor
            val colorInt = Color.parseColor(hex)

            val drawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(colorInt)
            }
            holder.circle.background = drawable

            val isSelected = preset == selectedPreset
            holder.checkmark.visibility = if (isSelected) View.VISIBLE else View.GONE

            holder.itemView.setOnClickListener {
                onPresetSelected(preset)
            }
        }

        override fun getItemCount(): Int = presets.size
    }
}
