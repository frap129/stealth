# GraphQL Post Formatting Implementation Plan

> **For agentic workers:** REQUIRED: Use the execution workflow selected at handoff. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Preserve formatting in GraphQL-backed post bodies by preferring Reddit's structured richtext over its formatting-free HTML field.

**Architecture:** Keep `RichTextHtmlConverter` as the single conversion seam for post and comment bodies. Return a nonblank richtext rendering first, then fall back to HTML and the existing Markdown media-resolution path; `PostMapper2`, persistence, and UI code remain unchanged.

**Tech Stack:** Kotlin 1.7.20, Moshi 1.14.0 codegen, JUnit 4.13.2, Android Gradle Plugin 7.3.1, Gradle 7.5.1.

## Global Constraints

- Modify only the two files listed in Task 1 plus this plan.
- Keep `fun toHtml(content: GraphQlContent?): String?` unchanged.
- Add no runtime or test dependencies.
- Tests are JVM-only and must not access the network.
- Preserve existing HTML escaping and safe-URL behavior in richtext rendering.
- Use explicit Kotlin variable and function types, English names, and no blank lines inside functions.
- Follow TDD: prove the regression test fails before changing production code.
- Use the `codebase-design` skill: keep the existing converter as the single seam and add no new interface or abstraction.
- Use the `test-driven-development` skill during implementation and the `verification-before-completion` skill before reporting completion.
- Do not commit automatically; the controller owns the atomic commit after verification.

## Verified Evidence

- Live `PostsByIds` response for `t3_1wzvqsb`: `content.html` contained 2,595 characters, 12 newlines, and zero HTML tags.
- The same response's `content.richtext` contained paragraph nodes, bold format ranges `[1, 8, 3]` and `[1, 63, 3]`, and link nodes for `u/frankmonza`, `u/ginlemon`, and `Wild Pixels`.
- A harness against the current converter returned `content.html` unchanged with no `<b>` or `<a>` tags; removing `html` made the same richtext render bold text and links.
- Current selection logic is in `RichTextHtmlConverter.kt:18-25`; it rejects rendered richtext for text-only content because `rendered.hasMedia` is false.

## File Structure

- `app/src/main/java/com/cosmos/unreddit/data/remote/api/reddit/graphql/mapper/RichTextHtmlConverter.kt`: owns representation selection and all richtext-to-HTML rendering.
- `app/src/test/java/com/cosmos/unreddit/data/remote/api/reddit/graphql/mapper/RichTextHtmlConverterTest.kt`: owns regression and fallback coverage for the converter's public interface.

---

### Task 1: Prefer formatted richtext and preserve fallbacks

**Files:**

- Modify: `app/src/main/java/com/cosmos/unreddit/data/remote/api/reddit/graphql/mapper/RichTextHtmlConverter.kt:14-26`
- Test: `app/src/test/java/com/cosmos/unreddit/data/remote/api/reddit/graphql/mapper/RichTextHtmlConverterTest.kt:46-57`

**Interfaces:**

- Consumes: `GraphQlContent.html`, `GraphQlContent.markdown`, `GraphQlContent.richtext`, and `GraphQlContent.richtextMedia` from Moshi's generated `GraphQlContent` adapter.
- Produces: `RichTextHtmlConverter.toHtml(content: GraphQlContent?): String?` with the same public signature.
- Contract: Return a nonblank richtext rendering whenever rendering succeeds; otherwise return nonblank HTML; otherwise return the existing Markdown media-resolution result. Null content returns null. Malformed or empty richtext must not suppress valid HTML. Existing media resolution, escaping, and safe-URL filtering remain unchanged.

- [x] **Step 1: Replace the synthetic HTML-preference test with the real-wire-shape regression**

Add `import com.squareup.moshi.Moshi` in alphabetical import order. Replace the `prefers html for text-only posts` test at lines 47-57 with the following test, and add the fallback test after it.

```kotlin
@Test
fun `prefers formatted richtext when html is plain text`() {
    val inputJson: String =
        """
        {
          "html": "Hello bold and link\nNext paragraph",
          "markdown": "Hello **bold** and [link](https://example.com)\n\nNext paragraph",
          "richtext": "{\"document\":[{\"e\":\"par\",\"c\":[{\"e\":\"text\",\"t\":\"Hello \"},{\"e\":\"text\",\"t\":\"bold\",\"f\":[[1,0,4]]},{\"e\":\"text\",\"t\":\" and \"},{\"e\":\"link\",\"t\":\"link\",\"u\":\"https://example.com\"}]},{\"e\":\"par\",\"c\":[{\"e\":\"text\",\"t\":\"Next paragraph\"}]}]}"
        }
        """.trimIndent()
    val inputContent: GraphQlContent =
        requireNotNull(
            Moshi.Builder()
                .build()
                .adapter(GraphQlContent::class.java)
                .fromJson(inputJson),
        )
    val actualHtml: String? = RichTextHtmlConverter.toHtml(inputContent)
    val expectedHtml: String =
        """<p>Hello <b>bold</b> and <a href="https://example.com">link</a></p><p>Next paragraph</p>"""
    assertEquals(expectedHtml, actualHtml)
}

@Test
fun `falls back to html when richtext renders no content`() {
    val inputContent: GraphQlContent =
        GraphQlContent(
            html = "<p>Fallback</p>",
            richtext = """{"document":[]}""",
        )
    val actualHtml: String? = RichTextHtmlConverter.toHtml(inputContent)
    val expectedHtml: String = "<p>Fallback</p>"
    assertEquals(expectedHtml, actualHtml)
}
```

- [x] **Step 2: Run the focused test and verify the regression is red**

Run:

```bash
JAVA_HOME=/nix/store/1kznfbw8jsmbx2b4sdn6s9ia4kqv80ip-openjdk-17.0.20.1+1 \
ANDROID_HOME=/var/home/joe/workspace/stealth/.android-sdk \
ANDROID_SDK_ROOT=/var/home/joe/workspace/stealth/.android-sdk \
./gradlew testDebugUnitTest --tests 'com.cosmos.unreddit.data.remote.api.reddit.graphql.mapper.RichTextHtmlConverterTest'
```

Expected: `BUILD FAILED`; `prefers formatted richtext when html is plain text` fails because actual output is `Hello bold and link\nNext paragraph` instead of the expected HTML. `falls back to html when richtext renders no content` passes.

- [x] **Step 3: Change representation selection in the converter**

Replace the current `toHtml` function at lines 14-26 with:

```kotlin
fun toHtml(content: GraphQlContent?): String? {
    if (content == null) return null
    val mediaById: Map<String, GraphQlRichTextMedia> =
        content.richtextMedia.associateBy { it.id.orEmpty() }
    val rendered: RichTextRenderResult? =
        content.richtext?.takeIf { it.isNotBlank() }?.let { render(it, mediaById) }
    val renderedHtml: String? = rendered?.html?.takeIf { it.isNotBlank() }
    // Reddit's html field may be plain text; richtext retains formatting and links.
    if (renderedHtml != null) return renderedHtml
    val html: String? = content.html?.takeIf { it.isNotBlank() }
    return html ?: content.markdown?.let { resolveMarkdownMedia(it, mediaById) }
}
```

Do not change the rendering helpers or `RichTextRenderResult`; this fix is limited to source selection.

- [x] **Step 4: Run converter and mapper tests and verify they are green**

Run:

```bash
JAVA_HOME=/nix/store/1kznfbw8jsmbx2b4sdn6s9ia4kqv80ip-openjdk-17.0.20.1+1 \
ANDROID_HOME=/var/home/joe/workspace/stealth/.android-sdk \
ANDROID_SDK_ROOT=/var/home/joe/workspace/stealth/.android-sdk \
./gradlew testDebugUnitTest \
  --tests 'com.cosmos.unreddit.data.remote.api.reddit.graphql.mapper.RichTextHtmlConverterTest' \
  --tests 'com.cosmos.unreddit.data.remote.api.reddit.graphql.GraphQlPostMapperTest' \
  --tests 'com.cosmos.unreddit.data.remote.api.reddit.graphql.GraphQlCommentMapperTest'
```

Expected: `BUILD SUCCESSFUL`; all three test classes pass, including media rendering, malformed-richtext fallback, empty-richtext fallback, post mapping, and comment mapping.

- [x] **Step 5: Run the full JVM unit suite**

Run:

```bash
JAVA_HOME=/nix/store/1kznfbw8jsmbx2b4sdn6s9ia4kqv80ip-openjdk-17.0.20.1+1 \
ANDROID_HOME=/var/home/joe/workspace/stealth/.android-sdk \
ANDROID_SDK_ROOT=/var/home/joe/workspace/stealth/.android-sdk \
./gradlew testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`; zero test failures.

- [x] **Step 6: Verify compilation and diff hygiene**

Run:

```bash
JAVA_HOME=/nix/store/1kznfbw8jsmbx2b4sdn6s9ia4kqv80ip-openjdk-17.0.20.1+1 \
ANDROID_HOME=/var/home/joe/workspace/stealth/.android-sdk \
ANDROID_SDK_ROOT=/var/home/joe/workspace/stealth/.android-sdk \
./gradlew assembleDebug
git diff --check
```

Expected: `BUILD SUCCESSFUL`; `git diff --check` produces no output and exits zero.

- [x] **Step 7: Report task ready**

Report the changed files, RED and GREEN command results, full-suite result, assembly result, and self-review findings. Leave changes unstaged and do not commit.

### Commit 1: fix(graphql): preserve post text formatting

- Includes: Task 1.
- Rationale: The regression test and source-selection fix form one independently verified bug fix.
- Verify: The three focused test classes, full `testDebugUnitTest`, `assembleDebug`, and `git diff --check` all pass.
- Stage: `.opencode/plans/graphql-post-formatting.md`, `app/src/main/java/com/cosmos/unreddit/data/remote/api/reddit/graphql/mapper/RichTextHtmlConverter.kt`, `app/src/test/java/com/cosmos/unreddit/data/remote/api/reddit/graphql/mapper/RichTextHtmlConverterTest.kt`.

## Sources

- Reddit GraphQL `PostsByIds` persisted operation, Reddit Android 2026.42.0 build 2642001, SHA-256 `29a8d4c6e135fffb95fc4cd303c57ad926c6c5ff582f4c4577c8a69b7f322d91`; live response captured 2026-11-26 at `https://gql-fed.reddit.com/`. No public version-matched schema documentation is available, so the captured wire shape is authoritative for this fix.
- Moshi 1.14.0 official API documentation: `JsonClass.generateAdapter` triggers annotation-processor-generated adapters — https://square.github.io/moshi/1.x/moshi/moshi/com.squareup.moshi/-json-class/generate-adapter.html.
- Project dependency versions: `buildSrc/src/main/kotlin/Dependencies.kt`.
