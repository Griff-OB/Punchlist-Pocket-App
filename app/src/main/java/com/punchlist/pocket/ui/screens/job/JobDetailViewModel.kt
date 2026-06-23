package com.punchlist.pocket.ui.screens.job

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.punchlist.pocket.PunchListApp
import com.punchlist.pocket.data.local.Job
import com.punchlist.pocket.data.local.Photo
import com.punchlist.pocket.data.local.PunchItem
import com.punchlist.pocket.data.repository.AppRepository
import com.punchlist.pocket.utils.DateUtils
import com.punchlist.pocket.utils.FileHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ItemFilter { ALL, OPEN, IN_PROGRESS, RESOLVED, OVERDUE }

/** Sort orders offered in Job Detail. See [applySort] for the comparisons. */
enum class ItemSort(val label: String) {
    NEWEST("Newest"),
    OLDEST("Oldest"),
    DUE_DATE("Due date"),
    PRIORITY("Priority"),
    STATUS("Status"),
    LOCATION("Location")
}

data class JobDetailUiState(
    val job: Job? = null,
    val items: List<PunchItemWithPhotos> = emptyList(),
    val filter: ItemFilter = ItemFilter.ALL,
    val sort: ItemSort = ItemSort.NEWEST,
    val selectedLocation: String? = null,
    val availableLocations: List<String> = emptyList(),
    val total: Int = 0
)

data class PunchItemWithPhotos(
    val item: PunchItem,
    val photos: List<Photo>
)

class JobDetailViewModel(
    private val repository: AppRepository,
    private val jobId: Long
) : ViewModel() {

    private val _filter = MutableStateFlow(ItemFilter.ALL)
    private val _sort = MutableStateFlow(ItemSort.NEWEST)
    private val _selectedLocation = MutableStateFlow<String?>(null)

    val job: StateFlow<Job?> = repository.observeJob(jobId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<JobDetailUiState> = combine(
        repository.observeJob(jobId),
        repository.observeItemsByJob(jobId),
        _filter,
        _sort,
        _selectedLocation
    ) { job, items, filter, sort, location ->
        JobDetailInputs(job, items, filter, sort, location)
    }.flatMapLatest { inputs ->
        val items = inputs.items
        // Distinct non-blank locations present in this job, for the chip row.
        val locations = items
            .map { it.location }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()

        val today = DateUtils.startOfToday()
        val filtered = items
            .asSequence()
            .filter { item ->
                when (inputs.filter) {
                    ItemFilter.ALL -> true
                    ItemFilter.OPEN -> item.status == PunchItem.STATUS_OPEN
                    ItemFilter.IN_PROGRESS -> item.status == PunchItem.STATUS_IN_PROGRESS
                    ItemFilter.RESOLVED -> item.status == PunchItem.STATUS_RESOLVED
                    ItemFilter.OVERDUE -> item.status != PunchItem.STATUS_RESOLVED &&
                        item.dueDate != null && item.dueDate < today
                }
            }
            .filter { item ->
                inputs.location == null || item.location == inputs.location
            }
            .sortedWith(applySort(inputs.sort))
            .toList()

        val enriched = filtered.map { PunchItemWithPhotos(it, repository.getPhotos(it.id)) }
        flowOf(
            JobDetailUiState(
                job = inputs.job,
                items = enriched,
                filter = inputs.filter,
                sort = inputs.sort,
                selectedLocation = inputs.location,
                availableLocations = locations,
                total = items.size
            )
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = JobDetailUiState()
    )

    val filter: StateFlow<ItemFilter> = _filter.asStateFlow()

    fun setFilter(filter: ItemFilter) {
        _filter.value = filter
    }

    fun setSort(sort: ItemSort) {
        _sort.value = sort
    }

    fun setSelectedLocation(location: String?) {
        _selectedLocation.value = location
    }

    fun cycleStatus(item: PunchItem) {
        viewModelScope.launch {
            val next = when (item.status) {
                PunchItem.STATUS_OPEN -> PunchItem.STATUS_IN_PROGRESS
                PunchItem.STATUS_IN_PROGRESS -> PunchItem.STATUS_RESOLVED
                else -> PunchItem.STATUS_OPEN
            }
            repository.updateItem(item.copy(status = next))
        }
    }

    fun deleteItem(item: PunchItem) {
        viewModelScope.launch {
            // Delete the photo files on disk; the photo rows cascade-delete
            // automatically when the punch item is removed.
            withContext(Dispatchers.IO) {
                repository.getPhotos(item.id).forEach { photo ->
                    FileHelper.deleteFile(photo.filePath)
                }
            }
            repository.deleteItem(item)
        }
    }

    /** Builds the comparator backing each [ItemSort] option. */
    private fun applySort(sort: ItemSort): Comparator<PunchItem> = when (sort) {
        ItemSort.NEWEST -> compareByDescending { it: PunchItem -> it.updatedAt }
        ItemSort.OLDEST -> compareBy { it: PunchItem -> it.updatedAt }
        // Nulls last: items with a date sort before undated ones
        // (it.dueDate == null → true sorts after false), then by date asc.
        ItemSort.DUE_DATE -> compareBy<PunchItem> { it.dueDate == null }
            .thenBy { it.dueDate ?: Long.MAX_VALUE }
        ItemSort.PRIORITY -> compareBy { it: PunchItem -> DateUtils.priorityRank(it.priority) }
            .thenByDescending { it: PunchItem -> it.updatedAt }
        ItemSort.STATUS -> compareBy { it: PunchItem -> DateUtils.statusRank(it.status) }
            .thenByDescending { it: PunchItem -> it.updatedAt }
        ItemSort.LOCATION -> compareBy { it: PunchItem -> it.location.lowercase() }
            .thenByDescending { it: PunchItem -> it.updatedAt }
    }

    companion object {
        fun factory(app: PunchListApp, jobId: Long): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return JobDetailViewModel(app.repository, jobId) as T
                }
            }
    }
}

/** Bundled inputs for the combined uiState flow (5 values from combine()). */
private data class JobDetailInputs(
    val job: Job?,
    val items: List<PunchItem>,
    val filter: ItemFilter,
    val sort: ItemSort,
    val location: String?
)
