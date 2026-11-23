package com.example.ui.hub

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.StudioDatabase
import com.example.data.local.StudioProjectEntity
import com.example.data.local.StudioProjectRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class StudioHubViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = StudioProjectRepository(
        StudioDatabase.getInstance(application).projectDao()
    )

    val projects: StateFlow<List<StudioProjectEntity>> = repository.observeProjects()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList()
        )

    fun deleteProject(id: Long) {
        viewModelScope.launch {
            repository.deleteProject(id)
        }
    }
}
