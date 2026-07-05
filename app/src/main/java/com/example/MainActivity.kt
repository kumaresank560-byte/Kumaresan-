package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.data.DocumentDatabase
import com.example.data.DocumentRepository
import com.example.ui.DocumentViewModel
import com.example.ui.DocumentViewModelFactory
import com.example.ui.screens.CameraScreen
import com.example.ui.screens.CropScreen
import com.example.ui.screens.FilterScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ReviewScreen
import com.example.ui.screens.SyncScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize SQLite Offline Persistence database & repository
        val database = DocumentDatabase.getDatabase(applicationContext)
        val repository = DocumentRepository(database.documentDao())
        val factory = DocumentViewModelFactory(repository)

        setContent {
            MyApplicationTheme {
                val navController = rememberNavController()
                val viewModel: DocumentViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = factory)

                NavHost(
                    navController = navController,
                    startDestination = "home"
                ) {
                    composable("home") {
                        HomeScreen(
                            viewModel = viewModel,
                            onNavigateToScan = { navController.navigate("camera") },
                            onNavigateToSync = { navController.navigate("sync") }
                        )
                    }
                    composable("camera") {
                        CameraScreen(
                            viewModel = viewModel,
                            onNavigateToCrop = { navController.navigate("crop") },
                            onNavigateToReview = { navController.navigate("review") },
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                    composable("crop") {
                        CropScreen(
                            viewModel = viewModel,
                            onNavigateToFilter = { navController.navigate("filter") },
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                    composable("filter") {
                        FilterScreen(
                            viewModel = viewModel,
                            onNavigateToReview = { navController.navigate("review") },
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                    composable("review") {
                        ReviewScreen(
                            viewModel = viewModel,
                            onNavigateToCamera = { navController.navigate("camera") },
                            onNavigateToCrop = { navController.navigate("crop") },
                            onNavigateHome = {
                                navController.navigate("home") {
                                    popUpTo("home") { inclusive = true }
                                }
                            }
                        )
                    }
                    composable("sync") {
                        SyncScreen(
                            viewModel = viewModel,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
