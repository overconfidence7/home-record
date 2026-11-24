package com.kurtz.homerecord.fieldlog

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/** The catalog's stand-in photos answer only the previews' fake paths, never a real media file. */
class PreviewPhotosTest {
    @Test fun fakePreviewPathsAreDrawn() {
        assertThat(PreviewPhotos.isFake(File("/none/a1.jpg"))).isTrue()
        assertThat(PreviewPhotos.isFake("/preview/receipt.jpg")).isTrue()
        assertThat(PreviewPhotos.isFake(File("/media/kitchen-north-rough.jpg"))).isTrue()
    }

    @Test fun realMediaPathsLoadAsUsual() {
        assertThat(PreviewPhotos.isFake(File("/data/user/0/com.kurtz.homerecord.fieldlog/files/media/3f2a.jpg"))).isFalse()
        assertThat(PreviewPhotos.isFake("https://example.test/a.jpg")).isFalse()
        assertThat(PreviewPhotos.isFake(null)).isFalse()
        assertThat(PreviewPhotos.image(File("/data/media/3f2a.jpg"))).isNull()
    }
}
