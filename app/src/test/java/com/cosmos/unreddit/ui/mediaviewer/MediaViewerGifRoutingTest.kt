package com.cosmos.unreddit.ui.mediaviewer

import com.cosmos.unreddit.data.model.GalleryMedia
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaViewerGifRoutingTest {
    @Test
    fun `routes a stale raw gif video url to the image viewer`() {
        val inputLink: String = "https://i.redd.it/5apc9mfquqyd1.gif"

        val actualType: GalleryMedia.Type = resolveVideoMediaType(inputLink)

        assertEquals(GalleryMedia.Type.IMAGE, actualType)
    }

    @Test
    fun `keeps mp4 bytes and gifv wrappers on the video viewer`() {
        val inputMp4GifLink: String =
            "https://cf.preview.redd.it/5apc9mfquqyd1.gif?width=640&format=mp4&s=abc"
        val inputGifvLink: String = "https://i.imgur.com/abc.gifv"
        val inputMp4Link: String = "https://i.imgur.com/abc.mp4"

        val actualMp4GifType: GalleryMedia.Type = resolveVideoMediaType(inputMp4GifLink)
        val actualGifvType: GalleryMedia.Type = resolveVideoMediaType(inputGifvLink)
        val actualMp4Type: GalleryMedia.Type = resolveVideoMediaType(inputMp4Link)

        assertEquals(GalleryMedia.Type.VIDEO, actualMp4GifType)
        assertEquals(GalleryMedia.Type.VIDEO, actualGifvType)
        assertEquals(GalleryMedia.Type.VIDEO, actualMp4Type)
    }

    @Test
    fun `keeps a blank video url on the video viewer`() {
        val actualType: GalleryMedia.Type = resolveVideoMediaType("")

        assertEquals(GalleryMedia.Type.VIDEO, actualType)
    }
}
