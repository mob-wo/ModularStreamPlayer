package com.example.feature_browser

import android.content.Context
import androidx.compose.animation.core.copy
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.distinctUntilChanged
import com.example.core_model.MediaItem
import com.example.data_repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

data class BrowserUiState(
    val currentPath: String? = null,
    val items: List<MediaItem> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class BrowserViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(BrowserUiState())
    val uiState: StateFlow<BrowserUiState> = _uiState

    private val argPath = context.getString(R.string.nav_arg_path)

    init {
        // SavedStateHandle から path の変更を監視し、uiState.currentPath を更新
        savedStateHandle.getStateFlow<String?>(argPath, null) // 初期値 null
            .onEach { newPath ->
                _uiState.value = _uiState.value.copy(isLoading = true, currentPath = newPath)
                //loadItems(newPath)
            }
            .launchIn(viewModelScope)
    }

    fun loadItemsForCurrentPath() {
        val currentPathToLoad = _uiState.value.currentPath
        viewModelScope.launch {
            try {
                val items = repository.getItemsIn(currentPathToLoad)
                _uiState.update { it.copy(items = items, isLoading = false, error = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(items = emptyList(), isLoading = false, error = e.message) }
            }
        }
    }
}