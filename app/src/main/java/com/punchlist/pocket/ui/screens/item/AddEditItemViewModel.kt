package com.punchlist.pocket.ui.screens.item

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.punchlist.pocket.PunchListApp
import com.punchlist.pocket.data.local.Photo
import com.punchlist.pocket.data.local.PunchItem
import com.punchlist.pocket.data.repository.AppRepository
import com.punchlist.pocket.utils.FileHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AddEditItemUiState(
    val title: String = "",
    val description: String = "",
    val location: String = "",
    val trade: String = "",
    val status: String = PunchItem.STATUS_OPEN,
    val priority: String = PunchItem.PRIORITY_MEDIUM,
    val dueDate: Long? = null,
    val isExisting: Boolean = false,
    val isSaving: Boolean = false,
    val photos: List<Photo> = emptyList(),
    val titleError: String? = null
)

/**
 * Keys persisted in the [SavedStateHandle]. Because the camera capture flow
 * launches an external app (which the system may use to kill our process to
 * reclaim memory), everything the user has typed must survive process death.
 * The SavedStateHandle is part of the navigation back-stack entry's saved
 * state, so it is automatically restored on return from the camera.
 */
private object Keys {
    const val TITLE = "title"
    const val DESCRIPTION = "description"
    const val LOCATION = "location"
    const val TRADE = "trade"
    const val STATUS = "status"
    const val PRIORITY = "priority"
    const val DUE_DATE = "dueDate"
    // The real row id of the punch item once it has been persisted to the DB.
    // Surviving this across process death is what lets a second photo attach to
    // the right row after the camera round-trip resets the in-memory id.
    const val PERSISTED_ID = "persistedItemId"
    // Uri of the in-flight camera capture, so the result callback can still
    // find the target file after the process is recreated.
    const val PENDING_CAPTURE_URI = "pendingCaptureUri"
}

class AddEditItemViewModel(
    private val repository: AppRepository,
    private val jobId: Long,
    private val itemId: Long,
    private val savedState: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(buildInitialState())
    val uiState: StateFlow<AddEditItemUiState> = _uiState.asStateFlow()

    /** The Uri the camera should write to (and our callback reads from). */
    val pendingCaptureUri: Uri?
        get() = savedState.get<String>(Keys.PENDING_CAPTURE_URI)?.let { runCatching { Uri.parse(it) }.getOrNull() }

    init {
        if (itemId > 0L) loadItem()
    }

    /**
     * Builds the initial UI state from whatever survived in the SavedStateHandle.
     * On a fresh launch all keys are absent, so we get the default empty form.
     * On a return-from-camera process recreation, the user's typed values and
     * the persisted item id are all restored.
     */
    private fun buildInitialState(): AddEditItemUiState {
        // The "is existing" flag is true if either the caller passed a real
        // itemId, or a previous incarnation of this ViewModel already created
        // the draft row (persisted id recorded in saved state).
        val persistedId = savedState.get<Long>(Keys.PERSISTED_ID) ?: -1L
        val existing = itemId > 0L || persistedId > 0L
        return AddEditItemUiState(
            title = savedState[Keys.TITLE] ?: "",
            description = savedState[Keys.DESCRIPTION] ?: "",
            location = savedState[Keys.LOCATION] ?: "",
            trade = savedState[Keys.TRADE] ?: "",
            status = savedState[Keys.STATUS] ?: PunchItem.STATUS_OPEN,
            priority = savedState[Keys.PRIORITY] ?: PunchItem.PRIORITY_MEDIUM,
            dueDate = savedState.get<Long>(Keys.DUE_DATE),
            isExisting = existing
        )
    }

    private fun loadItem() {
        viewModelScope.launch {
            repository.getItem(itemId)?.let { item ->
                _uiState.value = AddEditItemUiState(
                    title = item.title,
                    description = item.description,
                    location = item.location,
                    trade = item.trade,
                    status = item.status,
                    priority = item.priority,
                    dueDate = item.dueDate,
                    isExisting = true,
                    photos = repository.getPhotos(itemId)
                )
                // Seed saved state so a subsequent process death still restores.
                savedState[Keys.TITLE] = item.title
                savedState[Keys.DESCRIPTION] = item.description
                savedState[Keys.LOCATION] = item.location
                savedState[Keys.TRADE] = item.trade
                savedState[Keys.STATUS] = item.status
                savedState[Keys.PRIORITY] = item.priority
                savedState[Keys.DUE_DATE] = item.dueDate
            }
        }
    }

    fun onTitleChange(v: String) {
        savedState[Keys.TITLE] = v
        _uiState.update {
            it.copy(title = v, titleError = if (v.isBlank()) "Title is required" else null)
        }
    }

    fun onDescriptionChange(v: String) {
        savedState[Keys.DESCRIPTION] = v
        _uiState.update { it.copy(description = v) }
    }

    fun onLocationChange(v: String) {
        savedState[Keys.LOCATION] = v
        _uiState.update { it.copy(location = v) }
    }

    fun onTradeChange(v: String) {
        savedState[Keys.TRADE] = v
        _uiState.update { it.copy(trade = v) }
    }

    fun onStatusChange(v: String) {
        savedState[Keys.STATUS] = v
        _uiState.update { it.copy(status = v) }
    }

    fun onPriorityChange(v: String) {
        savedState[Keys.PRIORITY] = v
        _uiState.update { it.copy(priority = v) }
    }

    fun onDueDateChange(v: Long?) {
        savedState[Keys.DUE_DATE] = v
        _uiState.update { it.copy(dueDate = v) }
    }

    /**
     * Records the [uri] the camera app will write the captured image to. Must
     * be called *before* launching the camera so that, even if the system kills
     * our process while the camera is open, the result callback can still find
     * the right file when we're recreated.
     *
     * NOTE: [SavedStateHandle.remove] is generic (`fun <T> remove(key): T?`),
     * so the call must be typed explicitly as `<String?>`. In an if/else
     * *expression* whose other branch is an assignment (Unit), Kotlin would
     * otherwise unify the branches to Unit and infer `remove<Unit>(key)`, which
     * then does `state[key] as Unit` at runtime and throws
     * `ClassCastException: String cannot be cast to Unit`. The early return +
     * explicit type parameter below sidesteps that entirely.
     */
    fun setPendingCaptureUri(uri: Uri?) {
        if (uri == null) {
            savedState.remove<String?>(Keys.PENDING_CAPTURE_URI)
            return
        }
        savedState[Keys.PENDING_CAPTURE_URI] = uri.toString()
    }

    /**
     * Persists the photo referenced by [uri] into the app's private image
     * directory and inserts a [Photo] row for the current item (creating the
     * item first if it doesn't yet exist). Returns the new photo id via
     * [onMarkup] if the caller wants to immediately launch markup editing.
     */
    fun addPhotoFromUri(
        context: Context,
        uri: Uri,
        onMarkup: ((Long) -> Unit)? = null
    ) = addPhotoFromUri(context, uri, prefix = "cam", onMarkup = onMarkup)

    /**
     * Imports the photo referenced by [uri] into the app's private image
     * directory and inserts a [Photo] row for the current item (creating the
     * item first if it doesn't yet exist). Returns the new photo id via
     * [onMarkup] if the caller wants to immediately launch markup editing.
     *
     * [prefix] controls the saved filename so the source (camera vs. gallery)
     * stays identifiable on disk.
     */
    fun addPhotoFromUri(
        context: Context,
        uri: Uri,
        prefix: String,
        onMarkup: ((Long) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val currentItemId = ensureItemPersisted()
            val path = withContext(Dispatchers.IO) {
                val bitmap = decodeUri(context, uri)
                    ?: return@withContext null
                // Private copy the item row references.
                val saved = FileHelper.saveImageBitmap(context, bitmap, prefix = prefix)
                // Also publish to MediaStore so the image is visible in the
                // device gallery and re-selectable from the in-app gallery grid
                // (GalleryPickerSheet queries MediaStore). Gallery imports
                // (prefix "gallery") already live in MediaStore, so we only
                // republish freshly-captured camera photos.
                if (prefix == "cam") {
                    FileHelper.publishToMediaStore(context, bitmap, prefix = prefix)
                }
                saved
            } ?: return@launch
            val photo = Photo(punchItemId = currentItemId, filePath = path)
            val photoId = repository.insertPhoto(photo)
            _uiState.update { state ->
                state.copy(photos = state.photos + photo.copy(id = photoId))
            }
            onMarkup?.invoke(photoId)
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

    /**
     * The row id of the punch item backing this screen. For an existing item it
     * is the id passed in via the constructor; for a brand-new item it starts
     * negative (the placeholder id) until the item is first persisted, after
     * which it is updated in place to the newly assigned id. Persisted in the
     * SavedStateHandle so it survives process death.
     */
    private val isItemPersisted: Boolean
        get() = _uiState.value.isExisting || itemId > 0L

    /** Ensures the punch item has been written to the database. Returns its id. */
    private suspend fun ensureItemPersisted(): Long {
        // Already persisted (existing item, or a new item we created earlier)?
        if (isItemPersisted) return persistedItemId

        val now = System.currentTimeMillis()
        val id = repository.insertItem(
            PunchItem(
                jobId = jobId,
                title = _uiState.value.title.ifBlank { "(Draft)" },
                description = _uiState.value.description,
                location = _uiState.value.location,
                trade = _uiState.value.trade,
                status = _uiState.value.status,
                priority = _uiState.value.priority,
                dueDate = _uiState.value.dueDate,
                createdAt = now,
                updatedAt = now
            )
        )
        // Record the real id so every subsequent photo / save targets the
        // newly created row instead of the original (still -1) constructor id.
        savedState[Keys.PERSISTED_ID] = id
        _uiState.update { it.copy(isExisting = true) }
        return id
    }

    /** The persisted row id, reading through saved state so it survives death. */
    private val persistedItemId: Long
        get() = savedState.get<Long>(Keys.PERSISTED_ID)
            ?.takeIf { it > 0L }
            ?: itemId

    fun deletePhoto(photo: Photo) {
        viewModelScope.launch {
            FileHelper.deleteFile(photo.filePath)
            repository.deletePhoto(photo)
            _uiState.update { state ->
                state.copy(photos = state.photos.filterNot { it.id == photo.id })
            }
        }
    }

    fun save(onComplete: () -> Unit) {
        val current = _uiState.value
        if (current.title.isBlank()) {
            _uiState.update { it.copy(titleError = "Title is required") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val now = System.currentTimeMillis()
            val effectiveId = ensureItemPersisted()
            repository.getItem(effectiveId)?.let { existing ->
                repository.updateItem(
                    existing.copy(
                        title = current.title,
                        description = current.description,
                        location = current.location,
                        trade = current.trade,
                        status = current.status,
                        priority = current.priority,
                        dueDate = current.dueDate,
                        updatedAt = now
                    )
                )
            }
            _uiState.update { it.copy(isSaving = false) }
            onComplete()
        }
    }

    companion object {
        fun factory(app: PunchListApp, jobId: Long, itemId: Long): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val savedState = createSavedStateHandle()
                    AddEditItemViewModel(app.repository, jobId, itemId, savedState)
                }
            }
    }
}
