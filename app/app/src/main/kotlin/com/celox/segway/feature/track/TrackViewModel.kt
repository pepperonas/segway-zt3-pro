package com.celox.segway.feature.track

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.celox.segway.core.data.TrackEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TrackViewModel @Inject constructor(
    private val repo: TrackRepository,
) : ViewModel() {

    val tracks: StateFlow<List<TrackEntity>> =
        repo.all.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(id: Long) = viewModelScope.launch { repo.delete(id) }
}
