package com.roblobsta.lobstachat.ui.screens.chat

data class ErrorDialog(
    val title: String,
    val message: String,
    val positiveButtonText: String,
    val onPositiveButtonClick: () -> Unit,
    val negativeButtonText: String?,
    val onNegativeButtonClick: (() -> Unit)?
)
