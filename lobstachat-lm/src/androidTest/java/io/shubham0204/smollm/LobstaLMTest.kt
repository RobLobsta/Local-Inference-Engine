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

package io.shubham0204.lobstalm

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roblobsta.lobstachat.lm.GGUFReader
import com.roblobsta.lobstachat.lm.LobstaLM
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LobstaLMTest {
    private val modelPath = "/data/local/tmp/lobstalm2-360m-instruct-q8_0.gguf"
    private val minP = 0.05f
    private val temperature = 1.0f
    private val systemPrompt = "You are a helpful assistant"
    private val query = "How are you?"
    private val chatTemplate =
        "{% set loop_messages = messages %}{% for message in loop_messages %}{% set content = '<|start_header_id|>' + message['role'] + '<|end_header_id|>\n\n'+ message['content'] | trim + '<|eot_id|>' %}{% if loop.index0 == 0 %}{% set content = bos_token + content %}{% endif %}{{ content }}{% endfor %}{{ '<|start_header_id|>assistant<|end_header_id|>\n\n' }}"
    private val lobstaLM = LobstaLM()

    @Before
    fun setup() =
        runTest {
            lobstaLM.load(
                modelPath,
                LobstaLM.InferenceParams(
                    minP,
                    temperature,
                    storeChats = true,
                    contextSize = 0,
                    chatTemplate,
                    numThreads = 4,
                    useMmap = true,
                    useMlock = false,
                ),
            )
            lobstaLM.addSystemPrompt(systemPrompt)
        }

    @Test
    fun getResponse_AsFlow_works() =
        runTest {
            val responseFlow = lobstaLM.getResponseAsFlow(query)
            val responseTokens = responseFlow.toList()
            assert(responseTokens.isNotEmpty())
        }

    @Test
    fun getResponseAsFlowGenerationSpeed_works() =
        runTest {
            val speedBeforePrediction = lobstaLM.getResponseGenerationSpeed().toInt()
            lobstaLM.getResponseAsFlow(query).toList()
            val speedAfterPrediction = lobstaLM.getResponseGenerationSpeed().toInt()
            assert(speedBeforePrediction == 0)
            assert(speedAfterPrediction > 0)
        }

    @Test
    fun getContextSize_works() =
        runTest {
            val ggufReader = GGUFReader()
            ggufReader.load(modelPath)
            val contextSize = ggufReader.getContextSize()
            assert(contextSize == 8192L)
        }

    @After
    fun close() {
        lobstaLM.close()
    }
}
