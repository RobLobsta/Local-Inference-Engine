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

package com.roblobsta.lobstachat.ui.screens.model_download

import android.app.DownloadManager
import android.app.Application
import android.app.DownloadManager
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import androidx.compose.runtime.mutableStateOf
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.paging.PagingData
import com.roblobsta.lobstachat.R
import com.roblobsta.lobstachat.data.AppDB
import com.roblobsta.lobstachat.data.HFModelsAPI
import com.roblobsta.lobstachat.data.LLMModel
import com.roblobsta.lobstachat.hf_api.HFModelInfo
import com.roblobsta.lobstachat.hf_api.HFModelSearch
import com.roblobsta.lobstachat.hf_api.HFModelTree
import com.roblobsta.lobstachat.lm.GGUFReader
import com.roblobsta.lobstachat.lm.LobstaLM
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Paths

sealed class CopyFileState {
    data object Idle : CopyFileState()
    data object InProgress : CopyFileState()
    data object Complete : CopyFileState()
    data class Error(val message: String) : CopyFileState()
}

@Single
class DownloadModelsViewModel(
    private val application: Application,
    private val appDB: AppDB,
    private val hfModelsAPI: HFModelsAPI,
) : ViewModel() {
    private val _modelInfoAndTree =
        MutableStateFlow<Pair<HFModelInfo.ModelInfo, List<HFModelTree.HFModelFile>>?>(null)
    val modelInfoAndTree: StateFlow<Pair<HFModelInfo.ModelInfo, List<HFModelTree.HFModelFile>>?> =
        _modelInfoAndTree

    val selectedModelState = mutableStateOf<LLMModel?>(null)
    val modelUrlState = mutableStateOf("")

    private val _copyFileState = MutableStateFlow<CopyFileState>(CopyFileState.Idle)
    val copyFileState: StateFlow<CopyFileState> = _copyFileState

    private val downloadManager =
        application.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    fun downloadModel() {
        // Downloading files in Android with the DownloadManager API
        // Ref: https://youtu.be/4t8EevQSYK4?feature=shared
        val modelUrl = modelUrlState.value.ifEmpty { selectedModelState.value?.url ?: return }
        val fileName = modelUrl.substring(modelUrl.lastIndexOf('/') + 1)
        val request =
            DownloadManager
                .Request(modelUrl.toUri())
                .setTitle(fileName)
                .setDescription(
                    application.getString(R.string.download_model_description),
                ).setMimeType("application/octet-stream")
                .setAllowedNetworkTypes(
                    DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE,
                ).setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED,
                ).setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        downloadManager.enqueue(request)
    }

    fun getModels(query: String): Flow<PagingData<HFModelSearch.ModelSearchResult>> = hfModelsAPI.getModelsList(query)

    /**
     * Given the model file URI, copy the model file to the app's internal directory. Once copied,
     * add a new LLMModel entity with modelName=fileName where fileName is the name of the model
     * file.
     */
    fun copyModelFile(
        uri: Uri,
    ) {
        var fileName = ""
        application.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            cursor.moveToFirst()
            fileName = cursor.getString(nameIndex)
        }
        if (fileName.isNotEmpty()) {
            _copyFileState.value = CopyFileState.InProgress
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    application.contentResolver.openInputStream(uri)?.use { inputStream ->
                        FileOutputStream(File(application.filesDir, fileName)).use { outputStream ->
                            val buffer = ByteArray(1024)
                            var bytesRead: Int
                            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                outputStream.write(buffer, 0, bytesRead)
                            }
                        }
                    }
                    val ggufReader = GGUFReader()
                    ggufReader.load(File(application.filesDir, fileName).absolutePath)
                    val contextSize = ggufReader.getContextSize() ?: LobstaLM.DefaultInferenceParams.contextSize
                    val chatTemplate = ggufReader.getChatTemplate() ?: LobstaLM.DefaultInferenceParams.chatTemplate
                    appDB.addModel(
                        fileName,
                        "",
                        Paths.get(application.filesDir.absolutePath, fileName).toString(),
                        contextSize.toInt(),
                        chatTemplate,
                    )
                    withContext(Dispatchers.Main) {
                        _copyFileState.value = CopyFileState.Complete
                    }
                } catch (e: Exception) {
                    _copyFileState.value = CopyFileState.Error(e.message ?: "Unknown error")
                }
            }
        } else {
            _copyFileState.value = CopyFileState.Error(application.getString(R.string.toast_invalid_file))
        }
    }

    fun fetchModelInfoAndTree(modelId: String) {
        _modelInfoAndTree.value = null
        CoroutineScope(Dispatchers.IO).launch {
            val modelInfo = hfModelsAPI.getModelInfo(modelId)
            var modelTree = hfModelsAPI.getModelTree(modelId)
            modelTree =
                modelTree.filter { modelFile ->
                    modelFile.path.endsWith("gguf")
                }
            _modelInfoAndTree.value = Pair(modelInfo, modelTree)
        }
    }
}
