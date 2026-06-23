package com.punchlist.pocket.ui.screens.job

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AddEditJobUiState(
    val name: String = "",
    val client: String = "",
    val address: String = "",
    val description: String = "",
    val imagePath: String? = null,
    val isExisting: Boolean = false,
    val isSaving: Boolean = false,
    val nameError: String? = null
)

class AddEditJobViewModel(
    private val repository: AppRepository,
    private val jobId: Long
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddEditJobUiState())
    val uiState: StateFlow<AddEditJobUiState> = _uiState.asStateFlow()

    init {
        if (jobId > 0L) loadJob()
    }

    private fun loadJob() {
        viewModelScope.launch {
            repository.getJob(jobId)?.let { job ->
                _uiState.value = AddEditJobUiState(
                    name = job.name,
                    client = job.client,
                    address = job.address,
                    description = job.description,
                    imagePath = job.imagePath,
                    isExisting = true
                )
            }
        }
    }

    fun onNameChange(value: String) {
        _uiState.update {
            it.copy(name = value, nameError = if (value.isBlank()) "Name is required" else null)
        }
    }

    fun onClientChange(value: String) = _uiState.update { it.copy(client = value) }
    fun onAddressChange(value: String) = _uiState.update { it.copy(address = value) }
    fun onDescriptionChange(value: String) = _uiState.update { it.copy(description = value) }

    /** Clears the optional cover image (back to the letter-avatar fallback). */
    fun clearImage() = _uiState.update { it.copy(imagePath = null) }

    /**
     * Imports the picked cover image into app-private storage and stores its
     * path. Mirrors how punch-item photos are persisted via [FileHelper].
     */
    fun onImagePicked(context: Context, uri: Uri) {
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) {
                val bitmap = decodeUri(context, uri) ?: return@withContext null
                FileHelper.saveImageBitmap(context, bitmap, prefix = "job_cover")
            } ?: return@launch
            _uiState.update { it.copy(imagePath = path) }
        }
    }

    /** Decodes an arbitrary content [uri] into a bitmap on the IO dispatcher. */
    private suspend fun decodeUri(context: Context, uri: Uri): Bitmap? =
        withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    BitmapFactory.decodeStream(input)
                }
            }.getOrNull()
        }

    fun save(onComplete: () -> Unit) {
        val current = _uiState.value
        if (current.name.isBlank()) {
            _uiState.update { it.copy(nameError = "Name is required") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val now = System.currentTimeMillis()
            if (current.isExisting) {
                repository.getJob(jobId)?.let { existing ->
                    repository.updateJob(
                        existing.copy(
                            name = current.name,
                            client = current.client,
                            address = current.address,
                            description = current.description,
                            imagePath = current.imagePath,
                            updatedAt = now
                        )
                    )
                }
            } else {
                repository.insertJob(
                    Job(
                        name = current.name,
                        client = current.client,
                        address = current.address,
                        description = current.description,
                        imagePath = current.imagePath,
                        createdAt = now,
                        updatedAt = now
                    )
                )
            }
            _uiState.update { it.copy(isSaving = false) }
            onComplete()
        }
    }

    companion object {
        fun factory(app: PunchListApp, jobId: Long): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AddEditJobViewModel(app.repository, jobId) as T
                }
            }
    }
}
