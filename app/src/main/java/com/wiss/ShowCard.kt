package com.wiss

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/**
 * 卡片浏览组件：接受一个链接作为参数。
 * - 顶栏：标题 + 两个单选菜单（筛选 / 排序），选项数据从页面
 *   document.querySelector(".flex.justify-between.mb-6") 的筛选/排序下拉解析，切换后从第 1 页重新加载；
 * - 中部：可滑动的两列卡片网格（复用 InfoCard），含加载骨架屏 / 失败 / 空态提示；
 * - 底栏：控制当前第几页（上一页 / 下一页 + 页码指示）。
 *
 * @param url 请求链接（筛选 / 排序查询参数与页码在此基础上拼接）
 * @param onBack 传非空时顶栏显示返回按钮
 * @param onOpenCard 点击卡片回调；为空时默认跳转到视频播放页
 */
@Composable
fun ShowCardScreen(
    url: String,
    modifier: Modifier = Modifier,
    title: String = "浏览",
    onBack: (() -> Unit)? = null,
    onOpenCard: ((WebContent) -> Unit)? = null
) {
    val context = LocalContext.current
    // 筛选/排序选项：首次抓取页面后从站点下拉控件解析并固定
    var filterOptions by remember { mutableStateOf<List<RadioOption>>(emptyList()) }
    var sortOptions by remember { mutableStateOf<List<RadioOption>>(emptyList()) }
    var selectedFilter by remember { mutableStateOf<RadioOption?>(null) }
    var selectedSort by remember { mutableStateOf<RadioOption?>(null) }
    var page by remember { mutableIntStateOf(1) }
    var contents by remember { mutableStateOf<List<WebContent>?>(null) }
    var totalPages by remember { mutableStateOf<Int?>(null) }
    var failed by remember { mutableStateOf(false) }
    // 跳转指定页对话框
    var showPageDialog by remember { mutableStateOf(false) }

    // 当前请求地址 = 链接 + 筛选/排序查询参数（& 连接）+ 页码参数
    val requestUrl = remember(url, selectedSort, selectedFilter, page) {
        val params = buildList {
            selectedSort?.query?.takeIf { it.isNotBlank() }?.let { add(it) }
            selectedFilter?.query?.takeIf { it.isNotBlank() }?.let { add(it) }
        }
        var u = url.trimEnd('/')
        if (params.isNotEmpty()) u += (if (u.contains('?')) "&" else "?") + params.joinToString("&")
        pageUrl(u, page)
    }

    // 请求地址变化（翻页 / 切换筛选或排序）时重新抓取
    LaunchedEffect(requestUrl) {
        contents = null
        failed = false
        contents = try {
            val result = fetchWebContentPage(requestUrl)
            totalPages = result.totalPages
            // 首次抓取后解析出筛选/排序下拉选项并选中默认项（选项固定，仅赋值一次）
            if (filterOptions.isEmpty() && result.filterOptions.isNotEmpty()) {
                filterOptions = result.filterOptions
                selectedFilter = result.filterOptions.first()
            }
            if (sortOptions.isEmpty() && result.sortOptions.isNotEmpty()) {
                sortOptions = result.sortOptions
                selectedSort = result.sortOptions.first()
            }
            result.contents
        } catch (e: Exception) {
            failed = true
            null
        }
    }

    // 翻页可用性：已知总页数按页数判断，未知则以上一页是否还有内容判断
    val hasNext = totalPages?.let { page < it } ?: !contents.isNullOrEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ---------- 顶栏：主色铺满并延伸到状态栏后（沉浸），内容避开状态栏 ----------
        Surface(color = MaterialTheme.colorScheme.primary) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(56.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onBack != null) {
                    TextButton(onClick = onBack) {
                        Text(
                            text = "←",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 20.sp
                        )
                    }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.weight(1f)
                )
                // 两个单选菜单：一个控制筛选，一个控制排序
                RadioMenuButton(
                    label = "筛选",
                    options = filterOptions,
                    selected = selectedFilter,
                    onSelect = {
                        selectedFilter = it
                        page = 1
                    }
                )
                RadioMenuButton(
                    label = "排序",
                    options = sortOptions,
                    selected = selectedSort,
                    onSelect = {
                        selectedSort = it
                        page = 1
                    }
                )
            }
        }

        // ---------- 中部：可滑动的卡片网格 ----------
        Box(modifier = Modifier.weight(1f)) {
            when {
                failed -> CenterHint("加载失败，请检查网络后重试")
                contents == null -> GridSkeleton()
                contents.orEmpty().isEmpty() -> CenterHint("没有更多内容")
                else -> CardGrid(
                    contents = contents.orEmpty(),
                    onOpenCard = onOpenCard ?: { content ->
                        context.startActivity(
                            Intent(context, VideoActivity::class.java)
                                .putExtra(VideoActivity.EXTRA_URL, content.url)
                        )
                    }
                )
            }
        }

        // ---------- 底栏：页码控制（浅色铺到导航栏后，内容避开导航栏） ----------
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(onClick = { page-- }, enabled = page > 1) {
                    Text("上一页")
                }
                // 页码指示：点击可跳转到指定页
                Text(
                    text = totalPages?.let { "第 $page / $it 页" } ?: "第 $page 页",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showPageDialog = true }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
                FilledTonalButton(onClick = { page++ }, enabled = hasNext) {
                    Text("下一页")
                }
            }
        }
    }

    // 跳转到指定页对话框（现代化：圆角卡片 + 数字键盘输入 + 快捷步进 + 主题色主按钮）
    if (showPageDialog) {
        val maxPage = totalPages ?: Int.MAX_VALUE
        var input by remember { mutableStateOf(page.coerceIn(1, maxPage).toString()) }
        // 解析输入：非法或越界时视为不可跳转
        val target = input.toIntOrNull()?.coerceIn(1, maxPage)

        Dialog(onDismissRequest = { showPageDialog = false }) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 4.dp,
                shadowElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "跳转到指定页",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (totalPages != null) "共 $totalPages 页，输入页码快速跳转" else "输入页码快速跳转",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    // 页码输入：大号居中数字，数字键盘
                    OutlinedTextField(
                        value = input,
                        onValueChange = { v ->
                            if (v.length <= 9 && v.all { it.isDigit() }) input = v
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        ),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            cursorColor = MaterialTheme.colorScheme.primary
                        ),
                        label = { Text("页码") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    // 快捷步进：−10 / −1 / +1 / +10
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(-10, -1, 1, 10).forEach { step ->
                            val base = target ?: page.coerceIn(1, maxPage)
                            val next = (base + step).coerceIn(1, maxPage)
                            AssistChip(
                                onClick = { input = next.toString() },
                                enabled = next != base,
                                label = { Text(if (step > 0) "+$step" else "$step") }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    // 操作按钮：取消 + 跳转（主题色）
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showPageDialog = false }) { Text("取消") }
                        Spacer(modifier = Modifier.weight(1f))
                        Button(
                            onClick = {
                                target?.let { page = it }
                                showPageDialog = false
                            },
                            enabled = target != null
                        ) { Text("跳转") }
                    }
                }
            }
        }
    }
}

/** 中部卡片网格：两列、与首页一致的间距与留白 */
@Composable
private fun CardGrid(
    contents: List<WebContent>,
    onOpenCard: (WebContent) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(contents) { content ->
            InfoCard(
                data = InfoCardData(content.imageUrl, content.name, content.time),
                onClick = { onOpenCard(content) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** 顶栏单选菜单按钮：点击展开菜单，菜单内每个选项带 RadioButton 单选标记；选项未加载时按钮禁用 */
@Composable
private fun RadioMenuButton(
    label: String,
    options: List<RadioOption>,
    selected: RadioOption?,
    onSelect: (RadioOption) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }, enabled = options.isNotEmpty()) {
            Text(
                text = selected?.let { "$label · ${it.label}" } ?: label,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = if (selected != null && selected != options.firstOrNull()) FontWeight.Bold else FontWeight.Normal
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    leadingIcon = {
                        RadioButton(selected = option == selected, onClick = null)
                    },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

/** 中部居中提示（加载失败 / 空数据） */
@Composable
private fun CenterHint(text: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(24.dp)
        )
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun ShowCardScreenPreview() {
    MaterialTheme {
        ShowCardScreen(
            url = "https://missav.ws/cn/search/demo",
            title = "示例浏览"
        )
    }
}
