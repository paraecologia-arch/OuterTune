package com.zionhuang.innertube.pages

import com.zionhuang.innertube.models.MusicCarouselShelfRenderer
import com.zionhuang.innertube.models.MusicMultiRowListItemRenderer
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
    fun podcastCardBecomesPlayableSongWithItsRealEndpoint() {
        val podcastEndpoint = WatchEndpoint(
            videoId = "podcast-episode",
            params = "podcast-params",
        )
        val renderer = MusicMultiRowListItemRenderer(
            thumbnail = thumbnailRenderer(),
            onTap = NavigationEndpoint(watchEndpoint = podcastEndpoint),
            title = Runs(listOf(Run("Podcast episode", null))),
            secondTitle = Runs(listOf(Run("Podcast show", null))),
        )

        val carousel = MusicCarouselShelfRenderer(
            header = MusicCarouselShelfRenderer.Header(
                MusicCarouselShelfRenderer.Header.MusicCarouselShelfBasicHeaderRenderer(
                    strapline = null,
                    title = Runs(listOf(Run("Podcasts", null))),
                    thumbnail = null,
                    moreContentButton = null,
                )
            ),
            contents = listOf(
                MusicCarouselShelfRenderer.Content(
                    musicTwoRowItemRenderer = null,
                    musicMultiRowListItemRenderer = renderer,
                    musicResponsiveListItemRenderer = null,
                    musicNavigationButtonRenderer = null,
                )
            ),
            itemSize = "MUSIC_CAROUSEL_SHELF_ITEM_SIZE_MEDIUM",
            numItemsPerColumn = null,
        )

        val section = checkNotNull(HomePage.Section.fromMusicCarouselShelfRenderer(carousel))
        val item = section.items.single() as SongItem

        assertEquals("Podcasts", section.title)
        assertEquals("podcast-episode", item.id)
        assertEquals("Podcast episode", item.title)
        assertEquals("Podcast show", item.artists.single().name)
        assertEquals(podcastEndpoint, item.endpoint)
    }

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
            thumbnailRenderer = thumbnailRenderer(),
            navigationEndpoint = NavigationEndpoint(watchPlaylistEndpoint = stationEndpoint),
            thumbnailOverlay = null,
        )

        val item = HomePage.Section.fromMusicTwoRowItemRenderer(renderer) as SongItem

        assertEquals("station-video", item.id)
        assertEquals(stationEndpoint, item.endpoint)
    }

    private fun thumbnailRenderer() = ThumbnailRenderer(
        musicThumbnailRenderer = ThumbnailRenderer.MusicThumbnailRenderer(
            thumbnail = Thumbnails(
                listOf(Thumbnail("https://example.test/thumbnail.jpg", 100, 100))
            ),
            thumbnailCrop = null,
            thumbnailScale = null,
        ),
        musicAnimatedThumbnailRenderer = null,
        croppedSquareThumbnailRenderer = null,
    )
}
