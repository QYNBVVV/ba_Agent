package com.baam.mobile.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.baam.mobile.engine.driver.AccessibilityServiceHolder
import com.baam.mobile.engine.task.TaskBatchRunner
import com.baam.mobile.service.AutomationForegroundService
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen()
                }
            }
        }
    }
}

@Composable
private fun MainScreen(vm: MainViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val logs by vm.logs.collectAsState()
    val enabledIds by vm.enabledTaskIds.collectAsState()
    val batchState by vm.batchState.collectAsState()
    val accessibilityOn = remember { mutableStateOf(AccessibilityServiceHolder.isAvailable) }
    val overlayOn = remember { mutableStateOf(canDrawOverlays(context)) }

    DisposableEffect(LocalLifecycleOwner.current) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accessibilityOn.value = AccessibilityServiceHolder.isAvailable
                overlayOn.value = canDrawOverlays(context)
            }
        }
        LocalLifecycleOwner.current.lifecycle.addObserver(obs)
        onDispose { LocalLifecycleOwner.current.lifecycle.removeObserver(obs) }
    }

    val isRunning = batchState is TaskBatchRunner.BatchState.Running

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("BAAM Mobile", style = MaterialTheme.typography.headlineSmall)
        Text("蔚蓝档案移动端自动化 · v0.2（含咖啡厅任务）", style = MaterialTheme.typography.bodySmall)

        // 权限状态
        PermissionRow("无障碍服务", accessibilityOn.value) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        PermissionRow("悬浮窗权限", overlayOn.value) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}"),
                    )
                )
            }
        }

        HorizontalDivider()

        // 批次进度
        if (isRunning) {
            val running = batchState as TaskBatchRunner.BatchState.Running
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(12.dp)) {
                    Text("执行中: ${running.currentTaskId}", style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(
                        progress = { (running.current + 1f) / running.total },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                    Text(
                        "${running.current + 1} / ${running.total}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(Modifier.padding(top = 8.dp)) {
                        Button(
                            onClick = {
                                val stopIntent = Intent(context, AutomationForegroundService::class.java).apply {
                                    action = AutomationForegroundService.ACTION_STOP
                                }
                                androidx.core.content.ContextCompat.startForegroundService(context, stopIntent)
                            }
                        ) { Text("立即停止") }
                    }
                }
            }
        }

        // 一键执行
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = {
                    val ids = enabledIds.toList()
                    if (ids.isNotEmpty() && accessibilityOn.value) {
                        AutomationForegroundService.startBatch(context, ids, recovery = true)
                    }
                },
                enabled = accessibilityOn.value && !isRunning && enabledIds.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) { Text("一键执行已选 (${enabledIds.size})") }
        }

        HorizontalDivider()

        // 任务开关列表
        Text("任务", style = MaterialTheme.typography.titleMedium)
        vm.tasks.forEach { task ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = task.id in enabledIds,
                        onCheckedChange = { vm.toggleTask(task.id, it) },
                        enabled = !isRunning,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(task.displayName, style = MaterialTheme.typography.bodyLarge)
                        Text("id=${task.id}", style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedButton(
                        onClick = {
                            if (accessibilityOn.value) {
                                AutomationForegroundService.startTask(context, task.id)
                            }
                        },
                        enabled = accessibilityOn.value && !isRunning,
                    ) { Text("单跑") }
                }
            }
        }

        HorizontalDivider()

        // 日志
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("日志", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { vm.clearLogs() }) { Text("清空") }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(logs) { line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(name: String, granted: Boolean, onFix: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$name：${if (granted) "✓ 已开启" else "✗ 未开启"}",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (!granted) OutlinedButton(onClick = onFix) { Text("去开启") }
    }
}

private fun canDrawOverlays(context: android.content.Context): Boolean =
    android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M ||
        Settings.canDrawOverlays(context)
