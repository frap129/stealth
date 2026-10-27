package com.cosmos.unreddit.data.remote.api.reddit.graphql.mapper

import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.GraphQlContent
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.GraphQlRichTextMedia
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonReader
import okio.Buffer
import java.io.IOException

object RichTextHtmlConverter {

    private val MEDIA_PLACEHOLDER_REGEX = Regex("""!\[(?:img|gif|video)]\(([^)]*)\)""")

    fun toHtml(content: GraphQlContent?): String? {
        if (content == null) return null
        val mediaById: Map<String, GraphQlRichTextMedia> =
            content.richtextMedia.associateBy { it.id.orEmpty() }
        val rendered: RichTextRenderResult? =
            content.richtext?.takeIf { it.isNotBlank() }?.let { render(it, mediaById) }
        val html: String? = content.html?.takeIf { it.isNotBlank() }
        val renderedHtml: String? = rendered?.html?.takeIf { it.isNotBlank() }
        if (renderedHtml != null && (html == null || rendered.hasMedia)) {
            return renderedHtml
        }
        return html ?: content.markdown?.let { resolveMarkdownMedia(it, mediaById) }
    }

    private fun render(
        richtext: String,
        mediaById: Map<String, GraphQlRichTextMedia>,
    ): RichTextRenderResult? {
        val document: Map<String, Any?> = parse(richtext) ?: return null
        val nodes: List<Any?> = asList(document["document"]) ?: return null
        return renderNodes(nodes, mediaById)
    }

    private fun parse(richtext: String): Map<String, Any?>? =
        try {
            JsonReader.of(Buffer().writeUtf8(richtext)).use { reader ->
                @Suppress("UNCHECKED_CAST")
                reader.readJsonValue() as? Map<String, Any?>
            }
        } catch (exception: IOException) {
            null
        } catch (exception: JsonDataException) {
            null
        }

    private fun renderNodes(
        nodes: List<Any?>,
        mediaById: Map<String, GraphQlRichTextMedia>,
    ): RichTextRenderResult {
        val html = StringBuilder()
        var hasMedia = false
        for (nodeValue in nodes) {
            val node: Map<*, *> = asMap(nodeValue) ?: continue
            val rendered: RichTextRenderResult = renderNode(node, mediaById)
            html.append(rendered.html)
            hasMedia = hasMedia || rendered.hasMedia
        }
        return RichTextRenderResult(html.toString(), hasMedia)
    }

    private fun renderNode(
        node: Map<*, *>,
        mediaById: Map<String, GraphQlRichTextMedia>,
    ): RichTextRenderResult =
        when (val element: String? = asString(node["e"])) {
            "par" -> wrap("p", node, mediaById)
            "text" -> RichTextRenderResult(
                formatText(asString(node["t"]).orEmpty(), node["f"]),
                false,
            )
            "link" -> renderLink(node)
            "r/" -> renderRedditLink("r", node)
            "u/" -> renderRedditLink("u", node)
            "br" -> RichTextRenderResult("<br>", false)
            "img", "gif", "video" -> renderMedia(element, node, mediaById)
            "list" -> wrap(if (asBoolean(node["o"]) == true) "ol" else "ul", node, mediaById)
            "li" -> wrap("li", node, mediaById)
            "blockquote" -> wrap("blockquote", node, mediaById)
            "table" -> renderTable(node, mediaById)
            "h" -> renderHeading(node, mediaById)
            "hr" -> RichTextRenderResult("<hr>", false)
            "code" -> renderCodeBlock(node)
            "p/", "c/" -> renderPermalink(node)
            "spoiler", "spoilertext" -> wrap("span", node, mediaById, " class=\"md-spoiler-text\"")
            "sup" -> wrap("sup", node, mediaById)
            "em" -> wrap("i", node, mediaById)
            "strong" -> wrap("b", node, mediaById)
            "del" -> wrap("strike", node, mediaById)
            else -> renderUnknown(node, mediaById)
        }

    private fun renderLink(node: Map<*, *>): RichTextRenderResult {
        val text: String = formatText(asString(node["t"]).orEmpty(), node["f"])
        val url: String = safeUrl(asString(node["u"]).orEmpty())
            ?: return RichTextRenderResult(text, false)
        return RichTextRenderResult(
            "<a href=\"${escapeAttribute(url)}\">$text</a>",
            false,
        )
    }

    private fun renderRedditLink(prefix: String, node: Map<*, *>): RichTextRenderResult {
        val name: String = asString(node["t"]).orEmpty()
        val label: String = "$prefix/$name"
        return RichTextRenderResult(
            "<a href=\"${escapeAttribute("/$prefix/$name")}\">${escapeHtml(label)}</a>",
            false,
        )
    }

    private fun renderPermalink(node: Map<*, *>): RichTextRenderResult {
        val permalink: String = asString(node["t"]).orEmpty()
        val url: String = safeUrl(permalink)
            ?: return RichTextRenderResult(escapeHtml(permalink), false)
        return RichTextRenderResult(
            "<a href=\"${escapeAttribute(url)}\">${escapeHtml(permalink)}</a>",
            false,
        )
    }

    private fun renderCodeBlock(node: Map<*, *>): RichTextRenderResult {
        val code: String = extractText(node["c"])
        return RichTextRenderResult("<pre><code>${escapeHtml(code)}</code></pre>", false)
    }

    private fun renderMedia(
        element: String,
        node: Map<*, *>,
        mediaById: Map<String, GraphQlRichTextMedia>,
    ): RichTextRenderResult {
        val id: String = asString(node["id"]).orEmpty()
        val url: String? = safeMediaUrl(findMedia(id, mediaById)?.let(::resolveMediaUrl))
        if (url.isNullOrBlank()) return RichTextRenderResult("", false)
        val label: String = mediaLabel(element, url)
        return RichTextRenderResult(
            "<a href=\"${escapeAttribute(url)}\">${escapeHtml(label)}</a>",
            true,
        )
    }

    private fun renderTable(
        node: Map<*, *>,
        mediaById: Map<String, GraphQlRichTextMedia>,
    ): RichTextRenderResult {
        val html = StringBuilder("<table>")
        var hasMedia = false
        val headerRow: List<Any?>? = asList(node["h"])
        if (!headerRow.isNullOrEmpty()) {
            html.append("<tr>")
            for (cellValue in headerRow) {
                val cell: Map<*, *> = asMap(cellValue) ?: continue
                val rendered: RichTextRenderResult = renderChildren(cell["c"], mediaById)
                html.append("<th")
                    .append(alignmentAttribute(asString(cell["a"]).orEmpty()))
                    .append(">")
                    .append(rendered.html)
                    .append("</th>")
                hasMedia = hasMedia || rendered.hasMedia
            }
            html.append("</tr>")
        }
        val rows: List<Any?> = asList(node["c"]).orEmpty()
        for (rowValue in rows) {
            val cells: List<Any?> = asList(rowValue) ?: continue
            html.append("<tr>")
            for (cellValue in cells) {
                val cell: Map<*, *> = asMap(cellValue) ?: continue
                val rendered: RichTextRenderResult = renderChildren(cell["c"], mediaById)
                html.append("<td>").append(rendered.html).append("</td>")
                hasMedia = hasMedia || rendered.hasMedia
            }
            html.append("</tr>")
        }
        html.append("</table>")
        return RichTextRenderResult(html.toString(), hasMedia)
    }

    private fun renderHeading(
        node: Map<*, *>,
        mediaById: Map<String, GraphQlRichTextMedia>,
    ): RichTextRenderResult {
        val level: Int = asInt(node["l"])?.coerceIn(MIN_HEADING_LEVEL, MAX_HEADING_LEVEL)
            ?: DEFAULT_HEADING_LEVEL
        return wrap("h$level", node, mediaById)
    }

    private fun renderUnknown(
        node: Map<*, *>,
        mediaById: Map<String, GraphQlRichTextMedia>,
    ): RichTextRenderResult {
        val children: RichTextRenderResult = renderChildren(node["c"], mediaById)
        if (children.html.isNotEmpty()) return children
        val text: String? = asString(node["t"])
        return if (text.isNullOrEmpty()) {
            RichTextRenderResult("", false)
        } else {
            RichTextRenderResult(formatText(text, node["f"]), false)
        }
    }

    private fun wrap(
        tag: String,
        node: Map<*, *>,
        mediaById: Map<String, GraphQlRichTextMedia>,
        attributes: String = "",
    ): RichTextRenderResult {
        val rendered: RichTextRenderResult = renderChildren(node["c"], mediaById)
        val content: String =
            rendered.html.takeIf { it.isNotEmpty() }
                ?: formatText(asString(node["t"]).orEmpty(), node["f"])
        return RichTextRenderResult(
            "<$tag$attributes>$content</$tag>",
            rendered.hasMedia,
        )
    }

    private fun renderChildren(
        value: Any?,
        mediaById: Map<String, GraphQlRichTextMedia>,
    ): RichTextRenderResult {
        val nodes: List<Any?> = asList(value) ?: return RichTextRenderResult("", false)
        return renderNodes(nodes, mediaById)
    }

    private fun findMedia(
        id: String,
        mediaById: Map<String, GraphQlRichTextMedia>,
    ): GraphQlRichTextMedia? {
        val mediaId: String = id.substringAfterLast('|')
        return mediaById[id] ?: mediaById[mediaId] ?: mediaById.values.firstOrNull {
            it.id?.substringAfterLast('|') == mediaId
        }
    }

    private fun resolveMediaUrl(media: GraphQlRichTextMedia): String? =
        media.url?.takeIf { it.isNotBlank() }
            ?: media.mp4Url?.takeIf { it.isNotBlank() }
            ?: media.packagedMedia?.playbackMp4s?.permutations
                ?.asSequence()
                ?.mapNotNull { it.source?.url }
                ?.firstOrNull { it.isNotBlank() }
            ?: media.still?.content?.url?.takeIf { it.isNotBlank() }

    private fun mediaLabel(element: String, url: String): String =
        when (element) {
            "gif" -> "GIF"
            "video" -> "Video"
            else -> url
        }

    private fun resolveMarkdownMedia(
        markdown: String,
        mediaById: Map<String, GraphQlRichTextMedia>,
    ): String =
        MEDIA_PLACEHOLDER_REGEX.replace(markdown) { match ->
            val id: String = match.groupValues[1]
            val url: String? = safeMediaUrl(findMedia(id, mediaById)?.let(::resolveMediaUrl))
            if (url.isNullOrBlank()) {
                match.value
            } else {
                "<a href=\"${escapeAttribute(url)}\">${escapeHtml(url)}</a>"
            }
        }

    private fun formatText(text: String, formatting: Any?): String {
        val formats: List<RichTextFormat> = parseFormats(formatting)
        if (formats.isEmpty()) return escapeText(text)
        val boundaries: java.util.TreeSet<Int> = java.util.TreeSet()
        boundaries.add(0)
        boundaries.add(text.length)
        for (format in formats) {
            boundaries.add(format.start.coerceIn(0, text.length))
            boundaries.add((format.start + format.length).coerceIn(0, text.length))
        }
        val points: List<Int> = boundaries.toList()
        val html = StringBuilder()
        for (index in 0 until points.lastIndex) {
            val start: Int = points[index]
            val end: Int = points[index + 1]
            if (start >= end) continue
            val active: List<RichTextFormat> = formats.filter {
                it.start <= start && it.start + it.length >= end
            }
            for (format in active) html.append(format.openTag)
            html.append(escapeText(text.substring(start, end)))
            for (format in active.asReversed()) html.append(format.closeTag)
        }
        return html.toString()
    }

    private fun parseFormats(value: Any?): List<RichTextFormat> {
        val items: List<Any?> = asList(value) ?: return emptyList()
        return items.mapNotNull { item ->
            val triple: List<Any?> = asList(item) ?: return@mapNotNull null
            if (triple.size < FORMAT_TRIPLE_SIZE) return@mapNotNull null
            val type: Int = asInt(triple[0]) ?: return@mapNotNull null
            val start: Int = asInt(triple[1]) ?: return@mapNotNull null
            val length: Int = asInt(triple[2]) ?: return@mapNotNull null
            val tag: Pair<String, String> = formatTag(type) ?: return@mapNotNull null
            RichTextFormat(start, length, tag.first, tag.second)
        }
    }

    private fun formatTag(type: Int): Pair<String, String>? =
        when (type) {
            FORMAT_BOLD -> "<b>" to "</b>"
            FORMAT_ITALIC -> "<i>" to "</i>"
            FORMAT_STRIKETHROUGH -> "<strike>" to "</strike>"
            FORMAT_SUPERSCRIPT -> "<sup>" to "</sup>"
            FORMAT_CODE -> "<code>" to "</code>"
            FORMAT_SPOILER -> "<span class=\"md-spoiler-text\">" to "</span>"
            else -> null
        }

    private fun extractText(value: Any?): String {
        val nodes: List<Any?> = asList(value) ?: return ""
        val text = StringBuilder()
        for (nodeValue in nodes) {
            val node: Map<*, *> = asMap(nodeValue) ?: continue
            when (asString(node["e"])) {
                "text" -> text.append(asString(node["t"]).orEmpty())
                "br" -> text.append("\n")
                else -> {
                    val nodeText: String? = asString(node["t"])
                    if (nodeText != null) {
                        text.append(nodeText)
                    } else {
                        text.append(extractText(node["c"]))
                    }
                }
            }
        }
        return text.toString()
    }

    private fun alignmentAttribute(alignment: String): String =
        alignment
            .takeIf { it.isNotBlank() }
            ?.let { " align=\"${escapeAttribute(it)}\"" }
            .orEmpty()

    private fun safeUrl(url: String): String? {
        val trimmed: String = url.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.startsWith("/")) return trimmed
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
        return null
    }

    private fun safeMediaUrl(url: String?): String? =
        url
            ?.let(::safeUrl)
            ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }

    private fun escapeText(value: String): String =
        escapeHtml(value).replace("\n", "<br>")

    private fun escapeHtml(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

    private fun escapeAttribute(value: String): String =
        escapeHtml(value).replace("\"", "&quot;")

    private fun asMap(value: Any?): Map<*, *>? = value as? Map<*, *>

    private fun asList(value: Any?): List<Any?>? {
        @Suppress("UNCHECKED_CAST")
        return value as? List<Any?>
    }

    private fun asString(value: Any?): String? = value as? String

    private fun asBoolean(value: Any?): Boolean? = value as? Boolean

    private fun asInt(value: Any?): Int? = (value as? Number)?.toInt()

    private data class RichTextRenderResult(val html: String, val hasMedia: Boolean)

    private data class RichTextFormat(
        val start: Int,
        val length: Int,
        val openTag: String,
        val closeTag: String,
    )

    private const val FORMAT_TRIPLE_SIZE = 3
    private const val FORMAT_BOLD = 1
    private const val FORMAT_ITALIC = 2
    private const val FORMAT_STRIKETHROUGH = 3
    private const val FORMAT_SUPERSCRIPT = 4
    private const val FORMAT_CODE = 5
    private const val FORMAT_SPOILER = 6
    private const val MIN_HEADING_LEVEL = 1
    private const val MAX_HEADING_LEVEL = 6
    private const val DEFAULT_HEADING_LEVEL = 2
}
