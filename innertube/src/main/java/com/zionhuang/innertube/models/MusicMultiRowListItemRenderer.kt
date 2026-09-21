package com.zionhuang.innertube.models

import kotlinx.serialization.Serializable

/** Podcast episode card returned by filtered YouTube Music home responses. */
@Serializable
data class MusicMultiRowListItemRenderer(
    val thumbnail: ThumbnailRenderer?,
    val onTap: NavigationEndpoint,
    val title: Runs,
    val secondTitle: Runs?,
)
