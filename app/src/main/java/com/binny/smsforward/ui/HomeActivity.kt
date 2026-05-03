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
import android.text.Editable
import android.text.TextWatcher
import androidx.core.app.NotificationManagerCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.binny.smsforward.R
import com.binny.smsforward.data.AppDatabase
import com.binny.smsforward.data.MessageEntity
import com.binny.smsforward.databinding.ActivityHomeBinding
import com.binny.smsforward.service.ForwardService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var adapter: MessageAdapter
    private val db by lazy { AppDatabase.get(this) }
    private var currentSource: LiveData<List<MessageEntity>>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestPermissions()
        requestBatteryExemption()
        requestNotificationAccess()
        setupRecyclerView()
        setupToggle()
        setupSearch()
        setupSettingsButton()
        observeData()
        cleanupOldMessages()
    }

    private fun setupRecyclerView() {
        adapter = MessageAdapter()
        binding.recyclerMessages.layoutManager = LinearLayoutManager(this)
        binding.recyclerMessages.adapter = adapter
    }

    private fun setupToggle() {
        val prefs = getSharedPreferences("sms_forward", MODE_PRIVATE)
        val enabled = prefs.getBoolean("forwarding_enabled", false)
        binding.switchForward.isChecked = enabled
        updateToggleState(enabled)

        binding.switchForward.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("forwarding_enabled", isChecked).apply()
            updateToggleState(isChecked)

            if (isChecked) {
                startForegroundService(Intent(this, ForwardService::class.java))
            } else {
                stopService(Intent(this, ForwardService::class.java))
            }
        }
    }

    private fun updateToggleState(enabled: Boolean) {
        binding.tvStatus.text = if (enabled) "Active" else "Inactive"
        binding.tvStatusSub.text = if (enabled) "Forwarding incoming SMS" else "Slide to start forwarding"
        binding.viewStatusDot.setBackgroundResource(
            if (enabled) R.drawable.dot_active else R.drawable.dot_inactive
        )
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim() ?: ""
                switchDataSource(query)
            }
        })
    }

    private fun switchDataSource(query: String) {
        currentSource?.removeObservers(this)

        val source = if (query.isEmpty()) {
            binding.tvRecentLabel.text = "Recent"
            db.messageDao().getRecent(20)
        } else {
            binding.tvRecentLabel.text = "Results"
            db.messageDao().search(query)
        }

        currentSource = source
        source.observe(this) { messages ->
            adapter.submitList(messages)
            binding.tvEmpty.visibility = if (messages.isEmpty()) View.VISIBLE else View.GONE
            binding.tvEmpty.text = if (query.isEmpty()) "No messages forwarded yet" else "No results"
        }
    }

    private fun setupSettingsButton() {
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun observeData() {
        switchDataSource("")

        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }.timeInMillis

        db.messageDao().getSentCountSince(todayStart).observe(this) { count ->
            binding.tvCount.text = "$count sent today"
        }
    }

    private fun cleanupOldMessages() {
        val prefs = getSharedPreferences("sms_forward", MODE_PRIVATE)
        val days = prefs.getInt("retention_days", 30)
        val cutoff = System.currentTimeMillis() - (days.toLong() * 24 * 60 * 60 * 1000)
        CoroutineScope(Dispatchers.IO).launch {
            db.messageDao().deleteOlderThan(cutoff)
        }
    }

    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100
                )
            }
        }
    }

    private fun requestNotificationAccess() {
        val listeners = NotificationManagerCompat.getEnabledListenerPackages(this)
        if (packageName !in listeners) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }

    private fun requestBatteryExemption() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }
    }
}

class MessageAdapter : RecyclerView.Adapter<MessageAdapter.ViewHolder>() {

    private var messages: List<MessageEntity> = emptyList()

    fun submitList(list: List<MessageEntity>) {
        messages = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(messages[position])
    }

    override fun getItemCount() = messages.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvSender: TextView = view.findViewById(R.id.tv_sender)
        private val tvBody: TextView = view.findViewById(R.id.tv_body)
        private val tvTime: TextView = view.findViewById(R.id.tv_time)
        private val tvStatus: TextView = view.findViewById(R.id.tv_msg_status)

        fun bind(msg: MessageEntity) {
            tvSender.text = msg.sender
            tvBody.text = msg.body
            tvTime.text = getRelativeTime(msg.timestamp)
            tvStatus.text = msg.status
            tvStatus.setTextColor(
                when (msg.status) {
                    "sent" -> 0xFF4CAF50.toInt()
                    "failed" -> 0xFFE57373.toInt()
                    else -> 0xFFFFA726.toInt()
                }
            )
        }

        private fun getRelativeTime(timestamp: Long): String {
            val diff = System.currentTimeMillis() - timestamp
            return when {
                diff < 60_000 -> "now"
                diff < 3_600_000 -> "${diff / 60_000}m ago"
                diff < 86_400_000 -> "${diff / 3_600_000}h ago"
                else -> SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(timestamp))
            }
        }
    }
}
