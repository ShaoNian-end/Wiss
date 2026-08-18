package com.wiss

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/** 底部导航项数据：名称 + 图标 */
private data class BottomNavItem(
    val label: String,
    val icon: ImageVector
)

/**
 * 主页脚手架：TopAppBar + 底部 NavigationBar。
 * 底部导航包含「首页 / 我的」两个标签，点击切换内容；
 * 顶栏右侧提供站点多级菜单（数据来自 div.ml-4 div.py-1）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold() {
    var selectedIndex by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    var menuItems by remember { mutableStateOf<List<MenuNode>?>(null) }
    // 记录上次按返回键的时间，用于「首页连续按两次返回退出」
    var lastBackTime by remember { mutableStateOf(0L) }

    // 返回键：在「我的」页时先返回首页；在首页时连续按两次返回才退出应用
    BackHandler {
        when {
            selectedIndex == 1 -> selectedIndex = 0
            else -> {
                val now = System.currentTimeMillis()
                if (now - lastBackTime < 2000) {
                    (context as? Activity)?.finish()
                } else {
                    lastBackTime = now
                    Toast.makeText(context, "再按一次返回键退出", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 加载站点顶栏多级菜单
    LaunchedEffect(Unit) {
        menuItems = fetchSiteMenu(context)
    }

    val navItems = listOf(
        BottomNavItem("首页", Icons.Default.Home),
        BottomNavItem("我的", Icons.Default.Person)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(navItems[selectedIndex].label) },
                actions = {
                    SiteMenuButton(
                        items = menuItems,
                        onOpen = { url ->
                            if (url.isNotBlank()) {
                                context.startActivity(
                                    Intent(context, ShowCardActivity::class.java)
                                        .putExtra(ShowCardActivity.EXTRA_URL, url)
                                )
                            }
                        }
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        bottomBar = {
            NavigationBar {
                navItems.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = selectedIndex == index,
                        onClick = { selectedIndex = index },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedIndex) {
                0 -> HomeScreen()
                1 -> UserScreen()
                else -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("「${navItems[selectedIndex].label}」页面建设中")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MainScaffoldPreview() {
    MaterialTheme {
        MainScaffold()
    }
}

/** 顶栏多级菜单按钮：点击展开站点导航菜单（父级可逐级展开/收起） */
@Composable
private fun SiteMenuButton(items: List<MenuNode>?, onOpen: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var expandedKeys by remember { mutableStateOf(setOf<String>()) }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "菜单",
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            when {
                items == null -> DropdownMenuItem(text = { Text("菜单加载中…") }, onClick = {})
                items.isEmpty() -> DropdownMenuItem(text = { Text("暂无菜单") }, onClick = {})
                else -> Column(
                    modifier = Modifier
                        .heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState())
                        .widthIn(max = 300.dp)
                ) {
                    items.forEachIndexed { i, node ->
                        renderMenuItem(
                            node = node,
                            path = "$i",
                            depth = 0,
                            expandedKeys = expandedKeys,
                            onToggle = { key ->
                                expandedKeys = if (key in expandedKeys) expandedKeys - key else expandedKeys + key
                            },
                            onOpen = { url ->
                                onOpen(url)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

/** 递归渲染菜单节点：叶子项点击打开链接；父级项点击展开/收起子菜单（按层级缩进） */
@Composable
private fun renderMenuItem(
    node: MenuNode,
    path: String,
    depth: Int,
    expandedKeys: Set<String>,
    onToggle: (String) -> Unit,
    onOpen: (String) -> Unit
) {
    if (node.children.isEmpty()) {
        DropdownMenuItem(
            text = {
                Text(
                    text = node.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = (depth * 16).dp)
                )
            },
            onClick = { onOpen(node.url) }
        )
    } else {
        val isExpanded = path in expandedKeys
        DropdownMenuItem(
            text = {
                Text(
                    text = node.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = (depth * 16).dp)
                )
            },
            trailingIcon = {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null
                )
            },
            onClick = { onToggle(path) }
        )
        if (isExpanded) {
            node.children.forEachIndexed { i, child ->
                renderMenuItem(
                    node = child,
                    path = "$path.$i",
                    depth = depth + 1,
                    expandedKeys = expandedKeys,
                    onToggle = onToggle,
                    onOpen = onOpen
                )
            }
        }
    }
}
