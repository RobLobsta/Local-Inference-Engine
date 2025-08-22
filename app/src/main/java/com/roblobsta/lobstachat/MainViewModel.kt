package com.roblobsta.lobstachat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roblobsta.lobstachat.llm.ModelsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class MainViewModel(
    private val modelsRepository: ModelsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.Loading)
    val uiState: StateFlow<MainUiState> = _uiState

    init {
        viewModelScope.launch {
            val hasModels = modelsRepository.checkIfModelsDownloaded()
            _uiState.value = if (hasModels) MainUiState.GoToChat else MainUiState.GoToModelDownload
        }
    }
}

sealed class MainUiState {
    object Loading : MainUiState()
    object GoToChat : MainUiState()
    object GoToModelDownload : MainUiState()
}
