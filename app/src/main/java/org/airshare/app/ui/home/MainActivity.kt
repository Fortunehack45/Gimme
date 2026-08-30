package org.airshare.app.ui.home

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.airshare.app.AirShareApplication
import org.airshare.app.R
import org.airshare.app.databinding.ActivityMainBinding
import org.airshare.app.ui.history.HistoryFragment
import org.airshare.app.ui.onboarding.OnboardingActivity
import org.airshare.app.ui.settings.SettingsFragment
import org.airshare.app.ui.theme.ThemeManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check Onboarding
        if (!AirShareApplication.instance.settingsRepository.isOnboardingCompleted) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            loadFragment(HomeFragment())
        }

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.menu_home -> {
                    loadFragment(HomeFragment())
                    true
                }
                R.id.menu_history -> {
                    loadFragment(HistoryFragment())
                    true
                }
                R.id.menu_settings -> {
                    loadFragment(SettingsFragment())
                    true
                }
                else -> false
            }
        }

        lifecycleScope.launch {
            ThemeManager.activePresetFlow.collectLatest {
                applyNavDynamicTheme()
            }
        }
    }

    private fun applyNavDynamicTheme() {
        val activeColor = ThemeManager.getActiveColorInt(this)
        val mutedColor = getColor(R.color.text_dark_muted)

        val states = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_checked)
        )
        val colors = intArrayOf(activeColor, mutedColor)
        val colorStateList = ColorStateList(states, colors)

        binding.bottomNavigation.itemIconTintList = colorStateList
        binding.bottomNavigation.itemTextColor = colorStateList
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(
                android.R.anim.fade_in,
                android.R.anim.fade_out
            )
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}
