/*
 * Copyright (C) 2025 Shubham Panchal
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

package com.roblobsta.lobstachat.llm

import android.util.Log
import com.roblobsta.lobstachat.lm.LobstaLM
import com.roblobsta.lobstachat.data.AppDB
import com.roblobsta.lobstachat.data.Chat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import kotlin.time.measureTime

private const val LOGTAG = "[LobstaLMManager-Kt]"
private val LOGD: (String) -> Unit = { Log.d(LOGTAG, it) }

@Single
class LobstaLMManager(
    private val appDB: AppDB,
) {
    private var instance: LobstaLM? = null
    private var responseGenerationJob: Job? = null
    private var modelInitJob: Job? = null
    private var chat: Chat? = null
    private var cachedChatId: Long? = null

    var isInferenceOn = false

    data class LobstaLMResponse(
        val response: String,
        val generationSpeed: Float,
        val generationTimeSecs: Int,
        val contextLengthUsed: Int,
    )

    fun load(
        chat: Chat,
        modelPath: String,
        params: LobstaLM.InferenceParams = LobstaLM.InferenceParams(),
        onError: (Exception) -> Unit,
        onSuccess: () -> Unit,
    ) {
        if (cachedChatId == chat.id && instance != null) {
            LOGD("Using cached LLM instance for chat ${chat.id}")
            this.chat = chat
            onSuccess()
            return
        }

        this.chat = chat
        modelInitJob =
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    instance?.close()
                    instance = LobstaLM().apply {
                        load(modelPath, params)
                        LOGD("Model loaded")
                        if (chat.systemPrompt.isNotEmpty()) {
                            addSystemPrompt(chat.systemPrompt)
                            LOGD("System prompt added")
                        }
                        if (!chat.isTask) {
                            appDB.getMessagesForModel(chat.id).forEach { message ->
                                if (message.isUserMessage) {
                                    addUserMessage(message.message)
                                    LOGD("User message added: ${message.message}")
                                } else {
                                    addAssistantMessage(message.message)
                                    LOGD("Assistant message added: ${message.message}")
                                }
                            }
                        }
                    }
                    withContext(Dispatchers.Main) {
                        cachedChatId = chat.id
                        onSuccess()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        onError(e)
                    }
                }
            }
    }

    fun getResponse(
        query: String,
        responseTransform: (String) -> String,
        onPartialResponseGenerated: (String) -> Unit,
        onSuccess: (LobstaLMResponse) -> Unit,
        onCancelled: () -> Unit,
        onError: (Exception) -> Unit,
    ) {
        val currentInstance = instance ?: run {
            onError(IllegalStateException("Model not loaded"))
            return
        }
        val currentChat = chat ?: run {
            onError(IllegalStateException("Chat not loaded"))
            return
        }

        responseGenerationJob =
            CoroutineScope(Dispatchers.Default).launch {
                isInferenceOn = true
                var response = ""
                try {
                    val duration =
                        measureTime {
                            currentInstance.getResponseAsFlow(query).collect { piece ->
                                response += piece
                                withContext(Dispatchers.Main) {
                                    onPartialResponseGenerated(response)
                                }
                            }
                        }
                    response = responseTransform(response)
                    // once the response is generated
                    // add it to the messages database
                    appDB.addAssistantMessage(currentChat.id, response)
                    withContext(Dispatchers.Main) {
                        isInferenceOn = false
                        onSuccess(
                            LobstaLMResponse(
                                response = response,
                                generationSpeed = currentInstance.getResponseGenerationSpeed(),
                                generationTimeSecs = duration.inWholeSeconds.toInt(),
                                contextLengthUsed = currentInstance.getContextLengthUsed(),
                            ),
                        )
                    }
                } catch (e: CancellationException) {
                    withContext(Dispatchers.Main) {
                        isInferenceOn = false
                        onCancelled()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isInferenceOn = false
                        onError(e)
                    }
                }
            }
    }

    fun stopResponseGeneration() {
        responseGenerationJob?.let { cancelJobIfActive(it) }
    }

    fun close() {
        stopResponseGeneration()
        modelInitJob?.let { cancelJobIfActive(it) }
        instance?.close()
        instance = null
        cachedChatId = null
    }

    private fun cancelJobIfActive(job: Job) {
        if (job.isActive) {
            job.cancel()
        }
    }
}
