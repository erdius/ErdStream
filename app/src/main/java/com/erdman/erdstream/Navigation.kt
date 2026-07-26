package com.erdman.erdstream

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val label: String, val icon: ImageVector?) {
    object ServerSetup : Screen("serverSetup", "Connect", null)

    object Home : Screen("home", "Home", Icons.Outlined.Home)
    object RecentlyAdded : Screen("recentlyAdded", "Recently Added", Icons.Outlined.LibraryMusic)
    object RecentlyPlayedAll : Screen("recentlyPlayedAll", "Recently Played", Icons.Outlined.LibraryMusic)
    object MostPlayedAll : Screen("mostPlayedAll", "Most Played", Icons.Outlined.LibraryMusic)

    object Artists : Screen("artists", "Artists", Icons.Outlined.PersonOutline)
    object ArtistDetails : Screen("artistDetails", "Artist", Icons.Outlined.PersonOutline)
    object AlbumDetails : Screen("albumDetails", "Album", Icons.Outlined.LibraryMusic)
    object Playlists : Screen("playlists", "Playlists", Icons.Outlined.LibraryMusic)
    object PlaylistDetails : Screen("playlistDetails", "Playlist", Icons.Outlined.LibraryMusic)
    object Search : Screen("search", "Search", Icons.Outlined.Search)
    object Settings : Screen("settings", "Settings", Icons.Outlined.Settings)
}

val navItems = listOf(
    Screen.Home,
    Screen.Artists,
    Screen.Playlists,
    Screen.Search,
    Screen.Settings,
)
