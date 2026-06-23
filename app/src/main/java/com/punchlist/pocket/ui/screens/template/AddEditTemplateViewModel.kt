package com.punchlist.pocket.ui.screens.template

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.punchlist.pocket.PunchListApp
import com.punchlist.pocket.data.local.PunchItem
import com.punchlist.pocket.data.local.Template
import com.punchlist.pocket.data.local.TemplateItem
import com.punchlist.pocket.data.repository.AppRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Editable line item in the add/edit template form. [id] is null for rows that
 * haven't been persisted yet (they'll be inserted on save); non-null for rows
 * being edited in place.
 */
data class EditableTemplateItem(
    val id: Long?,
    val title: String,
    val trade: String,
    val priority: String
)

data class AddEditTemplateUiState(
    val name: String = "",
    val description: String = "",
    val items: List<EditableTemplateItem> = emptyList(),
    val isExisting: Boolean = false,
    val nameError: String? = null
) {
    companion object {
        const val NEW_ID = -1L
    }
}

class AddEditTemplateViewModel(
    private val repository: AppRepository,
    private val templateId: Long
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddEditTemplateUiState())
    val uiState: StateFlow<AddEditTemplateUiState> = _uiState.asStateFlow()

    init {
        if (templateId > 0L) load()
    }

    private fun load() {
        viewModelScope.launch {
            val template = repository.getTemplate(templateId) ?: return@launch
            val items = repository.getTemplateItems(templateId)
            _uiState.value = AddEditTemplateUiState(
                name = template.name,
                description = template.description,
                items = items.map {
                    EditableTemplateItem(it.id, it.title, it.trade, it.priority)
                },
                isExisting = true
            )
        }
    }

    fun onNameChange(v: String) {
        _uiState.update {
            it.copy(name = v, nameError = if (v.isBlank()) "Name is required" else null)
        }
    }

    fun onDescriptionChange(v: String) = _uiState.update { it.copy(description = v) }

    fun addItem() {
        _uiState.update {
            it.copy(
                items = it.items + EditableTemplateItem(
                    id = null,
                    title = "",
                    trade = "",
                    priority = PunchItem.PRIORITY_MEDIUM
                )
            )
        }
    }

    fun updateItem(index: Int, transform: (EditableTemplateItem) -> EditableTemplateItem) {
        _uiState.update { state ->
            val updated = state.items.toMutableList()
            if (index in updated.indices) {
                updated[index] = transform(updated[index])
            }
            state.copy(items = updated)
        }
    }

    fun removeItem(index: Int) {
        _uiState.update { state ->
            val updated = state.items.toMutableList()
            if (index in updated.indices) updated.removeAt(index)
            state.copy(items = updated)
        }
    }

    fun save(onComplete: () -> Unit) {
        val current = _uiState.value
        if (current.name.isBlank()) {
            _uiState.update { it.copy(nameError = "Name is required") }
            return
        }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val template = Template(
                id = if (current.isExisting) templateId else 0L,
                name = current.name,
                description = current.description,
                createdAt = now
            )
            val effectiveId = repository.insertTemplate(template)
            // Replace the template's items wholesale: delete existing rows then
            // re-insert the edited set. Simple and avoids per-row diffing.
            repository.deleteTemplateItemsByTemplate(effectiveId)
            repository.insertTemplateItems(
                current.items
                    .filter { it.title.isNotBlank() }
                    .map { row ->
                        TemplateItem(
                            templateId = effectiveId,
                            title = row.title,
                            trade = row.trade,
                            priority = row.priority
                        )
                    }
            )
            onComplete()
        }
    }

    companion object {
        fun factory(app: PunchListApp, templateId: Long): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AddEditTemplateViewModel(app.repository, templateId) as T
                }
            }
    }
}
