package com.punchlist.pocket.ui.screens.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.punchlist.pocket.PunchListApp
import com.punchlist.pocket.data.local.Job
import com.punchlist.pocket.data.local.PunchItem
import com.punchlist.pocket.data.repository.AppRepository
import com.punchlist.pocket.utils.DateUtils
import com.punchlist.pocket.utils.FileHelper
import com.punchlist.pocket.utils.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Top-level Home tabs, modeled after the reference design. */
enum class HomeTab(val label: String) {
    ALL("All"),
    ACTIVE("Active"),
    DUE_SOON("Due Soon"),
    COMPLETED("Completed")
}

/**
 * One job plus its Open / In Progress / Resolved item counts and an
 * "is anything overdue / due soon" flag, for the Home dashboard.
 */
data class JobWithProgress(
    val job: Job,
    val open: Int,
    val inProgress: Int,
    val resolved: Int,
    val dueToday: Int,
    val dueSoon: Int,
    val overdue: Int,
    val photoCount: Int,
    val itemCount: Int
) {
    val total: Int get() = open + inProgress + resolved

    /** "Active" = has at least one non-resolved item. */
    val isActive: Boolean get() = open + inProgress > 0

    /** "Completed" = every item is resolved (and there is at least one). */
    val isCompleted: Boolean get() = total > 0 && resolved == total

    /**
     * Belongs in the "Due Soon" tab if anything is overdue or due within the
     * next 4 days (today included). `dueSoon` already rolls up both buckets.
     */
    val isDueSoon: Boolean get() = dueSoon > 0
}

/** Aggregate counts shown in the Home summary tiles. */
data class HomeMetrics(
    val activeJobs: Int = 0,
    val openItems: Int = 0,
    val dueToday: Int = 0,
    val overdue: Int = 0
)

data class HomeUiState(
    val query: String = "",
    val tab: HomeTab = HomeTab.ALL,
    val rows: List<JobWithProgress> = emptyList(),
    val metrics: HomeMetrics = HomeMetrics(),
    val isLoading: Boolean = true
)

class HomeViewModel(
    private val repository: AppRepository,
    private val appContext: Context
) : ViewModel() {

    /**
     * How many days (inclusive of today) the Home "Due Soon" tab covers. An
     * item due any time today through this many days out counts as due soon;
     * overdue items are always included as well.
     */
    private val DUE_SOON_WINDOW_DAYS = 4

    /**
     * Job ids we've already posted a due-soon notification for this session.
     * Keeps the reminder from re-firing on every dashboard refresh; a job only
     * becomes eligible again once it leaves the due-soon window (its id is
     * evicted in [maybeNotifyDueSoon]). In-memory only — one reminder per
     * session per job is the intended cadence.
     */
    private val notifiedDueSoonJobIds: MutableSet<Long> = mutableSetOf()

    private val _query = MutableStateFlow("")
    private val _tab = MutableStateFlow(HomeTab.ALL)

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<HomeUiState> = combine(_query, _tab) { q, tab -> q to tab }
        .debounce(120)
        .distinctUntilChanged()
        .flatMapLatest { (q, tab) ->
            // Resolve the job list (filtered by query), then enrich each with
            // its status + due-date counts. Done on each emission so the
            // dashboard stays live as items change.
            val source = if (q.isBlank()) repository.observeJobs() else repository.searchJobs(q)
            source.map { jobs ->
                val enriched = jobs.map { job -> enrichJob(job) }
                // Fire due-soon reminders for any job that just entered the
                // window. Done off the IO map so a notification hiccup can't
                // break the list render.
                maybeNotifyDueSoon(enriched)
                val filtered = enriched.filter { row -> matchesTab(row, tab) }
                HomeUiState(
                    query = q,
                    tab = tab,
                    rows = filtered,
                    metrics = computeMetrics(enriched),
                    isLoading = false
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState()
        )

    val query: StateFlow<String> = _query

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun onTabChange(tab: HomeTab) {
        _tab.value = tab
    }

    /** Builds the [JobWithProgress] for one job by querying its items. */
    private suspend fun enrichJob(job: Job): JobWithProgress {
        val items = repository.observeItemsByJob(job.id).first()
        var open = 0; var inProgress = 0; var resolved = 0
        var dueToday = 0; var dueSoon = 0; var overdue = 0
        val today = DateUtils.startOfToday()
        // "Due Soon" = anything due today through 4 days out. Overdue items are
        // rolled into dueSoon too so the tab surfaces anything past or imminent.
        val dueSoonCutoff = today + DUE_SOON_WINDOW_DAYS * 24L * 60 * 60 * 1000
        items.forEach { item ->
            when (item.status) {
                PunchItem.STATUS_OPEN -> open++
                PunchItem.STATUS_IN_PROGRESS -> inProgress++
                PunchItem.STATUS_RESOLVED -> resolved++
            }
            if (item.status != PunchItem.STATUS_RESOLVED && item.dueDate != null) {
                when {
                    item.dueDate < today -> overdue++
                    item.dueDate < today + 24L * 60 * 60 * 1000 -> dueToday++
                }
                if (item.dueDate < dueSoonCutoff) dueSoon++
            }
        }
        val photoCount = repository.photoCountForJob(job.id)
        return JobWithProgress(
            job = job,
            open = open,
            inProgress = inProgress,
            resolved = resolved,
            dueToday = dueToday,
            dueSoon = dueSoon,
            overdue = overdue,
            photoCount = photoCount,
            itemCount = items.size
        )
    }

    /** Whether a row should appear under the given [HomeTab]. */
    private fun matchesTab(row: JobWithProgress, tab: HomeTab): Boolean = when (tab) {
        HomeTab.ALL -> true
        HomeTab.ACTIVE -> row.isActive
        HomeTab.DUE_SOON -> row.isDueSoon
        HomeTab.COMPLETED -> row.isCompleted
    }

    /** Rolls up all jobs into the four summary tiles. */
    private fun computeMetrics(rows: List<JobWithProgress>): HomeMetrics {
        var activeJobs = 0; var openItems = 0; var dueToday = 0; var overdue = 0
        rows.forEach { row ->
            if (row.isActive) activeJobs++
            openItems += row.open + row.inProgress
            dueToday += row.dueToday
            overdue += row.overdue
        }
        return HomeMetrics(activeJobs, openItems, dueToday, overdue)
    }

    /**
     * Fires a due-soon reminder notification for each job that has entered the
     * due-soon window since the last refresh. Deduped via
     * [notifiedDueSoonJobIds]: a job only fires once until it leaves the window
     * (at which point its id is evicted, so a future re-entry can remind again).
     *
     * Runs on whatever dispatcher backs the flow map (IO during enrichment); it
     * only touches the in-memory dedup set and a no-op-guarded notification post.
     */
    private fun maybeNotifyDueSoon(rows: List<JobWithProgress>) {
        val nowDueSoon = rows.filter { it.isDueSoon }.map { it.job.id }.toSet()
        // Evict jobs that have left the window so they can re-remind later.
        notifiedDueSoonJobIds.retainAll(nowDueSoon)
        nowDueSoon.forEach { id ->
            if (notifiedDueSoonJobIds.add(id)) {
                // add() returns true only for newly-inserted ids: the exact
                // "just became due soon" transition we want to notify on.
                val row = rows.first { it.job.id == id }
                NotificationHelper.postDueSoon(
                    context = appContext,
                    jobId = id,
                    jobName = row.job.name.ifBlank { "Untitled Job" },
                    dueSoonCount = row.dueSoon.coerceAtLeast(1)
                )
            }
        }
    }

    /**
     * Deletes a job and every photo file attached to its items. The DB rows
     * cascade-delete via foreign keys, but files on disk must be removed
     * explicitly or the private image directory would leak orphaned JPEGs.
     * Also removes the job's optional cover image, if present.
     */
    fun deleteJob(job: Job) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                job.imagePath?.let { FileHelper.deleteFile(it) }
                val items = repository.observeItemsByJob(job.id).first()
                items.forEach { item ->
                    repository.getPhotos(item.id).forEach { photo ->
                        FileHelper.deleteFile(photo.filePath)
                    }
                }
            }
            repository.deleteJob(job)
        }
    }

    companion object {
        fun factory(app: PunchListApp): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    // ApplicationContext outlives any single screen, which is
                    // what the notification post needs.
                    return HomeViewModel(app.repository, app.applicationContext) as T
                }
            }
    }
}
