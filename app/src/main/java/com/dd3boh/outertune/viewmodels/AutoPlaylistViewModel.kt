package com.dd3boh.outertune.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dd3boh.outertune.constants.SongSortDescendingKey
import com.dd3boh.outertune.constants.SongSortType
import com.dd3boh.outertune.constants.SongSortTypeKey
import com.dd3boh.outertune.db.MusicDatabase
import com.dd3boh.outertune.extensions.toEnum
import com.dd3boh.outertune.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AutoPlaylistViewModel @Inject constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val playlistId = savedStateHandle.get<String>("playlistId")!!

    val thumbnail: StateFlow<Int> = MutableStateFlow(
        when (playlistId) {
            "liked" -> com.dd3boh.outertune.ui.icons.XenoPlayerIcons.Favorite
            "downloaded" -> com.dd3boh.outertune.ui.icons.XenoSystemIcons.CloudDownload
            else -> com.dd3boh.outertune.ui.icons.XenoIcons.AutoMirrored.QueueMusic
        }
    ).asStateFlow()

    val songs = context.dataStore.data
        .map {
            it[SongSortTypeKey].toEnum(SongSortType.CREATE_DATE) to (it[SongSortDescendingKey] ?: true)
        }
        .distinctUntilChanged()
        .flatMapLatest { (sortType, descending) ->
            when (playlistId) {
                "liked" -> database.likedSongs(sortType, descending)
                "downloaded" -> database.downloadSongs(sortType, descending)
                else -> MutableStateFlow(emptyList())
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}
