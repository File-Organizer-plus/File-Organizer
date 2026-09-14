package fileorganizer.app.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import fileorganizer.app.ui.screens.HomeScreen
import fileorganizer.app.ui.screens.VideoCompressorScreen
import fileorganizer.app.ui.screens.DuplicateFilesScreen
import fileorganizer.app.ui.screens.LargeFilesScreen
import fileorganizer.app.ui.screens.PdfToolsScreen
import fileorganizer.app.ui.screens.ImagesToPdfScreen
import fileorganizer.app.ui.screens.SettingsScreen

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object VideoCompressor : Screen("video_compressor")
    object DuplicateFiles : Screen("duplicate_files")
    object LargeFiles : Screen("large_files")
    object PdfTools : Screen("pdf_tools")
    object ImagesToPdf : Screen("images_to_pdf")
    object Settings : Screen("settings")
    object Subscription : Screen("subscription")
}

@Composable
fun AppNavigation(sharedPdfUris: List<Uri> = emptyList()) {
    val navController = rememberNavController()

    val startDestination = if (sharedPdfUris.isNotEmpty()) Screen.PdfTools.route else Screen.Home.route

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToVideoCompressor = { navController.navigate(Screen.VideoCompressor.route) },
                onNavigateToDuplicateFiles = { navController.navigate(Screen.DuplicateFiles.route) },
                onNavigateToLargeFiles = { navController.navigate(Screen.LargeFiles.route) },
                onNavigateToPdfTools = { navController.navigate(Screen.PdfTools.route) },
                onNavigateToImagesToPdf = { navController.navigate(Screen.ImagesToPdf.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
            )
        }
        composable(Screen.VideoCompressor.route) {
            VideoCompressorScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Screen.DuplicateFiles.route) {
            DuplicateFilesScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Screen.LargeFiles.route) {
            LargeFilesScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Screen.PdfTools.route) {
            PdfToolsScreen(onNavigateBack = { navController.popBackStack() }, sharedPdfUris = sharedPdfUris)
        }
        composable(Screen.ImagesToPdf.route) {
            ImagesToPdfScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSubscription = { navController.navigate(Screen.Subscription.route) }
            )
        }
        composable(Screen.Subscription.route) {
            fileorganizer.app.ui.screens.SubscriptionScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}

