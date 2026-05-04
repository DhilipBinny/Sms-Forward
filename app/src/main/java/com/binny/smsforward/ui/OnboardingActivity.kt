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
import android.widget.TextView
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

        binding.rowOnboardSms.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS), 101
                )
            }
        }

        binding.rowOnboardPostNotif.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(
                        this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 102
                    )
                }
            }
        }

        binding.btnContinue.setOnClickListener {
            completeSetup()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshState()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refreshState()
    }

    private fun refreshState() {
        val hasNotifAccess = packageName in NotificationManagerCompat.getEnabledListenerPackages(this)
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val hasBattery = pm.isIgnoringBatteryOptimizations(packageName)
        val hasSms = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        val hasPostNotif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true

        setStatus(binding.tvOnboardNotif, hasNotifAccess)
        setStatus(binding.tvOnboardBattery, hasBattery)
        setStatus(binding.tvOnboardSms, hasSms)
        setStatus(binding.tvOnboardPostNotif, hasPostNotif)

        binding.btnContinue.isEnabled = hasNotifAccess && hasBattery && hasSms && hasPostNotif
    }

    private fun setStatus(tv: TextView, granted: Boolean) {
        if (granted) {
            tv.text = "Granted"
            tv.setTextColor(getColor(R.color.success))
        } else {
            tv.text = "Tap to grant"
            tv.setTextColor(getColor(R.color.error))
        }
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
