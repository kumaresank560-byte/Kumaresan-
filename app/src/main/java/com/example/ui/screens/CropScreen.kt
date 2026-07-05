package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.DocumentViewModel
import java.io.InputStream
import kotlin.math.pow
import kotlin.math.sqrt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropScreen(
    viewModel: DocumentViewModel,
    onNavigateToFilter: () -> Unit,
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

    // Relative corner points (0f..1f)
    var tl by remember { mutableStateOf(page.topLeft) }
    var tr by remember { mutableStateOf(page.topRight) }
    var br by remember { mutableStateOf(page.bottomRight) }
    var bl by remember { mutableStateOf(page.bottomLeft) }

    var activeDragCorner by remember { mutableStateOf<Int?>(null) } // 0: TL, 1: TR, 2: BR, 3: BL
    var currentDragOffset by remember { mutableStateOf(PointF(0f, 0f)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Adjust Borders", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            // Reset borders to full screen
                            tl = PointF(0.01f, 0.01f)
                            tr = PointF(0.99f, 0.01f)
                            br = PointF(0.99f, 0.99f)
                            bl = PointF(0.01f, 0.99f)
                        }
                    ) {
                        Icon(Icons.Default.AspectRatio, contentDescription = "Full Screen", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                        .navigationBarsPadding(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Drag corners to fit document.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )

                    Button(
                        onClick = {
                            viewModel.updateCropPoints(editingIndex, tl, tr, br, bl)
                            onNavigateToFilter()
                        },
                        shape = RoundedCornerShape(24.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                        modifier = Modifier.testTag("apply_crop_button")
                    ) {
                        Text("Apply Crop")
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.Default.Check, contentDescription = null)
                    }
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                val canvasWidth = constraints.maxWidth.toFloat()
                val canvasHeight = constraints.maxHeight.toFloat()

                // Display source image to crop
                AsyncImage(
                    model = page.originalUri,
                    contentDescription = "Original scan target",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )

                // Draggable Quadrilateral Overlay Canvas
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(canvasWidth, canvasHeight) {
                            detectDragGestures(
                                onDragStart = { touchOffset ->
                                    val touchX = touchOffset.x / canvasWidth
                                    val touchY = touchOffset.y / canvasHeight

                                    // Find which corner is nearest to touch coordinates
                                    val distances = listOf(
                                        distance(touchX, touchY, tl.x, tl.y) to 0,
                                        distance(touchX, touchY, tr.x, tr.y) to 1,
                                        distance(touchX, touchY, br.x, br.y) to 2,
                                        distance(touchX, touchY, bl.x, bl.y) to 3
                                    )

                                    val nearest = distances.minByOrNull { it.first }
                                    // Touch target is active if within 0.15 normalized distance (roughly 45-60dp depending on screen)
                                    if (nearest != null && nearest.first < 0.15f) {
                                        activeDragCorner = nearest.second
                                        currentDragOffset = PointF(touchX, touchY)
                                    }
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val corner = activeDragCorner ?: return@detectDragGestures

                                    // Calculate updated position, clamping boundaries
                                    val nextX = (currentDragOffset.x + dragAmount.x / canvasWidth).coerceIn(0f, 1f)
                                    val nextY = (currentDragOffset.y + dragAmount.y / canvasHeight).coerceIn(0f, 1f)
                                    val nextPoint = PointF(nextX, nextY)

                                    currentDragOffset = nextPoint

                                    when (corner) {
                                        0 -> tl = nextPoint
                                        1 -> tr = nextPoint
                                        2 -> br = nextPoint
                                        3 -> bl = nextPoint
                                    }
                                },
                                onDragEnd = {
                                    activeDragCorner = null
                                }
                            )
                        }
                ) {
                    val pTl = Offset(tl.x * size.width, tl.y * size.height)
                    val pTr = Offset(tr.x * size.width, tr.y * size.height)
                    val pBr = Offset(br.x * size.width, br.y * size.height)
                    val pBl = Offset(bl.x * size.width, bl.y * size.height)

                    // Draw connecting boundary lines
                    val path = Path().apply {
                        moveTo(pTl.x, pTl.y)
                        lineTo(pTr.x, pTr.y)
                        lineTo(pBr.x, pBr.y)
                        lineTo(pBl.x, pBl.y)
                        close()
                    }

                    // Mask the outer area with semi-transparent gray
                    clipPath(path, clipOp = ClipOp.Difference) {
                        drawRect(color = Color.Black.copy(alpha = 0.5f))
                    }

                    // Highlight cropped inside outline
                    drawPath(
                        path = path,
                        color = Color(0xFF00B4D8), // bright custom cyan
                        style = Stroke(width = 3.dp.toPx())
                    )

                    // Draw corner anchor circles
                    val anchorColor = Color(0xFF00B4D8)
                    val anchorInnerColor = Color.White
                    val radius = 10.dp.toPx()

                    listOf(pTl, pTr, pBr, pBl).forEachIndexed { index, p ->
                        val isSelected = activeDragCorner == index
                        drawCircle(
                            color = anchorColor,
                            radius = if (isSelected) radius * 1.4f else radius,
                            center = p
                        )
                        drawCircle(
                            color = anchorInnerColor,
                            radius = if (isSelected) radius * 0.7f else radius * 0.5f,
                            center = p
                        )
                    }
                }

                // Loupe Magnifier overlay displayed above active drag corner
                activeDragCorner?.let { active ->
                    val activePoint = when (active) {
                        0 -> tl
                        1 -> tr
                        2 -> br
                        else -> bl
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp)
                            .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(Color(0xFF00B4D8), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Fine-tuning corner ${active + 1}",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
    return sqrt((x2 - x1).pow(2) + (y2 - y1).pow(2))
}
