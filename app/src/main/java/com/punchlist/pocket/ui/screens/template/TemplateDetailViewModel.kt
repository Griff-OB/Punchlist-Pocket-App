package com.punchlist.pocket.ui.screens.template

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.punchlist.pocket.PunchListApp
import com.punchlist.pocket.data.local.Template
import com.punchlist.pocket.data.local.TemplateItem
import com.punchlist.pocket.data.repository.AppRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TemplateDetailUiState(
    val template: Template? = null,
    val items: List<TemplateItem> = emptyList()
)

/**
 * Backs the template detail screen. Reads a single template and its items
 * reactively. When [jobId] is non-null the screen is in "apply" mode and
 * [applyToJob] copies the template's items into that job as open punch items.
 */
class TemplateDetailViewModel(
    private val repository: AppRepository,
    private val templateId: Long,
    val jobId: Long?
) : ViewModel() {

    val uiState: StateFlow<TemplateDetailUiState> = combine(
        repository.observeTemplate(templateId),
        repository.observeTemplateItems(templateId)
    ) { template, items ->
        TemplateDetailUiState(template = template, items = items)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TemplateDetailUiState())

    /**
     * Applies this template to [jobId], inserting one open [PunchItem] per
     * template item. Reports the number created via [onApplied], after which
     * the screen typically shows a toast and pops back.
     */
    fun applyToJob(onApplied: (Int) -> Unit) {
        val targetJobId = jobId ?: return
        viewModelScope.launch {
            val ids = repository.applyTemplateToJob(targetJobId, templateId)
            onApplied(ids.size)
        }
    }

    companion object {
        fun factory(app: PunchListApp, templateId: Long, jobId: Long?): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return TemplateDetailViewModel(app.repository, templateId, jobId) as T
                }
            }
    }
}
