package com.wiss

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.Dns
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

/** 一组自定义 Hosts：仅 IP 列表（无域名），自动应用于当前主站 */
data class CustomHosts(
    val ips: List<String>
)

/**
 * 内置 Hosts 引擎（参考 Han1meViewer 的 HDns 思路）：
 * 应用内拦截 OkHttp 的 DNS 解析，用内置/自定义的 IP 直接连接目标域名，
 * 绕过系统 DNS（慢解析 / 污染），实现加速。无需 VPN、无需 root。
 *
 * - 内置 Hosts 自动应用于全部主站域名，始终生效；
 * - 自定义 Hosts 仅填写 IP（不指定域名），自动应用于当前主站，优先于内置 IP 尝试。
 */
object Hosts {

    private const val PREFS_NAME = "hosts_prefs"
    private const val KEY_ENTRIES = "entries"
    private const val KEY_CF_IPS = "cf_best_ips"

    // 与 UserActivity 中主站存储保持一致（user_prefs / main_site）
    private const val USER_PREFS_NAME = "user_prefs"
    private const val KEY_SITE = "main_site"

    private const val DEFAULT_MAIN_SITE = "missav.ws"

    /** CF 优选后应用的节点数量 */
    const val CF_TOP_COUNT = 8

    /** CF 节点探测超时（毫秒） */
    private const val CF_PROBE_TIMEOUT_MS = 2500

    /**
     * Cloudflare 优选候选节点：取自 CF 官方 anycast 段，
     * 已在大陆网络实测 443 端口均可达（延迟 1~80ms）。
     */
    val cloudflareCandidateIps = listOf(
        "104.16.0.5", "104.16.5.5", "104.16.10.5", "104.16.15.5", "104.16.20.5",
        "104.16.25.5", "104.16.30.5", "104.16.40.5", "104.16.50.5", "104.16.60.5",
        "104.16.80.5", "104.16.100.5", "104.16.120.5", "104.17.5.5", "104.17.50.5",
        "104.18.5.5", "104.18.50.5", "104.19.5.5", "104.19.50.5", "104.20.31.186",
        "104.26.6.107", "104.24.0.5", "104.24.5.5", "172.64.0.5", "172.64.10.5",
        "172.64.20.5", "172.64.30.5", "172.64.50.5", "172.66.167.65", "172.67.72.106",
        "162.159.0.5", "162.159.8.5", "198.41.128.5", "198.41.129.5", "188.114.96.5",
        "141.101.64.5", "108.162.192.5", "173.245.48.5", "190.93.240.5", "197.234.240.5"
    )

    /** 内置映射：主站域名 -> IP 列表，自动应用于全部主站（取自 DNS 解析结果，多为 Cloudflare/AWS 边缘节点） */
    val builtInHosts: Map<String, List<String>> = mapOf(
        "missav.ws" to listOf("104.20.31.186", "172.66.167.65"),
        "missav.live" to listOf("104.26.6.107", "104.26.7.107", "172.67.72.106"),
        "missav123.com" to listOf("104.26.12.189", "104.26.13.189", "172.67.71.238"),
        "missav.ai" to listOf("104.20.16.252", "172.66.173.34")
    )

    @Volatile
    private var customEntries: List<CustomHosts> = emptyList()

    /** CF 优选后应用到当前主站的节点 */
    @Volatile
    private var cfBestIps: List<String> = emptyList()

    /** 当前主站域名（自定义 Hosts 的应用目标） */
    @Volatile
    private var mainSiteHost: String = DEFAULT_MAIN_SITE

    private lateinit var appContext: Context

    private val prefs: SharedPreferences
        get() = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 初始化：读取主站与自定义记录。需在 Application 启动时调用 */
    fun init(context: Context) {
        appContext = context.applicationContext
        mainSiteHost = readMainSiteHost()
        reload()
    }

    /** 当前主站域名 */
    fun mainSiteHost(): String = mainSiteHost

    /** 主站切换时同步（用户在主站对话框选择后调用），使自定义 Hosts 跟随新的主站 */
    fun setMainSite(siteUrl: String) {
        mainSiteHost = hostOf(siteUrl)
    }

    /** 用户自定义的 Hosts 记录（仅 IP，无域名） */
    fun customs(): List<CustomHosts> = customEntries

    /** 当前已应用的 CF 优选节点 */
    fun cfBestIps(): List<String> = cfBestIps

    /** 应用 CF 优选结果（空列表表示清除） */
    fun setCfBestIps(ips: List<String>) {
        cfBestIps = ips.distinct()
        persist()
    }

    /**
     * 并行 TCP 探测 Cloudflare 候选节点（443 端口，2.5s 超时），
     * 返回按延迟升序排列的可达节点 (ip, 延迟毫秒)。
     */
    suspend fun testCloudflareNodes(): List<Pair<String, Long>> = coroutineScope {
        cloudflareCandidateIps.map { ip ->
            async(Dispatchers.IO) {
                val start = System.currentTimeMillis()
                val ms = try {
                    Socket().use { socket ->
                        socket.connect(InetSocketAddress(ip, 443), CF_PROBE_TIMEOUT_MS)
                        System.currentTimeMillis() - start
                    }
                } catch (e: Exception) {
                    Long.MAX_VALUE
                }
                ip to ms
            }
        }.map { it.await() }
            .filter { it.second != Long.MAX_VALUE }
            .sortedBy { it.second }
    }

    /**
     * 按域名查询 IP 地址列表；未命中或 IP 均无效时返回 null（回退系统 DNS）。
     * 优先级：CF 优选 > 自定义 > 内置；其中 CF 优选与自定义仅应用于当前主站。
     */
    fun lookup(hostname: String): List<InetAddress>? {
        val isMainSite = hostname == mainSiteHost
        val cfIps = if (isMainSite) cfBestIps else emptyList()
        val customIps = if (isMainSite) customEntries.flatMap { it.ips } else emptyList()
        val builtinIps = builtInHosts[hostname] ?: emptyList()
        val all = (cfIps + customIps + builtinIps).distinct()
        if (all.isEmpty()) return null
        return all.mapNotNull { ip ->
            runCatching { InetAddress.getByName(ip) }.getOrNull()
        }.takeIf { it.isNotEmpty() }
    }

    /** 添加一组自定义 IP；无有效 IP 时返回 false */
    fun addCustom(ips: List<String>): Boolean {
        val cleanIps = ips.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (cleanIps.isEmpty()) return false
        customEntries = customEntries + CustomHosts(cleanIps)
        persist()
        return true
    }

    /** 删除一组自定义 IP */
    fun removeCustom(index: Int) {
        if (index !in customEntries.indices) return
        customEntries = customEntries.toMutableList().apply { removeAt(index) }
        persist()
    }

    /** 共享 OkHttpClient：页面抓取 / 图片 / 视频 统一走内置 Hosts 自定义 DNS */
    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .dns(HostsDns)
            .build()
    }

    /** 持久化自定义记录与 CF 优选结果（内置映射由代码定义，始终生效，无需存储） */
    private fun persist() {
        val array = JSONArray()
        customEntries.forEach { e ->
            array.put(JSONObject().apply { put("ips", JSONArray(e.ips)) })
        }
        prefs.edit()
            .putString(KEY_ENTRIES, array.toString())
            .putString(KEY_CF_IPS, JSONArray(cfBestIps).toString())
            .apply()
    }

    private fun reload() {
        val raw = prefs.getString(KEY_ENTRIES, null)
        customEntries = if (raw.isNullOrBlank()) {
            emptyList()
        } else {
            runCatching {
                val arr = JSONArray(raw)
                val builtInDomains = builtInHosts.keys
                buildList {
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        // 跳过旧版本存储过的内置记录（旧格式含 host 字段）
                        val oldHost = o.optString("host")
                        if (oldHost.isNotBlank() && oldHost in builtInDomains) continue
                        val ips = o.optJSONArray("ips")?.let { ja ->
                            buildList {
                                for (j in 0 until ja.length()) {
                                    ja.optString(j).takeIf { it.isNotBlank() }?.let { add(it) }
                                }
                            }
                        } ?: emptyList()
                        if (ips.isNotEmpty()) add(CustomHosts(ips))
                    }
                }
            }.getOrDefault(emptyList())
        }
        // 读取 CF 优选结果
        cfBestIps = runCatching {
            val arr = JSONArray(prefs.getString(KEY_CF_IPS, null) ?: "[]")
            buildList {
                for (i in 0 until arr.length()) {
                    arr.optString(i).takeIf { it.isNotBlank() }?.let { add(it) }
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun readMainSiteHost(): String {
        val stored = appContext.getSharedPreferences(USER_PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SITE, null)
        return if (stored.isNullOrBlank()) DEFAULT_MAIN_SITE else hostOf(stored)
    }

    /** 从主站地址提取域名（支持完整 URL 或裸域名） */
    private fun hostOf(siteUrl: String): String {
        return Uri.parse(siteUrl.trim()).host?.takeIf { it.isNotBlank() }
            ?: siteUrl.trim().trim('/').lowercase()
    }
}

/** OkHttp 自定义 DNS：命中 Hosts 记录时返回内置 IP，否则回退系统 DNS */
object HostsDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        return Hosts.lookup(hostname) ?: Dns.SYSTEM.lookup(hostname)
    }
}
