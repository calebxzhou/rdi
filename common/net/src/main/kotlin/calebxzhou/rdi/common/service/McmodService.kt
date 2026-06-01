package calebxzhou.rdi.common.service

object McmodService {
    val mcmodSearchUrl = "https://search.mcmod.cn/s?key="
    val mcmodHeader
        get() =
            """  
Host: search.mcmod.cn
User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:137.0) Gecko/20100101 Firefox/137.0
Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8
Accept-Language: zh-CN,zh;q=0.5
Accept-Encoding: 1
Connection: keep-alive
Cookie: MCMOD_SEED=${
                (1..26)
                    .map { ('a'..'z') + ('0'..'9').random() } // Pick a random character from the allowed set
                    .joinToString("")
            }; search_history_list=
Upgrade-Insecure-Requests: 1
Sec-Fetch-Dest: document
Sec-Fetch-Mode: navigate
Sec-Fetch-Site: none
Sec-Fetch-User: ?1
Priority: u=0, i
        """.trimIndent().split("\n").map { it.split(": ") }.map { it[0] to it[1] }
   /* private val mcmodCacheRoot by lazy { File(File(RDI.DIR, "cache"), "mcmod").apply { mkdirs() } }

    private fun mcmodCacheFile(url: String): File? {
        val path = runCatching { URI(url).path }.getOrNull()?.trimStart('/') ?: return null
        val safeSegments = path.split('/')
            .filter { it.isNotBlank() && it != "." && it != ".." }
        if (safeSegments.isEmpty()) {
            return null
        }
        val relativePath = safeSegments.joinToString(File.separator)
        return File(mcmodCacheRoot, relativePath)
    }*/
    /*suspend fun getInfoMcmod(modId: String,modName: String): McmodInfo? {

            val modUrl = httpStringRequest(url = "${mcmodSearchUrl}${"$modId $modName".urlEncoded}", headers = mcmodHeader).body
                .let { Jsoup.parse(it) }
                .select(".result-item>.head>a").firstOrNull()
                ?.attr("href")
                ?: let {
                    lgr.warn { "mcmod未找到mod信息，关键词：$modId $modName" }
                    return null
                }
            val cacheFile = mcmodCacheFile(modUrl)
            val cachedBody = cacheFile?.takeIf { it.exists() }?.let { file ->
                runCatching { file.readText(StandardCharsets.UTF_8) }
                    .onFailure { err -> lgr.warn { "读取mcmod缓存失败: ${file.absolutePath}\n$err" } }
                    .getOrNull()
            }
            val cacheExpired = cacheFile?.let { !it.exists() || System.currentTimeMillis() - it.lastModified() > MCMOD_CACHE_TTL_MS } ?: true

            val modBody = if (!cacheExpired && !cachedBody.isNullOrBlank()) {
                cachedBody
            } else {
                val modHost = runCatching { URI(modUrl).host }.getOrNull()
                val detailHeaders = if (modHost.isNullOrBlank()) {
                    mcmodHeader
                } else {
                    mcmodHeader.map { (key, value) ->
                        if (key.equals("Host", ignoreCase = true)) key to modHost else key to value
                    }
                }

                val modResponse = httpStringRequest(url = modUrl, headers = detailHeaders)

                if (!modResponse.success) {
                    if (!cachedBody.isNullOrBlank()) {
                        lgr.warn { "mcmod详情页请求失败: HTTP ${modResponse.statusCode()} url=$modUrl，使用本地缓存" }
                        cachedBody
                    } else {
                        lgr.warn { "mcmod详情页请求失败: HTTP ${modResponse.statusCode()} url=$modUrl" }
                        return null
                    }
                } else {
                    val networkBody = modResponse.body()
                    if (networkBody.isNullOrBlank()) {
                        if (!cachedBody.isNullOrBlank()) {
                            lgr.warn { "mcmod详情页返回空内容，url=$modUrl，使用本地缓存" }
                            cachedBody
                        } else {
                            lgr.warn { "mcmod详情页返回空内容，url=$modUrl" }
                            return null
                        }
                    } else {
                        cacheFile?.let { file ->
                            runCatching {
                                file.parentFile?.mkdirs()
                                file.writeText(networkBody, StandardCharsets.UTF_8)
                            }.onFailure { err ->
                                lgr.warn { "mcmod详情页缓存写入失败: ${file.absolutePath}\n$err" }
                            }
                        }
                        networkBody
                    }
                }
            }

            if (modBody.isNullOrBlank()) {
                lgr.warn { "mcmod详情页内容为空，url=$modUrl" }
                return null
            }

            val document = Jsoup.parse(modBody, modUrl)

            val classTitle = document.selectFirst(".class-text .class-title")
            val nameCn = classTitle?.selectFirst("h3")?.text()?.trim().orEmpty()
            val englishName = classTitle?.selectFirst("h4")?.text()?.trim().orEmpty()
            val resolvedName = englishName.ifBlank { nameCn }

            val categories = document
                .select(".class-text-top .common-class-category li a")
                .map { it.text().trim() }
                .filter { it.isNotEmpty() }
                .distinct()

            val introElement = document.selectFirst("div.class-menu-main[data-frame=\"2\"] li.text-area")
            val intro = introElement?.let { element ->
                element.select("script,style").remove()
                val lines = element
                    .select("p,li")
                    .map { it.text().trim() }
                    .filter { it.isNotEmpty() }
                when {
                    lines.isNotEmpty() -> lines.joinToString("\n")
                    else -> element.text().trim()
                }
            } ?: ""

            val authors = document
                .select("li.col-lg-12.author div.frame ul li")
                .mapNotNull { authorLi ->
                    val displayName =
                        authorLi.selectFirst(".member .name")?.text()?.trim().orEmpty()
                    if (displayName.isBlank()) {
                        return@mapNotNull null
                    }
                    val avatarUrl =
                        authorLi.selectFirst(".avatar img")?.absUrl("src")?.trim().orEmpty()
                    val role = authorLi.selectFirst(".member .position")?.text()?.trim().orEmpty()

                    ModAuthor(
                        name = displayName,
                        avatarUrl = avatarUrl,
                        role = role
                    )
                }
            val logoUrl = document.select(".class-cover-image img").firstOrNull()?.absUrl("src")?.trim().orEmpty()
            return McmodInfo(
                pageUrl = modUrl,
                logoUrl = logoUrl,
                name = resolvedName.ifBlank { modName },
                nameCn = nameCn.ifBlank { resolvedName.ifBlank { modName } },
                categories = categories,
                intro = intro,
                authors = authors
            )

        }*/
}
