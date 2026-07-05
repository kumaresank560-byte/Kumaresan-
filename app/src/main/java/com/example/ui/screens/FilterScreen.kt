package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.DocumentViewModel
import com.example.util.ImageProcessing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterScreen(
    viewModel: DocumentViewModel,
    onNavigateToReview: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activePages by viewModel.activePages.collectAsState()
    val editingIndex by viewModel.editingPageIndex.collectAsState()

    val page = remember(activePages, editingIndex) {
        if (editingIndex in activePages.indices) activePages[editingIndex] else null
    }

    if (page == null) {
        LaunchedEffect(Unit) { onNavigateBack() }
        return
    }

    var selectedFilter by remember { mutableStateOf(page.filter) }
    var rotation by remember { mutableStateOf(page.rotation) }

    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    // Run perspective crop, rotation, and filters in a background thread to maintain fluid UI
    LaunchedEffect(page, selectedFilter, rotation) {
        isProcessing = true
        withContext(Dispatchers.Default) {
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(page.originalUri)
                val original = BitmapFactory.decodeStream(inputStream)
                if (original != null) {
                    // 1. Warp perspective
                    val warped = ImageProcessing.warpPerspective(
                        original,
                        page.topLeft, page.topRight, page.bottomRight, page.bottomLeft
                    )
                    original.recycle()

                    // 2. Rotate
                    val rotated = ImageProcessing.rotateBitmap(warped, rotation.toFloat())
                    if (rotated != warped) warped.recycle()

                    // 3. Filter
                    val filtered = ImageProcessing.applyFilter(rotated, selectedFilter)
                    if (filtered != rotated) rotated.recycle()

                    previewBitmap = filtered
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isProcessing = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Enhance Document", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            rotation = (rotation + 90) % 360
                        }
                    ) {
                        Icon(Icons.Default.RotateRight, contentDescription = "Rotate 90", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Filters row
                    Text(
                        text = "Document Filters",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        FilterCard(
                            title = "Original",
                            isSelected = selectedFilter == "ORIGINAL",
                            onClick = { selectedFilter = "ORIGINAL" }
                        )
                        FilterCard(
                            title = "Magic Color",
                            isSelected = selectedFilter == "MAGIC",
                            onClick = { selectedFilter = "MAGIC" }
                        )
                        FilterCard(
                            title = "Grayscale",
                            isSelected = selectedFilter == "GRAYSCALE",
                            onClick = { selectedFilter = "GRAYSCALE" }
                        )
                        FilterCard(
                            title = "Photocopy B&W",
                            isSelected = selectedFilter == "BW",
                            onClick = { selectedFilter = "BW" }
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            viewModel.rotateActivePage(editingIndex)
                            // Match loop rotation updates
                            if (page.rotation != rotation) {
                                val diff = (rotation - page.rotation + 360) % 360
                                for (i in 0 until (diff / 90)) {
                                    viewModel.rotateActivePage(editingIndex)
                                }
                            }
                            viewModel.applyFilterToActivePage(editingIndex, selectedFilter)
                            onNavigateToReview()
                        },
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .height(48.dp)
                            .testTag("accept_page_button")
                    ) {
                        Text("Accept Page")
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.Default.Check, contentDescription = null)
                    }
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isProcessing || previewBitmap == null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Applying document enhancements...",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                previewBitmap?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Enhanced image preview",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }
    }
}

@Composable
fun FilterCard(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface

    Box(
        modifier = Modifier
            .width(80.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(backgroundColor)
            .border(2.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 14.sp
        )
    }
}
