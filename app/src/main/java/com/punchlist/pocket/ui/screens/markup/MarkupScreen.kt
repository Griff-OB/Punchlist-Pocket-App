package com.punchlist.pocket.ui.screens.markup

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.punchlist.pocket.PunchListApp
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkupScreen(
    photoId: Long,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: MarkupViewModel = viewModel(
        factory = MarkupViewModel.factory(
            app = LocalContext.current.applicationContext as PunchListApp,
            photoId = photoId
        )
    )
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Most recently measured drawing surface, in pixels. Captured so the save
    // call can map strokes back onto the source bitmap.
    var surfaceSize by remember { androidx.compose.runtime.mutableStateOf(Size.Zero) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Markup") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::undo, enabled = state.strokes.isNotEmpty()) {
                        Icon(Icons.Default.Undo, contentDescription = "Undo")
                    }
                    IconButton(onClick = viewModel::clear, enabled = state.strokes.isNotEmpty()) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (surfaceSize != Size.Zero) {
                        viewModel.save(
                            context = context,
                            surfaceWidth = surfaceSize.width,
                            surfaceHeight = surfaceSize.height,
                            onComplete = onSaved
                        )
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Save, contentDescription = "Save markup")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Canvas surface
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
                val heightPx = with(LocalDensity.current) { maxHeight.toPx() }
                surfaceSize = Size(widthPx, heightPx)

                val bitmap = state.sourceBitmap
                if (bitmap != null) {
                    DrawingSurface(
                        bitmap = bitmap,
                        state = state,
                        onBegin = viewModel::beginStroke,
                        onExtend = viewModel::extendStroke,
                        onEnd = viewModel::endStroke,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            Spacer(Modifier.size(4.dp))

            // Color palette
            Text("Brush color", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(
                    MarkupUiState.MARKUP_RED,
                    MarkupUiState.MARKUP_YELLOW,
                    MarkupUiState.MARKUP_GREEN,
                    MarkupUiState.MARKUP_BLACK
                ).forEach { color ->
                    val selected = color == state.brushColor
                    ColorSwatch(
                        color = color,
                        selected = selected,
                        onTap = { viewModel.setColor(color) }
                    )
                }
            }

            // Brush width
            Text("Brush width", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
            Slider(
                value = state.brushWidth,
                onValueChange = viewModel::setBrushWidth,
                valueRange = 2f..32f
            )
        }
    }
}

@Composable
private fun ColorSwatch(color: Int, selected: Boolean, onTap: () -> Unit) {
    Box(
        modifier = Modifier
            .size(if (selected) 40.dp else 32.dp)
            .background(Color(color), CircleShape)
            .pointerInput(color) {
                detectDragGestures(
                    onDragStart = { onTap() }
                ) { _, _ -> }
            }
    )
}

@Composable
private fun DrawingSurface(
    bitmap: Bitmap,
    state: MarkupUiState,
    onBegin: (Float, Float) -> Unit,
    onExtend: (Float, Float) -> Unit,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        // Base image, fit to the surface
        AsyncImage(
            model = remember(state.photo?.filePath) {
                state.photo?.filePath?.let { File(it) }
            },
            contentDescription = "Source photo",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )

        // Overlay stroke canvas. Strokes are stored in raw surface coordinates
        // so they can be plotted here directly.
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            onBegin(offset.x, offset.y)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            onExtend(change.position.x, change.position.y)
                        },
                        onDragEnd = { onEnd() },
                        onDragCancel = { onEnd() }
                    )
                }
        ) {
            val all = (state.strokes + listOfNotNull(state.currentStroke))
            all.forEach { stroke ->
                if (stroke.points.size < 2) return@forEach
                val path = Path()
                val first = stroke.points.first()
                path.moveTo(first.first, first.second)
                for (i in 1 until stroke.points.size) {
                    val (x, y) = stroke.points[i]
                    path.lineTo(x, y)
                }
                drawPath(
                    path = path,
                    color = Color(stroke.color),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = stroke.width,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                        pathEffect = PathEffect.cornerPathEffect(stroke.width)
                    )
                )
            }
        }
    }
}
