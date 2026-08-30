package org.airshare.app.ui.home

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.airshare.app.AirShareApplication
import org.airshare.app.databinding.FragmentHomeBinding
import org.airshare.app.ui.clone.PhoneCloneActivity
import org.airshare.app.ui.group.GroupActivity
import org.airshare.app.ui.receive.ReceiveActivity
import org.airshare.app.ui.send.SendActivity
import org.airshare.app.ui.theme.ThemeManager
import org.airshare.app.ui.webconnect.WebConnectActivity

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val deviceName = AirShareApplication.instance.settingsRepository.deviceName
        binding.tvDeviceNameBadge.text = "$deviceName • Ready"

        binding.cardSend.setOnClickListener {
            startActivity(Intent(requireContext(), SendActivity::class.java))
        }

        binding.cardReceive.setOnClickListener {
            startActivity(Intent(requireContext(), ReceiveActivity::class.java))
        }

        binding.cardGroupBroadcast.setOnClickListener {
            startActivity(Intent(requireContext(), GroupActivity::class.java))
        }

        binding.cardWebConnect.setOnClickListener {
            startActivity(Intent(requireContext(), WebConnectActivity::class.java))
        }

        binding.cardPhoneClone.setOnClickListener {
            startActivity(Intent(requireContext(), PhoneCloneActivity::class.java))
        }

        // Apply dynamic theme accent updates
        viewLifecycleOwner.lifecycleScope.launch {
            ThemeManager.activePresetFlow.collectLatest {
                applyDynamicTheme()
            }
        }
    }

    private fun applyDynamicTheme() {
        if (_binding == null) return
        val activeColor = ThemeManager.getActiveColorInt(requireContext())

        // Update send tile gradient with dynamic color
        val density = resources.displayMetrics.density
        val radiusPx = 26f * density
        val sendGradient = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(activeColor, adjustColorBrightness(activeColor, 0.8f))
        ).apply {
            cornerRadius = radiusPx
        }
        binding.layoutSendTile.background = sendGradient

        // Update badges
        ThemeManager.applySubtlePillBackground(binding.tvSecurityChip)
        ThemeManager.applySubtlePillBackground(binding.privacyBadge)
        ThemeManager.applySubtlePillBackground(binding.frameGroupIcon)
        ThemeManager.applySubtlePillBackground(binding.frameWebIcon)
        ThemeManager.applySubtlePillBackground(binding.frameCloneIcon)

        binding.tvSecurityChip.setTextColor(activeColor)
        binding.tvPrivacyText.setTextColor(activeColor)
        binding.ivGroupIcon.setColorFilter(activeColor)
    }

    private fun adjustColorBrightness(color: Int, factor: Float): Int {
        val a = android.graphics.Color.alpha(color)
        val r = (android.graphics.Color.red(color) * factor).toInt().coerceIn(0, 255)
        val g = (android.graphics.Color.green(color) * factor).toInt().coerceIn(0, 255)
        val b = (android.graphics.Color.blue(color) * factor).toInt().coerceIn(0, 255)
        return android.graphics.Color.argb(a, r, g, b)
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) {
            val deviceName = AirShareApplication.instance.settingsRepository.deviceName
            binding.tvDeviceNameBadge.text = "$deviceName • Ready"
            applyDynamicTheme()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
