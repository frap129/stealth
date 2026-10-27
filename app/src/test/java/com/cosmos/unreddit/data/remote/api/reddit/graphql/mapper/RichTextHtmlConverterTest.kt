package com.cosmos.unreddit.data.remote.api.reddit.graphql.mapper

import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.GraphQlContent
import com.cosmos.unreddit.data.remote.api.reddit.graphql.model.GraphQlRichTextMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RichTextHtmlConverterTest {

    @Test
    fun `renders image media from richtext`() {
        val content: GraphQlContent =
            GraphQlContent(
                richtext = """{"document":[{"e":"par","c":[{"e":"text","t":"Look:"}]},{"e":"img","id":"abc"}]}""",
                richtextMedia =
                    listOf(
                        GraphQlRichTextMedia(
                            typename = "ImageAsset",
                            id = "abc",
                            url = "https://i.redd.it/abc.png",
                        ),
                    ),
            )

        val actual: String? = RichTextHtmlConverter.toHtml(content)

        assertEquals(
            """<p>Look:</p><a href="https://i.redd.it/abc.png">https://i.redd.it/abc.png</a>""",
            actual,
        )
    }

    @Test
    fun `renders comments when html is missing`() {
        val content: GraphQlContent =
            GraphQlContent(
                richtext = """{"document":[{"e":"par","c":[{"e":"text","t":"hi"}]}]}""",
            )

        val actual: String? = RichTextHtmlConverter.toHtml(content)

        assertEquals("<p>hi</p>", actual)
    }

    @Test
    fun `prefers html for text-only posts`() {
        val content: GraphQlContent =
            GraphQlContent(
                html = "<p>Hello</p>",
                richtext = """{"document":[{"e":"par","c":[{"e":"text","t":"Hello"}]}]}""",
            )

        val actual: String? = RichTextHtmlConverter.toHtml(content)

        assertEquals("<p>Hello</p>", actual)
    }

    @Test
    fun `resolves markdown media placeholder when richtext is missing`() {
        val content: GraphQlContent =
            GraphQlContent(
                markdown = "![img](abc)",
                richtextMedia =
                    listOf(
                        GraphQlRichTextMedia(
                            id = "abc",
                            url = "https://i.redd.it/abc.png",
                        ),
                    ),
            )

        val actual: String? = RichTextHtmlConverter.toHtml(content)

        assertEquals(
            """<a href="https://i.redd.it/abc.png">https://i.redd.it/abc.png</a>""",
            actual,
        )
    }

    @Test
    fun `renders giphy media using the giphy id`() {
        val content: GraphQlContent =
            GraphQlContent(
                markdown = "![gif](giphy|abc)",
                richtextMedia =
                    listOf(
                        GraphQlRichTextMedia(
                            typename = "AnimatedImageAsset",
                            id = "giphy|abc",
                            url = "https://i.redd.it/abc.gif",
                        ),
                    ),
            )

        val actual: String? = RichTextHtmlConverter.toHtml(content)

        assertEquals(
            """<a href="https://i.redd.it/abc.gif">https://i.redd.it/abc.gif</a>""",
            actual,
        )
    }

    @Test
    fun `renders links and formatting`() {
        val content: GraphQlContent =
            GraphQlContent(
                richtext =
                    """{"document":[{"e":"par","c":[{"e":"text","f":[[1,0,3]],"t":"Hi "},{"e":"link","t":"there","u":"https://example.com"}]}]}""",
            )

        val actual: String? = RichTextHtmlConverter.toHtml(content)

        assertEquals(
            """<p><b>Hi </b><a href="https://example.com">there</a></p>""",
            actual,
        )
    }

    @Test
    fun `renders ordered lists`() {
        val content: GraphQlContent =
            GraphQlContent(
                richtext =
                    """{"document":[{"e":"list","o":true,"c":[{"e":"li","c":[{"e":"par","c":[{"e":"text","t":"one"}]}]}]}]}""",
            )

        val actual: String? = RichTextHtmlConverter.toHtml(content)

        assertEquals("<ol><li><p>one</p></li></ol>", actual)
    }

    @Test
    fun `renders code blocks from child content`() {
        val content: GraphQlContent =
            GraphQlContent(
                richtext =
                    """{"document":[{"e":"code","c":[{"e":"text","t":"val x = 1\nval y = 2"}],"l":"kt"}]}""",
            )

        val actual: String? = RichTextHtmlConverter.toHtml(content)

        assertEquals("<pre><code>val x = 1\nval y = 2</code></pre>", actual)
    }

    @Test
    fun `renders table header and body rows`() {
        val content: GraphQlContent =
            GraphQlContent(
                richtext =
                    """{"document":[{"e":"table","h":[{"a":"left","c":[{"e":"text","t":"Name"}]}],"c":[[{"c":[{"e":"text","t":"Value"}]}]]}]}""",
            )

        val actual: String? = RichTextHtmlConverter.toHtml(content)

        assertEquals(
            """<table><tr><th align="left">Name</th></tr><tr><td>Value</td></tr></table>""",
            actual,
        )
    }

    @Test
    fun `renders spoilertext elements`() {
        val content: GraphQlContent =
            GraphQlContent(
                richtext =
                    """{"document":[{"e":"spoilertext","c":[{"e":"text","t":"secret"}]}]}""",
            )

        val actual: String? = RichTextHtmlConverter.toHtml(content)

        assertEquals("""<span class="md-spoiler-text">secret</span>""", actual)
    }

    @Test
    fun `falls back to html when media metadata is missing`() {
        val content: GraphQlContent =
            GraphQlContent(
                html = "<p>Hello</p>",
                richtext = """{"document":[{"e":"img","id":"missing"}]}""",
            )

        val actual: String? = RichTextHtmlConverter.toHtml(content)

        assertEquals("<p>Hello</p>", actual)
    }

    @Test
    fun `falls back to markdown when richtext is empty`() {
        val content: GraphQlContent =
            GraphQlContent(
                richtext = """{"document":[]}""",
                markdown = "fallback",
            )

        val actual: String? = RichTextHtmlConverter.toHtml(content)

        assertEquals("fallback", actual)
    }

    @Test
    fun `renders media when html is present`() {
        val content: GraphQlContent =
            GraphQlContent(
                html = "<p>Body</p>",
                richtext = """{"document":[{"e":"par","c":[{"e":"text","t":"Body"}]},{"e":"img","id":"abc"}]}""",
                richtextMedia =
                    listOf(
                        GraphQlRichTextMedia(
                            id = "abc",
                            url = "https://i.redd.it/abc.png",
                        ),
                    ),
            )

        val actual: String? = RichTextHtmlConverter.toHtml(content)

        assertEquals(
            """<p>Body</p><a href="https://i.redd.it/abc.png">https://i.redd.it/abc.png</a>""",
            actual,
        )
    }

    @Test
    fun `renders horizontal rules`() {
        val content: GraphQlContent = GraphQlContent(richtext = """{"document":[{"e":"hr"}]}""")

        val actual: String? = RichTextHtmlConverter.toHtml(content)

        assertEquals("<hr>", actual)
    }

    @Test
    fun `does not link unsafe schemes`() {
        val content: GraphQlContent =
            GraphQlContent(
                richtext =
                    """{"document":[{"e":"link","t":"bad","u":"javascript:alert(1)"}]}""",
            )

        val actual: String? = RichTextHtmlConverter.toHtml(content)

        assertEquals("bad", actual)
    }

    @Test
    fun `falls back to html when richtext is malformed`() {
        val content: GraphQlContent =
            GraphQlContent(
                html = "<p>Hello</p>",
                richtext = "not json",
            )

        val actual: String? = RichTextHtmlConverter.toHtml(content)

        assertEquals("<p>Hello</p>", actual)
    }

    @Test
    fun `returns null for null content`() {
        assertNull(RichTextHtmlConverter.toHtml(null))
    }
}
