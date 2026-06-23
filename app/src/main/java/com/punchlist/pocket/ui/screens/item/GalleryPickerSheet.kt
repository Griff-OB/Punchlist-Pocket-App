package com.punchlist.pocket.ui.screens.item

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A single image surfaced by the in-app gallery browser, loaded from the
 * device's [MediaStore]. The [uri] is a stable content Uri backed by the
 * MediaStore row id, so it stays valid for the lifetime of the process.
 */
data class GalleryImage(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val dateAdded: Long
)

/**
 * Outcome of a single gallery load attempt. [Error] carries the message so
 * the UI can surface the real reason instead of silently reporting "no
 * photos" when the query itself blew up.
 */
sealed interface GalleryLoadResult {
    data class Success(val images: List<GalleryImage>) : GalleryLoadResult
    data object Empty : GalleryLoadResult
    data class Error(val message: String) : GalleryLoadResult
}

/**
 * Queries the device photo library via [MediaStore] and returns the most
 * recently added images (newest first). Runs on the IO dispatcher.
 *
 * Caps the result count by stopping the cursor walk at [limit] rather than
 * relying on a `LIMIT` SQL clause in the sort order — appending `LIMIT n`
 * to MediaStore's sortOrder is unsupported on some Android versions and
 * throws, which is what previously produced an empty grid.
 *
 * Queries both the external and internal volumes and merges the results so
 * photos are surfaced regardless of which volume they live on.
 */
suspend fun loadGalleryImages(context: Context, limit: Int = 300): GalleryLoadResult =
    withContext(Dispatchers.IO) {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        val external = queryVolume(
            context,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            sortOrder,
            limit
        )
        // Only query the internal volume if external came up short; saves a
        // second cursor walk on the common path where external is non-empty.
        val merged = if (external is GalleryLoadResult.Success && external.images.size >= limit) {
            external.images
        } else {
            val internal = queryVolume(
                context,
                MediaStore.Images.Media.INTERNAL_CONTENT_URI,
                projection,
                sortOrder,
                limit
            )
            val ext = (external as? GalleryLoadResult.Success)?.images.orEmpty()
            val int = (internal as? GalleryLoadResult.Success)?.images.orEmpty()
            // Prefer the error message if external blew up; otherwise merge.
            if (external is GalleryLoadResult.Error) return@withContext external
            if (external !is GalleryLoadResult.Success && internal is GalleryLoadResult.Error) {
                return@withContext internal
            }
            (ext + int)
                .sortedByDescending { it.dateAdded }
                .take(limit)
        }

        when {
            merged.isEmpty() -> GalleryLoadResult.Empty
            else -> GalleryLoadResult.Success(merged)
        }
    }

/**
 * Queries a single MediaStore [collection] volume. Caps at [limit] by walking
 * the cursor rather than using an unsupported `LIMIT` clause. Returns [Empty]
 * if the query yields no rows, or [Error] if it throws.
 */
private fun queryVolume(
    context: Context,
    collection: Uri,
    projection: Array<String>,
    sortOrder: String,
    limit: Int
): GalleryLoadResult {
    val results = ArrayList<GalleryImage>(limit.coerceAtMost(64))
    return try {
        context.contentResolver.query(collection, projection, null, null, sortOrder)
            ?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                while (cursor.moveToNext() && results.size < limit) {
                    val id = cursor.getLong(idCol)
                    results += GalleryImage(
                        id = id,
                        uri = ContentUris.withAppendedId(collection, id),
                        displayName = cursor.getString(nameCol) ?: "",
                        dateAdded = cursor.getLong(dateCol)
                    )
                }
            }
        when {
            results.isEmpty() -> GalleryLoadResult.Empty
            else -> GalleryLoadResult.Success(results)
        }
    } catch (e: Exception) {
        GalleryLoadResult.Error(e.message ?: e.javaClass.simpleName)
    }
}

/**
 * A bottom sheet that lists the device's photos as a thumbnail grid the user
 * can actually see and pick from. Replaces the system photo picker, which
 * silently falls back to a thumbnail-less document chooser on devices that
 * don't ship a working Photo Picker app.
 *
 * Tapping a thumbnail invokes [onPick] with the chosen image's content Uri;
 * dismissing the sheet invokes [onDismiss].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryPickerSheet(
    onPick: (Uri) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var loading by remember { mutableStateOf(true) }
    var result by remember { mutableStateOf<GalleryLoadResult>(GalleryLoadResult.Empty) }

    // Load once when the sheet appears.
    LaunchedEffect(Unit) {
        result = loadGalleryImages(context)
        loading = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Select photo",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            // Bound the grid height so the sheet doesn't try to grow to the full
            // gallery; the grid scrolls internally.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .padding(horizontal = 16.dp)
            ) {
                val images = (result as? GalleryLoadResult.Success)?.images.orEmpty()
                when {
                    loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 64.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(36.dp))
                        }
                    }
                    result is GalleryLoadResult.Error -> {
                        val msg = (result as GalleryLoadResult.Error).message
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Couldn't load photos: $msg",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    images.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 64.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No photos found on this device.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(images, key = { it.id }) { image ->
                                AsyncImage(
                                    model = image.uri,
                                    contentDescription = image.displayName.ifBlank { "Photo" },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { onPick(image.uri) },
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                }
            }
            Box(modifier = Modifier.size(16.dp))
        }
    }
}
