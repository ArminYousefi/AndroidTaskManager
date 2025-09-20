package com.google.mytaskmanager.presentation.ui.conflict

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mytaskmanager.data.conflict.Conflict
import com.google.mytaskmanager.data.conflict.ConflictManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ConflictResolverViewModel : ViewModel() {
    private val _conflicts = MutableStateFlow<List<Conflict>>(emptyList())
    val conflicts: StateFlow<List<Conflict>> = _conflicts

    init {
        viewModelScope.launch {
            ConflictManager.conflicts.collect { c ->
                _conflicts.value = _conflicts.value + c
            }
        }
    }
}
