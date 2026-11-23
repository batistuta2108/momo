package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MovieCreation
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.core.configureAppCheck
import com.example.ui.hub.StudioHubScreen
import com.example.ui.music.MusicStudioScreen
import com.example.ui.photo.PhotoStudioScreen
import com.example.ui.theme.HydraStudioTheme
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceHigh
import com.example.ui.theme.StudioTextSecondary
import com.example.ui.video.VideoStudioScreen

const val ROUTE_HUB = "studio_hub"
const val ROUTE_MUSIC = "music_studio"
const val ROUTE_PHOTO = "photo_studio"
const val ROUTE_VIDEO = "video_studio"

data class StudioNavDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val studioDestinations = listOf(
    StudioNavDestination(
        route = ROUTE_HUB,
        labelRes = R.string.nav_home,
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
        testTag = "nav_home"
    ),
    StudioNavDestination(
        route = ROUTE_MUSIC,
        labelRes = R.string.nav_music,
        selectedIcon = Icons.Filled.GraphicEq,
        unselectedIcon = Icons.Outlined.GraphicEq,
        testTag = "nav_music"
    ),
    StudioNavDestination(
        route = ROUTE_PHOTO,
        labelRes = R.string.nav_photo,
        selectedIcon = Icons.Filled.PhotoCamera,
        unselectedIcon = Icons.Outlined.PhotoCamera,
        testTag = "nav_photo"
    ),
    StudioNavDestination(
        route = ROUTE_VIDEO,
        labelRes = R.string.nav_video,
        selectedIcon = Icons.Filled.MovieCreation,
        unselectedIcon = Icons.Outlined.MovieCreation,
        testTag = "nav_video"
    )
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before UI or Firebase operations
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            HydraStudioTheme {
                HydraStudioApp()
            }
        }
    }
}

@Composable
fun HydraStudioApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    fun navigateTo(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = StudioObsidian,
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        containerColor = StudioSurface,
                        contentColor = StudioCyan
                    ) {
                        studioDestinations.forEach { dest ->
                            val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                            val label = stringResource(dest.labelRes)
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTo(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = StudioCyan,
                                    selectedTextColor = StudioCyan,
                                    indicatorColor = StudioSurfaceHigh,
                                    unselectedIconColor = StudioTextSecondary,
                                    unselectedTextColor = StudioTextSecondary
                                ),
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        containerColor = StudioSurface,
                        contentColor = StudioCyan
                    ) {
                        studioDestinations.forEach { dest ->
                            val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                            val label = stringResource(dest.labelRes)
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTo(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = StudioCyan,
                                    selectedTextColor = StudioCyan,
                                    indicatorColor = StudioSurfaceHigh,
                                    unselectedIconColor = StudioTextSecondary,
                                    unselectedTextColor = StudioTextSecondary
                                ),
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = ROUTE_HUB,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(ROUTE_HUB) {
                        StudioHubScreen(
                            onNavigateMusic = { navigateTo(ROUTE_MUSIC) },
                            onNavigatePhoto = { navigateTo(ROUTE_PHOTO) },
                            onNavigateVideo = { navigateTo(ROUTE_VIDEO) }
                        )
                    }
                    composable(ROUTE_MUSIC) {
                        MusicStudioScreen()
                    }
                    composable(ROUTE_PHOTO) {
                        PhotoStudioScreen()
                    }
                    composable(ROUTE_VIDEO) {
                        VideoStudioScreen()
                    }
                }
            }
        }
    }
}
