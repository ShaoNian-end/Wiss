package com.wiss

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wiss.ui.theme.ColorPicker
import com.wiss.ui.theme.ThemeSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** 语言选项：名称 + 国旗 + 语言代码 + 对应站点路径 */
private data class LanguageOption(
    val name: String,
    val flag: String,
    val code: String,
    val path: String
)

/** 备选语言列表 */
private val languageOptions = listOf(
    LanguageOption("繁體中文", "🇹🇼", "zh-Hant", "/?localized=1"),
    LanguageOption("简体中文", "🇨🇳", "zh-Hans", "/cn"),
    LanguageOption("English", "🇺🇸", "en", "/en"),
    LanguageOption("日本語", "🇯🇵", "ja", "/ja")
)

/** 语言选择的本地存储配置 */
private const val PREFS_NAME = "user_prefs"
private const val KEY_LANGUAGE = "language"

/** 主站选项：显示名称 + 站点地址；isCustom 表示自定义站点 */
private data class SiteOption(
    val label: String,
    val url: String,
    val isCustom: Boolean = false
)

/** 备选主站列表 */
private val siteOptions = listOf(
    SiteOption("missav.ws", "https://missav.ws/"),
    SiteOption("missav.live", "https://missav.live/"),
    SiteOption("missav123.com", "https://missav123.com/"),
    SiteOption("missav.ai", "https://missav.ai/"),
    SiteOption("自定义", "", isCustom = true)
)

/** 主站选择的本地存储 key */
private const val KEY_SITE = "main_site"

/** 从本地存储解析当前设置的主站；未设置时返回默认 missav.ws */
private fun currentSiteOption(context: Context): SiteOption {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getString(KEY_SITE, null)
        ?.takeIf { it.isNotBlank() }
        ?.let { stored ->
            siteOptions.find { it.url == stored }
                ?: SiteOption(label = stored, url = stored, isCustom = true)
        }
        ?: siteOptions[0]  // 默认 missav.ws
}

/**
 * 组合首页请求地址：设置的主站地址 + 语言路径。
 * 从本地存储读取「主站」与「语言」，未设置时使用默认值（missav.ws + 繁體中文）。
 */
internal fun homePageUrl(context: Context): String {
    val site = currentSiteOption(context)
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val language = prefs.getString(KEY_LANGUAGE, null)
        ?.let { code -> languageOptions.find { it.code == code } }
        ?: languageOptions[0]  // 默认繁體中文
    // 站点以 / 结尾时去掉尾部斜杠，避免与语言路径拼接出双斜杠
    return if (language.path.startsWith("/")) site.url.trimEnd('/') + language.path else site.url + language.path
}

/**
 * 组合搜索请求地址：首页地址（主站 + 语言路径）+ /search/ 关键词（关键词做 URL 编码）。
 */
internal fun searchUrl(context: Context, keyword: String): String {
    return homePageUrl(context).trimEnd('/') + "/search/" + Uri.encode(keyword)
}

/** 测速用短超时客户端：复用内置 Hosts 的 DNS 配置，让延迟反映真实加速路径 */
private val latencyClient by lazy {
    Hosts.okHttpClient.newBuilder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()
}

/**
 * 测量站点延迟：HEAD 请求站点根路径，耗时包含 TCP 连接 + TLS 握手 + 响应头；
 * 返回毫秒值，不可达（超时/连接失败）返回 null。
 */
private suspend fun measureLatency(url: String): Long? = withContext(Dispatchers.IO) {
    val request = Request.Builder()
        .url(url)
        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0 Safari/537.36")
        .head()
        .build()
    val start = System.currentTimeMillis()
    try {
        latencyClient.newCall(request).execute().use {
            // 收到响应即视为可达，无论状态码（403/405 也说明服务器可达）
            System.currentTimeMillis() - start
        }
    } catch (e: Exception) {
        null
    }
}

/** 个人页功能入口：图标 + 名称 */
private data class UserMenuEntry(
    val icon: ImageVector,
    val label: String
)

/**
 * 个人页面：顶部为「部分1 头像 + 名称」，
 * 下方为「部分2 观看历史 / 部分3 收藏 / 部分4 设置（语言 / 主站）」功能入口卡片。
 */
@Composable
fun UserScreen() {
    val context = LocalContext.current

    // 语言设置：启动时读取本地存储，选择后立即持久化
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    var selectedLanguage by remember {
        mutableStateOf(
            prefs.getString(KEY_LANGUAGE, null)
                ?.let { code -> languageOptions.find { it.code == code } }
                ?: languageOptions[0]  // 默认繁體中文
        )
    }
    var showLanguageDialog by remember { mutableStateOf(false) }

    // 主站设置：启动时读取本地存储；存储值能匹配固定站点则用之，否则视为自定义站点
    var selectedSite by remember {
        mutableStateOf(
            prefs.getString(KEY_SITE, null)
                ?.takeIf { it.isNotBlank() }
                ?.let { stored ->
                    siteOptions.find { it.url == stored }
                        ?: SiteOption(label = stored, url = stored, isCustom = true)
                }
                ?: siteOptions[0]  // 默认 missav.ws
        )
    }
    var showSiteDialog by remember { mutableStateOf(false) }
    var showCustomSiteDialog by remember { mutableStateOf(false) }
    var customSiteInput by remember { mutableStateOf("") }
    // 主题色设置对话框
    var showThemeDialog by remember { mutableStateOf(false) }
    // 深色模式设置对话框
    var showDarkModeDialog by remember { mutableStateOf(false) }

    // 主站延迟测速：url -> 延迟毫秒（null 表示不可达）
    var latencies by remember { mutableStateOf<Map<String, Long?>>(emptyMap()) }
    var measuring by remember { mutableStateOf(false) }

    // 自定义 Hosts 条数（从 Hosts 页返回后刷新）
    var customHostsCount by remember { mutableStateOf(Hosts.customs().size) }
    val hostsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        customHostsCount = Hosts.customs().size
    }

    // 打开主站对话框时并行测速各站点（含当前自定义主站，若已设置）
    LaunchedEffect(showSiteDialog) {
        if (!showSiteDialog) return@LaunchedEffect
        measuring = true
        latencies = emptyMap()
        val targets = buildList {
            siteOptions.filterNot { it.isCustom }.forEach { add(it.url) }
            if (selectedSite.isCustom) add(selectedSite.url)
        }.distinct()
        val jobs = targets.map { url ->
            launch {
                val ms = measureLatency(url)
                latencies = latencies + (url to ms)
            }
        }
        jobs.forEach { it.join() }
        measuring = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // 部分1：头像 + 名称
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "头像",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(48.dp)
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Wiss 用户",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        // 部分2、3：观看历史 / 收藏（同一卡片，两行）
        UserMenuCard(
            entries = listOf(
                UserMenuEntry(Icons.AutoMirrored.Filled.List, "观看历史"),
                UserMenuEntry(Icons.Filled.Star, "收藏")
            )
        ) { label ->
            Toast.makeText(context, "「$label」功能建设中", Toast.LENGTH_SHORT).show()
        }

        // 部分4：设置 —— 语言 / 主站设置项
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column {
                // 语言：左侧国旗，右侧展示当前选择
                UserMenuRow(
                    leadingText = selectedLanguage.flag,
                    label = "语言",
                    trailingText = "${selectedLanguage.flag} ${selectedLanguage.name}",
                    onClick = { showLanguageDialog = true }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 64.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                // 主站：右侧展示当前站点，自定义时显示自定义地址
                UserMenuRow(
                    icon = Icons.Filled.Home,
                    label = "主站",
                    trailingText = if (selectedSite.isCustom) selectedSite.url else selectedSite.label,
                    onClick = { showSiteDialog = true }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 64.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                // 主题色：固定自定义主色 / 动态配色（Material You）
                UserMenuRow(
                    leadingText = "🎨",
                    label = "主题色",
                    trailingText = if (ThemeSettings.dynamic) "动态配色" else "固定",
                    onClick = { showThemeDialog = true }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 64.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                // 深色模式：跟随系统 / 开启 / 关闭
                UserMenuRow(
                    leadingText = "🌙",
                    label = "深色模式",
                    trailingText = when (ThemeSettings.darkMode) {
                        ThemeSettings.MODE_DARK_ON -> "开启"
                        ThemeSettings.MODE_DARK_OFF -> "关闭"
                        else -> "跟随系统"
                    },
                    onClick = { showDarkModeDialog = true }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 64.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                // Hosts：内置自动应用于全部主站，右侧展示自定义记录数
                UserMenuRow(
                    icon = Icons.Filled.Settings,
                    label = "Hosts 加速",
                    trailingText = "$customHostsCount 条自定义",
                    onClick = {
                        hostsLauncher.launch(Intent(context, HostsActivity::class.java))
                    }
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    // 语言选择对话框：三个备选项均带国旗，当前项打勾
    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text("选择语言") },
            text = {
                Column {
                    languageOptions.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedLanguage = option
                                    prefs.edit().putString(KEY_LANGUAGE, option.code).apply()
                                    showLanguageDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = option.flag, fontSize = 22.sp)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = option.name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            if (option.code == selectedLanguage.code) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "已选择",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showLanguageDialog = false }) { Text("取消") }
            }
        )
    }

    // 主站选择对话框：固定站点直接选择，自定义进入地址输入
    if (showSiteDialog) {
        AlertDialog(
            onDismissRequest = { showSiteDialog = false },
            title = { Text("选择主站") },
            text = {
                Column {
                    siteOptions.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (option.isCustom) {
                                        // 进入自定义输入：预填当前自定义地址
                                        customSiteInput = if (selectedSite.isCustom) selectedSite.url else ""
                                        showSiteDialog = false
                                        showCustomSiteDialog = true
                                    } else {
                                        selectedSite = option
                                        prefs.edit().putString(KEY_SITE, option.url).apply()
                                        Hosts.setMainSite(option.url)
                                        showSiteDialog = false
                                    }
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            // 站点延迟：固定站点按地址取测速结果，自定义站点取当前自定义地址
                            SiteLatencyText(
                                latency = if (option.isCustom) {
                                    if (selectedSite.isCustom) latencies[selectedSite.url] else null
                                } else {
                                    latencies[option.url]
                                },
                                visible = !(option.isCustom && !selectedSite.isCustom),
                                measuring = measuring
                            )
                            // 当前选中项打勾：固定站点按地址匹配，自定义按 isCustom 匹配
                            if ((option.isCustom && selectedSite.isCustom) || (!option.isCustom && option.url == selectedSite.url)) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "已选择",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSiteDialog = false }) { Text("取消") }
            }
        )
    }

    // 自定义主站地址输入对话框
    if (showCustomSiteDialog) {
        AlertDialog(
            onDismissRequest = { showCustomSiteDialog = false },
            title = { Text("自定义主站") },
            text = {
                OutlinedTextField(
                    value = customSiteInput,
                    onValueChange = { customSiteInput = it },
                    label = { Text("站点地址") },
                    placeholder = { Text("https://example.com/") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val url = customSiteInput.trim()
                        if (url.isNotBlank()) {
                            selectedSite = SiteOption(label = url, url = url, isCustom = true)
                            prefs.edit().putString(KEY_SITE, url).apply()
                            Hosts.setMainSite(url)
                            showCustomSiteDialog = false
                        }
                    }
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showCustomSiteDialog = false }) { Text("取消") }
            }
        )
    }

    // 主题色选择对话框：固定（自定义主色，带颜色选择器）/ 动态配色（跟随系统壁纸），选择后立即生效并持久化
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("选择主题色") },
            text = {
                Column {
                    SettingOptionRow(
                        label = "固定",
                        desc = "自定义主色",
                        selected = !ThemeSettings.dynamic,
                        onClick = { ThemeSettings.setDynamic(context, false) }
                    )
                    SettingOptionRow(
                        label = "动态配色",
                        desc = "跟随系统壁纸（Material You）",
                        selected = ThemeSettings.dynamic,
                        onClick = {
                            ThemeSettings.setDynamic(context, true)
                            showThemeDialog = false
                        }
                    )
                    // 固定模式：展示颜色选择器，选色即时生效
                    if (!ThemeSettings.dynamic) {
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(8.dp))
                        ColorPicker(
                            color = ThemeSettings.fixedColor,
                            onColorChange = { ThemeSettings.setFixedColor(context, it) }
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showThemeDialog = false }) { Text("完成") }
            }
        )
    }

    // 深色模式选择对话框：跟随系统 / 开启 / 关闭，选择后立即生效并持久化
    if (showDarkModeDialog) {
        AlertDialog(
            onDismissRequest = { showDarkModeDialog = false },
            title = { Text("选择深色模式") },
            text = {
                Column {
                    SettingOptionRow(
                        label = "跟随系统",
                        desc = "随系统深色/浅色切换",
                        selected = ThemeSettings.darkMode == ThemeSettings.MODE_DARK_SYSTEM,
                        onClick = {
                            ThemeSettings.setDarkMode(context, ThemeSettings.MODE_DARK_SYSTEM)
                            showDarkModeDialog = false
                        }
                    )
                    SettingOptionRow(
                        label = "开启",
                        desc = "始终使用深色",
                        selected = ThemeSettings.darkMode == ThemeSettings.MODE_DARK_ON,
                        onClick = {
                            ThemeSettings.setDarkMode(context, ThemeSettings.MODE_DARK_ON)
                            showDarkModeDialog = false
                        }
                    )
                    SettingOptionRow(
                        label = "关闭",
                        desc = "始终使用浅色",
                        selected = ThemeSettings.darkMode == ThemeSettings.MODE_DARK_OFF,
                        onClick = {
                            ThemeSettings.setDarkMode(context, ThemeSettings.MODE_DARK_OFF)
                            showDarkModeDialog = false
                        }
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDarkModeDialog = false }) { Text("完成") }
            }
        )
    }
}

/** 设置选项行：名称 + 说明，当前项打勾 */
@Composable
private fun SettingOptionRow(
    label: String,
    desc: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = desc,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(12.dp))
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "已选择",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * 站点延迟文本：测速中显示「测速中...」；测出结果显示毫秒；失败显示「不可达」。
 * @param latency 延迟毫秒，null 表示尚无结果或不可达
 * @param visible 是否显示（未设置自定义主站时其所在行不显示）
 * @param measuring 是否正在测速（仅当尚无结果时展示「测速中...」）
 */
@Composable
private fun SiteLatencyText(
    latency: Long?,
    visible: Boolean,
    measuring: Boolean
) {
    if (!visible) return
    val (text, color) = when {
        latency != null -> "${latency} ms" to MaterialTheme.colorScheme.primary
        measuring -> "测速中..." to MaterialTheme.colorScheme.onSurfaceVariant
        else -> "不可达" to MaterialTheme.colorScheme.error
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = color
    )
    Spacer(Modifier.width(12.dp))
}

/** 功能入口卡片：按行渲染多个入口，行与行之间用分隔线隔开 */
@Composable
private fun UserMenuCard(
    entries: List<UserMenuEntry>,
    onEntryClick: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            entries.forEachIndexed { index, entry ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 64.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
                UserMenuRow(
                    icon = entry.icon,
                    label = entry.label,
                    onClick = { onEntryClick(entry.label) }
                )
            }
        }
    }
}

/**
 * 单行功能入口：左侧图标块（图标或国旗文字）+ 名称 + 右侧附加文本 + 右箭头。
 * @param leadingText 左侧图标块内显示的文字（如国旗），优先于 [icon]
 */
@Composable
private fun UserMenuRow(
    icon: ImageVector? = null,
    leadingText: String? = null,
    label: String,
    trailingText: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (leadingText != null) {
                Text(text = leadingText, fontSize = 20.sp)
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        if (trailingText != null) {
            Text(
                text = trailingText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 180.dp)
            )
            Spacer(Modifier.width(8.dp))
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun UserScreenPreview() {
    MaterialTheme {
        UserScreen()
    }
}
