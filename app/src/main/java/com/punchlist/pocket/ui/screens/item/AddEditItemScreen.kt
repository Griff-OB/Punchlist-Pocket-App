package com.punchlist.pocket.ui.screens.item

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.punchlist.pocket.PunchListApp
import com.punchlist.pocket.data.local.Photo
import com.punchlist.pocket.data.local.PunchItem
import com.punchlist.pocket.utils.FileHelper
import com.punchlist.pocket.utils.DateUtils
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditItemScreen(
    jobId: Long,
    itemId: Long,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onMarkup: (Long) -> Unit,
    viewModel: AddEditItemViewModel = viewModel(
        factory = AddEditItemViewModel.factory(
            app = LocalContext.current.applicationContext as PunchListApp,
            jobId = jobId,
            itemId = itemId
        )
    )
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // URI picked from the in-app gallery grid, awaiting the user's
    // confirmation in a preview dialog.
    var pendingGalleryUri by remember { mutableStateOf<Uri?>(null) }

    // Whether the in-app gallery bottom sheet is currently open.
    var showGallerySheet by remember { mutableStateOf(false) }

    // Camera capture launcher. The pending capture Uri lives in the ViewModel's
    // SavedStateHandle (not a local remember{}) so that if the system kills our
    // process while the camera app is open, the recreated instance can still
    // find the target file and actually save the photo on return. Without this,
    // the callback would see a null Uri and silently drop the capture.
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { ok ->
        val uri = viewModel.pendingCaptureUri
        if (ok && uri != null) {
            viewModel.addPhotoFromUri(context, uri) { photoId -> onMarkup(photoId) }
        }
        viewModel.setPendingCaptureUri(null)
    }

    // Permission to read the device photo library so the in-app gallery grid
    // (see GalleryPickerSheet) can show real thumbnails. READ_MEDIA_IMAGES is
    // the API 33+ replacement for READ_EXTERNAL_STORAGE; on older versions we
    // still request the legacy storage permission. This replaces the system
    // PickVisualMedia picker, which silently fell back to a thumbnail-less
    // document chooser on devices without a working Photo Picker app.
    val mediaPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showGallerySheet = true
        } else {
            Toast.makeText(
                context,
                "Photo access is required to browse the gallery.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // Camera permission launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.pendingCaptureUri?.let { uri ->
                try {
                    cameraLauncher.launch(uri)
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(context, "No camera app found.", Toast.LENGTH_SHORT).show()
                } catch (_: Exception) {
                    Toast.makeText(context, "Unable to open camera.", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            // Clear the pending Uri so a later grant doesn't fire a stale capture.
            viewModel.setPendingCaptureUri(null)
            Toast.makeText(
                context,
                "Camera permission is required to take photos.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isExisting) "Edit Item" else "New Item") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.save(onSaved) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Save, contentDescription = "Save")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::onTitleChange,
                label = { Text("Title *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = state.titleError != null,
                supportingText = state.titleError?.let { { Text(it) } }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = state.location,
                    onValueChange = viewModel::onLocationChange,
                    label = { Text("Location") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = state.trade,
                    onValueChange = viewModel::onTradeChange,
                    label = { Text("Trade") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::onDescriptionChange,
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )

            HorizontalDivider()

            Text("Status", style = MaterialTheme.typography.labelLarge)
            ChipRow(
                options = listOf(
                    PunchItem.STATUS_OPEN,
                    PunchItem.STATUS_IN_PROGRESS,
                    PunchItem.STATUS_RESOLVED
                ),
                selected = state.status,
                onSelect = viewModel::onStatusChange,
                pretty = ::pretty
            )

            Text("Priority", style = MaterialTheme.typography.labelLarge)
            ChipRow(
                options = listOf(
                    PunchItem.PRIORITY_LOW,
                    PunchItem.PRIORITY_MEDIUM,
                    PunchItem.PRIORITY_HIGH
                ),
                selected = state.priority,
                onSelect = viewModel::onPriorityChange,
                pretty = ::pretty
            )

            Text("Due date", style = MaterialTheme.typography.labelLarge)
            DueDateRow(
                dueDate = state.dueDate,
                onChange = viewModel::onDueDateChange
            )

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Photos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Camera capture
                    IconButton(onClick = {
                        val granted = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                        // Build the FileProvider Uri the camera will write to and
                        // stash it in the ViewModel BEFORE launching. If the
                        // process is killed while the camera is open, the
                        // SavedStateHandle restores this Uri so the capture still
                        // lands on return.
                        val uri = createTempCaptureUri(context)
                        viewModel.setPendingCaptureUri(uri)
                        if (granted) {
                            try {
                                cameraLauncher.launch(uri)
                            } catch (_: ActivityNotFoundException) {
                                Toast.makeText(
                                    context, "No camera app found.", Toast.LENGTH_SHORT
                                ).show()
                            } catch (_: Exception) {
                                Toast.makeText(
                                    context, "Unable to open camera.", Toast.LENGTH_SHORT
                                ).show()
                            }
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }) {
                        Icon(Icons.Default.AddAPhoto, contentDescription = "Take photo")
                    }
                    // Gallery / file import
                    IconButton(onClick = {
                        val granted = ContextCompat.checkSelfPermission(
                            context, mediaPermission
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            showGallerySheet = true
                        } else {
                            mediaPermissionLauncher.launch(mediaPermission)
                        }
                    }) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Add from gallery")
                    }
                }
            }

            if (state.photos.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No photos. Use the camera or gallery icon to add one.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.photos, key = { it.id }) { photo ->
                        PhotoThumb(
                            photo = photo,
                            onMarkup = { onMarkup(photo.id) },
                            onDelete = { viewModel.deletePhoto(photo) }
                        )
                    }
                }
            }
            Spacer(Modifier.size(24.dp))
        }
    }

    // In-app gallery grid: lets the user actually see device photos as
    // thumbnails and pick one. Replaces the system photo picker.
    if (showGallerySheet) {
        GalleryPickerSheet(
            onPick = { uri ->
                pendingGalleryUri = uri
                showGallerySheet = false
            },
            onDismiss = { showGallerySheet = false }
        )
    }

    // Gallery preview/confirm dialog: show the picked image full-size so the
    // user can verify their selection before it is committed to the item.
    val pendingUri = pendingGalleryUri
    if (pendingUri != null) {
        AlertDialog(
            onDismissRequest = { pendingGalleryUri = null },
            title = { Text("Use this photo?") },
            text = {
                AsyncImage(
                    model = pendingUri,
                    contentDescription = "Selected photo preview",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Fit
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.addPhotoFromUri(context, pendingUri, prefix = "gallery")
                    pendingGalleryUri = null
                }) {
                    Text("Add photo")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingGalleryUri = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ChipRow(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    pretty: (String) -> String
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            AssistChip(
                onClick = { onSelect(option) },
                label = { Text(pretty(option)) },
                colors = if (option == selected) {
                    AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        labelColor = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    AssistChipDefaults.assistChipColors()
                }
            )
        }
    }
}

@Composable
private fun PhotoThumb(
    photo: Photo,
    onMarkup: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.size(width = 160.dp, height = 200.dp)) {
        Box {
            AsyncImage(
                model = File(photo.filePath),
                contentDescription = photo.caption.ifBlank { "Photo" },
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            ) {
                IconButton(
                    onClick = onMarkup,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.45f))
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Markup", tint = Color.White)
                }
                Spacer(Modifier.size(4.dp))
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.45f))
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White)
                }
            }
            if (photo.markedUp) {
                Text(
                    text = "Marked",
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

private fun createTempCaptureUri(context: android.content.Context): Uri {
    FileHelper.ensureDirs(context)
    val dir = File(context.filesDir, "job_images")
    val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
}

private fun pretty(value: String): String =
    value.split("_").joinToString(" ") { word ->
        word.lowercase().replaceFirstChar { it.titlecase() }
    }

/**
 * Due-date selector row. Shows the current due date (or "No due date") with a
 * calendar button that opens a Material3 [DatePicker]. The picker works in
 * UTC-millis for a selected calendar day; we store that value directly since
 * all due-date comparisons elsewhere are day-granularity. A Clear button sets
 * the date back to null.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DueDateRow(
    dueDate: Long?,
    onChange: (Long?) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    val dateText = remember(dueDate) {
        dueDate?.let { DateUtils.formatFullDate(it) } ?: "No due date"
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AssistChip(
            onClick = { showPicker = true },
            label = { Text(dateText) },
            leadingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = null) }
        )
        if (dueDate != null) {
            IconButton(onClick = { onChange(null) }) {
                Icon(Icons.Default.Clear, contentDescription = "Clear due date")
            }
        }
    }

    if (showPicker) {
        // initialSelectedDateMillis is the UTC millis of the currently-chosen
        // day (if any); the picker seeds its UI from it. Any value we pass in
        // is also a local-day-midnight (see onConfirm below), and since the
        // picker treats seeds as UTC midnight it would otherwise render one
        // day behind in timezones west of UTC, so we re-base it to UTC.
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = dueDate?.let {
                DateUtils.localDayMillisToUtcDay(it)
            }
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let {
                            // The picker returns the chosen day at 00:00 UTC.
                            // Convert it to local-midnight so the stored value,
                            // the displayed "Due" label, and every overdue /
                            // due-soon comparison all agree on the same day.
                            onChange(DateUtils.datePickerMillisToLocalDay(it))
                        }
                        showPicker = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
