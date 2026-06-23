package com.punchlist.pocket.ui.screens.template

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.punchlist.pocket.PunchListApp
import com.punchlist.pocket.data.local.Template
import com.punchlist.pocket.data.repository.AppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Lightweight row model for the templates list: the template plus the count of
 * its child items.
 */
data class TemplateRow(
    val template: Template,
    val itemCount: Int
)

data class TemplatesUiState(
    val rows: List<TemplateRow> = emptyList(),
    val isLoading: Boolean = true
)

class TemplatesViewModel(
    private val repository: AppRepository
) : ViewModel() {

    // Holds the most recently computed rows + loading flag. The templates
    // Flow drives re-computation whenever the template set changes.
    private val _rows = MutableStateFlow<List<TemplateRow>>(emptyList())
    private val _isLoading = MutableStateFlow(true)

    init {
        // Observe the templates table and refresh row counts whenever it
        // emits. Runs for the lifetime of the ViewModel.
        viewModelScope.launch {
            repository.observeTemplates().collect { templates ->
                val rows = templates.map {
                    TemplateRow(it, repository.getTemplateItems(it.id).size)
                }
                _rows.value = rows
                _isLoading.value = false
            }
        }
    }

    val uiState: StateFlow<TemplatesUiState> = combine(
        _rows,
        _isLoading
    ) { rows, loading ->
        TemplatesUiState(rows, isLoading = loading)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TemplatesUiState())

    fun deleteTemplate(template: Template) {
        viewModelScope.launch(Dispatchers.IO) {
            // Child TemplateItem rows cascade-delete via FK; just delete the
            // parent. The collector above will refresh the list.
            repository.deleteTemplate(template)
        }
    }

    companion object {
        fun factory(app: PunchListApp): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return TemplatesViewModel(app.repository) as T
                }
            }
    }
}
