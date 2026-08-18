package com.wiss

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/** 网格通用参数：两列、间距、四周留白 */
private val GridContentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)
private val GridSpacing = 12.dp

/**
 * 主页内容网格：在一个懒加载网格中轮番渲染多个区块。
 * 每个区块 = 占满整行的标题 + 若干卡片（每行两个）。
 */
@Composable
fun HomeContentGrid(
    sections: List<WebSection>,
    modifier: Modifier = Modifier,
    onClick: (WebContent) -> Unit = {}
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxSize(),
        contentPadding = GridContentPadding,
        horizontalArrangement = Arrangement.spacedBy(GridSpacing),
        verticalArrangement = Arrangement.spacedBy(GridSpacing)
    ) {
        sections.forEach { section ->
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }
            items(section.contents) { content ->
                InfoCard(
                    data = InfoCardData(content.imageUrl, content.name, content.time),
                    onClick = { onClick(content) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/** 数据未返回时的网格骨架屏：标题条 + 若干张 shimmer 卡片 */
@Composable
internal fun GridSkeleton() {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = GridContentPadding,
        horizontalArrangement = Arrangement.spacedBy(GridSpacing),
        verticalArrangement = Arrangement.spacedBy(GridSpacing)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .padding(top = 8.dp, bottom = 4.dp)
            )
        }
        items(6) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column {
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                    )
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun HomeScreen() {
    var sections by remember { mutableStateOf<List<WebSection>?>(null) }
    var failed by remember { mutableStateOf(false) }
    val context = LocalContext.current
    // 首页地址 = 设置的主站 + 语言路径（进入本页时读取最新设置）
    val homeUrl = remember { homePageUrl(context) }

    LaunchedEffect(homeUrl) {
        sections = try {
            failed = false
            fetchWebSections(
                url = homeUrl,                  // 网页地址：主站 + 语言路径
                rootSelector = "div.sm\\:container"  // 根元素选择器，对应 querySelectorAll(...)
            )
        } catch (e: Exception) {
            failed = true  // 网络失败或解析异常
            null
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 搜索组件（固定 4:3），置于卡片上方；提交关键词后跳转卡片浏览页
        SearchBar(
            onSearch = { keyword ->
                val intent = Intent(context, ShowCardActivity::class.java)
                    .putExtra(ShowCardActivity.EXTRA_URL, searchUrl(context, keyword))
                context.startActivity(intent)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )
        // 卡片区（可滚动）
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (val secs = sections) {
                null -> if (failed) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "加载失败，请检查网络后重试",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    GridSkeleton()  // 加载中：骨架屏占位
                }
                else -> HomeContentGrid(sections = secs) { content ->
                    // 点击卡片：携带 url 参数跳转到视频页
                    val intent = Intent(context, VideoActivity::class.java)
                        .putExtra(VideoActivity.EXTRA_URL, content.url)
                    context.startActivity(intent)
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun HomeContentGridPreview() {
    MaterialTheme {
        HomeContentGrid(
            sections = listOf(
                WebSection(
                    title = "热门推荐",
                    contents = listOf(
                        WebContent("示例卡片一：这是一段较长的标题文字", "02:12:52", "https://example.com/1", "https://picsum.photos/400/300"),
                        WebContent("示例卡片二", "12:34", "https://example.com/2", "https://picsum.photos/400/300")
                    )
                ),
                WebSection(
                    title = "最新上架",
                    contents = listOf(
                        WebContent("示例卡片三", "45:01", "https://example.com/3", "https://picsum.photos/400/300"),
                        WebContent("示例卡片四", "1:30", "https://example.com/4", "https://picsum.photos/400/300")
                    )
                )
            )
        )
    }
}
