package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FlashAuto
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.DocumentViewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import java.io.File
import java.util.*

@Composable
fun CameraScreen(
    viewModel: DocumentViewModel,
    onNavigateToCrop: () -> Unit,
    onNavigateToReview: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (hasCameraPermission) {
        CameraContent(
            viewModel = viewModel,
            onNavigateToCrop = onNavigateToCrop,
            onNavigateToReview = onNavigateToReview,
            onNavigateBack = onNavigateBack,
            modifier = modifier
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Camera Permission Required",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Tn Doc scanner needs access to the camera to digitize documents and create high-contrast scans.",
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Grant Permission")
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onNavigateBack) {
                    Text("Go Back", color = Color.White)
                }
            }
        }
    }
}

@SuppressLint("RestrictedApi")
@Composable
fun CameraContent(
    viewModel: DocumentViewModel,
    onNavigateToCrop: () -> Unit,
    onNavigateToReview: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activePages by viewModel.activePages.collectAsState()

    val cameraController = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
        }
    }

    var flashMode by remember { mutableStateOf(ImageCapture.FLASH_MODE_OFF) }
    var isCapturing by remember { mutableStateOf(false) }
    var isBatchMode by remember { mutableStateOf(false) }

    // Shutter flash effect
    var showShutterFlash by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Black
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Camera Preview View
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        controller = cameraController
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = {
                    cameraController.bindToLifecycle(lifecycleOwner)
                }
            )

            // Document Alignment Guidelines Overlay
            CameraGuideOverlay()

            // Flash effect screen overlay
            if (showShutterFlash) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = 0.8f))
                )
                LaunchedEffect(showShutterFlash) {
                    kotlinx.coroutines.delay(80)
                    showShutterFlash = false
                }
            }

            // Top Toolbar Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }

                // Cycle flash states: Off -> On -> Auto
                IconButton(
                    onClick = {
                        flashMode = when (flashMode) {
                            ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
                            ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
                            else -> ImageCapture.FLASH_MODE_OFF
                        }
                        cameraController.imageCaptureFlashMode = flashMode
                    },
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    val icon = when (flashMode) {
                        ImageCapture.FLASH_MODE_ON -> Icons.Outlined.FlashOn
                        ImageCapture.FLASH_MODE_AUTO -> Icons.Outlined.FlashAuto
                        else -> Icons.Outlined.FlashOff
                    }
                    Icon(icon, contentDescription = "Flash settings", tint = Color.White)
                }
            }

            // Bottom Controlling Row
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(24.dp)
                    .align(Alignment.BottomCenter),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Mode selector (Single Page vs Batch Mode)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { isBatchMode = false },
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = if (!isBatchMode) Color.White.copy(alpha = 0.2f) else Color.Transparent,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.padding(horizontal = 4.dp).testTag("single_mode_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Single Page", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    TextButton(
                        onClick = { isBatchMode = true },
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = if (isBatchMode) Color.White.copy(alpha = 0.2f) else Color.Transparent,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.padding(horizontal = 4.dp).testTag("batch_mode_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Batch Mode", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Text(
                    text = if (isBatchMode) "Batch Mode: Tap shutter sequentially to capture multiple pages." else "Align paper inside guidelines. Tap shutter to digitize.",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Preview thumbnail showing multi-page counts
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.15f))
                            .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .clickable(enabled = activePages.isNotEmpty()) { onNavigateToReview() }
                            .testTag("preview_thumbnail"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (activePages.isNotEmpty()) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = "Scanned queue", tint = Color.White)
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(4.dp, (-4).dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = activePages.size.toString(),
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Icon(Icons.Default.Photo, contentDescription = "Empty queue", tint = Color.White.copy(alpha = 0.4f))
                        }
                    }

                    // Shutter Capture Button
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .border(4.dp, Color.White, CircleShape)
                            .padding(4.dp)
                            .clip(CircleShape)
                            .background(if (isCapturing) Color.LightGray else Color.White)
                            .clickable(enabled = !isCapturing) {
                                isCapturing = true
                                showShutterFlash = true
                                capturePhoto(context, cameraController) { uri ->
                                    isCapturing = false
                                    viewModel.addTempPage(uri)
                                    if (!isBatchMode) {
                                        onNavigateToCrop()
                                    } else {
                                        Toast.makeText(context, "Page captured sequentially! (${activePages.size + 1} pages total)", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            .testTag("shutter_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isCapturing) {
                            CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(24.dp))
                        }
                    }

                    // Skip / Direct Review button
                    TextButton(
                        onClick = onNavigateToReview,
                        enabled = activePages.isNotEmpty()
                    ) {
                        Text(
                            text = "REVIEW",
                            color = if (activePages.isNotEmpty()) Color.White else Color.White.copy(alpha = 0.4f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CameraGuideOverlay() {
    val guideColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        val cropWidth = width * 0.85f
        val cropHeight = height * 0.6f
        val left = (width - cropWidth) / 2f
        val top = (height - cropHeight) / 2.3f

        // Draw camera notch grid boundaries
        drawRoundRect(
            color = guideColor,
            topLeft = Offset(left, top),
            size = Size(cropWidth, cropHeight),
            cornerRadius = CornerRadius(16.dp.toPx()),
            style = Stroke(
                width = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
            )
        )

        // Draw solid corners for edge framing emphasis
        val len = 25.dp.toPx()
        val thickness = 4.dp.toPx()

        // Top Left
        drawLine(guideColor, Offset(left - thickness/2, top), Offset(left + len, top), thickness)
        drawLine(guideColor, Offset(left, top - thickness/2), Offset(left, top + len), thickness)

        // Top Right
        drawLine(guideColor, Offset(left + cropWidth - len, top), Offset(left + cropWidth + thickness/2, top), thickness)
        drawLine(guideColor, Offset(left + cropWidth, top - thickness/2), Offset(left + cropWidth, top + len), thickness)

        // Bottom Left
        drawLine(guideColor, Offset(left - thickness/2, top + cropHeight), Offset(left + len, top + cropHeight), thickness)
        drawLine(guideColor, Offset(left, top + cropHeight - len), Offset(left, top + cropHeight + thickness/2), thickness)

        // Bottom Right
        drawLine(guideColor, Offset(left + cropWidth - len, top + cropHeight), Offset(left + cropWidth + thickness/2, top + cropHeight), thickness)
        drawLine(guideColor, Offset(left + cropWidth, top + cropHeight - len), Offset(left + cropWidth, top + cropHeight + thickness/2), thickness)
    }
}

/**
 * Fires the camera shutter capture operation and outputs the raw offline photo Jpeg.
 */
fun capturePhoto(context: Context, controller: LifecycleCameraController, onSaved: (Uri) -> Unit) {
    val tempFile = File(context.cacheDir, "RAW_SCAN_${System.currentTimeMillis()}.jpg")
    val outputOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()

    controller.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                val uri = outputFileResults.savedUri ?: Uri.fromFile(tempFile)
                onSaved(uri)
            }

            override fun onError(exception: ImageCaptureException) {
                Toast.makeText(context, "Scanning error: ${exception.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    )
}
