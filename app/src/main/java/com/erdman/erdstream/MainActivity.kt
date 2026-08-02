package com.erdman.erdstream

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.erdman.erdstream.data.SubsonicException
import com.erdman.erdstream.playback.PlaybackService
import com.erdman.erdstream.ui.AlbumDetail
import com.erdman.erdstream.ui.AlbumDetailsScreen
import com.erdman.erdstream.ui.AlbumUiModel
import com.erdman.erdstream.ui.ArtistDetailsScreen
import com.erdman.erdstream.ui.ArtistUiModel
import com.erdman.erdstream.ui.ArtistsScreen
import com.erdman.erdstream.ui.HomeScreen
import com.erdman.erdstream.ui.NowPlayingScreen
import com.erdman.erdstream.ui.PlaylistDetail
import com.erdman.erdstream.ui.PlaylistDetailsScreen
import com.erdman.erdstream.ui.PlaylistUiModel
import com.erdman.erdstream.ui.PlaylistsScreen
import com.erdman.erdstream.ui.SearchResults
import com.erdman.erdstream.ui.SearchScreen
import com.erdman.erdstream.ui.ServerSetupScreen
import com.erdman.erdstream.ui.SettingsScreen
import com.erdman.erdstream.ui.SongUiModel
import com.erdman.erdstream.ui.theme.ErdStreamTheme
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ErdStreamTheme {
                val app = application as ErdStreamApplication
                ErdStreamApp(app)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    companion object {
        /** Set on the media notification's tap intent to open Now Playing directly. */
        const val EXTRA_OPEN_NOW_PLAYING = "open_now_playing"
    }
}

@Composable
fun ErdStreamApp(app: ErdStreamApplication) {
    val credentials by app.credentialsManager.credentials.collectAsState()

    if (credentials == null) {
        ServerSetupScreen(
            onTestConnection = { url, username, password ->
                try {
                    app.subsonicRepository.testConnection(url, username, password)
                    Result.success(Unit)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            },
            onConnected = { url, username, password ->
                app.credentialsManager.save(url, username, password)
            },
        )
    } else {
        ErdStreamMainUi(app)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ErdStreamMainUi(app: ErdStreamApplication) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val scope = rememberCoroutineScope()

    val viewModel: ErdStreamViewModel = viewModel(factory = ErdStreamViewModel.factory(app))
    val playbackState by viewModel.playbackState.collectAsState()
    var mediaController by remember { mutableStateOf<MediaController?>(null) }
    var showNowPlaying by remember { mutableStateOf(false) }

    // Tapping the media notification/lock-screen controls' body should open
    // Now Playing directly, not just relaunch the app to whatever screen it
    // was last on. PlaybackService tags that tap's intent with this extra.
    val activity = context as? MainActivity
    val lifecycleOwner = LocalLifecycleOwner.current

    fun consumeOpenNowPlayingIntent() {
        val intent = activity?.intent ?: return
        if (intent.getBooleanExtra(MainActivity.EXTRA_OPEN_NOW_PLAYING, false)) {
            showNowPlaying = true
            intent.removeExtra(MainActivity.EXTRA_OPEN_NOW_PLAYING)
        }
    }

    // Battery-optimization exemption: the actual mechanism that stops Android
    // from killing background playback. Requesting it just launches a system
    // dialog; there's no callback, so re-check the real state on every resume.
    fun isIgnoringBatteryOptimizations(): Boolean {
        val powerManager = context.getSystemService(PowerManager::class.java)
        return powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
    }

    var hasBatteryOptimizationExemption by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        hasBatteryOptimizationExemption = isIgnoringBatteryOptimizations()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                consumeOpenNowPlayingIntent()
                hasBatteryOptimizationExemption = isIgnoringBatteryOptimizations()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // DuraSpeed (MediaTek's background-app killer, present on some devices
    // including the Mudita Kompakt) can silently stop playback even with the
    // battery-optimization exemption granted -- it's a separate OEM-specific
    // mechanism. Only show the row if the DuraSpeed app is actually present.
    val isDuraSpeedAvailable = remember {
        try {
            context.packageManager.getPackageInfo("com.mediatek.duraspeed", 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    // Android 13+ requires this runtime permission for any notification to
    // show, including the media session's playback-controls notification --
    // without it the foreground service has no visible controls.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = {},
    )

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val bitrate by app.transcodeSettingsManager.bitrate.collectAsState()

    val tabSettings by app.tabSettingsManager.tabSettings.collectAsState()
    val visibleNavItems = remember(tabSettings) {
        tabSettings
            .filter { it.visible }
            .mapNotNull { setting -> navItems.find { it.route == setting.route } }
            .ifEmpty { navItems }
    }
    val startDestination = remember {
        app.tabSettingsManager.getTabSettingsSync()
            .firstOrNull { it.visible }?.route ?: Screen.Home.route
    }

    // Bumped by Settings > Resync library to force artists/playlists/Home to
    // refetch from the server. There's no local database to resync against,
    // so this is really "refresh everything from the server right now."
    var libraryRefreshTrigger by remember { mutableStateOf(0) }

    var artists by remember { mutableStateOf<List<ArtistUiModel>>(emptyList()) }
    var isLoadingArtists by remember { mutableStateOf(false) }
    var artistsError by remember { mutableStateOf<String?>(null) }
    var hasLoadedArtists by remember { mutableStateOf(false) }

    var playlists by remember { mutableStateOf<List<PlaylistUiModel>>(emptyList()) }
    var isLoadingPlaylists by remember { mutableStateOf(false) }
    var playlistsError by remember { mutableStateOf<String?>(null) }
    var hasLoadedPlaylists by remember { mutableStateOf(false) }

    var recentlyAddedAlbums by remember { mutableStateOf<List<AlbumUiModel>>(emptyList()) }
    var recentlyPlayedAlbums by remember { mutableStateOf<List<AlbumUiModel>>(emptyList()) }
    var mostPlayedAlbums by remember { mutableStateOf<List<AlbumUiModel>>(emptyList()) }
    var isLoadingHome by remember { mutableStateOf(true) }
    var homeError by remember { mutableStateOf<String?>(null) }
    var isBuildingMix by remember { mutableStateOf(false) }
    var mixError by remember { mutableStateOf<String?>(null) }

    var selectedArtist by remember { mutableStateOf<ArtistUiModel?>(null) }
    var artistAlbums by remember { mutableStateOf<List<AlbumUiModel>>(emptyList()) }
    var isLoadingArtistAlbums by remember { mutableStateOf(false) }
    var artistAlbumsError by remember { mutableStateOf<String?>(null) }

    var selectedAlbumId by remember { mutableStateOf<String?>(null) }
    var albumDetail by remember { mutableStateOf<AlbumDetail?>(null) }
    var isLoadingAlbum by remember { mutableStateOf(false) }
    var albumError by remember { mutableStateOf<String?>(null) }

    var selectedPlaylistId by remember { mutableStateOf<String?>(null) }
    var playlistDetail by remember { mutableStateOf<PlaylistDetail?>(null) }
    var isLoadingPlaylist by remember { mutableStateOf(false) }
    var playlistError by remember { mutableStateOf<String?>(null) }

    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<SearchResults?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }

    fun errorText(e: Throwable): String =
        (e as? SubsonicException)?.message ?: e.message ?: "Something went wrong"

    // Connect to the playback service.
    LaunchedEffect(Unit) {
        try {
            val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            val future = MediaController.Builder(context, sessionToken).buildAsync()
            future.addListener({
                try {
                    mediaController = future.get()
                } catch (_: Exception) {
                }
            }, ContextCompat.getMainExecutor(context))
        } catch (_: Exception) {
        }
    }

    LaunchedEffect(mediaController) {
        mediaController?.let { viewModel.startPlaybackMonitoring(it) }
    }

    suspend fun loadArtists() {
        isLoadingArtists = true
        artistsError = null
        try {
            artists = app.subsonicRepository.getArtists()
            hasLoadedArtists = true
        } catch (e: Exception) {
            artistsError = errorText(e)
        } finally {
            isLoadingArtists = false
        }
    }

    suspend fun loadPlaylists() {
        isLoadingPlaylists = true
        playlistsError = null
        try {
            playlists = app.subsonicRepository.getPlaylists()
            hasLoadedPlaylists = true
        } catch (e: Exception) {
            playlistsError = errorText(e)
        } finally {
            isLoadingPlaylists = false
        }
    }

    // Artists/Playlists used to fetch eagerly at launch alongside Home,
    // meaning 5 requests competed for bandwidth right when only Home's 3
    // were actually needed for the screen the user lands on. Load each lazily
    // the first time its tab is actually visited instead.
    LaunchedEffect(currentDestination?.route) {
        if (currentDestination?.route == Screen.Artists.route && !hasLoadedArtists) loadArtists()
        if (currentDestination?.route == Screen.Playlists.route && !hasLoadedPlaylists) loadPlaylists()
    }

    // Settings > Resync library refreshes whatever's already been loaded.
    LaunchedEffect(libraryRefreshTrigger) {
        if (libraryRefreshTrigger == 0) return@LaunchedEffect
        if (hasLoadedArtists) loadArtists()
        if (hasLoadedPlaylists) loadPlaylists()
    }

    LaunchedEffect(libraryRefreshTrigger) {
        isLoadingHome = true
        homeError = null
        try {
            coroutineScope {
                val addedDeferred = async { app.subsonicRepository.getAlbumList("newest", size = 5) }
                val playedDeferred = async { app.subsonicRepository.getAlbumList("recent", size = 5) }
                val frequentDeferred = async { app.subsonicRepository.getAlbumList("frequent", size = 5) }
                recentlyAddedAlbums = addedDeferred.await()
                recentlyPlayedAlbums = playedDeferred.await()
                mostPlayedAlbums = frequentDeferred.await()
            }
        } catch (e: Exception) {
            homeError = errorText(e)
        } finally {
            isLoadingHome = false
        }
    }

    LaunchedEffect(selectedArtist?.id) {
        val artist = selectedArtist ?: return@LaunchedEffect
        isLoadingArtistAlbums = true
        artistAlbumsError = null
        try {
            artistAlbums = app.subsonicRepository.getArtistAlbums(artist.id)
        } catch (e: Exception) {
            artistAlbumsError = errorText(e)
        } finally {
            isLoadingArtistAlbums = false
        }
    }

    LaunchedEffect(selectedAlbumId) {
        val albumId = selectedAlbumId ?: return@LaunchedEffect
        isLoadingAlbum = true
        albumError = null
        try {
            albumDetail = app.subsonicRepository.getAlbumDetail(albumId)
        } catch (e: Exception) {
            albumError = errorText(e)
        } finally {
            isLoadingAlbum = false
        }
    }

    suspend fun loadPlaylistDetail(playlistId: String) {
        isLoadingPlaylist = true
        playlistError = null
        try {
            playlistDetail = app.subsonicRepository.getPlaylistDetail(playlistId)
        } catch (e: Exception) {
            playlistError = errorText(e)
        } finally {
            isLoadingPlaylist = false
        }
    }

    LaunchedEffect(selectedPlaylistId) {
        val playlistId = selectedPlaylistId ?: return@LaunchedEffect
        loadPlaylistDetail(playlistId)
    }

    fun performSearch() {
        if (searchQuery.isBlank()) return
        scope.launch {
            isSearching = true
            searchError = null
            try {
                searchResults = app.subsonicRepository.search(searchQuery)
            } catch (e: Exception) {
                searchError = errorText(e)
            } finally {
                isSearching = false
            }
        }
    }

    fun playQueue(queue: List<SongUiModel>, startIndex: Int) {
        viewModel.startPlaybackFromQueue(queue, startIndex, mediaController)
        showNowPlaying = true
    }

    fun playAlbumMix() {
        scope.launch {
            isBuildingMix = true
            mixError = null
            try {
                val songs = app.subsonicRepository.buildAlbumMixQueue(albumCount = 5)
                if (songs.isNotEmpty()) playQueue(songs, 0)
            } catch (e: Exception) {
                mixError = errorText(e)
            } finally {
                isBuildingMix = false
            }
        }
    }

    fun playTrackMix() {
        scope.launch {
            isBuildingMix = true
            mixError = null
            try {
                val songs = app.subsonicRepository.getRandomSongs(size = 50)
                if (songs.isNotEmpty()) playQueue(songs, 0)
            } catch (e: Exception) {
                mixError = errorText(e)
            } finally {
                isBuildingMix = false
            }
        }
    }

    @Composable
    fun SeeAllAlbumsRoute(fetchType: String) {
        var albums by remember(fetchType) { mutableStateOf<List<AlbumUiModel>>(emptyList()) }
        var isLoading by remember(fetchType) { mutableStateOf(true) }
        var error by remember(fetchType) { mutableStateOf<String?>(null) }

        LaunchedEffect(fetchType) {
            isLoading = true
            error = null
            try {
                albums = app.subsonicRepository.getAlbumList(fetchType, size = 15)
            } catch (e: Exception) {
                error = errorText(e)
            } finally {
                isLoading = false
            }
        }

        ArtistDetailsScreen(
            albums = albums,
            isLoading = isLoading,
            errorMessage = error,
            onAlbumClick = { album ->
                selectedAlbumId = album.id
                navController.navigate(Screen.AlbumDetails.route) { launchSingleTop = true }
            },
        )
    }

    val canNavigateBack = navController.previousBackStackEntry != null
    val nowPlayingSong = playbackState.nowPlayingSong

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = screenTitle(currentDestination?.route, selectedArtist?.name)) },
                navigationIcon = {
                    if (canNavigateBack && currentDestination?.route !in navItems.map { it.route }) {
                        IconButton(onClick = { navController.navigateUp() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    // Always-available way back into the player once a song
                    // is queued, since dismissing Now Playing otherwise has
                    // no other path back to it.
                    if (nowPlayingSong != null && !showNowPlaying) {
                        IconButton(onClick = { showNowPlaying = true }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.QueueMusic,
                                contentDescription = "Now Playing: ${nowPlayingSong.title}",
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (currentDestination?.route in visibleNavItems.map { it.route }) {
                NavigationBar {
                    visibleNavItems.forEach { screen ->
                        val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { screen.icon?.let { Icon(it, contentDescription = screen.label) } },
                            label = { Text(screen.label) },
                        )
                    }
                }
            }
        },
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(paddingValues),
            // No animated slide/fade between screens -- avoids visible motion on e-ink.
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    recentlyAdded = recentlyAddedAlbums,
                    recentlyPlayed = recentlyPlayedAlbums,
                    mostPlayed = mostPlayedAlbums,
                    isLoading = isLoadingHome,
                    errorMessage = homeError,
                    isBuildingMix = isBuildingMix,
                    mixError = mixError,
                    onAlbumClick = { album ->
                        selectedAlbumId = album.id
                        navController.navigate(Screen.AlbumDetails.route) { launchSingleTop = true }
                    },
                    onSeeAllRecentlyAddedClick = {
                        navController.navigate(Screen.RecentlyAdded.route) { launchSingleTop = true }
                    },
                    onSeeAllRecentlyPlayedClick = {
                        navController.navigate(Screen.RecentlyPlayedAll.route) { launchSingleTop = true }
                    },
                    onSeeAllMostPlayedClick = {
                        navController.navigate(Screen.MostPlayedAll.route) { launchSingleTop = true }
                    },
                    onAlbumMixClick = { playAlbumMix() },
                    onTrackMixClick = { playTrackMix() },
                )
            }
            composable(Screen.RecentlyAdded.route) { SeeAllAlbumsRoute(fetchType = "newest") }
            composable(Screen.RecentlyPlayedAll.route) { SeeAllAlbumsRoute(fetchType = "recent") }
            composable(Screen.MostPlayedAll.route) { SeeAllAlbumsRoute(fetchType = "frequent") }
            composable(Screen.Artists.route) {
                ArtistsScreen(
                    artists = artists,
                    isLoading = isLoadingArtists,
                    errorMessage = artistsError,
                    onArtistClick = { artist ->
                        selectedArtist = artist
                        navController.navigate(Screen.ArtistDetails.route) { launchSingleTop = true }
                    },
                )
            }
            composable(Screen.ArtistDetails.route) {
                ArtistDetailsScreen(
                    albums = artistAlbums,
                    isLoading = isLoadingArtistAlbums,
                    errorMessage = artistAlbumsError,
                    onAlbumClick = { album ->
                        selectedAlbumId = album.id
                        navController.navigate(Screen.AlbumDetails.route) { launchSingleTop = true }
                    },
                )
            }
            composable(Screen.AlbumDetails.route) {
                val songs = albumDetail?.songs.orEmpty()
                AlbumDetailsScreen(
                    songs = songs,
                    currentSongId = nowPlayingSong?.id,
                    isLoading = isLoadingAlbum,
                    errorMessage = albumError,
                    onPlaySongClick = { song ->
                        val index = songs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
                        playQueue(songs, index)
                    },
                    onShuffleClick = {
                        if (songs.isNotEmpty()) {
                            viewModel.startShuffledPlaybackFromQueue(songs, mediaController)
                            showNowPlaying = true
                        }
                    },
                )
            }
            composable(Screen.Playlists.route) {
                PlaylistsScreen(
                    playlists = playlists,
                    isLoading = isLoadingPlaylists,
                    errorMessage = playlistsError,
                    onPlaylistClick = { playlist ->
                        selectedPlaylistId = playlist.id
                        navController.navigate(Screen.PlaylistDetails.route) { launchSingleTop = true }
                    },
                )
            }
            composable(Screen.PlaylistDetails.route) {
                val songs = playlistDetail?.songs.orEmpty()
                PlaylistDetailsScreen(
                    songs = songs,
                    currentSongId = nowPlayingSong?.id,
                    isLoading = isLoadingPlaylist,
                    errorMessage = playlistError,
                    onPlaySongClick = { song ->
                        val index = songs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
                        playQueue(songs, index)
                    },
                    onShuffleClick = {
                        if (songs.isNotEmpty()) {
                            viewModel.startShuffledPlaybackFromQueue(songs, mediaController)
                            showNowPlaying = true
                        }
                    },
                    onRemoveSongClick = { index ->
                        val playlistId = selectedPlaylistId
                        val currentDetail = playlistDetail
                        if (playlistId != null && currentDetail != null && index in currentDetail.songs.indices) {
                            // Optimistic removal so the row disappears immediately;
                            // reload from the server only if the request fails, to
                            // roll back and re-sync.
                            playlistDetail = currentDetail.copy(
                                songs = currentDetail.songs.toMutableList().apply { removeAt(index) },
                            )
                            scope.launch {
                                try {
                                    app.subsonicRepository.removeSongFromPlaylist(playlistId, index)
                                } catch (e: Exception) {
                                    playlistError = errorText(e)
                                    loadPlaylistDetail(playlistId)
                                }
                            }
                        }
                    },
                )
            }
            composable(Screen.Search.route) {
                SearchScreen(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onSearchClick = { performSearch() },
                    isSearching = isSearching,
                    errorMessage = searchError,
                    results = searchResults,
                    currentSongId = nowPlayingSong?.id,
                    onArtistClick = { artist ->
                        selectedArtist = artist
                        navController.navigate(Screen.ArtistDetails.route) { launchSingleTop = true }
                    },
                    onAlbumClick = { album ->
                        selectedAlbumId = album.id
                        navController.navigate(Screen.AlbumDetails.route) { launchSingleTop = true }
                    },
                    onSongClick = { song ->
                        val songs = searchResults?.songs.orEmpty()
                        val index = songs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
                        playQueue(songs, index)
                    },
                )
            }
            composable(Screen.Settings.route) {
                val creds = app.credentialsManager.credentials.collectAsState().value
                val duraspeedConfirmed = app.duraspeedSettingsManager.confirmed.collectAsState().value
                SettingsScreen(
                    serverUrl = creds?.serverUrl.orEmpty(),
                    username = creds?.username.orEmpty(),
                    selectedBitrate = bitrate,
                    onBitrateSelected = { app.transcodeSettingsManager.setBitrate(it) },
                    onDisconnectClick = { app.credentialsManager.clear() },
                    hasBatteryOptimizationExemption = hasBatteryOptimizationExemption,
                    onRequestBatteryOptimizationExemption = {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = "package:${context.packageName}".toUri()
                        }
                        context.startActivity(intent)
                    },
                    isDuraSpeedAvailable = isDuraSpeedAvailable,
                    duraspeedConfirmed = duraspeedConfirmed,
                    onDuraSpeedConfirmedChange = { app.duraspeedSettingsManager.setConfirmed(it) },
                    onOpenDuraSpeed = {
                        try {
                            val intent = context.packageManager.getLaunchIntentForPackage("com.mediatek.duraspeed")
                            if (intent != null) {
                                context.startActivity(intent)
                            } else {
                                val explicitIntent = Intent().apply {
                                    component = ComponentName("com.mediatek.duraspeed", "com.mediatek.duraspeed.DuraSpeedMainActivity")
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(explicitIntent)
                            }
                        } catch (e: Exception) {
                            try {
                                val appInfoIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = "package:com.mediatek.duraspeed".toUri()
                                }
                                context.startActivity(appInfoIntent)
                            } catch (e2: Exception) {
                                context.startActivity(Intent(Settings.ACTION_SETTINGS))
                            }
                        }
                    },
                    isResyncing = isLoadingArtists || isLoadingPlaylists || isLoadingHome,
                    onResyncLibraryClick = { libraryRefreshTrigger++ },
                    tabSettings = tabSettings,
                    onTabSettingsChange = { app.tabSettingsManager.setTabSettings(it) },
                )
            }
        }
    }

    if (showNowPlaying && nowPlayingSong != null) {
        BackHandler { showNowPlaying = false }
        NowPlayingScreen(
            title = nowPlayingSong.title,
            artist = nowPlayingSong.artist,
            album = nowPlayingSong.album,
            isPlaying = playbackState.isPlaying,
            isBuffering = playbackState.isBuffering,
            currentPositionMs = playbackState.positionMs,
            durationMs = playbackState.durationMs,
            repeatMode = playbackState.repeatMode,
            isShuffleOn = playbackState.isShuffleOn,
            onPlayPauseClick = { viewModel.togglePlayback(mediaController) },
            onSeek = { position -> viewModel.seekTo(position, mediaController) },
            onPreviousClick = { viewModel.playPrevious(mediaController) },
            onNextClick = { viewModel.playNext(mediaController) },
            onShuffleClick = { viewModel.toggleShuffle(mediaController) },
            onRepeatClick = { viewModel.cycleRepeatMode(mediaController) },
            onBackClick = { showNowPlaying = false },
        )
    }
}

private fun screenTitle(route: String?, artistName: String?): String = when (route) {
    Screen.Home.route -> "Home"
    Screen.RecentlyAdded.route -> "Recently Added"
    Screen.RecentlyPlayedAll.route -> "Recently Played"
    Screen.MostPlayedAll.route -> "Most Played"
    Screen.Artists.route -> "Artists"
    Screen.ArtistDetails.route -> artistName ?: "Artist"
    Screen.AlbumDetails.route -> "Album"
    Screen.Playlists.route -> "Playlists"
    Screen.PlaylistDetails.route -> "Playlist"
    Screen.Search.route -> "Search"
    Screen.Settings.route -> "Settings"
    else -> "ErdStream"
}
