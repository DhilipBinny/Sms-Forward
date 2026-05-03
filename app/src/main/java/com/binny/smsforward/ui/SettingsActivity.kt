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
import androidx.appcompat.app.AppCompatDelegate
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.binny.smsforward.R
import com.binny.smsforward.data.AppDatabase
import com.binny.smsforward.data.DestinationEntity
import com.binny.smsforward.data.FilterEntity
import com.binny.smsforward.databinding.ActivitySettingsBinding
import com.binny.smsforward.destination.ForwarderFactory
import kotlinx.coroutines.launch
import org.json.JSONObject

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val db by lazy { AppDatabase.get(this) }
    private lateinit var destinationAdapter: DestinationAdapter
    private lateinit var filterAdapter: FilterAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        setupPermissions()
        setupDestinations()
        setupFilters()
        setupAdvanced()
    }

    override fun onResume() {
        super.onResume()
        refreshPermissions()
    }

    private fun setupPermissions() {
        binding.rowNotifAccess.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        binding.rowBattery.setOnClickListener {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            })
        }
        binding.rowNotifications.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                })
            }
        }
        refreshPermissions()
    }

    private fun refreshPermissions() {
        val hasNotifAccess = packageName in NotificationManagerCompat.getEnabledListenerPackages(this)
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val hasBattery = pm.isIgnoringBatteryOptimizations(packageName)
        val hasNotifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true

        setPermStatus(binding.tvPermNotifAccess, hasNotifAccess)
        setPermStatus(binding.tvPermBattery, hasBattery)
        setPermStatus(binding.tvPermNotifications, hasNotifications)
    }

    private fun setPermStatus(tv: TextView, granted: Boolean) {
        if (granted) {
            tv.text = "Granted"
            tv.setTextColor(getColor(R.color.success))
        } else {
            tv.text = "Tap to fix"
            tv.setTextColor(getColor(R.color.error))
        }
    }

    private fun setupDestinations() {
        destinationAdapter = DestinationAdapter(
            onEdit = { destination -> editDestination(destination) },
            onTest = { destination -> testDestination(destination) },
            onDelete = { destination -> deleteDestination(destination) }
        )
        binding.recyclerDestinations.layoutManager = LinearLayoutManager(this)
        binding.recyclerDestinations.adapter = destinationAdapter

        db.destinationDao().getAll().observe(this) { destinations ->
            destinationAdapter.submitList(destinations)
            binding.tvNoDestinations.visibility =
                if (destinations.isEmpty()) View.VISIBLE else View.GONE
        }

        binding.btnAddDestination.setOnClickListener { showAddDestinationDialog() }
    }

    private fun setupFilters() {
        filterAdapter = FilterAdapter(
            onDelete = { filter ->
                lifecycleScope.launch { db.filterDao().delete(filter) }
            }
        )
        binding.recyclerFilters.layoutManager = LinearLayoutManager(this)
        binding.recyclerFilters.adapter = filterAdapter

        db.filterDao().getAll().observe(this) { filters ->
            filterAdapter.submitList(filters)
        }

        binding.btnAddFilter.setOnClickListener { showAddFilterDialog() }
    }

    private fun showAddDestinationDialog() {
        val types = arrayOf("Telegram", "Ntfy", "Webhook")
        AlertDialog.Builder(this, R.style.DialogTheme)
            .setTitle("Add Destination")
            .setItems(types) { _, which ->
                when (which) {
                    0 -> showTelegramConfigDialog()
                    1 -> showNtfyConfigDialog()
                    2 -> showWebhookConfigDialog()
                }
            }
            .show()
    }

    private fun setupAdvanced() {
        val prefs = getSharedPreferences("sms_forward", MODE_PRIVATE)

        val maxRetries = prefs.getInt("max_retries", 5)
        binding.sliderRetries.value = maxRetries.toFloat()
        binding.tvRetryValue.text = maxRetries.toString()
        binding.sliderRetries.addOnChangeListener { _, value, _ ->
            val retries = value.toInt()
            binding.tvRetryValue.text = retries.toString()
            prefs.edit().putInt("max_retries", retries).apply()
        }

        val retention = prefs.getInt("retention_days", 30)
        binding.sliderRetention.value = retention.toFloat()
        binding.tvRetentionValue.text = "$retention days"
        binding.sliderRetention.addOnChangeListener { _, value, _ ->
            val days = value.toInt()
            binding.tvRetentionValue.text = "$days days"
            prefs.edit().putInt("retention_days", days).apply()
        }

        val themeMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        binding.tvThemeValue.text = themeLabel(themeMode)

        binding.rowTheme.setOnClickListener {
            val options = arrayOf("Light", "Dark", "System")
            val modes = intArrayOf(
                AppCompatDelegate.MODE_NIGHT_NO,
                AppCompatDelegate.MODE_NIGHT_YES,
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            )
            val current = modes.indexOf(prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM))

            AlertDialog.Builder(this, R.style.DialogTheme)
                .setTitle("Theme")
                .setSingleChoiceItems(options, current) { dialog, which ->
                    val selected = modes[which]
                    prefs.edit().putInt("theme_mode", selected).apply()
                    AppCompatDelegate.setDefaultNightMode(selected)
                    binding.tvThemeValue.text = options[which]
                    dialog.dismiss()
                }
                .show()
        }
    }

    private fun themeLabel(mode: Int): String {
        return when (mode) {
            AppCompatDelegate.MODE_NIGHT_NO -> "Light"
            AppCompatDelegate.MODE_NIGHT_YES -> "Dark"
            else -> "System"
        }
    }

    private fun showTelegramConfigDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_telegram, null)
        val etToken = view.findViewById<EditText>(R.id.et_token)
        val etChatId = view.findViewById<EditText>(R.id.et_chat_id)

        AlertDialog.Builder(this, R.style.DialogTheme)
            .setTitle("Telegram")
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                val config = JSONObject().apply {
                    put("bot_token", etToken.text.toString().trim())
                    put("chat_id", etChatId.text.toString().trim())
                }
                lifecycleScope.launch {
                    db.destinationDao().insert(
                        DestinationEntity(
                            type = "telegram",
                            name = "Telegram",
                            config = config.toString()
                        )
                    )
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showNtfyConfigDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_ntfy, null)
        val etServer = view.findViewById<EditText>(R.id.et_server)
        val etTopic = view.findViewById<EditText>(R.id.et_topic)
        etServer.setText("https://ntfy.sh")

        AlertDialog.Builder(this, R.style.DialogTheme)
            .setTitle("Ntfy")
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                val config = JSONObject().apply {
                    put("server", etServer.text.toString().trim())
                    put("topic", etTopic.text.toString().trim())
                }
                lifecycleScope.launch {
                    db.destinationDao().insert(
                        DestinationEntity(
                            type = "ntfy",
                            name = "Ntfy",
                            config = config.toString()
                        )
                    )
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showWebhookConfigDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_webhook, null)
        val etUrl = view.findViewById<EditText>(R.id.et_url)

        AlertDialog.Builder(this, R.style.DialogTheme)
            .setTitle("Webhook")
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                val config = JSONObject().apply {
                    put("url", etUrl.text.toString().trim())
                }
                lifecycleScope.launch {
                    db.destinationDao().insert(
                        DestinationEntity(
                            type = "webhook",
                            name = "Webhook",
                            config = config.toString()
                        )
                    )
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddFilterDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_filter, null)
        val etValue = view.findViewById<EditText>(R.id.et_filter_value)
        val types = arrayOf("Sender contains", "Message contains")
        var selectedType = "sender"

        val tvType = view.findViewById<TextView>(R.id.tv_filter_type)
        tvType.text = types[0]
        tvType.setOnClickListener {
            AlertDialog.Builder(this, R.style.DialogTheme)
                .setItems(types) { _, which ->
                    selectedType = if (which == 0) "sender" else "keyword"
                    tvType.text = types[which]
                }
                .show()
        }

        AlertDialog.Builder(this, R.style.DialogTheme)
            .setTitle("Add Filter")
            .setView(view)
            .setPositiveButton("Add") { _, _ ->
                val value = etValue.text.toString().trim()
                if (value.isNotBlank()) {
                    lifecycleScope.launch {
                        db.filterDao().insert(
                            FilterEntity(type = selectedType, value = value)
                        )
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun editDestination(destination: DestinationEntity) {
        when (destination.type) {
            "telegram" -> editTelegramDialog(destination)
            "ntfy" -> editNtfyDialog(destination)
            "webhook" -> editWebhookDialog(destination)
        }
    }

    private fun editTelegramDialog(dest: DestinationEntity) {
        val view = layoutInflater.inflate(R.layout.dialog_telegram, null)
        val etToken = view.findViewById<EditText>(R.id.et_token)
        val etChatId = view.findViewById<EditText>(R.id.et_chat_id)

        val config = JSONObject(dest.config)
        etToken.setText(config.getString("bot_token"))
        etChatId.setText(config.getString("chat_id"))

        AlertDialog.Builder(this, R.style.DialogTheme)
            .setTitle("Edit Telegram")
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                val newConfig = JSONObject().apply {
                    put("bot_token", etToken.text.toString().trim())
                    put("chat_id", etChatId.text.toString().trim())
                }
                lifecycleScope.launch {
                    db.destinationDao().update(dest.copy(config = newConfig.toString()))
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun editNtfyDialog(dest: DestinationEntity) {
        val view = layoutInflater.inflate(R.layout.dialog_ntfy, null)
        val etServer = view.findViewById<EditText>(R.id.et_server)
        val etTopic = view.findViewById<EditText>(R.id.et_topic)

        val config = JSONObject(dest.config)
        etServer.setText(config.optString("server", "https://ntfy.sh"))
        etTopic.setText(config.getString("topic"))

        AlertDialog.Builder(this, R.style.DialogTheme)
            .setTitle("Edit Ntfy")
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                val newConfig = JSONObject().apply {
                    put("server", etServer.text.toString().trim())
                    put("topic", etTopic.text.toString().trim())
                }
                lifecycleScope.launch {
                    db.destinationDao().update(dest.copy(config = newConfig.toString()))
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun editWebhookDialog(dest: DestinationEntity) {
        val view = layoutInflater.inflate(R.layout.dialog_webhook, null)
        val etUrl = view.findViewById<EditText>(R.id.et_url)

        val config = JSONObject(dest.config)
        etUrl.setText(config.getString("url"))

        AlertDialog.Builder(this, R.style.DialogTheme)
            .setTitle("Edit Webhook")
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                val newConfig = JSONObject().apply {
                    put("url", etUrl.text.toString().trim())
                }
                lifecycleScope.launch {
                    db.destinationDao().update(dest.copy(config = newConfig.toString()))
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun testDestination(destination: DestinationEntity) {
        lifecycleScope.launch {
            try {
                val forwarder = ForwarderFactory.create(destination)
                val result = forwarder.test()
                result.fold(
                    onSuccess = { msg ->
                        Toast.makeText(this@SettingsActivity, msg, Toast.LENGTH_LONG).show()
                    },
                    onFailure = { e ->
                        Toast.makeText(this@SettingsActivity, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                )
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun deleteDestination(destination: DestinationEntity) {
        AlertDialog.Builder(this, R.style.DialogTheme)
            .setTitle("Remove ${destination.name}?")
            .setPositiveButton("Remove") { _, _ ->
                lifecycleScope.launch { db.destinationDao().delete(destination) }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

class DestinationAdapter(
    private val onEdit: (DestinationEntity) -> Unit,
    private val onTest: (DestinationEntity) -> Unit,
    private val onDelete: (DestinationEntity) -> Unit
) : RecyclerView.Adapter<DestinationAdapter.ViewHolder>() {

    private var items: List<DestinationEntity> = emptyList()

    fun submitList(list: List<DestinationEntity>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_destination, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvName: TextView = view.findViewById(R.id.tv_dest_name)
        private val tvDetail: TextView = view.findViewById(R.id.tv_dest_detail)
        private val btnTest: View = view.findViewById(R.id.btn_test)
        private val btnRemove: View = view.findViewById(R.id.btn_remove)

        fun bind(dest: DestinationEntity) {
            tvName.text = dest.name
            tvDetail.text = formatConfig(dest)
            itemView.setOnClickListener { onEdit(dest) }
            btnTest.setOnClickListener { onTest(dest) }
            btnRemove.setOnClickListener { onDelete(dest) }
        }

        private fun formatConfig(dest: DestinationEntity): String {
            return try {
                val json = org.json.JSONObject(dest.config)
                when (dest.type) {
                    "telegram" -> {
                        val token = json.getString("bot_token")
                        val masked = token.take(6) + "..." + token.takeLast(4)
                        "Token: $masked\nChat: ${json.getString("chat_id")}"
                    }
                    "ntfy" -> {
                        "${json.optString("server", "ntfy.sh")}/${json.getString("topic")}"
                    }
                    "webhook" -> json.getString("url")
                    else -> dest.config
                }
            } catch (_: Exception) {
                dest.config
            }
        }
    }
}

class FilterAdapter(
    private val onDelete: (FilterEntity) -> Unit
) : RecyclerView.Adapter<FilterAdapter.ViewHolder>() {

    private var items: List<FilterEntity> = emptyList()

    fun submitList(list: List<FilterEntity>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_filter, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvFilter: TextView = view.findViewById(R.id.tv_filter_text)
        private val btnRemove: View = view.findViewById(R.id.btn_filter_remove)

        fun bind(filter: FilterEntity) {
            val prefix = if (filter.type == "sender") "From:" else "Contains:"
            tvFilter.text = "$prefix ${filter.value}"
            btnRemove.setOnClickListener { onDelete(filter) }
        }
    }
}
