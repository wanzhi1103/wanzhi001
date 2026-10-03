package eu.kanade.tachiyomi.extension.zh.lovemeizi

import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element

class LoveMeizi : HttpSource() {

    override val name = "爱妹子"
    override val baseUrl = "https://www.lovecutes.com"
    override val lang = "zh"
    override val supportsLatest = true

    // 1. 获取热门/最新列表（解析首页或分类页）
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/sort/new/page/$page/", headers)
    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        // 根据 HTML 中的 article 标签提取列表
        val mangas = document.select("article.excerpt").map { element ->
            SManga.create().apply {
                // 标题从 h2 里的 a 标签获取
                title = element.select("h2 a").text()
                // 链接从 h2 里的 a 标签的 href 获取
                setUrlWithoutDomain(element.select("h2 a").attr("href"))
                // 封面图从 img 标签的 src 获取
                thumbnail_url = element.select("img.imgbox-img").attr("src")
                // 封面图是相对路径，需要补全
                if (thumbnail_url!!.startsWith("/")) {
                    thumbnail_url = "$baseUrl$thumbnail_url"
                }
            }
        }
        // 判断是否还有下一页
        val hasNextPage = document.select("a.next-page").isNotEmpty()
        return MangasPage(mangas, hasNextPage)
    }

    // 2. 获取漫画详情
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            // 从 h1 获取标题
            title = document.select("h1.focusbox-title").text()
            // 从 meta 标签获取封面
            thumbnail_url = document.select("meta[property=og:image]").attr("content")
            // 提取标签作为描述或作者
            val tags = document.select("div.article-tags a").map { it.text() }.joinToString(", ")
            author = tags.ifBlank { "未知" }
            description = document.select("meta[name=description]").attr("content")
        }
    }

    // 3. 获取章节列表（将分页转换为章节）
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapters = mutableListOf<SChapter>()
        
        // 提取总页数，HTML里写着 total_pages: 10
        val totalPagesText = document.select("script#article-page-config").html()
        val totalPages = Regex("\"total_pages\":\\s*(\\d+)").find(totalPagesText)?.groupValues?.get(1)?.toIntOrNull() ?: 1

        // 获取当前URL，例如 https://www.lovecutes.com/article/32714/
        val currentUrl = response.request.url.toString()

        for (i in 1..totalPages) {
            val chapter = SChapter.create()
            chapter.name = "第 $i 页"
            // 如果是第1页，URL不加后缀；其他页加 page/2/ 这种后缀
            if (i == 1) {
                chapter.setUrlWithoutDomain(currentUrl)
            } else {
                // 去掉末尾的斜杠再拼接
                val cleanUrl = currentUrl.trimEnd('/')
                chapter.setUrlWithoutDomain("$cleanUrl/page/$i/")
            }
            chapters.add(chapter)
        }
        return chapters
    }

    // 4. 获取图片页面列表（核心：读取图片源）
    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        // 根据 HTML 结构提取图片
        return document.select("p.item-image img.item-image__img").mapIndexed { i, element ->
            val imageUrl = element.attr("src")
            // 如果是相对路径（以 / 开头），拼接 baseUrl
            val fullUrl = if (imageUrl.startsWith("/")) "$baseUrl$imageUrl" else imageUrl
            Page(i, imageUrl = fullUrl)
        }
    }
}
