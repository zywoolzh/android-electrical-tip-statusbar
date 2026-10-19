package com.zywoolzh.electricaltip

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private lateinit var preferences: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = AppPreferences(this)
        setContent {
            MaterialTheme {
                ElectricalTipSettings(
                    preferences = preferences,
                    onStartService = ::ensureNotificationService,
                    onNext = ::showNextTip,
                    onSettingsChanged = ::refreshService,
                )
            }
        }
    }

    private fun ensureNotificationService() {
        ContextCompat.startForegroundService(this, Intent(this, KnowledgeNotificationService::class.java))
    }

    private fun showNextTip() {
        ensureNotificationService()
        startService(
            Intent(this, KnowledgeNotificationService::class.java)
                .setAction(KnowledgeNotificationService.ACTION_NEXT),
        )
    }

    private fun refreshService() {
        ensureNotificationService()
        startService(
            Intent(this, KnowledgeNotificationService::class.java)
                .setAction(KnowledgeNotificationService.ACTION_REFRESH),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ElectricalTipSettings(
    preferences: AppPreferences,
    onStartService: () -> Unit,
    onNext: () -> Unit,
    onSettingsChanged: () -> Unit,
) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(preferences.rotationEnabled) }
    var intervalText by remember { mutableStateOf(preferences.intervalValue.toString()) }
    var unit by remember { mutableStateOf(preferences.intervalUnit) }
    var tipIndex by remember { mutableIntStateOf(preferences.currentIndex) }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) onStartService() }

    fun requestAndStart() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            onStartService()
        } else {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("电气知识状态栏") },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val tip = KnowledgeContent.tips[tipIndex]
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(tip.category, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(tip.text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("自动轮换", style = MaterialTheme.typography.titleMedium)
                    Text("关闭后通知仍保持当前知识点", style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = {
                        enabled = it
                        preferences.rotationEnabled = it
                        requestAndStart()
                        onSettingsChanged()
                    },
                )
            }

            TextField(
                value = intervalText,
                onValueChange = { value ->
                    if (value.length <= 3 && value.all(Char::isDigit)) intervalText = value
                },
                label = { Text("轮换间隔（1—365）") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                IntervalUnit.entries.forEach { option ->
                    RadioButton(selected = unit == option, onClick = { unit = option })
                    Text(if (option == IntervalUnit.HOURS) "小时" else "天")
                }
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val value = intervalText.toIntOrNull()?.coerceIn(1, 365) ?: 1
                    intervalText = value.toString()
                    preferences.intervalValue = value
                    preferences.intervalUnit = unit
                    requestAndStart()
                    onSettingsChanged()
                },
            ) {
                Text("保存并显示常驻通知")
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    onNext()
                    tipIndex = KnowledgeContent.normalizedIndex(tipIndex + 1)
                },
            ) {
                Text("手动切换知识点")
            }
        }
    }
}
