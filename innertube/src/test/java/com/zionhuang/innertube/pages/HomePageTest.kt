package com.zionhuang.innertube.pages

import com.zionhuang.innertube.models.MusicTwoRowItemRenderer
import com.zionhuang.innertube.models.NavigationEndpoint
import com.zionhuang.innertube.models.Run
import com.zionhuang.innertube.models.Runs
import com.zionhuang.innertube.models.SongItem
import com.zionhuang.innertube.models.Thumbnail
import com.zionhuang.innertube.models.ThumbnailRenderer
import com.zionhuang.innertube.models.Thumbnails
import com.zionhuang.innertube.models.WatchEndpoint
import org.junit.Assert.assertEquals
import org.junit.Test

class HomePageTest {
    @Test
    fun stationCardPreservesItsRealWatchPlaylistEndpoint() {
        val stationEndpoint = WatchEndpoint(
            videoId = "station-video",
            playlistId = "RDAMVMstation",
            params = "station-params",
            index = 3,
        )
        val renderer = MusicTwoRowItemRenderer(
            title = Runs(listOf(Run("Station", null))),
            subtitle = null,
            subtitleBadges = null,
            menu = null,
            thumbnailRenderer = ThumbnailRenderer(
                musicThumbnailRenderer = ThumbnailRenderer.MusicThumbnailRenderer(
                    thumbnail = Thumbnails(
                        listOf(Thumbnail("https://example.test/station.jpg", 100, 100))
                    ),
                    thumbnailCrop = null,
                    thumbnailScale = null,
                ),
                musicAnimatedThumbnailRenderer = null,
                croppedSquareThumbnailRenderer = null,
            ),
            navigationEndpoint = NavigationEndpoint(watchPlaylistEndpoint = stationEndpoint),
            thumbnailOverlay = null,
        )

        val item = HomePage.Section.fromMusicTwoRowItemRenderer(renderer) as SongItem

        assertEquals("station-video", item.id)
        assertEquals(stationEndpoint, item.endpoint)
    }
}
