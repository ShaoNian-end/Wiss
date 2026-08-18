package com.wiss

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** 从网页解析出的内容：名称、时间、页面地址、图片地址 */
data class WebContent(
    val name: String,
    val time: String,
    val url: String,
    val imageUrl: String
)

/** 网页中的一个区块：大标题 + 一系列卡片数据 */
data class WebSection(
    val title: String,
    val contents: List<WebContent>
)

/** 桌面版 Chrome UA（保证页面按桌面布局渲染，与选择器匹配） */
private const val DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

/**
 * 用 OkHttp 抓取页面 HTML（走共享 OkHttpClient，内置 Hosts 自定义 DNS 生效）。
 * 非 2xx 响应视为异常，与 jsoup 的 HttpStatusException 行为保持一致。
 */
internal fun fetchHtml(url: String): String {
    val request = Request.Builder()
        .url(url)
        .header("User-Agent", DESKTOP_UA)
        .build()
    Hosts.okHttpClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
        return response.body?.string().orEmpty()
    }
}

/** 创建带浏览器 UA 的连接（基于共享 OkHttpClient，内置 Hosts 自定义 DNS 生效） */
private fun connect(url: String): Document = Jsoup.parse(fetchHtml(url), url)

/**
 * 从根元素中解析一系列卡片数据（名称、时间、地址、图片地址）。
 * 站点结构：卡片为 .thumbnail；标题取 .text-secondary；时间取 .bottom-1 第一个元素（视频时长）；
 * 地址为 baseUrl + .relative a 的 href；图片为 .relative img 的 data-src（懒加载图，回退 src）。
 */
private fun parseCardItems(root: Element): List<WebContent> {
    val seen = HashSet<String>()
    return root.select(".thumbnail").mapNotNull { item ->
        // 地址：baseUrl + .relative 内 a 的 href（转为绝对地址）
        val itemUrl = item.selectFirst(".relative a")?.absUrl("href")
        // 过滤：无有效地址、重复地址
        if (itemUrl == null || !itemUrl.startsWith("http") || !seen.add(itemUrl)) return@mapNotNull null

        // 名称：.text-secondary，缺失时回退 title 属性 / 卡片文本
        val name = item.selectFirst(".text-secondary")?.text()?.takeIf { it.isNotBlank() }
            ?: item.attr("title").takeIf { it.isNotBlank() }
            ?: item.text().takeIf { it.isNotBlank() }
            ?: return@mapNotNull null
        
        // 时间：.right-1 第一个元素的文本（视频时长，如 2:12:52）
        val time = item.selectFirst(".right-1")?.text()?.trim() ?: ""

        // 图片地址：优先 data-src（懒加载真实图），其次 src；排除 data: 占位图
        val imageUrl = item.selectFirst(".relative img")?.let { img ->
            val raw = img.attr("data-src").ifBlank { img.attr("src") }
            if (raw.isBlank() || raw.startsWith("data:")) "" else img.absUrl(if (img.attr("data-src").isNotBlank()) "data-src" else "src")
        } ?: ""

        WebContent(name = name, time = time, url = itemUrl, imageUrl = imageUrl)
    }
}

/** 求一组元素最近的公共祖先元素 */
private fun findCommonAncestor(elements: List<Element>): Element? {
    if (elements.isEmpty()) return null
    val first = elements.first()
    var current: Element? = first
    while (current != null) {
        if (elements.all { it.parents().contains(current) }) return current
        current = current.parent()
    }
    return null
}

/**
 * 使用 jsoup 从网页获取名称、时间、地址与图片地址。
 * 字段均优先取 Open Graph 协议标签，缺失时降级到页面常规标签。
 * @param url 网页地址（字符串形参）
 */
suspend fun fetchWebContent(url: String): WebContent = withContext(Dispatchers.IO) {
    val doc = connect(url)

    // 名称：优先 og:title，其次 <title>
    val name = doc.selectFirst("meta[property=og:title]")?.attr("content")
        ?.takeIf { it.isNotBlank() }
        ?: doc.title()

    // 时间：优先 article:published_time，其次 <time> 的 datetime 属性或文本
    val time = doc.selectFirst("meta[property=article:published_time]")?.attr("content")
        ?.takeIf { it.isNotBlank() }
        ?: doc.selectFirst("time")?.attr("datetime")?.takeIf { it.isNotBlank() }
        ?: doc.selectFirst("time")?.text()
        ?: ""

    // 地址：优先 og:url，其次 <link rel="canonical">，最后回退为入参
    val pageUrl = doc.selectFirst("meta[property=og:url]")?.attr("content")
        ?.takeIf { it.isNotBlank() }
        ?: doc.selectFirst("link[rel=canonical]")?.attr("href")?.takeIf { it.isNotBlank() }
        ?: url

    // 图片地址：优先 og:image，其次页面第一张图片（转为绝对地址）
    val imageUrl = doc.selectFirst("meta[property=og:image]")?.attr("content")
        ?.takeIf { it.isNotBlank() }
        ?: doc.selectFirst("img[src]")?.absUrl("src")
        ?: ""

    WebContent(name = name, time = time, url = pageUrl, imageUrl = imageUrl)
}

/**
 * 使用 jsoup 从列表页获取一系列符合条件的数据，用于渲染卡片。
 * @param url 网页地址（字符串形参）
 */
suspend fun fetchWebContentList(url: String): List<WebContent> = withContext(Dispatchers.IO) {
    parseCardItems(connect(url).body())
}

/** 页面筛选/排序下拉的一个选项：显示名称 + 查询参数串（如 "filters=jav"、"sort=views"，空串表示默认/全部） */
data class RadioOption(
    val label: String,
    val query: String = ""
)

/** 分页结果：一页的卡片列表 + 最大页码（从页面分页链接解析，无法确定时为 null） + 筛选/排序选项 */
data class WebPage(
    val contents: List<WebContent>,
    val totalPages: Int?,
    val filterOptions: List<RadioOption>,
    val sortOptions: List<RadioOption>
)

/** 解析分页链接中的最大页码：取所有 a[href] 里 page= 参数的最大值 */
private fun parseTotalPages(doc: Document): Int? =
    doc.select("a[href]").asSequence()
        .mapNotNull { it.absUrl("href") }
        .mapNotNull { href -> Regex("[?&]page=(\\d+)").find(href)?.groupValues?.get(1)?.toIntOrNull() }
        .maxOrNull()

/**
 * 从筛选/排序下拉容器解析一组选项：取触发文本以 [labelPrefix]（"过滤"/"排序"）开头的 .relative 容器
 * （即站点 document.querySelector(".flex.justify-between.mb-6") 下的两个下拉），
 * 选项为容器内 div.py-1 的 a 链接：标签取文本，值为链接的查询参数串（如 "filters=individual"、"sort=views"）。
 */
private fun parseDropdownOptions(root: Element, labelPrefix: String): List<RadioOption> {
    val container = root.select("div.relative").firstOrNull { div ->
        div.selectFirst("span")?.text()?.startsWith(labelPrefix) == true
    } ?: return emptyList()
    return container.select("div.py-1 a").mapNotNull { a ->
        val label = a.text().trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val href = a.absUrl("href")
        if (href.isBlank() || href.endsWith("#")) return@mapNotNull null
        RadioOption(label = label, query = href.substringAfter('?', missingDelimiterValue = ""))
    }
}

/**
 * 分页抓取一页：解析卡片列表、尽量解析总页数，并从页面的筛选/排序控件解析下拉选项，
 * 供「顶栏筛选/排序 + 底栏翻页」的卡片浏览组件使用。
 * @param url 单页网页地址（含页码参数，由 [pageUrl] 生成）
 */
suspend fun fetchWebContentPage(url: String): WebPage = withContext(Dispatchers.IO) {
    val doc = Jsoup.parse(fetchHtml(url), url)
    // 站点筛选/排序控件容器，对应 document.querySelector(".flex.justify-between.mb-6")
    val toolbar = doc.selectFirst(".flex.justify-between.mb-6")
    WebPage(
        contents = parseCardItems(doc.body()),
        totalPages = parseTotalPages(doc),
        filterOptions = toolbar?.let { parseDropdownOptions(it, "过滤") } ?: emptyList(),
        sortOptions = toolbar?.let { parseDropdownOptions(it, "排序") } ?: emptyList()
    )
}

/**
 * 给链接拼接页码：第 1 页返回原链接，其余追加 ?page=N（链接已有查询参数时改用 & 连接）。
 */
fun pageUrl(base: String, page: Int): String {
    if (page <= 1) return base
    val sep = if (base.contains('?')) "&" else "?"
    return "$base${sep}page=$page"
}

/** 多个区块结果的内存缓存（以 url + 选择器为键），避免重复抓取同一页面 */
private val sectionsCache = ConcurrentHashMap<String, List<WebSection>>()

/**
 * 使用 OkHttp 抓取网页 HTML（走内置 Hosts 自定义 DNS），遍历所有匹配根元素，
 * 每个根元素解析为一个区块（大标题 + 卡片数据）。
 * 结果按请求参数缓存，同一页面只抓取一次。
 * @param url 网页地址（字符串形参）
 * @param rootSelector 根元素选择器（如 div.sm\:container），对应 querySelectorAll(...)，
 *                     会取全部匹配项；不传时自动取所有卡片条目的最近公共祖先作为唯一根元素
 */
suspend fun fetchWebSections(
    url: String,
    rootSelector: String? = null
): List<WebSection> = withContext(Dispatchers.IO) {
    val cacheKey = "$url|$rootSelector"
    sectionsCache[cacheKey]?.let { return@withContext it }

    val doc = Jsoup.parse(fetchHtml(url), url)

    // 遍历所有根元素：指定选择器时取全部匹配项（空容器后续会被过滤），否则取卡片条目的公共祖先
    val roots = if (rootSelector != null) {
        doc.select(rootSelector)
    } else {
        listOf(findCommonAncestor(doc.select(".thumbnail")) ?: doc.body())
    }

    // 每个根元素解析为一个区块；不含卡片数据的根元素（如登录弹窗）直接跳过
    val sections = roots.mapNotNull { root ->
        val contents = parseCardItems(root)
        if (contents.isEmpty()) return@mapNotNull null

        // 大标题：优先根元素内 h2，其次文档第一个 h2，最后页面标题
        val title = root.selectFirst("h2")?.text()?.takeIf { it.isNotBlank() }
            ?: doc.selectFirst("h2")?.text()?.takeIf { it.isNotBlank() }
            ?: doc.title()
        WebSection(title = title, contents = contents)
    }

    sectionsCache[cacheKey] = sections
    sections
}

/** 站点多级菜单节点：标签 + 链接 + 子菜单 */
data class MenuNode(
    val label: String,
    val url: String = "",
    val children: List<MenuNode> = emptyList()
)

/** 多级菜单缓存（以首页地址为键） */
private val menuCache = ConcurrentHashMap<String, List<MenuNode>>()

/**
 * 抓取并解析站点首页顶栏的多级菜单，
 * 数据源为 document.querySelector("div.ml-4 div.py-1")（移动端菜单根容器：
 * 直接子级 a 为菜单项，href="#" 的折叠分组头后随 span[x-show]，其内 a 链接为子菜单），结果按首页地址缓存。
 */
suspend fun fetchSiteMenu(context: Context): List<MenuNode> = withContext(Dispatchers.IO) {
    val url = homePageUrl(context)
    menuCache[url]?.let { return@withContext it }
    val menu = runCatching {
        parseMenu(Jsoup.parse(fetchHtml(url), url))
    }.getOrDefault(emptyList())
    menuCache[url] = menu
    menu
}

/** 用 jsoup 解析顶栏菜单：根容器取第一个 div.ml-4 div.py-1，其直接子级 a 为菜单项 */
internal fun parseMenu(doc: Document): List<MenuNode> {
    val root = doc.selectFirst("div.ml-4 div.py-1") ?: return emptyList()
    // 当前站点主机（用于区分站内内容链接与外站推广链接），取抓取时传入的页面地址
    val siteHost = runCatching { Uri.parse(doc.location()).host?.lowercase() }.getOrNull().orEmpty()
    return root.children()
        .filter { it.tagName() == "a" }
        .mapNotNull { parseMenuItem(it) }
        .mapNotNull { filterPromo(it, siteHost) }
}

/**
 * 推广/账号类叶子节点（按 URL 特征判断，与站点语言无关）：
 * - 链接指向外站域名（如 bit.ly、mycomic.com、myavlive.com、姊妹站等推广站）；
 * - 链接为账号相关路径（vip / saved / playlists / history）。
 */
private fun isPromoLeaf(node: MenuNode, siteHost: String): Boolean {
    val url = node.url
    if (url.isBlank()) return false
    val uri = Uri.parse(url)
    val host = uri.host?.lowercase()
    if (siteHost.isNotBlank() && host != null && host != siteHost) return true
    val path = uri.path ?: ""
    return path.contains("/vip") || path.contains("/saved") || path.contains("/playlists") || path.contains("/history")
}

/** 递归过滤推广/账号节点：叶子按规则移除；分组过滤子项后为空且自身无链接时整组移除 */
private fun filterPromo(node: MenuNode, siteHost: String): MenuNode? {
    if (node.children.isEmpty()) {
        return if (isPromoLeaf(node, siteHost)) null else node
    }
    val kept = node.children.mapNotNull { filterPromo(it, siteHost) }
    if (kept.isEmpty() && node.url.isBlank()) return null
    return MenuNode(node.label, node.url, kept)
}

/** 解析单个菜单项：折叠分组头（href="#" 且后随 x-show 的 span）时，span 内的 a 链接作为子菜单 */
private fun parseMenuItem(a: Element): MenuNode? {
    // 标签：链接文本（分组头的标题位于其内部 span 中，同样可被 text() 取到）
    val label = a.text().trim().takeIf { it.isNotBlank() } ?: return null
    // 链接：href="#" 或空视为无跳转（分组头）
    val href = a.absUrl("href")
    val url = if (href.isBlank() || href.endsWith("#")) "" else href
    // 子菜单：紧邻的下一个元素是 span 且带 x-show（折叠内容），取其内部所有 a 递归解析
    val children = a.nextElementSibling()
        ?.takeIf { it.tagName() == "span" && it.hasAttr("x-show") }
        ?.select("a")
        ?.mapNotNull { parseMenuItem(it) }
        ?: emptyList()
    return MenuNode(label = label, url = url, children = children)
}
