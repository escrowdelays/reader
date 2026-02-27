package com.example.walletconnect.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.walletconnect.sensors.MotionDetector
import com.example.walletconnect.txt.TxtReaderViewModel
import com.example.walletconnect.utils.TimerContractStore
import java.io.File

@Composable
fun TxtReaderScreen(
    txtFile: File,
    boxId: String,
    onBack: () -> Unit,
    viewModel: TxtReaderViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState = viewModel.uiState
    val remainingSeconds = viewModel.remainingSeconds
    val textMeasurer = rememberTextMeasurer()

    var pageWidth by remember { mutableStateOf(0f) }
    var pageHeight by remember { mutableStateOf(0f) }
    var isPageSizeMeasured by remember { mutableStateOf(false) }

    val timerParams = remember(boxId) {
        TimerContractStore.getTimerParams(context, boxId)
    }
    val hasHandControl = timerParams?.handControl == true

    val motionDetector = remember(hasHandControl) {
        if (hasHandControl) MotionDetector(context) else null
    }
    val motionState = motionDetector?.let {
        val state by it.motionState.collectAsState()
        state
    }

    DisposableEffect(hasHandControl, motionDetector) {
        if (hasHandControl && motionDetector != null) motionDetector.start()
        onDispose { motionDetector?.stop() }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> viewModel.setScreenPaused(true)
                Lifecycle.Event.ON_RESUME -> viewModel.setScreenPaused(false)
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(motionState) { viewModel.setMotionState(motionState) }

    LaunchedEffect(Unit) { viewModel.initialize(context, boxId) }

    DisposableEffect(Unit) {
        onDispose { viewModel.stopTimer(context) }
    }

    val readerStyle = remember { ReaderTextStyle }

    LaunchedEffect(isPageSizeMeasured) {
        if (isPageSizeMeasured && pageWidth > 0 && pageHeight > 0) {
            viewModel.loadTxtFile(
                context = context,
                txtFile = txtFile,
                pageWidth = pageWidth,
                pageHeight = pageHeight,
                textMeasurer = textMeasurer,
                textStyle = readerStyle
            )
        }
    }

    BackHandler {
        viewModel.goToHome(context)
        viewModel.stopTimer(context)
        onBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFFF8E1))
    ) {
        if (!isPageSizeMeasured || uiState.isLoading) {
            LoadingScreen(onPageSizeChanged = { width, height ->
                pageWidth = width
                pageHeight = height
                isPageSizeMeasured = true
            })
        } else if (uiState.error != null) {
            ErrorScreen(error = uiState.error!!, onBack = onBack)
        } else if (uiState.paginationResult != null) {
            PageContent(
                paginationResult = uiState.paginationResult!!,
                currentPage = uiState.currentPage,
                totalPages = uiState.totalPages,
                textStyle = readerStyle,
                onSwipeDetected = { viewModel.onSwipeDetected() },
                onGoToPage = { viewModel.goToPage(context, it) },
                onCheckpointClick = { checkpointIndex ->
                    viewModel.onCheckpointFound(context, checkpointIndex, textMeasurer, readerStyle)
                },
                onBack = {
                    viewModel.goToHome(context)
                    viewModel.stopTimer(context)
                    onBack()
                },
                remainingSeconds = remainingSeconds,
                motionState = if (hasHandControl) motionState else null,
                checkpointIndices = viewModel.checkpointIndicesState,
                foundCheckpointIndices = viewModel.foundCheckpointIndicesState
            )
        }
    }
}
