package com.dd3boh.outertune.db.daos

import androidx.room.Room
import com.dd3boh.outertune.db.DatabaseDao
import com.dd3boh.outertune.db.InternalDatabase
import com.dd3boh.outertune.db.entities.Playlist
import com.dd3boh.outertune.db.entities.PlaylistEntity
import com.dd3boh.outertune.db.entities.PlaylistSongMap
import com.dd3boh.outertune.models.MediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith

@RunWith(RobolectricTestRunner::class)
class PlaylistsDaoTest {
    private lateinit var database: InternalDatabase
    private lateinit var dao: DatabaseDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            InternalDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.dao
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun appendsAfterTheHighestExistingPositionWithoutReorderingExistingSongs() = runBlocking {
        val playlist = playlist()
        dao.insert(playlist.playlist)
        insertSongs("one", "two", "three")
        dao.insert(PlaylistSongMap(playlistId = playlist.id, songId = "one", position = 1))
        dao.insert(PlaylistSongMap(playlistId = playlist.id, songId = "two", position = 7))

        dao.addSongToPlaylist(playlist, listOf("three"))

        val maps = dao.playlistSongs(playlist.id).first().map { it.map }
        assertEquals(listOf("one", "two", "three"), maps.map { it.songId })
        assertEquals(listOf(1, 7, 8), maps.map { it.position })
    }

    @Test
    fun addAnywayKeepsAnIntentionalDuplicate() = runBlocking {
        val playlist = playlist()
        dao.insert(playlist.playlist)
        insertSongs("one")
        dao.insert(PlaylistSongMap(playlistId = playlist.id, songId = "one", position = 0))

        dao.addSongToPlaylist(playlist, listOf("one"))

        val maps = dao.playlistSongs(playlist.id).first().map { it.map }
        assertEquals(listOf("one", "one"), maps.map { it.songId })
        assertEquals(listOf(0, 1), maps.map { it.position })
    }

    @Test
    fun existingSongsRemainDetectableForTheNormalAddFlow() {
        val playlist = playlist()
        dao.insert(playlist.playlist)
        insertSongs("one", "two")
        dao.insert(PlaylistSongMap(playlistId = playlist.id, songId = "one", position = 0))

        assertEquals(listOf("one"), dao.playlistDuplicates(playlist.id, listOf("one", "two")))
    }

    @Test
    fun normalMultiSongInsertKeepsInputOrderAndUsesTheNextFreePosition() = runBlocking {
        val playlist = playlist()
        dao.insert(playlist.playlist)
        insertSongs("one", "two", "three")
        dao.insert(PlaylistSongMap(playlistId = playlist.id, songId = "one", position = 4))

        dao.addSongToPlaylist(playlist, listOf("two", "three"))

        val maps = dao.playlistSongs(playlist.id).first().map { it.map }
        assertEquals(listOf("one", "two", "three"), maps.map { it.songId })
        assertEquals(listOf(4, 5, 6), maps.map { it.position })
    }

    @Test
    fun positionsAreScopedToTheTargetPlaylist() = runBlocking {
        val first = playlist(id = "first")
        val second = playlist(id = "second")
        dao.insert(first.playlist)
        dao.insert(second.playlist)
        insertSongs("one", "two")
        dao.insert(PlaylistSongMap(playlistId = first.id, songId = "one", position = 7))

        dao.addSongToPlaylist(first, listOf("two"))
        dao.addSongToPlaylist(second, listOf("one"))

        val firstMaps = dao.playlistSongs(first.id).first().map { it.map }
        val secondMaps = dao.playlistSongs(second.id).first().map { it.map }
        assertEquals(listOf(7, 8), firstMaps.map { it.position })
        assertEquals(listOf(0), secondMaps.map { it.position })
    }

    @Test
    fun concurrentAppendsReceiveDistinctPositions() = runBlocking {
        val playlist = playlist()
        dao.insert(playlist.playlist)
        insertSongs("one", "two")

        listOf("one", "two").map { songId ->
            async(Dispatchers.IO) { dao.addSongToPlaylist(playlist, listOf(songId)) }
        }.awaitAll()

        val maps = dao.playlistSongs(playlist.id).first().map { it.map }
        assertEquals(setOf("one", "two"), maps.map { it.songId }.toSet())
        assertEquals(listOf(0, 1), maps.map { it.position }.sorted())
    }

    private fun playlist(id: String = "playlist") = Playlist(
        playlist = PlaylistEntity(id = id, name = "Playlist", isLocal = true),
        songCount = 0,
        downloadCount = 0,
        songThumbnails = emptyList(),
    )

    private fun insertSongs(vararg ids: String) {
        ids.forEach { id ->
            dao.insert(
                MediaMetadata(
                    id = id,
                    title = id,
                    artists = emptyList(),
                    duration = 60,
                    genre = null,
                )
            )
        }
    }
}
