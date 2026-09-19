package com.dd3boh.outertune.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dd3boh.outertune.constants.PlaylistFilter
import com.dd3boh.outertune.constants.PlaylistSortType
import com.dd3boh.outertune.db.MusicDatabase
import com.dd3boh.outertune.db.entities.Album
import com.dd3boh.outertune.db.entities.LocalItem
import com.dd3boh.outertune.db.entities.Song
import com.dd3boh.outertune.models.SimilarRecommendation
import com.dd3boh.outertune.utils.SyncUtils
import com.dd3boh.outertune.utils.reportException
import com.dd3boh.outertune.utils.syncCoroutine
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.PlaylistItem
import com.zionhuang.innertube.models.WatchEndpoint
import com.zionhuang.innertube.models.YTItem
import com.zionhuang.innertube.pages.ExplorePage
import com.zionhuang.innertube.pages.HomePage
import com.zionhuang.innertube.utils.completed
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext context: Context,
    val database: MusicDatabase,
    val syncUtils: SyncUtils
) : ViewModel() {
    val isRefreshing = MutableStateFlow(false)
    val isLoading = MutableStateFlow(false)

    val quickPicks = MutableStateFlow<List<Song>?>(null)
    val forgottenFavorites = MutableStateFlow<List<Song>?>(null)
    val keepListening = MutableStateFlow<List<LocalItem>?>(null)
    val similarRecommendations = MutableStateFlow<List<SimilarRecommendation>?>(null)
    val accountPlaylists = MutableStateFlow<List<PlaylistItem>?>(null)
    val homePage = MutableStateFlow<HomePage?>(null)
    val selectedChip = MutableStateFlow<HomePage.Chip?>(null)
    val chipUiState = MutableStateFlow(HomeChipUiState())
    private val previousHomePage = MutableStateFlow<HomePage?>(null)
    private var chipRequestJob: Job? = null
    private val chipRequestGuard = LatestRequestGuard()
    val explorePage = MutableStateFlow<ExplorePage?>(null)
    val playlists = database.playlists(PlaylistFilter.LIBRARY, PlaylistSortType.NAME, true)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)
    val recentActivity = database.recentActivity()
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val allLocalItems = MutableStateFlow<List<LocalItem>>(emptyList())
    val allYtItems = MutableStateFlow<List<YTItem>>(emptyList())

    private suspend fun load() {
        isLoading.value = true

        quickPicks.value = database.quickPicks()
            .first().shuffled().take(20)

        forgottenFavorites.value = database.forgottenFavorites()
            .first().shuffled().take(20)

        val fromTimeStamp = System.currentTimeMillis() - 86400000 * 7 * 2
        val keepListeningSongs = database.mostPlayedSongs(fromTimeStamp, limit = 15, offset = 5)
            .first().shuffled().take(10)
        val keepListeningAlbums = database.mostPlayedAlbums(fromTimeStamp, limit = 8, offset = 2)
            .first().filter { it.album.thumbnailUrl != null }.shuffled().take(5)
        val keepListeningArtists = database.mostPlayedArtists(0, 1)
            .first().filter { it.artist.isYouTubeArtist && it.artist.thumbnailUrl != null }.shuffled().take(5)
        keepListening.value = (keepListeningSongs + keepListeningAlbums + keepListeningArtists).shuffled()

        allLocalItems.value =
            (quickPicks.value.orEmpty() + forgottenFavorites.value.orEmpty() + keepListening.value.orEmpty())
                .filter { it is Song || it is Album }

        if (YouTube.cookie != null) { // if logged in
            // InnerTune way is YouTube.likedPlaylists().onSuccess { ... }
            // OuterTune uses YouTube.library("FEmusic_liked_playlists").completedL().onSuccess { ... }
            YouTube.library("FEmusic_liked_playlists").completed().onSuccess {
                accountPlaylists.value = it.items.filterIsInstance<PlaylistItem>()
            }.onFailure {
                reportException(it)
            }
        }

        // Similar to artists
        val artistRecommendations =
            database.mostPlayedArtists(0, 1, limit = 10).first()
                .filter { it.artist.isYouTubeArtist }
                .shuffled().take(3)
                .mapNotNull {
                    val items = mutableListOf<YTItem>()
                    YouTube.artist(it.id).onSuccess { page ->
                        items += page.sections.getOrNull(page.sections.size - 2)?.items.orEmpty()
                        items += page.sections.lastOrNull()?.items.orEmpty()
                    }
                    SimilarRecommendation(
                        title = it,
                        items = items
                            .shuffled()
                            .ifEmpty { return@mapNotNull null }
                    )
                }
        // Similar to songs
        val songRecommendations =
            database.mostPlayedSongs(fromTimeStamp, limit = 10).first()
                .filter { it.album != null }
                .shuffled().take(2)
                .mapNotNull { song ->
                    val endpoint = YouTube.next(WatchEndpoint(videoId = song.id)).getOrNull()?.relatedEndpoint
                        ?: return@mapNotNull null
                    val page = YouTube.related(endpoint).getOrNull() ?: return@mapNotNull null
                    SimilarRecommendation(
                        title = song,
                        items = (page.songs.shuffled().take(8) +
                                page.albums.shuffled().take(4) +
                                page.artists.shuffled().take(4) +
                                page.playlists.shuffled().take(4))
                            .shuffled()
                            .ifEmpty { return@mapNotNull null }
                    )
                }
        similarRecommendations.value = (artistRecommendations + songRecommendations).shuffled()

        YouTube.home().onSuccess { page ->
            homePage.value = page
        }.onFailure {
            reportException(it)
        }

        YouTube.explore().onSuccess { page ->
            explorePage.value = page
        }.onFailure {
            reportException(it)
        }

        syncUtils.syncRecentActivity()

        allYtItems.value = similarRecommendations.value?.flatMap { it.items }.orEmpty() +
                homePage.value?.sections?.flatMap { it.items }.orEmpty()

        isLoading.value = false
    }

    private val _isLoadingMore = MutableStateFlow(false)
    fun loadMoreYouTubeItems(continuation: String?) {
        if (continuation == null || _isLoadingMore.value) return

        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingMore.value = true
            val nextSections = YouTube.home(continuation).getOrNull() ?: run {
                _isLoadingMore.value = false
                return@launch
            }
            homePage.value = nextSections.copy(
                chips = homePage.value?.chips,
                sections = homePage.value?.sections.orEmpty() + nextSections.sections
            )
            _isLoadingMore.value = false
        }
    }

    fun toggleChip(chip: HomePage.Chip?) {
        if (chip == null || chip == selectedChip.value && previousHomePage.value != null) {
            chipRequestJob?.cancel()
            chipRequestGuard.invalidate()
            homePage.value = previousHomePage.value
            previousHomePage.value = null
            selectedChip.value = null
            chipUiState.value = HomeChipUiState()
            return
        }

        if (selectedChip.value == null) {
            // store the actual homepage for deselecting chips
            previousHomePage.value = homePage.value
        }
        selectedChip.value = chip
        chipUiState.value = HomeChipUiState(selectedChip = chip, isLoading = true)
        homePage.value = homePage.value?.copy(sections = emptyList(), continuation = null)
        chipRequestJob?.cancel()
        val requestId = chipRequestGuard.next()
        chipRequestJob = viewModelScope.launch(Dispatchers.IO) {
            YouTube.home(params = chip.endpoint.params)
                .onSuccess { nextSections ->
                    if (!chipRequestGuard.isLatest(requestId)) return@onSuccess
                    homePage.value = nextSections.copy(
                        chips = homePage.value?.chips ?: previousHomePage.value?.chips,
                        sections = nextSections.sections,
                        continuation = nextSections.continuation
                    )
                    chipUiState.value = HomeChipUiState(
                        selectedChip = chip,
                        isEmpty = nextSections.sections.isEmpty(),
                    )
                }
                .onFailure { throwable ->
                    if (!chipRequestGuard.isLatest(requestId)) return@onFailure
                    reportException(throwable)
                    chipUiState.value = HomeChipUiState(
                        selectedChip = chip,
                        hasError = true,
                    )
                }
        }
    }

    fun refresh() {
        if (isRefreshing.value) return
        viewModelScope.launch(syncCoroutine) {
            isRefreshing.value = true
            load()
            isRefreshing.value = false
        }
    }

    init {
        refresh()
        viewModelScope.launch(syncCoroutine) {
            syncUtils.tryAutoSync()
        }
    }
}

data class HomeChipUiState(
    val selectedChip: HomePage.Chip? = null,
    val isLoading: Boolean = false,
    val hasError: Boolean = false,
    val isEmpty: Boolean = false,
)

/** Small generation guard that prevents an older response from replacing a newer selection. */
internal class LatestRequestGuard {
    private var generation = 0L

    @Synchronized
    fun next(): Long = ++generation

    @Synchronized
    fun invalidate() {
        generation++
    }

    @Synchronized
    fun isLatest(candidate: Long): Boolean = candidate == generation
}
