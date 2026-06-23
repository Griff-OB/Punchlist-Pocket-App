package com.punchlist.pocket.ui.screens.job

import android.Manifest
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.punchlist.pocket.PunchListApp
import com.punchlist.pocket.ui.screens.item.GalleryPickerSheet
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditJobScreen(
    jobId: Long,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: AddEditJobViewModel = viewModel(
        factory = AddEditJobViewModel.factory(
            app = LocalContext.current.applicationContext as PunchListApp,
            jobId = jobId
        )
    )
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var showGallerySheet by remember { mutableStateOf(false) }

    // Permission to read the photo library so the in-app gallery grid can show
    // real thumbnails. READ_MEDIA_IMAGES on API 33+, READ_EXTERNAL_STORAGE below.
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
                "Photo access is required to pick a cover image.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isExisting) "Edit Job" else "New Job") },
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
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = { Text("Job name *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = state.nameError != null,
                supportingText = state.nameError?.let { { Text(it) } }
            )
            OutlinedTextField(
                value = state.client,
                onValueChange = viewModel::onClientChange,
                label = { Text("Client") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = state.address,
                onValueChange = viewModel::onAddressChange,
                label = { Text("Site address") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::onDescriptionChange,
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )

            // Optional cover image row.
            Text("Cover image", style = MaterialTheme.typography.labelLarge)
            CoverImageRow(
                imagePath = state.imagePath,
                onPick = {
                    val granted = ContextCompat.checkSelfPermission(
                        context, mediaPermission
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        showGallerySheet = true
                    } else {
                        mediaPermissionLauncher.launch(mediaPermission)
                    }
                },
                onClear = viewModel::clearImage
            )
        }
    }

    if (showGallerySheet) {
        GalleryPickerSheet(
            onPick = { uri: Uri ->
                viewModel.onImagePicked(context, uri)
                showGallerySheet = false
            },
            onDismiss = { showGallerySheet = false }
        )
    }
}

@Composable
private fun CoverImageRow(
    imagePath: String?,
    onPick: () -> Unit,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 96.dp, height = 72.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (imagePath != null) {
                AsyncImage(
                    model = File(imagePath),
                    contentDescription = "Cover image preview",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    "No image",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            AssistChip(
                onClick = onPick,
                label = {
                    Text(if (imagePath == null) "Choose cover image" else "Replace image")
                },
                leadingIcon = { Icon(Icons.Default.AddPhotoAlternate, contentDescription = null) }
            )
            if (imagePath != null) {
                Spacer(Modifier.size(4.dp))
                AssistChip(
                    onClick = onClear,
                    label = { Text("Remove") },
                    leadingIcon = { Icon(Icons.Default.Clear, contentDescription = null) },
                    colors = AssistChipDefaults.assistChipColors()
                )
            }
        }
    }
}
