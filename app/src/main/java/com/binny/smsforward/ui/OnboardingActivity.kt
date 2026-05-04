package com.binny.smsforward.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.binny.smsforward.R
import com.binny.smsforward.databinding.ActivityOnboardingBinding

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (isSetupComplete()) {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
            return
        }

        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.rowOnboardNotif.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        binding.rowOnboardBattery.setOnClickListener {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            })
        }

        binding.btnContinue.setOnClickListener {
            val permsNeeded = mutableListOf<String>()

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS)
                != PackageManager.PERMISSION_GRANTED) {
                permsNeeded.add(Manifest.permission.RECEIVE_SMS)
                permsNeeded.add(Manifest.permission.READ_SMS)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                    permsNeeded.add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            if (permsNeeded.isNotEmpty()) {
                ActivityCompat.requestPermissions(this, permsNeeded.toTypedArray(), 100)
                return@setOnClickListener
            }

            completeSetup()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshState()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            completeSetup()
        }
    }

    private fun refreshState() {
        val hasNotifAccess = packageName in NotificationManagerCompat.getEnabledListenerPackages(this)
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val hasBattery = pm.isIgnoringBatteryOptimizations(packageName)

        if (hasNotifAccess) {
            binding.tvOnboardNotif.text = "Granted"
            binding.tvOnboardNotif.setTextColor(getColor(R.color.success))
        } else {
            binding.tvOnboardNotif.text = "Tap to grant"
            binding.tvOnboardNotif.setTextColor(getColor(R.color.error))
        }

        if (hasBattery) {
            binding.tvOnboardBattery.text = "Granted"
            binding.tvOnboardBattery.setTextColor(getColor(R.color.success))
        } else {
            binding.tvOnboardBattery.text = "Tap to grant"
            binding.tvOnboardBattery.setTextColor(getColor(R.color.error))
        }

        binding.btnContinue.isEnabled = hasNotifAccess && hasBattery
    }

    private fun isSetupComplete(): Boolean {
        return getSharedPreferences("sms_forward", MODE_PRIVATE)
            .getBoolean("onboarding_done", false)
    }

    private fun completeSetup() {
        getSharedPreferences("sms_forward", MODE_PRIVATE)
            .edit().putBoolean("onboarding_done", true).apply()
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }
}
