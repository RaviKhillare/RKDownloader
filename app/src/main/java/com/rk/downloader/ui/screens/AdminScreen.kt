package com.rk.downloader.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rk.downloader.config.AdminConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*

data class DeviceRecord(
    val uuid: String,
    val model: String,
    val os: String,
    val installTime: Long,
    val lastActive: Long
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    var totalDevices by remember { mutableIntStateOf(0) }
    var activeDevices7Days by remember { mutableIntStateOf(0) }
    var deviceList by remember { mutableStateOf<List<DeviceRecord>>(emptyList()) }

    fun fetchStats() {
        isLoading = true
        errorMsg = null
        scope.launch {
            try {
                val stats = withContext(Dispatchers.IO) {
                    val supabaseUrl = AdminConfig.SUPABASE_URL
                    val anonKey = AdminConfig.SUPABASE_ANON_KEY
                    if (supabaseUrl.isEmpty() || supabaseUrl.contains("yourproject") || anonKey.isEmpty()) {
                        throw Exception("Supabase is not configured. Configure it in AdminConfig.kt first.")
                    }

                    val request = Request.Builder()
                        .url("$supabaseUrl/rest/v1/devices?select=*")
                        .header("apikey", anonKey)
                        .header("Authorization", "Bearer $anonKey")
                        .build()

                    val client = OkHttpClient()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) throw Exception("Database connection error: Code ${response.code}")
                        val bodyStr = response.body?.string() ?: "[]"

                        val records = mutableListOf<DeviceRecord>()
                        val jsonArray = JSONArray(bodyStr)

                        for (i in 0 until jsonArray.length()) {
                            val deviceObj = jsonArray.getJSONObject(i)
                            records.add(
                                DeviceRecord(
                                    uuid = deviceObj.optString("uuid", ""),
                                    model = deviceObj.optString("model", "Unknown Device"),
                                    os = deviceObj.optString("os", "Unknown OS"),
                                    installTime = deviceObj.optLong("install_time", 0L),
                                    lastActive = deviceObj.optLong("last_active", 0L)
                                )
                            )
                        }
                        records
                    }
                }

                val currentTime = System.currentTimeMillis()
                val sevenDaysAgo = currentTime - (7L * 24L * 60L * 60L * 1000L)

                deviceList = stats.sortedByDescending { it.lastActive }
                totalDevices = stats.size
                activeDevices7Days = stats.count { it.lastActive > sevenDaysAgo }
                isLoading = false
            } catch (e: Exception) {
                errorMsg = e.message ?: "Failed to retrieve statistics"
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        fetchStats()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Admin Dashboard") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (errorMsg != null) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = errorMsg!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Button(onClick = { fetchStats() }) {
                        Text("Retry")
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Card(
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Total Installs", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = totalDevices.toString(),
                                    style = MaterialTheme.typography.headlineLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Card(
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Active (7 Days)", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = activeDevices7Days.toString(),
                                    style = MaterialTheme.typography.headlineLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Device Distribution (${deviceList.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    HorizontalDivider()

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(top = 8.dp)
                    ) {
                        items(deviceList) { record ->
                            val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                            val dateStr = if (record.lastActive > 0) sdf.format(Date(record.lastActive)) else "Never"

                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = record.model,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = record.os,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Last Active: $dateStr",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}