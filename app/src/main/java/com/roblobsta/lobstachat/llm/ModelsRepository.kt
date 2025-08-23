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

package com.roblobsta.lobstachat.llm

import android.app.Application
import com.roblobsta.lobstachat.data.AppDB
import com.roblobsta.lobstachat.data.LLMModel
import kotlinx.coroutines.flow.Flow
import org.koin.core.annotation.Single
import java.io.File

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Single
class ModelsRepository(
    private val application: Application,
    private val appDB: AppDB,
) {

    suspend fun cleanupOrphanedModels() = withContext(Dispatchers.IO) {
        for (model in appDB.getModelsList()) {
            if (!File(model.path).exists()) {
                deleteModel(model.id)
            }
        }
    }

    suspend fun checkIfModelsDownloaded(): Boolean = withContext(Dispatchers.IO) {
        application.filesDir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.endsWith(".gguf")) {
                return@withContext true
            }
        }
        return@withContext false
    }

    suspend fun getModelFromId(id: Long): LLMModel? = appDB.getModel(id)

    fun getAvailableModels(): Flow<List<LLMModel>> = appDB.getModels()

    suspend fun deleteModel(id: Long) {
        appDB.getModel(id)?.let {
            File(it.path).delete()
            appDB.deleteModel(it.id)
        }
    }
}
