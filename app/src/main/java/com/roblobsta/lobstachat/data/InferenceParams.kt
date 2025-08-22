package com.roblobsta.lobstachat.data

data class InferenceParams(
    val minP: Float = 0.1f,
    val temperature: Float = 0.8f,
    val nThreads: Int = 4,
    val useMmap: Boolean = true,
    val useMlock: Boolean = false,
    val topK: Int = 40,
    val topP: Float = 0.9f,
    val xtcP: Float = 0.0f,
    val xtcT: Float = 1.0f,
)
