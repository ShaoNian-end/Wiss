package com.wiss

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wiss.ui.theme.ThemeSettings
import com.wiss.ui.theme.WissTheme
import kotlinx.coroutines.launch
import java.net.InetAddress

/**
 * Hosts 设置页：内置 Hosts 自动应用于全部主站（无需配置），
 * 自定义 Hosts 仅填写 IP（不指定域名），自动应用于当前主站；支持添加与删除。
 */
class HostsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 应用「主题色 / 深色模式」设置
        ThemeSettings.load(this)
        setContent {
            WissTheme {
                HostsScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostsScreen(onBack: () -> Unit) {
    var entries by remember { mutableStateOf(Hosts.customs()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var deleteIndex by remember { mutableStateOf<Int?>(null) }

    // Cloudflare 优选状态
    var cfTesting by remember { mutableStateOf(false) }
    var cfResults by remember { mutableStateOf<List<Pair<String, Long>>>(emptyList()) }
    var cfApplied by remember { mutableStateOf(Hosts.cfBestIps()) }
    val scope = rememberCoroutineScope()

    fun runCfTest() {
        scope.launch {
            cfTesting = true
            try {
                val results = Hosts.testCloudflareNodes()
                cfResults = results
                val top = results.take(Hosts.CF_TOP_COUNT).map { it.first }
                Hosts.setCfBestIps(top)
                cfApplied = top
            } finally {
                cfTesting = false
            }
        }
    }

    fun clearCfBest() {
        Hosts.setCfBestIps(emptyList())
        cfApplied = emptyList()
        cfResults = emptyList()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hosts 加速") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { HostsInfoCard() }
            item {
                CfBestCard(
                    testing = cfTesting,
                    results = cfResults,
                    applied = cfApplied,
                    onTest = ::runCfTest,
                    onClear = ::clearCfBest
                )
            }
            itemsIndexed(entries, key = { index, _ -> index }) { index, entry ->
                HostRow(entry = entry, index = index, onDelete = { deleteIndex = index })
            }
            item {
                Button(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("添加自定义 IP")
                }
            }
        }
    }

    // 添加自定义 IP 对话框
    if (showAddDialog) {
        AddCustomDialog(
            onDismiss = { showAddDialog = false },
            onAdded = {
                entries = Hosts.customs()
                showAddDialog = false
            }
        )
    }

    // 删除自定义 IP 确认对话框
    deleteIndex?.let { index ->
        entries.getOrNull(index)?.let { target ->
            AlertDialog(
                onDismissRequest = { deleteIndex = null },
                title = { Text("删除自定义 IP") },
                text = { Text("确定删除这组自定义 IP 吗？\n\n${target.ips.joinToString("\n")}") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            Hosts.removeCustom(index)
                            entries = Hosts.customs()
                            deleteIndex = null
                        }
                    ) { Text("删除") }
                },
                dismissButton = {
                    TextButton(onClick = { deleteIndex = null }) { Text("取消") }
                }
            )
        }
    }
}

/** 功能说明卡片：解释内置/自定义 Hosts 的应用范围 */
@Composable
private fun HostsInfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "Hosts 加速",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "内置 Hosts 已自动应用于全部主站（missav.ws / missav.live / missav123.com / missav.ai），请求时用内置 IP 直接连接，绕过系统 DNS 的慢解析/污染，无需手动开启。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "自定义 IP 无需指定域名，添加后自动应用于当前主站，并优先于内置 IP 尝试；切换主站后同样生效。页面抓取、图片、视频均走自定义 DNS，WebView 内部仍使用系统 DNS。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Cloudflare 优选卡片：批量测速 CF 边缘节点，自动应用最快的 [Hosts.CF_TOP_COUNT] 个到当前主站 */
@Composable
private fun CfBestCard(
    testing: Boolean,
    results: List<Pair<String, Long>>,
    applied: List<String>,
    onTest: () -> Unit,
    onClear: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "Cloudflare 优选",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "大陆网络下部分 CF 边缘 IP 会被限速/屏蔽。点击测试 ${Hosts.cloudflareCandidateIps.size} 个 CF 节点，自动应用最快的 ${Hosts.CF_TOP_COUNT} 个到当前主站（优先于内置/自定义 IP）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            if (testing) {
                Text(
                    text = "正在测试 ${Hosts.cloudflareCandidateIps.size} 个节点...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                Button(
                    onClick = onTest,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (applied.isEmpty()) "测试并应用最优节点" else "重新测试并应用")
                }
            }
            if (results.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "测速结果（已应用最快 ${Hosts.CF_TOP_COUNT} 个）：",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                results.take(Hosts.CF_TOP_COUNT).forEach { (ip, ms) ->
                    Text(
                        text = "$ip  ${ms} ms",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (applied.isNotEmpty() && !testing) {
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = onClear) { Text("清除优选，恢复默认") }
            }
        }
    }
}

/** 单组自定义 IP 记录行：序号 + IP 列表 + 删除按钮 */
@Composable
private fun HostRow(
    entry: CustomHosts,
    index: Int,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "自定义 IP ${index + 1}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = entry.ips.joinToString("  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 添加自定义 IP 对话框：仅填写 IP 列表（逗号/换行分隔），带格式校验 */
@Composable
private fun AddCustomDialog(
    onDismiss: () -> Unit,
    onAdded: () -> Unit
) {
    var ipsInput by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加自定义 IP") },
        text = {
            Column {
                OutlinedTextField(
                    value = ipsInput,
                    onValueChange = { ipsInput = it },
                    label = { Text("IP 地址（多个用逗号/换行分隔）") },
                    placeholder = { Text("1.2.3.4, 5.6.7.8") },
                    minLines = 2
                )
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = error!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val ips = ipsInput
                        .split(Regex("[,\\s，]+"))
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .distinct()
                    error = when {
                        ips.isEmpty() -> "请至少填写一个 IP 地址"
                        ips.any { !isIp(it) } -> "存在无效的 IP 地址"
                        else -> null
                    }
                    if (error == null) {
                        Hosts.addCustom(ips)
                        onAdded()
                    }
                }
            ) { Text("添加") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/** IP 合法性：可被 InetAddress 解析且为 IPv4 / IPv6 字面量 */
private fun isIp(text: String): Boolean {
    return try {
        InetAddress.getByName(text)
        text.contains(':') || Regex("^\\d{1,3}(\\.\\d{1,3}){3}$").matches(text)
    } catch (e: Exception) {
        false
    }
}
