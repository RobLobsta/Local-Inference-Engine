/*
 * Copyright (C) 2024 Shubham Panchal
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.shubham0204.smollmandroid.ui.screens.chat

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.ActivityManager.MemoryInfo
import android.content.Context
import android.graphics.Color
import android.text.util.Linkify
import android.util.Log
import android.util.TypedValue
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.ViewModel
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.core.CorePlugin
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import io.noties.markwon.syntax.Prism4jThemeDarkula
import io.noties.markwon.syntax.SyntaxHighlightPlugin
import io.noties.prism4j.Prism4j
import io.shubham0204.smollm.SmolLM
import io.shubham0204.smollmandroid.R
import io.shubham0204.smollmandroid.data.AppDB
import io.shubham0204.smollmandroid.data.Chat
import io.shubham0204.smollmandroid.data.ChatMessage
import io.shubham0204.smollmandroid.data.Folder
import io.shubham0204.smollmandroid.llm.ModelsRepository
import io.shubham0204.smollmandroid.llm.SmolLMManager
import io.shubham0204.smollmandroid.prism4j.PrismGrammarLocator
import io.shubham0204.smollmandroid.ui.components.createAlertDialog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.koin.android.annotation.KoinViewModel
import java.util.Date
import kotlin.math.pow

private const val LOGTAG = "[SmolLMAndroid-Kt]"
private val LOGD: (String) -> Unit = { Log.d(LOGTAG, it) }

sealed class ChatScreenUIEvent {
    data object Idle : ChatScreenUIEvent()

    sealed class DialogEvents {
        data class ToggleChangeFolderDialog(
            val visible: Boolean,
        ) : ChatScreenUIEvent()

        data class ToggleSelectModelListDialog(
            val visible: Boolean,
        ) : ChatScreenUIEvent()

        data class ToggleMoreOptionsPopup(
            val visible: Boolean,
        ) : ChatScreenUIEvent()

        data class ToggleTaskListBottomList(
            val visible: Boolean,
        ) : ChatScreenUIEvent()
    }
}

data class ChatScreenUiState(
    val currChat: Chat? = null,
    val isGeneratingResponse: Boolean = false,
    val modelLoadingState: ChatScreenViewModel.ModelLoadingState = ChatScreenViewModel.ModelLoadingState.NOT_LOADED,
    val partialResponse: String = "",
    val uiEvent: ChatScreenUIEvent = ChatScreenUIEvent.Idle,
    val showChangeFolderDialog: Boolean = false,
    val showSelectModelListDialog: Boolean = false,
    val showMoreOptionsPopup: Boolean = false,
    val showTaskListBottomList: Boolean = false,
    val showRAMUsageLabel: Boolean = false,
    val errorDialog: ErrorDialog? = null
)

data class ErrorDialog(
    val title: String,
    val message: String,
    val positiveButtonText: String,
    val onPositiveButtonClick: () -> Unit,
    val negativeButtonText: String?,
    val onNegativeButtonClick: (() -> Unit)?
)

@KoinViewModel
class ChatScreenViewModel(
    private val modelsRepository: ModelsRepository,
    private val smolLMManager: SmolLMManager,
    private val appDB: AppDB,
    val markwon: Markwon
) : ViewModel() {
    enum class ModelLoadingState {
        NOT_LOADED, // model loading not started
        IN_PROGRESS, // model loading in-progress
        SUCCESS, // model loading finished successfully
        FAILURE, // model loading failed
    }

    private val _uiState = MutableStateFlow(ChatScreenUiState())
    val uiState: StateFlow<ChatScreenUiState> = _uiState

    // Used to pre-set a value in the query text-field of the chat screen
    // It is set when a query comes from a 'share-text' intent in ChatActivity
    var questionTextDefaultVal: String? = null

    // regex to replace <think> tags with <blockquote>
    // to render them correctly in Markdown
    private val findThinkTagRegex = Regex("<think>(.*?)</think>", RegexOption.DOT_MATCHES_ALL)
    var responseGenerationsSpeed: Float? = null
    var responseGenerationTimeSecs: Int? = null

    init {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(currChat = appDB.loadDefaultChat())
        }
    }

    fun getChats(): Flow<List<Chat>> = appDB.getChats()

    fun getChatMessages(chatId: Long): Flow<List<ChatMessage>> = appDB.getMessages(chatId)

    fun getFolders(): Flow<List<Folder>> = appDB.getFolders()

    fun getChatsForFolder(folderId: Long): Flow<List<Chat>> = appDB.getChatsForFolder(folderId)

    fun updateChatLLMParams(
        modelId: Long,
        chatTemplate: String,
    ) {
        viewModelScope.launch {
            val updatedChat = _uiState.value.currChat?.copy(llmModelId = modelId, chatTemplate = chatTemplate)
            if (updatedChat != null) {
                appDB.updateChat(updatedChat)
                _uiState.value = _uiState.value.copy(currChat = updatedChat)
            }
        }
    }

    fun updateChatFolder(folderId: Long) {
        viewModelScope.launch {
            val updatedChat = _uiState.value.currChat?.copy(folderId = folderId)
            if (updatedChat != null) {
                appDB.updateChat(updatedChat)
            }
        }
    }

    fun updateChat(chat: Chat) {
        viewModelScope.launch {
            appDB.updateChat(chat)
            _uiState.value = _uiState.value.copy(currChat = chat)
            loadModel()
        }
    }

    fun deleteMessage(messageId: Long) {
        viewModelScope.launch {
            appDB.deleteMessage(messageId)
        }
    }

    fun sendUserQuery(
        query: String,
        addMessageToDB: Boolean = true,
    ) {
        viewModelScope.launch {
            _uiState.value.currChat?.let { chat ->
                // Update the 'dateUsed' attribute of the current Chat instance
                // when a query is sent by the user
                val updatedChat = chat.copy(dateUsed = Date())
                appDB.updateChat(updatedChat)

                if (chat.isTask) {
                    // If the chat is a 'task', delete all existing messages
                    // to maintain the 'stateless' nature of the task
                    appDB.deleteMessages(chat.id)
                }

                if (addMessageToDB) {
                    appDB.addUserMessage(chat.id, query)
                }
                _uiState.value = _uiState.value.copy(isGeneratingResponse = true, partialResponse = "")
                smolLMManager.getResponse(
                    query,
                    responseTransform = {
                        // Replace <think> tags with <blockquote> tags
                        // to get a neat Markdown rendering
                        findThinkTagRegex.replace(it) { matchResult ->
                            "<blockquote><i><h6>${matchResult.groupValues[1].trim()}</i></h6></blockquote>"
                        }
                    },
                    onPartialResponseGenerated = {
                        _uiState.value = _uiState.value.copy(partialResponse = it)
                    },
                    onSuccess = { response ->
                        _uiState.value = _uiState.value.copy(isGeneratingResponse = false)
                        responseGenerationsSpeed = response.generationSpeed
                        responseGenerationTimeSecs = response.generationTimeSecs
                        viewModelScope.launch {
                            appDB.updateChat(updatedChat.copy(contextSizeConsumed = response.contextLengthUsed))
                        }
                    },
                    onCancelled = {
                        // ignore CancellationException, as it was called because
                        // `responseGenerationJob` was cancelled in the `stopGeneration` method
                    },
                    onError = { exception ->
                        _uiState.value = _uiState.value.copy(
                            isGeneratingResponse = false,
                            errorDialog = ErrorDialog(
                                title = "An error occurred",
                                message = "The app is unable to process the query. The error message is: ${exception.message}",
                                positiveButtonText = "Change model",
                                onPositiveButtonClick = {
                                    _uiState.value = _uiState.value.copy(showSelectModelListDialog = true)
                                },
                                negativeButtonText = null,
                                onNegativeButtonClick = null
                            )
                        )
                    },
                )
            }
        }
    }

    fun stopGeneration() {
        _uiState.value = _uiState.value.copy(isGeneratingResponse = false, partialResponse = "")
        smolLMManager.stopResponseGeneration()
    }

    fun switchChat(chat: Chat) {
        stopGeneration()
        _uiState.value = _uiState.value.copy(currChat = chat)
    }

    fun deleteChat(chat: Chat) {
        viewModelScope.launch {
            stopGeneration()
            appDB.deleteChat(chat)
            appDB.deleteMessages(chat.id)
            _uiState.value = _uiState.value.copy(currChat = null)
        }
    }

    fun deleteChatMessages(chat: Chat) {
        viewModelScope.launch {
            stopGeneration()
            appDB.deleteMessages(chat.id)
        }
    }

    fun deleteModel(modelId: Long) {
        viewModelScope.launch {
            modelsRepository.deleteModel(modelId)
            if (_uiState.value.currChat?.llmModelId == modelId) {
                _uiState.value =
                    _uiState.value.copy(currChat = _uiState.value.currChat?.copy(llmModelId = -1))
            }
        }
    }

    /**
     * Load the model for the current chat. If chat is configured with a LLM (i.e. chat.llModelId !=
     * -1), then load the model. If not, show the model list dialog. Once the model is finalized,
     * read the system prompt and user messages from the database and add them to the model.
     */
    fun loadModel(onComplete: (ModelLoadingState) -> Unit = {}) {
        viewModelScope.launch {
            _uiState.value.currChat?.let { chat ->
                val model = modelsRepository.getModelFromId(chat.llmModelId)
                if (chat.llmModelId == -1L || model == null) {
                    _uiState.value = _uiState.value.copy(showSelectModelListDialog = true)
                } else {
                    _uiState.value = _uiState.value.copy(modelLoadingState = ModelLoadingState.IN_PROGRESS)
                    smolLMManager.load(
                        chat,
                        model.path,
                        SmolLM.InferenceParams(
                            chat.minP,
                            chat.temperature,
                            !chat.isTask,
                            chat.contextSize.toLong(),
                            chat.chatTemplate,
                            chat.nThreads,
                            chat.useMmap,
                            chat.useMlock,
                            chat.topP,
                            chat.topK,
                            chat.xtcP,
                            chat.xtcT,
                        ),
                        onError = { e ->
                            _uiState.value = _uiState.value.copy(
                                modelLoadingState = ModelLoadingState.FAILURE,
                                errorDialog = ErrorDialog(
                                    title = "Error loading model",
                                    message = "Failed to load the model. Error: ${e.message}",
                                    positiveButtonText = "Change model",
                                    onPositiveButtonClick = {
                                        _uiState.value =
                                            _uiState.value.copy(showSelectModelListDialog = true)
                                    },
                                    negativeButtonText = "Close",
                                    onNegativeButtonClick = {
                                        _uiState.value = _uiState.value.copy(errorDialog = null)
                                    }
                                )
                            )
                            onComplete(ModelLoadingState.FAILURE)
                        },
                        onSuccess = {
                            _uiState.value = _uiState.value.copy(modelLoadingState = ModelLoadingState.SUCCESS)
                            onComplete(ModelLoadingState.SUCCESS)
                        },
                    )
                }
            }
        }
    }

    /**
     * Clears the resources occupied by the model only
     * if the inference is not in progress.
     */
    fun unloadModel(): Boolean =
        if (!smolLMManager.isInferenceOn) {
            smolLMManager.close()
            _uiState.value = _uiState.value.copy(modelLoadingState = ModelLoadingState.NOT_LOADED)
            true
        } else {
            false
        }

    /**
     * Get the current memory usage of the device.
     * This method returns the memory consumed (in GBs) and the total
     * memory available on the device (in GBs)
     */
    fun getCurrentMemoryUsage(context: Context): Pair<Float, Float> {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        val totalMemory = (memoryInfo.totalMem) / 1024.0.pow(3.0)
        val usedMemory = (memoryInfo.availMem) / 1024.0.pow(3.0)
        return Pair(usedMemory.toFloat(), totalMemory.toFloat())
    }

    @SuppressLint("StringFormatMatches")
    fun showContextLengthUsageDialog(context: Context) {
        _uiState.value.currChat?.let { chat ->
            _uiState.value = _uiState.value.copy(
                errorDialog = ErrorDialog(
                    title = context.getString(R.string.dialog_ctx_usage_title),
                    message = context.getString(
                        R.string.dialog_ctx_usage_text,
                        chat.contextSizeConsumed,
                        chat.contextSize,
                    ),
                    positiveButtonText = context.getString(R.string.dialog_ctx_usage_close),
                    onPositiveButtonClick = {
                        _uiState.value = _uiState.value.copy(errorDialog = null)
                    },
                    negativeButtonText = null,
                    onNegativeButtonClick = null
                )
            )
        }
    }

    fun onEvent(event: ChatScreenUIEvent) {
        when (event) {
            is ChatScreenUIEvent.DialogEvents.ToggleSelectModelListDialog -> {
                _uiState.value = _uiState.value.copy(showSelectModelListDialog = event.visible)
            }

            is ChatScreenUIEvent.DialogEvents.ToggleMoreOptionsPopup -> {
                _uiState.value = _uiState.value.copy(showMoreOptionsPopup = event.visible)
            }

            is ChatScreenUIEvent.DialogEvents.ToggleTaskListBottomList -> {
                _uiState.value = _uiState.value.copy(showTaskListBottomList = event.visible)
            }

            is ChatScreenUIEvent.DialogEvents.ToggleChangeFolderDialog -> {
                _uiState.value = _uiState.value.copy(showChangeFolderDialog = event.visible)
            }

            else -> {}
        }
    }

    fun toggleRAMUsageLabelVisibility() {
        _uiState.value = _uiState.value.copy(showRAMUsageLabel = !_uiState.value.showRAMUsageLabel)
    }
}
