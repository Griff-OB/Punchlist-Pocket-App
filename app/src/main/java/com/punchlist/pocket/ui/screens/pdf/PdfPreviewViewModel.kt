package com.punchlist.pocket.ui.screens.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.punchlist.pocket.PunchListApp
import com.punchlist.pocket.data.local.Job
import com.punchlist.pocket.data.repository.AppRepository
import com.punchlist.pocket.utils.FileHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class PdfPreviewUiState(
    val job: Job? = null,
    val pdfPath: String? = null,
    val pageThumbnails: List<Bitmap> = emptyList(),
    val isGenerating: Boolean = true,
    val errorMessage: String? = null
)

class PdfPreviewViewModel(
    private val repository: AppRepository,
    private val jobId: Long
) : ViewModel() {

    private val _uiState = MutableStateFlow(PdfPreviewUiState())
    val uiState: StateFlow<PdfPreviewUiState> = _uiState.asStateFlow()

    /** Application context, set by the screen before [generate] is called. */
    private lateinit var appContext: Context

    init {
        // No work yet: the screen must call [bindContext] then [generate].
    }

    fun bindContext(context: Context) {
        if (!::appContext.isInitialized) {
            appContext = context.applicationContext
            generate()
        }
    }

    /**
     * Builds the multi-page report for this job via [FileHelper], then renders
     * each PDF page to a thumbnail bitmap for on-screen preview.
     */
    fun generate() {
        if (!::appContext.isInitialized) return
        viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true, errorMessage = null) }
            try {
                val job = repository.getJob(jobId)
                if (job == null) {
                    _uiState.update {
                        it.copy(isGenerating = false, errorMessage = "Job not found")
                    }
                    return@launch
                }
                val items = withContext(Dispatchers.IO) {
                    repository.observeItemsByJob(jobId).first()
                }
                val photosByItem = withContext(Dispatchers.IO) {
                    items.associate { it.id to repository.getPhotos(it.id) }
                }

                val path = withContext(Dispatchers.IO) {
                    FileHelper.generateReport(
                        context = appContext,
                        job = job,
                        items = items,
                        photosByItem = photosByItem
                    )
                }
                val thumbnails = withContext(Dispatchers.IO) {
                    renderPdfThumbnails(path)
                }
                _uiState.update {
                    it.copy(
                        job = job,
                        pdfPath = path,
                        pageThumbnails = thumbnails,
                        isGenerating = false
                    )
                }
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(isGenerating = false, errorMessage = t.message ?: "Failed to build PDF")
                }
            }
        }
    }

    /** Builds a shareable content [Uri] for the generated PDF file. */
    fun shareUri(): Uri? {
        if (!::appContext.isInitialized) return null
        val path = _uiState.value.pdfPath ?: return null
        return FileHelper.shareUri(appContext, path)
    }

    /** Renders up to [maxPages] pages of the PDF into preview bitmaps. */
    private fun renderPdfThumbnails(path: String, maxPages: Int = 24): List<Bitmap> {
        val file = File(path)
        if (!file.exists()) return emptyList()
        val output = mutableListOf<Bitmap>()
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        PdfRenderer(pfd).use { renderer ->
            val pageCount = minOf(renderer.pageCount, maxPages)
            for (i in 0 until pageCount) {
                renderer.openPage(i).use { page ->
                    val width = 800
                    val ratio = page.height.toFloat() / page.width.toFloat()
                    val height = (width * ratio).toInt()
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    // PdfRenderer requires a white background; transparent pages
                    // would otherwise render black.
                    val canvas = android.graphics.Canvas(bitmap)
                    canvas.drawColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    output.add(bitmap)
                }
            }
        }
        return output
    }

    companion object {
        fun factory(app: PunchListApp, jobId: Long): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PdfPreviewViewModel(app.repository, jobId) as T
                }
            }
    }
}
