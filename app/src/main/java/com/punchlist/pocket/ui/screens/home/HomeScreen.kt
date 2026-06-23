package com.punchlist.pocket.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.punchlist.pocket.PunchListApp
import com.punchlist.pocket.data.local.Job
import com.punchlist.pocket.data.settings.ThemeMode
import com.punchlist.pocket.ui.theme.LocalStatusPalette
import com.punchlist.pocket.utils.DateUtils
import kotlinx.coroutines.launch
import java.io.File

/**
 * The Home dashboard. Hosted in a [ModalNavigationDrawer] whose left rail holds
 * appearance settings (Light/Dark/System), a Templates shortcut, and app info.
 * The body is the redesigned overview: a search bar, four stat cards, filter
 * chips, and the "Your Jobs" list of rich job cards.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onJobClick: (Job) -> Unit,
    onJobPdf: (Job) -> Unit,
    onAddJob: () -> Unit,
    onOpenTemplates: () -> Unit,
    viewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.factory(
            app = LocalContext.current.applicationContext as PunchListApp
        )
    )
) {
    val state by viewModel.uiState.collectAsState()
    // The text field binds to the raw query flow so typing updates instantly;
    // the debounced uiState still drives the actual list filtering.
    val query by viewModel.query.collectAsState()
    var pendingDelete by remember { mutableStateOf<Job?>(null) }
    val settings: com.punchlist.pocket.ui.settings.SettingsViewModel = viewModel(
        factory = com.punchlist.pocket.ui.settings.SettingsViewModel.factory(
            LocalContext.current.applicationContext as PunchListApp
        )
    )
    val themeMode by settings.themeMode.collectAsState()
    // First-launch tutorial flag: false until the user finishes/skips. The
    // overlay reads this to decide whether to show on top of the dashboard.
    val onboardingCompleted by settings.onboardingCompleted.collectAsState()

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawer(
                themeMode = themeMode,
                onThemeSelected = settings::setThemeMode,
                onOpenTemplates = {
                    scope.launch { drawerState.close() }
                    onOpenTemplates()
                }
            )
        }
    ) {
        Scaffold(
            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
            topBar = {
                TopAppBar(
                    title = { Text("PunchList Pocket", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleLarge) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    },
                    actions = {
                        // Cloud icon + permanent OFFLINE badge. This is
                        // branding rather than live state (no real sync), so
                        // it's always shown.
                        OfflineIndicator()
                        OverflowMenu(
                            onOpenTemplates = {
                                scope.launch { drawerState.close() }
                                onOpenTemplates()
                            }
                        )
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
                // Blue circular FAB with a white plus, per the reference design.
                FloatingActionButton(
                    onClick = onAddJob,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Job")
                }
            }
        ) { innerPadding ->
            HomeBody(
                state = state,
                query = query,
                onQueryChange = viewModel::onQueryChange,
                onTabChange = viewModel::onTabChange,
                onJobClick = onJobClick,
                onJobPdf = onJobPdf,
                onMarkDelete = { pendingDelete = it },
                onClearFilters = {
                    viewModel.onQueryChange("")
                    viewModel.onTabChange(HomeTab.ALL)
                },
                contentPadding = innerPadding
            )
        }
    }

        // First-launch tutorial overlay — rendered on top of the dashboard
        // (and above the drawer) until the user finishes or skips it. Gated by
        // the persisted onboarding flag so it shows once per device install.
        if (!onboardingCompleted) {
            OnboardingOverlay(onFinished = settings::completeOnboarding)
        }
    }

    // Delete confirmation dialog.
    pendingDelete?.let { job ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete job?") },
            text = {
                Text(
                    "Delete \"${job.name.ifBlank { "Untitled Job" }}\"? " +
                        "This removes all of its punch items and photos."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteJob(job)
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun HomeBody(
    state: HomeUiState,
    query: String,
    onQueryChange: (String) -> Unit,
    onTabChange: (HomeTab) -> Unit,
    onJobClick: (Job) -> Unit,
    onJobPdf: (Job) -> Unit,
    onMarkDelete: (Job) -> Unit,
    onClearFilters: () -> Unit,
    contentPadding: PaddingValues
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
    ) {
        // Rounded white search field with a trailing filter affordance.
        SearchField(
            query = query,
            onQueryChange = onQueryChange,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )

        StatCardsRow(metrics = state.metrics)

        FilterChipsRow(
            selected = state.tab,
            onSelect = onTabChange,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
        )

        SectionHeader(
            count = state.rows.size,
            onViewAll = onClearFilters,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        if (state.rows.isEmpty()) {
            EmptyState(query = query, tab = state.tab)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp, bottom = 96.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.rows, key = { it.job.id }) { row ->
                    JobCard(
                        row = row,
                        onClick = { onJobClick(row.job) },
                        onJobPdf = { onJobPdf(row.job) },
                        onDelete = { onMarkDelete(row.job) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        // Force a smaller text size on both the typed query and the placeholder
        // so the search bar reads as a compact field rather than body copy.
        textStyle = MaterialTheme.typography.bodyMedium,
        placeholder = {
            Text(
                "Search jobs by name, client, or location.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        // Trailing filter button is a no-op affordance for now.
        trailingIcon = {
            IconButton(onClick = {}) {
                Icon(Icons.Default.FilterList, contentDescription = "Filter")
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline
        )
    )
}

/**
 * The four dashboard stat cards: Active Jobs (blue/briefcase), Open Items
 * (orange/checklist), Due Today (blue/calendar), Overdue (orange-red/warning).
 * Arranged as a 2x2 grid for readability on phones.
 */
@Composable
private fun StatCardsRow(metrics: HomeMetrics) {
    val palette = LocalStatusPalette.current
    val cards = listOf(
        StatCardData(
            value = metrics.activeJobs,
            label = "Active Jobs",
            icon = Icons.Default.Work,
            tint = MaterialTheme.colorScheme.primary,
            track = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        ),
        StatCardData(
            value = metrics.openItems,
            label = "Open Items",
            icon = Icons.Default.Checklist,
            tint = palette.inProgress,
            track = palette.inProgress.copy(alpha = 0.15f)
        ),
        StatCardData(
            value = metrics.dueToday,
            label = "Due Today",
            icon = Icons.Default.Event,
            tint = MaterialTheme.colorScheme.primary,
            track = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        ),
        StatCardData(
            value = metrics.overdue,
            label = "Overdue",
            icon = Icons.Default.Warning,
            tint = palette.open,
            track = palette.open.copy(alpha = 0.15f)
        )
    )
    // 2 x 2 grid.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        cards.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { card ->
                    StatCard(
                        data = card,
                        modifier = Modifier.weight(1f)
                    )
                }
                // Pad the last row if it has a single card (shouldn't happen
                // with a fixed 4-card set, but keeps the layout robust).
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

private data class StatCardData(
    val value: Int,
    val label: String,
    val icon: ImageVector,
    val tint: Color,
    val track: Color
)

@Composable
private fun StatCard(
    data: StatCardData,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(data.track, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = data.icon,
                    contentDescription = null,
                    tint = data.tint,
                    modifier = Modifier.size(26.dp)
                )
            }
            Column {
                Text(
                    text = data.value.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    color = data.tint,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = data.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FilterChipsRow(
    selected: HomeTab,
    onSelect: (HomeTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        // Center the chip group so All / Active / Due Soon / Completed sit in
        // the middle of the row instead of flush left.
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
    ) {
        FilterChipPill(
            label = "All",
            selected = selected == HomeTab.ALL,
            onClick = { onSelect(HomeTab.ALL) }
        )
        FilterChipPill(
            label = "Active",
            selected = selected == HomeTab.ACTIVE,
            onClick = { onSelect(HomeTab.ACTIVE) }
        )
        FilterChipPill(
            label = "Due Soon",
            selected = selected == HomeTab.DUE_SOON,
            onClick = { onSelect(HomeTab.DUE_SOON) }
        )
        FilterChipPill(
            label = "Completed",
            selected = selected == HomeTab.COMPLETED,
            onClick = { onSelect(HomeTab.COMPLETED) }
        )
    }
}

@Composable
private fun FilterChipPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = bg
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = fg,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun SectionHeader(
    count: Int,
    onViewAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.ContentPaste,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Your Jobs",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(4.dp))
        TextButton(onClick = onViewAll) {
            Text("View all", color = MaterialTheme.colorScheme.primary)
        }
    }
}

/**
 * Redesigned job card: left rounded thumbnail (or colored monogram fallback),
 * bold title, client + person icon, address + pin, last-updated top-right, a
 * three-dot menu (View PDF / Delete), status counts colored through
 * [LocalStatusPalette], a photos outline badge, and — when the job has items —
 * a dedicated, tappable "View PDF report" row below the status line.
 */
@Composable
private fun JobCard(
    row: JobWithProgress,
    onClick: () -> Unit,
    onJobPdf: () -> Unit,
    onDelete: () -> Unit
) {
    val job = row.job
    val palette = LocalStatusPalette.current
    val updatedText = remember(job.updatedAt) { DateUtils.relativeUpdated(job.updatedAt) }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left rounded thumbnail, or a colored monogram tile when the
                // user set no cover image.
                JobThumbnail(job = job, size = 68.dp)

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = job.name.ifBlank { "Untitled Job" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = updatedText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (job.client.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = job.client,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                    if (job.address.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Place,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = job.address,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }

                CardOverflowMenu(
                    onViewPdf = onJobPdf,
                    onDelete = onDelete
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            // Status counts + photos badge row. The "View PDF" action is given
            // its own row below (when the job has items) so the photos + PDF
            // badges no longer crowd onto one line and the PDF is an obvious
            // tappable button rather than a tiny outline chip.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatusCount(label = "Open", count = row.open, color = MaterialTheme.colorScheme.primary)
                StatusCount(label = "In Progress", count = row.inProgress, color = palette.inProgress)
                StatusCount(label = "Completed", count = row.resolved, color = palette.resolved)
                Spacer(Modifier.weight(1f))
                if (row.photoCount > 0) {
                    OutlineBadge(
                        icon = Icons.Default.PhotoCamera,
                        text = row.photoCount.toString(),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            // Dedicated, full-width "View PDF report" button — only when the
            // job actually has punch items to report on. Tapping it opens the
            // PDF preview for this job (routes to pdfPreview via onJobPdf).
            if (row.itemCount > 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                Surface(
                    onClick = onJobPdf,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "View PDF report",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun JobThumbnail(job: Job, size: androidx.compose.ui.unit.Dp) {
    val shape = RoundedCornerShape(12.dp)
    if (job.imagePath != null) {
        AsyncImage(
            model = File(job.imagePath),
            contentDescription = "${job.name} cover",
            modifier = Modifier
                .size(size)
                .clip(shape),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), shape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = (job.name.firstOrNull() ?: '?').uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun StatusCount(label: String, count: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, CircleShape)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "$count $label",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun OutlineBadge(icon: ImageVector, text: String, tint: Color) {
    Surface(
        shape = RoundedCornerShape(50),
        color = Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(1.dp, tint.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = tint,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun CardOverflowMenu(
    onViewPdf: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "More")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("View PDF") },
                leadingIcon = {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null)
                },
                onClick = {
                    expanded = false
                    onViewPdf()
                }
            )
            DropdownMenuItem(
                text = { Text("Delete") },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                onClick = {
                    expanded = false
                    onDelete()
                }
            )
        }
    }
}

/**
 * The cloud icon paired with a permanent "OFFLINE" pill. This is a branding
 * element (the app has no cloud sync) — it never changes state.
 */
@Composable
private fun OfflineIndicator() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(end = 4.dp)
    ) {
        Icon(
            imageVector = Icons.Default.CloudOff,
            contentDescription = "Offline",
            tint = MaterialTheme.colorScheme.onPrimary
        )
        Spacer(Modifier.width(6.dp))
        Surface(
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f),
            shape = RoundedCornerShape(50)
        ) {
            Text(
                text = "OFFLINE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun OverflowMenu(onOpenTemplates: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "More")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Templates") },
                leadingIcon = {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null)
                },
                onClick = {
                    expanded = false
                    onOpenTemplates()
                }
            )
        }
    }
}

@Composable
private fun EmptyState(query: String, tab: HomeTab) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                modifier = Modifier.size(40.dp),
                strokeWidth = 3.dp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(16.dp))
            val message = when {
                query.isNotBlank() -> "No jobs match \"$query\"."
                tab != HomeTab.ALL -> "Nothing under \"${tab.label}\" yet."
                else -> "No jobs yet.\nTap + to create one."
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * The hamburger drawer. Holds the appearance selector (Light / Dark / System),
 * a Templates shortcut, and a small app-info footer. The theme choice is read
 * from / written through [SettingsViewModel] so it persists across launches.
 */
@Composable
private fun AppDrawer(
    themeMode: ThemeMode,
    onThemeSelected: (ThemeMode) -> Unit,
    onOpenTemplates: () -> Unit
) {
    ModalDrawerSheet {
        // Branded header.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary)
                .padding(20.dp)
        ) {
            Column {
                Icon(
                    imageVector = Icons.Default.ContentPaste,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "PunchList Pocket",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Field punch lists, offline.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                )
            }
        }

        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            Text(
                text = "Appearance",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            ThemeMode.entries.forEach { mode ->
                NavigationDrawerItem(
                    label = { Text(mode.label) },
                    selected = mode == themeMode,
                    onClick = { onThemeSelected(mode) },
                    icon = {
                        Icon(
                            imageVector = mode.icon(),
                            contentDescription = null
                        )
                    },
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        selectedIconColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            )

            NavigationDrawerItem(
                label = { Text("Templates") },
                selected = false,
                onClick = onOpenTemplates,
                icon = {
                    Icon(
                        Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null
                    )
                },
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }

        Spacer(Modifier.weight(1f))
        Text(
            text = "PunchList Pocket • v1.0\nOffline by design.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(20.dp)
        )
    }
}

/** Picks a representative icon for each theme option. */
private fun ThemeMode.icon(): ImageVector = when (this) {
    ThemeMode.LIGHT -> Icons.Filled.LightMode
    ThemeMode.DARK -> Icons.Filled.DarkMode
    ThemeMode.SYSTEM -> Icons.Filled.BrightnessAuto
}
