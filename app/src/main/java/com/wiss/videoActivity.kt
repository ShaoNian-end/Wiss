package com.wiss

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn as AndroidXOptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.wiss.ui.theme.ThemeSettings
import com.wiss.ui.theme.WissTheme
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** 桌面版 Chrome UA（与站点桌面布局/直链防盗链匹配） */
private const val DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

/**
 * 基于 OkHttp 的 HLS 数据源工厂（带 UA/Referer/Cookie 防盗链头）。
 * 必须用 OkHttp：surrit.com 等防盗链对 ExoPlayer 默认的 HttpURLConnection（仅 HTTP/1.1）返回 403
 * （Cloudflare "Attention Required"），而对 OkHttp（HTTP/2）正常返回 200。
 * 复用共享 OkHttpClient，视频流请求同样走内置 Hosts 自定义 DNS。
 */
@AndroidXOptIn(markerClass = [UnstableApi::class])
private fun okHttpDataSourceFactory(props: Map<String, String>): HttpDataSource.Factory {
    val client = Hosts.okHttpClient
    return androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(client)
        .setUserAgent(DESKTOP_UA)
        .setDefaultRequestProperties(props)
}

/** 信息值：文本 + 链接（url 非空时按 a 链接椭圆胶囊展示） */
data class InfoValue(
    val text: String,
    val url: String = ""
)

/** 信息行：标签（第一个 span，加粗右对齐）+ 值列表 */
data class InfoRow(
    val label: String,
    val values: List<InfoValue> = emptyList()
)

/** 视频页信息：标题、详情（来自页面）、信息行（来自 div.space-y-2，span 为标签、a 链接为胶囊） */
data class VideoInfo(
    val title: String = "",
    val detailTitle: String = "",
    val detailContent: String = "",
    val infoRows: List<InfoRow> = emptyList()
)

/** 推荐视频条目：标题、时长、播放页地址、封面图 */
data class RecommendedVideo(
    val title: String,
    val time: String,
    val url: String,
    val image: String
)

/**
 * 视频播放页：通过 Intent 接收 URL 参数。
 * 用 OkHttp 抓取视频页，从静态 HTML 解析信息、推荐与 m3u8 直链（走内置 Hosts），ExoPlayer 原生播放。
 */
class VideoActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 音量键调节媒体音量
        volumeControlStream = AudioManager.STREAM_MUSIC
        // 应用「主题色」设置（固定 / 动态配色）
        ThemeSettings.load(this)
        val url = intent.getStringExtra(EXTRA_URL) ?: ""
        setContent {
            WissTheme {
                VideoScreen(url = url, onBack = { finish() })
            }
        }
    }

    companion object {
        const val EXTRA_URL = "video_url"
    }
}

/**
 * 解码视频页内嵌的 Dean Edwards packer 混淆串，返回其中的 m3u8 地址列表。
 * 页面格式：p(...,'e=\'8://7.6/5-4-3-2-1/d.0\';c=\'...\';',15,15,'m3u8|f8bdbcc30571|...|source')
 * 混淆串中 0-9 为字典索引，a-z 表示 10+（a=10, b=11, ...），替换后即为真实地址。
 */
private fun decodeM3u8Urls(html: String): List<String> {
    // 字典：最后一个单引号参数，如 'm3u8|f8bdbcc30571|8b16|...|source'
    val dict = Regex(",\\s*\\d+\\s*,\\s*\\d+\\s*,\\s*'([^']+)'").find(html)
        ?.groupValues?.get(1)?.split("|")
        ?: return emptyList()
    // 被混淆的变量赋值串：第一个单引号参数（含 \' 转义引号），如 e=\'8://...\';c=\'...\'
    val body = Regex("\\('((?:[^'\\\\]|\\\\.)*)'\\s*,\\s*\\d+\\s*,\\s*\\d+\\s*,").find(html)
        ?.groupValues?.get(1)
        ?: return emptyList()
    // 提取其中每个 \'...\' 内层串并解码，保留含 .m3u8 的地址（去重）
    return Regex("\\\\'([^\\\\']*)\\\\'").findAll(body)
        .map { decodePackerString(it.groupValues[1], dict) }
        .filter { it.contains(".m3u8") }
        .distinct()
        .toList()
}

/** 按字典替换混淆串中的索引 token（数字 0-9、字母 a-z 表示 10+） */
private fun decodePackerString(encoded: String, dict: List<String>): String {
    return encoded.map { ch ->
        when {
            ch.isDigit() -> dict.getOrElse(ch - '0') { ch.toString() }
            ch in 'a'..'z' -> dict.getOrElse(ch - 'a' + 10) { ch.toString() }
            else -> ch.toString()
        }
    }.joinToString("")
}

/**
 * 用 jsoup 解析视频页标题/详情/信息行。
 * 标题优先 og:title，其次 h1；详情标题取 DIV.border-gray-700 内 span；
 * 信息行来自 div.space-y-2 内 .text-secondary（标签为 span，值为 a 链接/文本）。
 */
private fun parseVideoInfo(doc: Document, pageUrl: String): VideoInfo {
    val title = doc.selectFirst("meta[property=og:title]")?.attr("content")
        ?.takeIf { it.isNotBlank() }
        ?: doc.selectFirst("h1")?.text()?.trim()
        ?: doc.title()
    val detailTitle = doc.selectFirst("div.border-gray-700 span")?.text()?.trim().orEmpty()
    val detailContent = doc.selectFirst("div.break-all")?.text()?.trim().orEmpty()
    val infoHtml = doc.selectFirst("div.space-y-2")?.outerHtml().orEmpty()
    return VideoInfo(
        title = title,
        detailTitle = detailTitle,
        detailContent = detailContent.take(3000),
        infoRows = parseInfoRows(infoHtml, pageUrl)
    )
}

/**
 * 用 jsoup 解析推荐视频：div.grid 内 .thumbnail 卡片（与首页卡片结构一致），
 * 取链接/标题/时长/封面。
 */
private fun parseRecommendedVideos(doc: Document): List<RecommendedVideo> {
    val seen = HashSet<String>()
    return doc.select("div.grid .thumbnail").mapNotNull { item ->
        val url = item.selectFirst(".relative a")?.absUrl("href")
        if (url == null || !url.startsWith("http") || !seen.add(url)) return@mapNotNull null
        val title = item.selectFirst(".text-secondary")?.text()?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: item.attr("title").takeIf { it.isNotBlank() }
            ?: item.text().trim().takeIf { it.isNotBlank() }
            ?: return@mapNotNull null
        val time = item.selectFirst(".right-1")?.text()?.trim() ?: ""
        val image = item.selectFirst(".relative img")?.let { img ->
            val raw = img.attr("data-src").ifBlank { img.attr("src") }
            if (raw.isBlank() || raw.startsWith("data:")) "" else img.absUrl(if (img.attr("data-src").isNotBlank()) "data-src" else "src")
        } ?: ""
        RecommendedVideo(title = title.take(200), time = time, url = url, image = image)
    }
}

/** Recombee client-rapi 配置（database + 公开 token + 区域基址） */
private data class RecombeeConfig(
    val db: String,
    val token: String,
    val baseUri: String
)

/** 各主站域名的 Recombee 配置缓存（从对应 app.js 提取，避免重复抓取） */
private val recombeeConfigCache = ConcurrentHashMap<String, RecombeeConfig>()

/**
 * 从视频页 HTML 中提取 app.js 路径并抓取，解析出 Recombee 配置（database/token/baseUri）。
 * 推荐数据由 Recombee 客户端 API 提供，需按 client-rapi 协议签名请求。
 */
private suspend fun recombeeConfig(origin: String, pageHtml: String): RecombeeConfig? {
    recombeeConfigCache[origin]?.let { return it }
    val jsPath = Regex("<script[^>]*src=\"([^\"]*build/assets/app\\.[a-f0-9]+\\.js)\"").find(pageHtml)
        ?.groupValues?.get(1) ?: return null
    val jsUrl = if (jsPath.startsWith("http")) jsPath else origin + jsPath
    val js = runCatching { fetchHtml(jsUrl) }.getOrNull() ?: return null
    val m = Regex("ApiClient\\(\"([^\"]+)\",\"([^\"]+)\"[^)]*?baseUri:\"([^\"]+)\"").find(js) ?: return null
    return RecombeeConfig(m.groupValues[1], m.groupValues[2], m.groupValues[3]).also {
        recombeeConfigCache[origin] = it
    }
}

/** HMAC-SHA1 十六进制签名（Recombee client-rapi 要求） */
private fun hmacSha1Hex(key: String, message: String): String {
    val mac = Mac.getInstance("HmacSHA1")
    mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA1"))
    return mac.doFinal(message.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

/** 秒数格式化为时长文本（0 返回空串） */
private fun formatDuration(seconds: Int): String {
    if (seconds <= 0) return ""
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** 毫秒格式化为播放时间文本（mm:ss / h:mm:ss） */
private fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

/** 滑动跳转幅度文本：1 分钟内显示「xx 秒」，以上显示 mm:ss */
private fun formatSeekDelta(ms: Long): String {
    val seconds = ms / 1000
    return if (seconds < 60) "${seconds} 秒" else formatTime(ms)
}

/** 按 Material Icons 路径构建播放器图标（Icon 的 tint 会覆盖填充色） */
private fun buildPlayerIcon(name: String, pathData: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).addPath(
        pathData = PathParser().parsePathString(pathData).toNodes(),
        fill = SolidColor(Color.Black)
    ).build()

private val IconPause = buildPlayerIcon("pause", "M6 19h4V5H6v14zm8-14v14h4V5h-4z")
private val IconFullscreen = buildPlayerIcon("fullscreen", "M7 14H5v5h5v-2H7v-3zm-2-4h2V7h3V5H5v5zm12 7h-3v2h5v-5h-2v3zM14 5v2h3v3h2V5h-5z")
private val IconFullscreenExit = buildPlayerIcon("fullscreen_exit", "M5 16h3v3h2v-5H5v2zm3-8H5v2h5V5H8v3zm6 11h2v-3h3v-2h-5v5zm2-11V5h-2v5h5V8h-3z")
private val IconLockOpen = buildPlayerIcon("lock_open", "M12 17c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm6-9h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6h1.9c0-1.71 1.39-3.1 3.1-3.1 1.71 0 3.1 1.39 3.1 3.1v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zm0 12H6V10h12v10z")

/** 系统媒体音量（0..1） */
private fun systemVolume(audioManager: AudioManager): Float {
    val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    return if (max > 0) audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max else 0.5f
}

/** 当前窗口亮度（0..1），未显式设置时取 0.5 */
private fun windowBrightness(context: Context): Float {
    val b = (context as? Activity)?.window?.attributes?.screenBrightness ?: -1f
    return if (b >= 0f) b else 0.5f
}

/**
 * 通过 Recombee client-rapi API 获取推荐视频（签名 POST，无需 JS）。
 * @param origin 当前主站协议+域名（如 https://missav123.com）
 * @param pageHtml 视频页 HTML（用于提取 app.js 路径、dvd_id、封面 CDN）
 */
private suspend fun fetchRecommendedVideos(origin: String, pageHtml: String): List<RecommendedVideo> =
    withContext(Dispatchers.IO) {
        val cfg = recombeeConfig(origin, pageHtml) ?: return@withContext emptyList()
        val dvdId = Regex("dvdId\\s*:\\s*'([^']+)'").find(pageHtml)?.groupValues?.get(1)
            ?: return@withContext emptyList()
        val ts = System.currentTimeMillis() / 1000
        val path = "/${cfg.db}/recomms/items/$dvdId/items/"
        val signMsg = "$path?frontend_timestamp=$ts"
        val url = "https://${cfg.baseUri}$signMsg&frontend_sign=${hmacSha1Hex(cfg.token, signMsg)}"
        val body = JSONObject().apply {
            put("targetUserId", UUID.randomUUID().toString())
            put("count", 16)
            put("scenario", "desktop-watch-next-side")
            put("returnProperties", true)
            put("cascadeCreate", true)
        }
        val request = Request.Builder().url(url)
            .addHeader("Accept", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val respJson = runCatching {
            Hosts.okHttpClient.newCall(request).execute().use { it.body?.string().orEmpty() }
        }.getOrNull() ?: return@withContext emptyList()
        val json = runCatching { JSONObject(respJson) }.getOrNull() ?: return@withContext emptyList()
        val arr = json.optJSONArray("recomms") ?: return@withContext emptyList()
        // 封面 CDN 域名：从视频页 data-poster 提取（如 fourhoi.com）
        val cdnBase = Regex("data-poster=\"https://([^\"/]+)/").find(pageHtml)
            ?.groupValues?.get(1)?.let { "https://$it" }
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("id").takeIf { it.isNotBlank() } ?: continue
                val v = o.optJSONObject("values") ?: continue
                val title = v.optString("title_zh").ifBlank { v.optString("title_cn") }
                    .ifBlank { v.optString("title_en") }.ifBlank { v.optString("title") }
                    .ifBlank { id }
                val dm = v.optString("dm")
                val url = if (dm.isNotBlank()) "$origin/dm$dm/$id" else "$origin/$id"
                add(
                    RecommendedVideo(
                        title = title,
                        time = formatDuration(v.optDouble("duration", 0.0).toInt()),
                        url = url,
                        image = cdnBase?.let { "$it/$id/cover-n.jpg" } ?: ""
                    )
                )
            }
        }
    }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@AndroidXOptIn(markerClass = [UnstableApi::class])
@Composable
private fun VideoScreen(url: String, onBack: () -> Unit) {
    var videoInfo by remember { mutableStateOf<VideoInfo?>(null) }
    var streamUrl by remember { mutableStateOf<String?>(null) }
    var recommended by remember { mutableStateOf<List<RecommendedVideo>>(emptyList()) }
    var playerStatus by remember { mutableStateOf("等待直链...") }
    var qualities by remember { mutableStateOf<List<Int>>(emptyList()) } // 可用画质（高度，升序）
    var selectedQuality by remember { mutableStateOf(0) }               // 0 = 自动
    var currentQuality by remember { mutableStateOf(0) }                // 当前实际画质
    var isFullscreen by remember { mutableStateOf(false) }              // 是否全屏
    val context = LocalContext.current
    val origin = remember(url) { Uri.parse(url).let { "${it.scheme}://${it.host}" } }

    // 页面标题：动态取信息区第二条内容（第二条信息行的首个值），缺失时回退默认标题
    val pageTitle = videoInfo?.infoRows?.getOrNull(1)?.values?.firstOrNull()?.text
        ?.takeIf { it.isNotBlank() } ?: "视频播放"

    // ExoPlayer 实例
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply { playWhenReady = false }
    }
    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    // 离开页面（跳转 ShowCard / 退到后台等，Activity 暂停）时暂停播放，避免视频在后台继续响
    val lifecycleOwner = context as? LifecycleOwner
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) exoPlayer.pause()
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }

    // 播放视频时不息屏
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // 播放状态/错误监控 + 画质跟踪
    LaunchedEffect(Unit) {
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                playerStatus = when (playbackState) {
                    Player.STATE_BUFFERING -> "缓冲中..."
                    Player.STATE_READY -> "播放中"
                    else -> playerStatus
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                playerStatus = "播放错误: ${error.errorCodeName} ${error.message}"
            }

            override fun onTracksChanged(tracks: Tracks) {
                // 收集主清单中所有可用视频画质（高度），用于画质选择
                val heights = tracks.groups
                    .asSequence()
                    .filter { it.type == C.TRACK_TYPE_VIDEO }
                    .flatMap { g -> (0 until g.length).asSequence().map { g.getTrackFormat(it).height } }
                    .filter { it > 0 }
                    .distinct()
                    .sorted()
                    .toList()
                qualities = heights
                // 当前选中的视频轨道高度（强制画质后即所选档位）
                val cur = tracks.groups
                    .firstOrNull { it.type == C.TRACK_TYPE_VIDEO }
                    ?.let { g -> (0 until g.length).firstOrNull { g.isTrackSelected(it) }?.let { g.getTrackFormat(it).height } }
                    ?: 0
                currentQuality = cur
            }
        })
    }

    // 切换画质：0 = 自动（清除覆盖、恢复自适应），否则强制指定高度的轨道
    fun selectQuality(q: Int) {
        selectedQuality = q
        val builder = exoPlayer.trackSelectionParameters.buildUpon()
        if (q == 0) {
            // 自动：清除轨道覆盖与画质上限，恢复码率自适应
            builder.clearOverrides()
            builder.setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
        } else {
            // 强制画质：用 TrackSelectionOverride 精确选中该高度的轨道。
            // 仅 setMaxVideoSize 只是"上限"约束，自适应算法按带宽决定档位，升/降档不一定立即生效。
            val group = exoPlayer.currentTracks.groups.firstOrNull { g ->
                g.type == C.TRACK_TYPE_VIDEO &&
                    (0 until g.length).any { g.getTrackFormat(it).height == q }
            }
            if (group != null) {
                val index = (0 until group.length).first { group.getTrackFormat(it).height == q }
                builder.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index))
            } else {
                // 兜底：找不到精确轨道时退化为高度上限
                builder.setMaxVideoSize(Int.MAX_VALUE, q)
            }
        }
        exoPlayer.trackSelectionParameters = builder.build()
    }

    // 切换全屏：进入时旋转为横屏，退出时恢复竖屏
    fun toggleFullscreen() {
        isFullscreen = !isFullscreen
        (context as? Activity)?.requestedOrientation = if (isFullscreen) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // 全屏时沉浸式：隐藏状态栏/导航栏
    DisposableEffect(isFullscreen) {
        val window = (context as? Activity)?.window
        if (window != null) {
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            if (isFullscreen) {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
                WindowCompat.setDecorFitsSystemWindows(window, true)
            }
        }
        onDispose {
            window?.let { w ->
                WindowInsetsControllerCompat(w, w.decorView).show(WindowInsetsCompat.Type.systemBars())
                WindowCompat.setDecorFitsSystemWindows(w, true)
            }
        }
    }

    // 找到直链后加载并播放（带 Referer/UA 防盗链头，OkHttp 数据源走内置 Hosts）
    LaunchedEffect(streamUrl) {
        val u = streamUrl ?: return@LaunchedEffect
        playerStatus = "加载直链: $u"
        val props = mapOf("Referer" to origin)
        val source = HlsMediaSource.Factory(okHttpDataSourceFactory(props))
            .createMediaSource(MediaItem.fromUri(u))
        exoPlayer.setMediaSource(source)
        // 不自动播放：仅预加载，等待用户点击播放控件
        exoPlayer.prepare()
    }

    // 用 OkHttp 抓取视频页，解析信息/推荐/直链（无 WebView，全部走内置 Hosts）
    LaunchedEffect(url) {
        if (url.isBlank()) return@LaunchedEffect
        runCatching {
            withContext(Dispatchers.IO) {
                val html = fetchHtml(url)
                val doc = Jsoup.parse(html, url)
                videoInfo = parseVideoInfo(doc, url)
                // 推荐优先走 Recombee 接口；失败时回退静态卡片解析
                recommended = fetchRecommendedVideos(origin, html).ifEmpty { parseRecommendedVideos(doc) }
                streamUrl = decodeM3u8Urls(html).firstOrNull()
            }
        }.onFailure {
            playerStatus = "页面加载失败: ${it.message}"
        }.onSuccess {
            if (streamUrl == null) playerStatus = "未找到播放直链"
        }
    }

    Scaffold(
        topBar = {
            if (!isFullscreen) {
                TopAppBar(
                    title = { Text(pageTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.Filled.ArrowBack,
                                contentDescription = "返回",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isFullscreen) PaddingValues(0.dp) else innerPadding)
        ) {
            // 16:9 原生播放器 + Compose 自定义控制层（全屏时填满屏幕）
            Box(
                modifier = Modifier
                    .background(Color.Black)
                    .then(
                        if (isFullscreen) Modifier.fillMaxSize()
                        else Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                    )
            ) {
                VideoPlayerControls(
                    exoPlayer = exoPlayer,
                    title = videoInfo?.title.orEmpty(),
                    isFullscreen = isFullscreen,
                    onToggleFullscreen = ::toggleFullscreen,
                    qualities = qualities,
                    selectedQuality = selectedQuality,
                    onSelectQuality = ::selectQuality,
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (!isFullscreen) {
                // 标题区 + 信息区（番号/标签）+ 详情区 + 推荐视频区（可滚动）
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    item { TitleSection(info = videoInfo) }
                    item { InfoSection(info = videoInfo) }
                    item { DetailSection(info = videoInfo) }
                    item {
                        RecommendedSection(videos = recommended) { recUrl ->
                            // 点击推荐卡片：跳转到对应视频播放页
                            val intent = Intent(context, VideoActivity::class.java)
                                .putExtra(VideoActivity.EXTRA_URL, recUrl)
                            context.startActivity(intent)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 用 jsoup 解析 div.space-y-2 的 HTML：容器为 Column，每个 .text-secondary 即一行 Row；
 * 行内首个子元素为标签（span），其余为值（a 链接携带 url，time/span 为纯文本），文案去除头尾空格。
 */
private fun parseInfoRows(html: String, baseUrl: String?): List<InfoRow> {
    if (html.isBlank()) return emptyList()
    val doc = Jsoup.parse(html, baseUrl ?: "")
    val rows = mutableListOf<InfoRow>()
    doc.select(".text-secondary").forEach { rowEl ->
        val children = rowEl.children()
        if (children.isEmpty()) return@forEach
        val label = children.first()?.text()?.trim().orEmpty()
        val values = children.drop(1).mapNotNull { child ->
            val text = child.text().trim()
            if (text.isBlank() || text.length > 200) null
            else InfoValue(
                text = text,
                url = if (child.tagName() == "a" || child.hasAttr("href")) child.absUrl("href") else ""
            )
        }
        if (label.isNotBlank()) rows.add(InfoRow(label, values))
    }
    return rows
}

/** 可折叠文本：默认展示 collapsedMaxLines 行，超出以 ... 结尾，右下角悬浮 展开/收起 按钮（不影响文本换行） */
@Composable
private fun ExpandableText(
    text: String,
    modifier: Modifier = Modifier,
    collapsedMaxLines: Int = 3,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    lineHeight: TextUnit = 22.sp
) {
    var expanded by remember { mutableStateOf(false) }
    // 折叠状态下是否溢出（溢出才显示展开按钮）
    var needsExpand by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Text(
            text = text,
            style = style,
            color = color,
            lineHeight = lineHeight,
            maxLines = if (expanded) Int.MAX_VALUE else collapsedMaxLines,
            overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis,
            onTextLayout = { layout ->
                // 仅在折叠态测量是否溢出，避免展开后按钮消失
                if (!expanded) needsExpand = layout.hasVisualOverflow
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = if (expanded || needsExpand) 26.dp else 0.dp)
        )
        if (expanded || needsExpand) {
            Text(
                text = if (expanded) "收起" else "展开",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = { expanded = !expanded })
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

/** 标题区：h3 大小，默认折叠为 2 行，超出以 ... + 展开/收起 结尾 */
@Composable
private fun TitleSection(info: VideoInfo?, modifier: Modifier = Modifier) {
    val title = info?.title.orEmpty()
    if (title.isBlank()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        ExpandableText(
            text = title,
            collapsedMaxLines = 2,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 30.sp
        )
    }
}

/** 视频信息区：Card 内 Column + Row 展示信息行（标签加粗右对齐、值左对齐、a 链接为椭圆胶囊） */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InfoSection(info: VideoInfo?, modifier: Modifier = Modifier) {
    if (info == null || info.infoRows.isEmpty()) return

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        // Card > Column > Row：每个 .text-secondary 一行
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            info.infoRows.forEach { row ->
                InfoRowView(row)
            }
        }
    }
}

/** 信息行：标签（等宽、加粗、右对齐）+ 值（左对齐；a 链接为椭圆胶囊） */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InfoRowView(row: InfoRow) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 标签：固定宽度、加粗、右对齐
        Text(
            text = row.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.width(88.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        // 值：含 a 链接时混排胶囊与文本，否则纯文本左对齐
        if (row.values.isEmpty()) {
            Spacer(modifier = Modifier.weight(1f))
        } else if (row.values.any { it.url.isNotBlank() }) {
            FlowRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                row.values.forEach { value ->
                    if (value.url.isNotBlank()) {
                        OvalTag(text = value.text, url = value.url)
                    } else {
                        Text(
                            text = value.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = 3.dp)
                        )
                    }
                }
            }
        } else {
            Text(
                text = row.values.joinToString(" / ") { it.text },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** 椭圆（胶囊）标签：a 链接展示样式，点击以链接为请求地址跳转到卡片浏览页 */
@Composable
private fun OvalTag(text: String, url: String) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.clickable {
            val intent = Intent(context, ShowCardActivity::class.java)
                .putExtra(ShowCardActivity.EXTRA_URL, url)
            context.startActivity(intent)
        }
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
        )
    }
}

/** 详情区：标题与内容均取自页面元素（适配站点语言），内容默认折叠为 3 行 */
@Composable
private fun DetailSection(info: VideoInfo?, modifier: Modifier = Modifier) {
    val title = info?.detailTitle.orEmpty()
    val content = info?.detailContent.orEmpty()
    // 没有详情内容则不显示该模块
    if (content.isBlank()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = title.ifBlank { "详情" },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(start = 16.dp, end = 12.dp, top = 16.dp, bottom = 4.dp)
        ) {
            ExpandableText(text = content, collapsedMaxLines = 3)
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

/** 推荐视频区域：两列卡片网格（复用首页 InfoCard），未提取到数据时不显示 */
@Composable
private fun RecommendedSection(videos: List<RecommendedVideo>, onOpen: (String) -> Unit) {
    if (videos.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Text(
            text = "推荐视频",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        // 每行两个卡片；奇数条时最后一行右侧留空
        videos.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                pair.forEach { v ->
                    InfoCard(
                        data = InfoCardData(imageUrl = v.image, name = v.title, time = v.time),
                        onClick = { onOpen(v.url) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

/**
 * Compose 自定义视频控制层（叠加在 PlayerView 之上）。
 * 功能：全屏时顶部控制条（返回 + 视频标题 + 锁定，总宽 80%）；底部垂直排列（LinearProgressIndicator 进度条 + 控制行），
 * 控制行内：已播放/总时长（左）、播放/暂停（中）、倍速/分辨率/全屏（右）。
 * 手势：单击显隐控制条、双击左/右 10 秒进退、左右滑动按距离浮动前进/后退、
 * 长按右半屏 2 倍速、左/右半屏上下拖动调亮度/音量、防止误触锁。
 * 主题色：进度条、缓冲圈、滑动跳转提示使用 MaterialTheme 主色。
 */
@AndroidXOptIn(markerClass = [UnstableApi::class])
@Composable
private fun VideoPlayerControls(
    exoPlayer: ExoPlayer,
    title: String,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    qualities: List<Int>,
    selectedQuality: Int,
    onSelectQuality: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    var isPlaying by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(false) }
    var ended by remember { mutableStateOf(false) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var controlsVisible by remember { mutableStateOf(true) }
    var locked by remember { mutableStateOf(false) }
    var gestureHint by remember { mutableStateOf<String?>(null) }
    var seekPreviewMs by remember { mutableStateOf<Long?>(null) } // 左右滑动跳转预览目标
    var speed by remember { mutableStateOf(1f) }
    var volume by remember { mutableStateOf(systemVolume(audioManager)) }
    var brightness by remember { mutableStateOf(windowBrightness(context)) }
    var qualityMenu by remember { mutableStateOf(false) }

    // 播放进度轮询
    LaunchedEffect(Unit) {
        while (true) {
            positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            durationMs = exoPlayer.duration.let { if (it == C.TIME_UNSET || it <= 0) 0L else it }
            delay(500)
        }
    }

    // 播放状态监听：暂停/播放图标、缓冲圈、播放结束
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                ended = state == Player.STATE_ENDED
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    // 播放中自动隐藏控制条（锁定或暂停时保持显示）
    LaunchedEffect(controlsVisible, isPlaying, locked) {
        if (controlsVisible && isPlaying && !locked) {
            delay(3000)
            controlsVisible = false
        }
    }

    // 手势/操作提示自动消失
    LaunchedEffect(gestureHint) {
        if (gestureHint != null) {
            delay(1000)
            gestureHint = null
        }
    }

    // 全屏切换（旋转重建窗口）后恢复亮度设置
    LaunchedEffect(isFullscreen) {
        val window = (context as? Activity)?.window ?: return@LaunchedEffect
        val lp = window.attributes
        if (lp.screenBrightness != brightness) {
            lp.screenBrightness = brightness
            window.attributes = lp
        }
    }

    // 退出全屏时自动解锁（非全屏不展示锁定按钮，避免无法解锁）
    LaunchedEffect(isFullscreen) {
        if (!isFullscreen) {
            locked = false
            controlsVisible = true
        }
    }

    fun showHint(text: String) {
        gestureHint = text
    }

    fun seekBy(deltaMs: Long) {
        val target = (exoPlayer.currentPosition + deltaMs).coerceIn(0L, durationMs.coerceAtLeast(0L))
        exoPlayer.seekTo(target)
        positionMs = target
        showHint(if (deltaMs > 0) "前进 10 秒" else "后退 10 秒")
    }

    fun togglePlay() {
        if (ended) {
            exoPlayer.seekTo(0)
            ended = false
        }
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            exoPlayer.play()
            exoPlayer.playWhenReady = true
        }
    }

    fun setSpeed(s: Float) {
        speed = s
        runCatching { exoPlayer.setPlaybackSpeed(s) }
    }

    fun adjustVolume(delta: Float) {
        val nv = (volume + delta).coerceIn(0f, 1f)
        volume = nv
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (nv * max).roundToInt(), 0)
        showHint("音量 ${(nv * 100).roundToInt()}%")
    }

    fun adjustBrightness(delta: Float) {
        val nv = (brightness + delta).coerceIn(0.02f, 1f)
        brightness = nv
        (context as? Activity)?.window?.let { w ->
            val lp = w.attributes
            lp.screenBrightness = nv
            w.attributes = lp
        }
        showHint("亮度 ${(nv * 100).roundToInt()}%")
    }

    // 控制条淡入淡出
    val controlsAlpha by animateFloatAsState(
        targetValue = if (controlsVisible && !locked) 1f else 0f,
        label = "controlsAlpha"
    )

    Box(modifier = modifier) {
        // 视频画面（隐藏 ExoPlayer 默认控制器，全部交互由 Compose 层接管）
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // 手势层：单击显隐控制条；双击左/右 10 秒进退；长按右半屏 2 倍速；
        // 锁定态单击仅提示解锁
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(locked) {
                    if (locked) return@pointerInput
                    val width = size.width
                    detectTapGestures(
                        onTap = { controlsVisible = !controlsVisible },
                        onDoubleTap = { p ->
                            if (p.x < width / 2f) seekBy(-10_000) else seekBy(10_000)
                        },
                        onLongPress = { p ->
                            if (p.x >= width / 2f) {
                                val newSpeed = if (speed == 1f) 2f else 1f
                                setSpeed(newSpeed)
                                showHint(if (newSpeed == 2f) "2.0x 倍速播放" else "恢复 1.0x 倍速")
                            }
                        }
                    )
                }
                // 左半屏上下拖动调亮度，右半屏上下拖动调音量
                .pointerInput(locked) {
                    if (locked) return@pointerInput
                    detectVerticalDragGestures { change, dragAmount ->
                        val isLeft = change.position.x < size.width / 2f
                        val delta = -dragAmount / size.height * 1.5f
                        if (isLeft) adjustBrightness(delta) else adjustVolume(delta)
                    }
                }
                // 左右滑动：按滑动距离浮动预览并跳转（全宽 = 整段时长）
                .pointerInput(locked) {
                    if (locked) return@pointerInput
                    var accum = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { accum = 0f },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            accum += dragAmount
                            val total = durationMs.coerceAtLeast(0L)
                            if (total > 0) {
                                val deltaMs = (accum / size.width * total).toLong()
                                seekPreviewMs = (positionMs + deltaMs).coerceIn(0L, total)
                            }
                        },
                        onDragEnd = {
                            seekPreviewMs?.let { target ->
                                exoPlayer.seekTo(target)
                                positionMs = target
                                showHint("已跳转 ${formatTime(target)}")
                            }
                            seekPreviewMs = null
                        },
                        onDragCancel = { seekPreviewMs = null }
                    )
                }
                .pointerInput(locked) {
                    if (!locked) return@pointerInput
                    detectTapGestures(onTap = { showHint("已锁定，点击右上角解锁") })
                }
        )

        // 缓冲中指示（主题色）
        if (buffering) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 4.dp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(52.dp)
            )
        }

        // 控制条（带淡入淡出）
        if (controlsAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = controlsAlpha }
            ) {
                // ---- 底部：进度条 + 控制行（垂直排列），总宽 80%，圆角 + 边框 ----
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .align(Alignment.BottomCenter)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                            )
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    // 进度条：LinearProgressIndicator（点击/拖动跳转）
                    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(20.dp)
                            .pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    val total = durationMs.coerceAtLeast(0L)
                                    if (total > 0) {
                                        val target = (offset.x / size.width * total).toLong().coerceIn(0L, total)
                                        exoPlayer.seekTo(target)
                                        positionMs = target
                                    }
                                }
                            }
                            .pointerInput(Unit) {
                                detectHorizontalDragGestures { change, _ ->
                                    change.consume()
                                    val total = durationMs.coerceAtLeast(0L)
                                    if (total > 0) {
                                        val target = (change.position.x / size.width * total).toLong().coerceIn(0L, total)
                                        exoPlayer.seekTo(target)
                                        positionMs = target
                                    }
                                }
                            },
                        contentAlignment = Alignment.CenterStart
                    ) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color.White.copy(alpha = 0.35f)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    // 控制行：已播放/总时长（左）· 播放/暂停（水平居中）· 倍速/分辨率/全屏（右）
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 左侧：已播放/总时长（占 1/2 权重区域，文本左对齐）
                        Text(
                            text = if (durationMs > 0) "${formatTime(positionMs)} / ${formatTime(durationMs)}"
                            else formatTime(positionMs),
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        // 播放/暂停（线性样式，无背景；左右等宽权重区域使其水平居中）
                        PlayerIconButton(
                            icon = if (isPlaying) IconPause else Icons.Filled.PlayArrow,
                            size = 44.dp,
                            background = Color.Transparent,
                            borderColor = Color.White.copy(alpha = 0.7f),
                            onClick = { togglePlay() }
                        )
                        // 右侧：倍速/分辨率/全屏（占 1/2 权重区域，右对齐）
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End
                        ) {
                            // 倍速
                            if (speed != 1f) {
                                PlayerChip(text = "${speed}x", onClick = { setSpeed(1f) })
                            }
                            // 分辨率（下拉菜单）
                            Box {
                                PlayerChip(
                                    text = if (selectedQuality == 0) "自动" else "${selectedQuality}p",
                                    onClick = { qualityMenu = true }
                                )
                                DropdownMenu(expanded = qualityMenu, onDismissRequest = { qualityMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text("自动") },
                                        onClick = {
                                            onSelectQuality(0)
                                            qualityMenu = false
                                        }
                                    )
                                    qualities.forEach { q ->
                                        DropdownMenuItem(
                                            text = { Text("${q}p") },
                                            onClick = {
                                                onSelectQuality(q)
                                                qualityMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                            // 全屏
                            PlayerIconButton(
                                icon = if (isFullscreen) IconFullscreenExit else IconFullscreen,
                                size = 42.dp,
                                onClick = onToggleFullscreen
                            )
                        }
                    }
                }
            }
        }

        // 顶部控制条（仅全屏）：返回 + 视频标题（左对齐）· 锁定（右对齐），总宽 80%。
        // 显示/隐藏跟随底部控制组（共用 controlsAlpha 淡入淡出）；锁定态保持可见以便解锁。
        if (isFullscreen && (controlsAlpha > 0.01f || locked)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .align(Alignment.TopCenter)
                    .graphicsLayer { alpha = if (locked) 1f else controlsAlpha }
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)
                        )
                    )
                    .padding(top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 返回（点击退出全屏；非全屏由页面顶栏提供返回）
                IconButton(
                    onClick = { onToggleFullscreen() },
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                ) {
                    Icon(
                        Icons.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                // 视频标题（左对齐，占剩余宽度）
                Text(
                    text = title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp)
                )
                // 锁定（右对齐；锁定后其余控件全部隐藏并禁用）
                IconButton(
                    onClick = {
                        locked = !locked
                        controlsVisible = !locked
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                ) {
                    Icon(
                        if (locked) IconLockOpen else Icons.Filled.Lock,
                        contentDescription = if (locked) "解锁" else "锁定",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // 左右滑动跳转预览（浮动显示前进/后退幅度与目标时间）
        seekPreviewMs?.let { target ->
            val delta = target - positionMs
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (delta >= 0) "前进 ${formatSeekDelta(delta)}" else "后退 ${formatSeekDelta(-delta)}",
                        color = if (delta >= 0) MaterialTheme.colorScheme.primary else Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${formatTime(target)} / ${formatTime(durationMs)}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        // 手势/操作提示（亮度、音量、倍速、进退等）
        gestureHint?.let { hint ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(text = hint, color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** 圆形半透明播放控制按钮（可选描边，线性样式） */
@Composable
private fun PlayerIconButton(
    icon: ImageVector,
    size: Dp,
    onClick: () -> Unit,
    background: Color = Color.Black.copy(alpha = 0.4f),
    borderColor: Color? = null
) {
    val shape = CircleShape
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(background)
            .then(
                if (borderColor != null) Modifier.border(1.5.dp, borderColor, shape) else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(size * 0.52f)
        )
    }
}

/** 播放器小胶囊按钮（画质 / 倍速） */
@Composable
private fun PlayerChip(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = Color.White,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}
