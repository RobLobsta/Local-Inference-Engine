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

package com.roblobsta.lobstachat

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import com.roblobsta.lobstachat.llm.ModelsRepository
import com.roblobsta.lobstachat.ui.screens.chat.ChatActivity
import com.roblobsta.lobstachat.ui.screens.model_download.DownloadModelActivity
import org.koin.android.ext.android.inject

import androidx.lifecycle.lifecycleScope
import com.roblobsta.lobstachat.llm.ModelsRepository
import com.roblobsta.lobstachat.ui.screens.chat.ChatActivity
import com.roblobsta.lobstachat.ui.screens.model_download.DownloadModelActivity
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                when (state) {
                    is MainUiState.GoToChat -> {
                        Intent(this@MainActivity, ChatActivity::class.java).apply {
                            startActivity(this)
                            finish()
                        }
                    }
                    is MainUiState.GoToModelDownload -> {
                        Intent(this@MainActivity, DownloadModelActivity::class.java).apply {
                            startActivity(this)
                            finish()
                        }
                    }
                    is MainUiState.Loading -> {
                        // Show a loading spinner or a splash screen
                    }
                }
            }
        }
    }
}
