package com.dd3boh.outertune.playback

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.media3.database.DatabaseProvider
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import com.dd3boh.outertune.constants.AudioQuality
import com.dd3boh.outertune.constants.AudioQualityKey
import com.dd3boh.outertune.constants.DownloadExtraPathKey
import com.dd3boh.outertune.constants.DownloadPathKey
import com.dd3boh.outertune.db.MusicDatabase
import com.dd3boh.outertune.db.entities.FormatEntity
import com.dd3boh.outertune.db.entities.PlaylistSong
import com.dd3boh.outertune.db.entities.Song
import com.dd3boh.outertune.db.entities.SongEntity
import com.dd3boh.outertune.di.AppModule.PlayerCache
import com.dd3boh.outertune.di.DownloadCache
import com.dd3boh.outertune.models.MediaMetadata
import com.dd3boh.outertune.playback.DownloadUtil.Companion.STATE_DOWNLOADING
import com.dd3boh.outertune.playback.DownloadUtil.Companion.STATE_INVALID
import com.dd3boh.outertune.playback.downloadManager.DownloadDirectoryManagerOt
import com.dd3boh.outertune.playback.downloadManager.DownloadManagerOt
import com.dd3boh.outertune.playback.stream.DownloadRangeDataSource
import com.dd3boh.outertune.playback.stream.InnerTubeXResolver
import com.dd3boh.outertune.playback.stream.ResolvedStream
import com.dd3boh.outertune.playback.stream.StreamResolver
import com.dd3boh.outertune.playback.stream.parseContentRangeTotalBytes
import com.dd3boh.outertune.utils.dataStore
import com.dd3boh.outertune.utils.dlCoroutine
import com.dd3boh.outertune.utils.enumPreference
import com.dd3boh.outertune.utils.get
import com.dd3boh.outertune.utils.reportException
import com.dd3boh.outertune.utils.scanners.InvalidAudioFileException
import com.dd3boh.outertune.utils.scanners.fileFromUri
import com.dd3boh.outertune.utils.scanners.uriListFromString
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.SongItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadUtil @Inject constructor(
    @ApplicationContext private val context: Context,
    val database: MusicDatabase,
    val databaseProvider: DatabaseProvider,
    @DownloadCache val downloadCache: SimpleCache,
    @PlayerCache val playerCache: SimpleCache,
) {
    val TAG = DownloadUtil::class.simpleName.toString()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val downloadStatusWriter = CoroutineScope(Dispatchers.IO.limitedParallelism(1))

    private val connectivityManager = context.getSystemService<ConnectivityManager>()!!
    private val audioQuality by enumPreference(context, AudioQualityKey, AudioQuality.AUTO)
    private val streamResolver: StreamResolver = InnerTubeXResolver()
    private val resolvedStreamCache = ConcurrentHashMap<String, ResolvedStream>()
    private val downloadHttpClient = OkHttpClient.Builder()
        .proxy(YouTube.proxy)
        .build()
    private val dataSourceFactory = DownloadRangeDataSource.Factory(
        CacheDataSource.Factory()
            .setCache(playerCache)
            .setUpstreamDataSourceFactory(OkHttpDataSource.Factory(downloadHttpClient)),
        ::resolveDownloadStream,
    )

    private fun discoverContentLength(resolvedStream: ResolvedStream): Long? {
        val request = Request.Builder()
            .url(resolvedStream.url)
            .apply {
                resolvedStream.headers.forEach { (name, value) ->
                    header(name, value)
                }
            }
            .header("Range", "bytes=0-0")
            .build()

        return downloadHttpClient.newCall(request).execute().use { response ->
            Log.d(TAG, "DOWNLOAD HTTP status=${response.code}")
            when (response.code) {
                200 -> response.body?.contentLength()?.takeIf { it >= 0 }
                206 -> response.header("Content-Range")?.let(::parseContentRangeTotalBytes)
                else -> null
            }
        }
    }

    private fun resolveDownloadStream(mediaId: String): ResolvedStream {
        val cachedStream = resolvedStreamCache[mediaId]
        if (cachedStream != null && cachedStream.expiresAtEpochMs > System.currentTimeMillis()) {
            Log.d(
                TAG,
                "DOWNLOAD resolve mediaId=$mediaId cacheHit=true " +
                    "client=${cachedStream.clientName} rangePolicy=${cachedStream.rangePolicy}",
            )
            return cachedStream
        }
        if (cachedStream != null) {
            resolvedStreamCache.remove(mediaId)
        }

        val resolvedStream = runBlocking(Dispatchers.IO) {
            streamResolver.resolve(
                videoId = mediaId,
                audioQuality = audioQuality,
                isActiveNetworkMetered = connectivityManager.isActiveNetworkMetered,
            )
        }.getOrThrow()
        val format = resolvedStream.format
        val contentLength = format.contentLength
            ?: runBlocking(Dispatchers.IO) {
                runCatching { discoverContentLength(resolvedStream) }.getOrNull()
            }

        if (contentLength != null) {
            val streamWithLength = if (format.contentLength == null) {
                resolvedStream.copy(format = format.copy(contentLength = contentLength))
            } else {
                resolvedStream
            }
            database.query {
                upsert(
                    FormatEntity(
                        id = mediaId,
                        itag = format.itag,
                        mimeType = format.mimeType,
                        codecs = format.codecs,
                        bitrate = format.bitrate,
                        sampleRate = format.sampleRate,
                        contentLength = contentLength,
                        loudnessDb = format.loudnessDb,
                        playbackTrackingUrl = streamWithLength.playbackTrackingUrl,
                    )
                )
            }
            resolvedStreamCache[mediaId] = streamWithLength
        } else {
            resolvedStreamCache[mediaId] = resolvedStream
        }

        Log.d(
            TAG,
            "DOWNLOAD resolve mediaId=$mediaId cacheHit=false " +
                "client=${resolvedStream.clientName} rangePolicy=${resolvedStream.rangePolicy}",
        )
        return resolvedStreamCache.getValue(mediaId)
    }
    val downloadNotificationHelper = DownloadNotificationHelper(context, ExoDownloadService.CHANNEL_ID)
    val downloadManager: DownloadManager =
        DownloadManager(context, databaseProvider, downloadCache, dataSourceFactory, Executor(Runnable::run)).apply {
            maxParallelDownloads = 3
            addListener(
                ExoDownloadService.TerminalStateNotificationHelper(
                    context = context,
                    notificationHelper = downloadNotificationHelper,
                    nextNotificationId = ExoDownloadService.NOTIFICATION_ID + 1
                )
            )
        }
    val downloads = MutableStateFlow<Map<String, LocalDateTime>>(emptyMap())
    private val pendingDownloadRequests =
        Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    var localMgr = DownloadDirectoryManagerOt(
        context,
        context.dataStore.get(DownloadPathKey, "").toUri(),
        uriListFromString(context.dataStore.get(DownloadExtraPathKey, ""))
    )
    val downloadMgr = DownloadManagerOt(localMgr)
    var isProcessingDownloads = MutableStateFlow(false)

    fun getDownload(songId: String): Flow<LocalDateTime?> = downloads.map { it[songId] }

    fun download(
        songs: List<MediaMetadata>,
        source: DownloadSource = DownloadSource.OTHER,
    ) {
        val appStates = songs.map { downloads.value[it.id] }
        Log.i(
            TAG,
            "DOWNLOAD_BATCH total=${songs.size} " +
                "completed=${appStates.count { it.isCompletedDownload() }} " +
                "active=${appStates.count { it == STATE_DOWNLOADING }} " +
                "enqueue=${appStates.count { it == null || it == STATE_INVALID }} " +
                "failed=${appStates.count { it == STATE_INVALID }}"
        )
        database.transaction {
            songs.forEach { song ->
                insert(song)
            }
        }
        songs.forEach { song ->
            requestDownload(song.id, song.title, source)
        }
    }

    fun download(
        song: MediaMetadata,
        source: DownloadSource = DownloadSource.OTHER,
    ) {
        database.transaction {
            insert(song)
        }
        requestDownload(song.id, song.title, source)
    }

    fun download(
        song: SongEntity,
        source: DownloadSource = DownloadSource.OTHER,
    ) {
        requestDownload(song.id, song.title, source)
    }

    private fun requestDownload(
        id: String,
        title: String,
        source: DownloadSource,
    ) {
        val appState = downloads.value[id]
        Log.i(
            TAG,
            "DOWNLOAD_ENTRY source=$source mediaId=$id " +
                "state=${appDownloadStateName(appState)}"
        )

        if (appState.isCompletedDownload()) {
            Log.i(TAG, "DOWNLOAD_DECISION source=$source mediaId=$id decision=SKIP_COMPLETED")
            return
        }

        if (!pendingDownloadRequests.add(id)) {
            Log.i(TAG, "DOWNLOAD_DECISION source=$source mediaId=$id decision=SKIP_PENDING")
            return
        }

        if (appState != STATE_DOWNLOADING) {
            downloads.update { map ->
                map.toMutableMap().apply {
                    set(id, STATE_DOWNLOADING)
                }
            }
        }

        CoroutineScope(dlCoroutine).launch {
            try {
            val dbPresent = runCatching {
                database.song(id).first() != null
            }.getOrDefault(false)
            Log.i(TAG, "DOWNLOAD_PREPARE source=$source mediaId=$id dbPresent=$dbPresent")

            val media3Download = runCatching {
                downloadManager.downloadIndex.getDownload(id)
            }.getOrNull()
            val media3State = media3Download?.state
            Log.i(
                TAG,
                "DOWNLOAD_STATE mediaId=$id media3State=${media3StateName(media3State)} " +
                    "appState=${appDownloadStateName(appState)}"
            )

            val decision = downloadDecision(appState, media3State)
            Log.i(TAG, "DOWNLOAD_DECISION source=$source mediaId=$id decision=$decision")

            when (decision) {
                DownloadDecision.ENQUEUE, DownloadDecision.RETRY, DownloadDecision.RESET_STALE -> {
                    val downloadRequest = DownloadRequest.Builder(id, id.toUri())
                        .setCustomCacheKey(id)
                        .setData(title.toByteArray())
                        .build()

                    try {
                        DownloadService.sendAddDownload(
                            context,
                            ExoDownloadService::class.java,
                            downloadRequest,
                            false
                        )
                        downloads.update { map ->
                            map.toMutableMap().apply {
                                set(id, STATE_DOWNLOADING)
                            }
                        }
                        downloadStatusWriter.launch {
                            database.updateDownloadStatus(id, STATE_DOWNLOADING)
                        }
                    } catch (e: Exception) {
                        downloads.update { map ->
                            map.toMutableMap().apply {
                                remove(id)
                            }
                        }
                        downloadStatusWriter.launch {
                            database.updateDownloadStatus(id, null)
                        }
                        Log.w(TAG, "DOWNLOAD_DECISION source=$source mediaId=$id decision=ERROR", e)
                    }
                }

                DownloadDecision.SKIP_ACTIVE -> {
                    downloads.update { map ->
                        map.toMutableMap().apply {
                            set(id, STATE_DOWNLOADING)
                        }
                    }
                    downloadStatusWriter.launch {
                        database.updateDownloadStatus(id, STATE_DOWNLOADING)
                    }
                }

                DownloadDecision.SKIP_COMPLETED -> {
                    if (media3Download?.state == Download.STATE_COMPLETED) {
                        val completedAt = Instant.ofEpochMilli(media3Download.updateTimeMs)
                            .atZone(ZoneOffset.UTC)
                            .toLocalDateTime()
                        downloads.update { map ->
                            map.toMutableMap().apply {
                                set(id, completedAt)
                            }
                        }
                        downloadStatusWriter.launch {
                            database.updateDownloadStatus(id, completedAt)
                        }
                    }
                }

                DownloadDecision.SKIP_REMOVING -> {
                    downloads.update { map ->
                        map.toMutableMap().apply {
                            remove(id)
                        }
                    }
                    downloadStatusWriter.launch {
                        database.updateDownloadStatus(id, null)
                    }
                }
            }
            } catch (e: Exception) {
                downloads.update { map ->
                    map.toMutableMap().apply {
                        remove(id)
                    }
                }
                downloadStatusWriter.launch {
                    database.updateDownloadStatus(id, null)
                }
                Log.w(TAG, "DOWNLOAD_REQUEST_FAILED source=$source mediaId=$id", e)
            } finally {
                pendingDownloadRequests.remove(id)
            }
        }
    }

    fun resumeDownloadsOnStart() {
        DownloadService.sendResumeDownloads(
            context,
            ExoDownloadService::class.java,
            false
        )
    }


// Deletes from custom dl

    fun delete(song: PlaylistSong) = deleteSong(song.song.id)

    fun delete(song: SongItem) = deleteSong(song.id)

    fun delete(song: Song) = deleteSong(song.song.id)

    fun delete(song: SongEntity) = deleteSong(song.id)

    fun delete(song: MediaMetadata) = deleteSong(song.id)

    private fun deleteSong(id: String): Boolean {
        val deleted = localMgr.deleteFile(id)
        if (!deleted) return false
        downloads.update { map ->
            map.toMutableMap().apply {
                remove(id)
            }
        }

        runBlocking {
            database.song(id).first()?.song?.copy(localPath = null)
            database.updateDownloadStatus(id, null)
        }
        return true
    }

    /**
     * Retrieve song from cache, and delete it from cache afterwards
     */
    fun getFromCache(cache: SimpleCache, mediaId: String): ByteArray? {
        val spans: Set<CacheSpan> = cache.getCachedSpans(mediaId)
        if (spans.isEmpty()) return null

        val output = ByteArrayOutputStream()
        try {
            for (span in spans) {
                val file: File? = span.file
                FileInputStream(file).use { fis ->
                    fis.copyTo(output)
                }
            }
            return output.toByteArray()
        } catch (e: IOException) {
            reportException(e)
        } finally {
            output.close()
        }
        return null
    }

    /**
     * Migrated existing downloads from the download cache to the new system in external storage
     */
    suspend fun migrateDownloads() {
        if (isProcessingDownloads.value) return
        isProcessingDownloads.value = true

        var runs = 0
        try {
            // "skeleton" of old download manager to access old download data
            val dataSourceFactory = ResolvingDataSource.Factory(
                CacheDataSource.Factory()
                    .setCache(playerCache)
                    .setUpstreamDataSourceFactory(
                        OkHttpDataSource.Factory(
                            OkHttpClient.Builder()
                                .proxy(YouTube.proxy)
                                .build()
                        )
                    )
            ) { dataSpec ->
                return@Factory dataSpec
            }

            val downloadManager: DownloadManager = DownloadManager(
                context,
                databaseProvider,
                downloadCache,
                dataSourceFactory,
                Executor(Runnable::run)
            ).apply {
                maxParallelDownloads = 3
            }

            // actual migration code
            val downloadedSongs = mutableMapOf<String, Download>()
            val cursor = downloadManager.downloadIndex.getDownloads()
            while (cursor.moveToNext()) {
                downloadedSongs[cursor.download.request.id] = cursor.download
            }

            // copy all completed downloads
            val toMigrate = downloadedSongs.filter { it.value.state == Download.STATE_COMPLETED }
            toMigrate.forEach { s ->
                if (runs++ % 10 == 0) {
                    Log.d(TAG, "Migrating download: $runs/${toMigrate.size}")
                    if (runs % 20 == 0) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "$runs/${toMigrate.size}", LENGTH_SHORT).show()
                        }
                    }
                }
                val songFromCache = getFromCache(downloadCache, s.key)
                if (songFromCache != null) {
                    downloadCache.removeResource(s.key)
                    downloadMgr.enqueue(
                        mediaId = s.key,
                        data = songFromCache,
                        displayName = runBlocking { database.song(s.key).first()?.title ?: "" })
                }
            }
            scanDownloads()
        } catch (e: Exception) {
            reportException(e)
        } finally {
            isProcessingDownloads.value = false
        }
    }


    fun cd() {
        localMgr.doInit(
            context,
            context.dataStore.get(DownloadPathKey, "").toUri(),
            uriListFromString(context.dataStore.get(DownloadExtraPathKey, ""))
        )
    }

    /**
     * Rescan download directory and updates songs
     */
    suspend fun rescanDownloads() {
        Log.i(TAG, "+rescanDownloads()")
        isProcessingDownloads.value = true
        val dbDownloads = database.downloadedOrQueuedSongs().first()
        val result = mutableMapOf<String, LocalDateTime>()

        // get missing files not in custom downloads or in internal downloads, remove them
        val missingFiles =
            localMgr.getMissingFiles(dbDownloads.filterNot { it.song.dateDownload == null }).toMutableList()
        Log.d(TAG, "Found ${missingFiles.size}/${dbDownloads.size} songs not in custom download directories")
        val cursor = downloadManager.downloadIndex.getDownloads()
        while (cursor.moveToNext()) {
            missingFiles.removeIf { it.id == cursor.download.request.id }
        }
        Log.d(
            TAG,
            "Found ${missingFiles.size}/${dbDownloads.size} song not in custom download directories + internal cache. Removing these files now"
        )

        database.transaction {
            missingFiles.forEach {
                Log.v(TAG, "Shedding: [${it.id}] ${it.song.title}")
                removeDownloadSong(it.song.id)
            }
        }

        // new files
        val availableDownloads = dbDownloads.minus(missingFiles)
        availableDownloads.forEach { s ->
            val dateDownload = s.song.dateDownload
            if (dateDownload != null && dateDownload >= STATE_DOWNLOADING) {
                result[s.song.id] = dateDownload
            }
        }

        downloads.value = result
        isProcessingDownloads.value = false
        Log.i(TAG, "-rescanDownloads()")
    }


    /**
     * Scan and import downloaded songs from main and extra directories.
     *
     * This is intended for re-importing existing songs (ex. songs get moved, after restoring app backup), thus all
     * songs will already need to exist in the database.
     */
    suspend fun scanDownloads() {
        Log.i(TAG, "+scanDownloads()")
        if (isProcessingDownloads.value) {
            Log.i(TAG, "-scanDownloads()")
            return
        }
        isProcessingDownloads.value = true

//            val scanner = LocalMediaScanner.getScanner(context, ScannerImpl.TAGLIB, SCANNER_OWNER_DL)
        database.removeAllDownloadedSongs()
        val timeNow = LocalDateTime.now()

        // add custom downloads
        val availableFiles = localMgr.getAvailableFiles(false)
        database.transaction {
            availableFiles.forEach { f ->
                try {
                    val file = fileFromUri(context, f.value)
                    if (file == null) throw (InvalidAudioFileException("Hello darkness my old friend"))
                    // TODO: validate files in download folder
//                        val format: FormatEntity? = scanner.advancedScan(f.value).format
//                        if (format != null) {
//                            database.upsert(format)
//                        }
                    registerDownloadSong(f.key, timeNow, file.absolutePath)

                } catch (e: InvalidAudioFileException) {
                    reportException(e)
                }
            }
        }
//            LocalMediaScanner.destroyScanner(SCANNER_OWNER_DL)
        Log.d(TAG, "Registered ${availableFiles.size} files from custom downloads")

        // add internal downloads
        val cursor = downloadManager.downloadIndex.getDownloads()
        var count = 0
        database.transaction {
            while (cursor.moveToNext()) {
                updateDownloadStatus(cursor.download.request.id, stateToLocalDateTime(cursor.download))
                count ++
            }
        }
        Log.d(TAG, "Registered $count files from internal downloads")
        isProcessingDownloads.value = false
        Log.d(TAG, "Database registration complete, triggering map registry rebuild")
        rescanDownloads()
        Log.i(TAG, "-scanDownloads()")
    }

    companion object {
        val STATE_DOWNLOADING: LocalDateTime = Instant.ofEpochMilli(1).atZone(ZoneOffset.UTC).toLocalDateTime()
        val STATE_INVALID: LocalDateTime = Instant.ofEpochMilli(0).atZone(ZoneOffset.UTC).toLocalDateTime()
    }

    private suspend fun reconcileDownloadStates() {
        try {
            val indexedDownloads = mutableMapOf<String, Download>()
            downloadManager.downloadIndex.getDownloads().use { cursor ->
                while (cursor.moveToNext()) {
                    indexedDownloads[cursor.download.request.id] = cursor.download
                }
            }

            indexedDownloads.forEach { (id, download) ->
                applyDownloadState(id, stateToLocalDateTime(download))
            }

            val staleActiveIds = downloads.value
                .filterValues { it == STATE_DOWNLOADING }
                .keys
                .filterNot { it in indexedDownloads }
            staleActiveIds.forEach { id ->
                Log.i(TAG, "DOWNLOAD_STATE mediaId=$id media3State=MISSING appState=STALE")
                downloads.update { map ->
                    map.toMutableMap().apply {
                        remove(id)
                    }
                }
                database.updateDownloadStatus(id, null)
            }

            downloads.update { map ->
                map.filterValues { it.isCompletedDownload() || it == STATE_DOWNLOADING }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to reconcile download states", e)
        }
    }

    private fun applyDownloadState(
        id: String,
        state: LocalDateTime?,
    ) {
        downloads.update { map ->
            map.toMutableMap().apply {
                if (state == null || state == STATE_INVALID) {
                    remove(id)
                } else {
                    set(id, state)
                }
            }
        }

        downloadStatusWriter.launch {
            database.updateDownloadStatus(
                songId = id,
                dateDownload = if (state == null || state == STATE_INVALID) null else state,
            )
        }
    }


    init {
        Log.i(TAG, "DownloadUtil init")
        CoroutineScope(dlCoroutine).launch {
            rescanDownloads()
        }

        downloadManager.addListener(
            object : DownloadManager.Listener {
                override fun onInitialized(downloadManager: DownloadManager) {
                    CoroutineScope(dlCoroutine).launch {
                        reconcileDownloadStates()
                    }
                }

                override fun onDownloadChanged(
                    downloadManager: DownloadManager,
                    download: Download,
                    finalException: Exception?
                ) {
                    Log.i(
                        TAG,
                        "DOWNLOAD_STATE mediaId=${download.request.id} " +
                            "media3State=${media3StateName(download.state)} " +
                            "appState=${appDownloadStateName(stateToLocalDateTime(download))}"
                    )
                    applyDownloadState(download.request.id, stateToLocalDateTime(download))
                }

                override fun onDownloadRemoved(
                    downloadManager: DownloadManager,
                    download: Download,
                ) {
                    Log.i(
                        TAG,
                        "DOWNLOAD_STATE mediaId=${download.request.id} " +
                            "media3State=REMOVED appState=NOT_DOWNLOADED"
                    )
                    applyDownloadState(download.request.id, null)
                }
            }
        )
    }
}

fun stateToLocalDateTime(download: Download): LocalDateTime {
    return when (download.state) {
        Download.STATE_COMPLETED -> {
            Instant.ofEpochMilli(download.updateTimeMs).atZone(ZoneOffset.UTC).toLocalDateTime()
        }

        Download.STATE_QUEUED,
        Download.STATE_DOWNLOADING,
        Download.STATE_RESTARTING,
        -> STATE_DOWNLOADING

        else -> STATE_INVALID
    }
}
