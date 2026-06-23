package com.punchlist.pocket.ui.screens.markup

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.punchlist.pocket.PunchListApp
import com.punchlist.pocket.data.local.Photo
import com.punchlist.pocket.data.repository.AppRepository
import com.punchlist.pocket.utils.FileHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Tracks one continuous polyline drawn by the user. Coordinates are stored in
 * the surface (canvas) coordinate space they were captured in; the [save] call
 * receives the surface and source-bitmap dimensions so it can map them back
 * onto the underlying image pixels.
 */
data class Stroke(
    val points: List<Pair<Float, Float>>,
    val color: Int,
    val width: Float
)

/** Carries the geometry needed to map surface coordinates back onto the bitmap. */
data class FitTransform(
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float
) {
    /** Converts a surface-space point into bitmap-space pixel coordinates. */
    fun toBitmap(x: Float, y: Float): Pair<Float, Float> {
        val bx = (x - offsetX) / scale
        val by = (y - offsetY) / scale
        return bx to by
    }

    companion object {
        fun fit(
            bitmapWidth: Float,
            bitmapHeight: Float,
            surfaceWidth: Float,
            surfaceHeight: Float
        ): FitTransform {
            if (bitmapWidth <= 0f || bitmapHeight <= 0f) {
                return FitTransform(1f, 0f, 0f)
            }
            val scale = minOf(surfaceWidth / bitmapWidth, surfaceHeight / bitmapHeight)
                .coerceAtLeast(0f)
            val offsetX = (surfaceWidth - bitmapWidth * scale) / 2f
            val offsetY = (surfaceHeight - bitmapHeight * scale) / 2f
            return FitTransform(scale, offsetX, offsetY)
        }
    }
}

data class MarkupUiState(
    val photo: Photo? = null,
    val sourceBitmap: Bitmap? = null,
    val strokes: List<Stroke> = emptyList(),
    val currentStroke: Stroke? = null,
    val brushColor: Int = MARKUP_RED,
    val brushWidth: Float = 8f,
    val isSaving: Boolean = false
) {
    companion object {
        const val MARKUP_RED = 0xFFE02424.toInt()
        const val MARKUP_YELLOW = 0xFFFFB020.toInt()
        const val MARKUP_GREEN = 0xFF1A7F37.toInt()
        const val MARKUP_BLACK = 0xFF000000.toInt()
    }
}

class MarkupViewModel(
    private val repository: AppRepository,
    private val photoId: Long
) : ViewModel() {

    private val _uiState = MutableStateFlow(MarkupUiState())
    val uiState: StateFlow<MarkupUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val photo = repository.getPhoto(photoId) ?: return@launch
            val bitmap = withContext(Dispatchers.IO) {
                FileHelper.loadBitmap(photo.filePath)
            }
            _uiState.value = _uiState.value.copy(photo = photo, sourceBitmap = bitmap)
        }
    }

    fun setColor(color: Int) = _uiState.update { it.copy(brushColor = color) }
    fun setBrushWidth(width: Float) = _uiState.update { it.copy(brushWidth = width) }

    fun beginStroke(x: Float, y: Float) {
        val current = _uiState.value
        val stroke = Stroke(
            points = listOf(x to y),
            color = current.brushColor,
            width = current.brushWidth
        )
        _uiState.value = current.copy(currentStroke = stroke)
    }

    fun extendStroke(x: Float, y: Float) {
        val current = _uiState.value
        val active = current.currentStroke ?: return
        _uiState.value = current.copy(
            currentStroke = active.copy(points = active.points + (x to y))
        )
    }

    fun endStroke() {
        val current = _uiState.value
        val active = current.currentStroke ?: return
        _uiState.value = current.copy(
            strokes = current.strokes + active,
            currentStroke = null
        )
    }

    fun undo() {
        _uiState.update {
            it.copy(strokes = it.strokes.dropLast(1), currentStroke = null)
        }
    }

    fun clear() {
        _uiState.update { it.copy(strokes = emptyList(), currentStroke = null) }
    }

    /**
     * Composes the source bitmap with all current strokes and writes it back
     * to the photo's storage location, replacing the original image. The
     * [Photo] row is marked `markedUp = true`.
     *
     * @param surfaceWidth  width (px) of the drawing surface the strokes were
     *                      captured on.
     * @param surfaceHeight height (px) of the drawing surface.
     */
    fun save(
        context: Context,
        surfaceWidth: Float,
        surfaceHeight: Float,
        onComplete: () -> Unit
    ) {
        val state = _uiState.value
        val source = state.sourceBitmap ?: return
        val photo = state.photo ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val transform = FitTransform.fit(
                bitmapWidth = source.width.toFloat(),
                bitmapHeight = source.height.toFloat(),
                surfaceWidth = surfaceWidth,
                surfaceHeight = surfaceHeight
            )
            // Scale brush width back to bitmap pixel units so markup looks the
            // same on the exported image as it did on screen.
            val bitmapStrokes = state.strokes.map { stroke ->
                Stroke(
                    points = stroke.points.map { (x, y) -> transform.toBitmap(x, y) },
                    color = stroke.color,
                    width = if (transform.scale > 0f) stroke.width / transform.scale else stroke.width
                )
            }
            val composed = withContext(Dispatchers.IO) {
                composeBitmap(source, bitmapStrokes)
            }
            val newPath = withContext(Dispatchers.IO) {
                FileHelper.saveMarkupBitmap(context, composed)
            }
            // Replace the underlying file and update the photo row.
            FileHelper.deleteFile(photo.filePath)
            repository.updatePhoto(
                photo.copy(filePath = newPath, markedUp = true)
            )
            _uiState.update { it.copy(isSaving = false) }
            onComplete()
        }
    }

    /** Draws every stroke on top of [source] using a mutable copy. */
    private fun composeBitmap(source: Bitmap, strokes: List<Stroke>): Bitmap {
        val mutable = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = android.graphics.Canvas(mutable)
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            style = android.graphics.Paint.Style.STROKE
            strokeJoin = android.graphics.Paint.Join.ROUND
            strokeCap = android.graphics.Paint.Cap.ROUND
        }
        strokes.forEach { stroke ->
            if (stroke.points.size < 2) return@forEach
            paint.color = stroke.color
            paint.strokeWidth = stroke.width
            val path = android.graphics.Path()
            path.moveTo(stroke.points[0].first, stroke.points[0].second)
            for (i in 1 until stroke.points.size) {
                path.lineTo(stroke.points[i].first, stroke.points[i].second)
            }
            canvas.drawPath(path, paint)
        }
        return mutable
    }

    companion object {
        fun factory(app: PunchListApp, photoId: Long): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MarkupViewModel(app.repository, photoId) as T
                }
            }
    }
}
