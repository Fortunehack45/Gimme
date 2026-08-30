package org.airshare.app.ui.onboarding

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import org.airshare.app.AirShareApplication
import org.airshare.app.R
import org.airshare.app.databinding.ActivityOnboardingBinding
import org.airshare.app.ui.home.MainActivity

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        completeOnboarding()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val pages = listOf(
            OnboardingPage(
                iconRes = R.drawable.ic_airshare_logo,
                title = getString(R.string.onboarding_1_title),
                description = getString(R.string.onboarding_1_desc)
            ),
            OnboardingPage(
                iconRes = R.drawable.ic_group,
                title = getString(R.string.onboarding_2_title),
                description = getString(R.string.onboarding_2_desc)
            ),
            OnboardingPage(
                iconRes = R.drawable.ic_shield_check,
                title = getString(R.string.onboarding_3_title),
                description = getString(R.string.onboarding_3_desc)
            )
        )

        val adapter = OnboardingAdapter(pages)
        binding.viewPagerOnboarding.adapter = adapter

        setupIndicators(pages.size)
        updateIndicators(0)

        binding.viewPagerOnboarding.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateIndicators(position)
                if (position == pages.size - 1) {
                    binding.btnAction.text = getString(R.string.btn_grant_permissions)
                } else {
                    binding.btnAction.text = getString(R.string.btn_next)
                }
            }
        })

        binding.btnAction.setOnClickListener {
            val current = binding.viewPagerOnboarding.currentItem
            if (current < pages.size - 1) {
                binding.viewPagerOnboarding.currentItem = current + 1
            } else {
                requestRequiredPermissions()
            }
        }
    }

    private fun setupIndicators(count: Int) {
        binding.indicatorLayout.removeAllViews()
        for (i in 0 until count) {
            val dot = View(this).apply {
                val size = 24
                layoutParams = LinearLayout.LayoutParams(size, 8).apply {
                    setMargins(6, 0, 6, 0)
                }
                setBackgroundResource(R.drawable.bg_pill_badge)
            }
            binding.indicatorLayout.addView(dot)
        }
    }

    private fun updateIndicators(position: Int) {
        for (i in 0 until binding.indicatorLayout.childCount) {
            val view = binding.indicatorLayout.getChildAt(i)
            view.alpha = if (i == position) 1.0f else 0.3f
        }
    }

    private fun requestRequiredPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissionsToRequest.add(Manifest.permission.READ_MEDIA_VIDEO)
            permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        val ungranted = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (ungranted.isNotEmpty()) {
            permissionLauncher.launch(ungranted.toTypedArray())
        } else {
            completeOnboarding()
        }
    }

    private fun completeOnboarding() {
        AirShareApplication.instance.settingsRepository.isOnboardingCompleted = true
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    data class OnboardingPage(val iconRes: Int, val title: String, val description: String)

    class OnboardingAdapter(private val items: List<OnboardingPage>) :
        RecyclerView.Adapter<OnboardingAdapter.OnboardingViewHolder>() {

        inner class OnboardingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val icon: ImageView = itemView.findViewById(R.id.ivOnboardingIcon)
            val title: TextView = itemView.findViewById(R.id.tvOnboardingTitle)
            val desc: TextView = itemView.findViewById(R.id.tvOnboardingDescription)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OnboardingViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_onboarding_page, parent, false)
            return OnboardingViewHolder(view)
        }

        override fun onBindViewHolder(holder: OnboardingViewHolder, position: Int) {
            val item = items[position]
            holder.icon.setImageResource(item.iconRes)
            holder.title.text = item.title
            holder.desc.text = item.description
        }

        override fun getItemCount(): Int = items.size
    }
}
