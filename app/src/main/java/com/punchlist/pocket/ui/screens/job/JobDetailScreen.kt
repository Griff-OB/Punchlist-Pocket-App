package com.punchlist.pocket.ui.screens.job

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.punchlist.pocket.PunchListApp
import com.punchlist.pocket.data.local.PunchItem
import com.punchlist.pocket.ui.theme.LocalStatusPalette
import com.punchlist.pocket.utils.DueDateStatus
import com.punchlist.pocket.utils.DateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobDetailScreen(
    jobId: Long,
    onBack: () -> Unit,
    onEditJob: () -> Unit,
    onAddItem: () -> Unit,
    onEditItem: (PunchItem) -> Unit,
    onPdf: () -> Unit,
    onApplyTemplate: () -> Unit,
    viewModel: JobDetailViewModel = viewModel(
        factory = JobDetailViewModel.factory(
            app = LocalContext.current.applicationContext as PunchListApp,
            jobId = jobId
        )
    )
) {
    val state by viewModel.uiState.collectAsState()
    val filter by viewModel.filter.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.job?.name ?: "Job") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onApplyTemplate) {
                        Icon(Icons.Default.PlaylistAddCheck, contentDescription = "Apply template")
                    }
                    IconButton(onClick = onEditJob) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit job")
                    }
                    IconButton(onClick = onPdf) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "PDF report")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddItem) {
                Icon(Icons.Default.Add, contentDescription = "Add item")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {

            state.job?.let { job ->
                if (job.client.isNotBlank() || job.address.isNotBlank()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (job.client.isNotBlank()) {
                            Text(
                                "Client: ${job.client}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (job.address.isNotBlank()) {
                            Text(
                                "Site: ${job.address}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            FilterChips(
                selected = filter,
                onSelect = viewModel::setFilter,
                total = state.total
            )

            SortRow(selected = state.sort, onSelect = viewModel::setSort)

            if (state.availableLocations.isNotEmpty()) {
                LocationChips(
                    locations = state.availableLocations,
                    selected = state.selectedLocation,
                    onSelect = viewModel::setSelectedLocation
                )
            }

            HorizontalDivider()

            if (state.items.isEmpty()) {
                EmptyItemsState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.items, key = { it.item.id }) { enriched ->
                        PunchItemCard(
                            enriched = enriched,
                            onClick = { onEditItem(enriched.item) },
                            onCycleStatus = { viewModel.cycleStatus(enriched.item) },
                            onDelete = { viewModel.deleteItem(enriched.item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChips(
    selected: ItemFilter,
    onSelect: (ItemFilter) -> Unit,
    total: Int
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val chips = ItemFilter.entries.map { f ->
            f to when (f) {
                ItemFilter.ALL -> "All ($total)"
                ItemFilter.OPEN -> "Open"
                ItemFilter.IN_PROGRESS -> "In Progress"
                ItemFilter.RESOLVED -> "Completed"
                ItemFilter.OVERDUE -> "Overdue"
            }
        }
        items(chips) { (f, label) ->
            FilterChip(
                selected = f == selected,
                onClick = { onSelect(f) },
                label = { Text(label) }
            )
        }
    }
}

@Composable
private fun SortRow(
    selected: ItemSort,
    onSelect: (ItemSort) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Sort",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(end = 8.dp)
        )
        Box {
            TextButton(onClick = { expanded = true }) {
                Icon(Icons.Default.Sort, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text(selected.label)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                ItemSort.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun LocationChips(
    locations: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text("All rooms") }
            )
        }
        items(locations) { location ->
            FilterChip(
                selected = selected == location,
                onClick = { onSelect(location) },
                label = { Text(location) }
            )
        }
    }
}

@Composable
private fun PunchItemCard(
    enriched: PunchItemWithPhotos,
    onClick: () -> Unit,
    onCycleStatus: () -> Unit,
    onDelete: () -> Unit
) {
    val item = enriched.item
    // Read status accents through the theme-aware palette so dark mode picks
    // up the brighter, higher-luminance variants instead of the muted light-mode
    // constants.
    val palette = LocalStatusPalette.current
    val statusColor = when (item.status) {
        PunchItem.STATUS_OPEN -> palette.open
        PunchItem.STATUS_IN_PROGRESS -> palette.inProgress
        PunchItem.STATUS_RESOLVED -> palette.resolved
        else -> palette.open
    }
    val dueStatus = remember(item.dueDate, item.status) {
        DueDateStatus.from(item.dueDate, isResolved = item.status == PunchItem.STATUS_RESOLVED)
    }
    val dueColor = when (dueStatus) {
        DueDateStatus.Overdue -> palette.open
        DueDateStatus.DueToday -> palette.inProgress
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .background(statusColor, CircleShape)
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    text = item.title.ifBlank { "Untitled Item" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onCycleStatus) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Cycle status",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete item",
                        tint = palette.open
                    )
                }
            }
            if (item.description.isNotBlank()) {
                Spacer(Modifier.size(4.dp))
                Text(
                    item.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    // Preview only: cap at two lines so a long description
                    // doesn't dominate the card. The full text is visible on
                    // the edit screen when the user taps the card.
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // Due-date line + badge.
            if (dueStatus != DueDateStatus.None) {
                Spacer(Modifier.size(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = dueColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        text = DueDateStatus.cardLine(item.dueDate),
                        style = MaterialTheme.typography.bodySmall,
                        color = dueColor,
                        fontWeight = if (dueStatus == DueDateStatus.Overdue ||
                            dueStatus == DueDateStatus.DueToday
                        ) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
            Spacer(Modifier.size(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Pill(text = DateUtils.pretty(item.status))
                if (item.trade.isNotBlank()) Pill(text = item.trade)
                Pill(text = DateUtils.pretty(item.priority))
                if (item.location.isNotBlank()) Pill(text = item.location)
                if (enriched.photos.isNotEmpty()) {
                    Pill(text = "${enriched.photos.size} photo${if (enriched.photos.size > 1) "s" else ""}")
                }
            }
        }
    }
}

@Composable
private fun Pill(text: String) {
    Box(
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(50)
            )
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyItemsState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "No punch items match these filters.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
