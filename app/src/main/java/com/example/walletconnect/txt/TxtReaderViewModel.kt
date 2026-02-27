package com.example.walletconnect.txt

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.walletconnect.epub.ComposePaginationEngine
import com.example.walletconnect.epub.PaginationResult
import com.example.walletconnect.epub.TextProcessor
import com.example.walletconnect.utils.CheckpointIndexStore
import com.example.walletconnect.utils.TimerContractStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class TxtReaderViewModel : ViewModel() {

    var uiState by mutableStateOf(TxtReaderUiState())
        private set

    private val paginationEngine = ComposePaginationEngine()
    private var cachedElements: List<TextProcessor.TextElement>? = null
    private var lastPageWidth: Float = 0f
    private var lastPageHeight: Float = 0f
    private var cachedTextStyle: TextStyle? = null

    private var currentBoxId: String = ""
    private var lastSavedPage: Int = -1

    private var checkpointIndices: List<Int> = emptyList()
    private var foundCheckpointIndices: MutableSet<Int> = mutableSetOf()
    private var checkpointLabel: String = " [I find checkpoint] "

    var checkpointIndicesState by mutableStateOf<List<Int>>(emptyList())
        private set
    var foundCheckpointIndicesState by mutableStateOf<Set<Int>>(emptySet())
        private set

    private var timerJob: Job? = null
    var remainingSeconds by mutableStateOf<Long?>(null)
        private set

    private var hasSwipeControl = false
    private var lastSwipeTime = System.currentTimeMillis()
    private var isTimerPaused = false
    private var hasHandControl = false
    private var isHandControlPaused = false
    private var isScreenPaused = false

    fun initialize(context: Context, boxId: String) {
        currentBoxId = boxId
        if (boxId.isNotEmpty()) {
            checkpointIndices = CheckpointIndexStore.getIndices(context, boxId)
            foundCheckpointIndices = CheckpointIndexStore.getFoundIndices(context, boxId).toMutableSet()
            checkpointLabel = CheckpointIndexStore.getCheckpointLabel(context, boxId)
            checkpointIndicesState = checkpointIndices.toList()
            foundCheckpointIndicesState = foundCheckpointIndices.toSet()

            val timerParams = TimerContractStore.getTimerParams(context, boxId)
            if (timerParams != null) {
                remainingSeconds = TimerContractStore.getRemainingSeconds(context, boxId)
                hasSwipeControl = timerParams.swipeControl
                hasHandControl = timerParams.handControl
                lastSwipeTime = System.currentTimeMillis()
                isTimerPaused = false
                isHandControlPaused = false
                isScreenPaused = false
                startTimer(context, boxId)
            }
        }
    }

    fun loadTxtFile(
        context: Context,
        txtFile: File,
        pageWidth: Float,
        pageHeight: Float,
        textMeasurer: TextMeasurer,
        textStyle: TextStyle
    ) {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, error = null)
            try {
                val rawText = withContext(Dispatchers.IO) { txtFile.readText(Charsets.UTF_8) }
                val elements = textToElements(rawText)
                cachedElements = elements
                lastPageWidth = pageWidth
                lastPageHeight = pageHeight
                cachedTextStyle = textStyle

                val paginationResult = paginationEngine.paginate(
                    elements = elements,
                    textMeasurer = textMeasurer,
                    textStyle = textStyle,
                    pageWidth = pageWidth,
                    pageHeight = pageHeight,
                    checkpointIndices = checkpointIndices,
                    foundCheckpointIndices = foundCheckpointIndices.toSet(),
                    checkpointLabel = checkpointLabel
                )

                val restoredPage = restorePage(context, paginationResult)
                lastSavedPage = restoredPage

                CheckpointIndexStore.saveTotalPages(context, currentBoxId, paginationResult.pages.size)

                uiState = uiState.copy(
                    isLoading = false,
                    paginationResult = paginationResult,
                    currentPage = restoredPage,
                    totalPages = paginationResult.pages.size
                )
            } catch (e: Exception) {
                uiState = uiState.copy(isLoading = false, error = "Error loading file: ${e.message}")
            }
        }
    }

    private fun textToElements(raw: String): List<TextProcessor.TextElement> {
        val elements = mutableListOf<TextProcessor.TextElement>()
        val paragraphs = raw.replace("\r\n", "\n").split("\n\n")
        paragraphs.forEachIndexed { idx, paragraph ->
            val trimmed = paragraph.trim()
            if (trimmed.isEmpty()) return@forEachIndexed
            val words = trimmed.split(Regex("\\s+"))
            words.forEach { w -> elements.add(TextProcessor.TextElement.Word(w)) }
            if (idx < paragraphs.lastIndex) {
                elements.add(TextProcessor.TextElement.ParagraphBreak(2))
            }
        }
        return elements
    }

    private fun restorePage(context: Context, paginationResult: PaginationResult): Int {
        if (currentBoxId.isEmpty()) return 0
        val savedCharIndex = CheckpointIndexStore.getCharIndex(context, currentBoxId)
        if (savedCharIndex < 0) return 0
        val foundPage = paginationResult.pages.indexOfFirst { page ->
            savedCharIndex >= page.startIndex && savedCharIndex < page.endIndex
        }
        return if (foundPage >= 0) foundPage else 0
    }

    fun onSwipeDetected() {
        if (hasSwipeControl) {
            lastSwipeTime = System.currentTimeMillis()
            isTimerPaused = false
        }
    }

    fun setMotionState(motionState: com.example.walletconnect.sensors.MotionDetector.MotionState?) {
        if (hasHandControl) {
            isHandControlPaused =
                motionState == com.example.walletconnect.sensors.MotionDetector.MotionState.STATIONARY
        }
    }

    fun setScreenPaused(paused: Boolean) {
        isScreenPaused = paused
    }

    fun goToPage(context: Context, pageIndex: Int) {
        val validIndex = pageIndex.coerceIn(0, (uiState.totalPages - 1).coerceAtLeast(0))
        uiState = uiState.copy(currentPage = validIndex)
        saveCurrentPageIfChanged(context, validIndex)
    }

    fun onCheckpointFound(
        context: Context,
        checkpointIndex: Int,
        textMeasurer: TextMeasurer,
        textStyle: TextStyle
    ) {
        if (currentBoxId.isEmpty() || checkpointIndex !in checkpointIndices) return
        if (checkpointIndex in foundCheckpointIndices) return

        foundCheckpointIndices.add(checkpointIndex)
        CheckpointIndexStore.markIndexAsFound(context, currentBoxId, checkpointIndex)
        foundCheckpointIndicesState = foundCheckpointIndices.toSet()

        val elements = cachedElements ?: return
        val currentResult = uiState.paginationResult
        val currentCharIndex = if (currentResult != null && uiState.currentPage < currentResult.pages.size) {
            currentResult.pages[uiState.currentPage].startIndex
        } else 0

        val effectiveStyle = cachedTextStyle ?: textStyle

        val paginationResult = paginationEngine.paginate(
            elements = elements,
            textMeasurer = textMeasurer,
            textStyle = effectiveStyle,
            pageWidth = lastPageWidth,
            pageHeight = lastPageHeight,
            checkpointIndices = checkpointIndices,
            foundCheckpointIndices = foundCheckpointIndices.toSet(),
            checkpointLabel = checkpointLabel
        )

        val newCurrentPage = paginationResult.pages.indexOfFirst { page ->
            currentCharIndex >= page.startIndex && currentCharIndex < page.endIndex
        }.coerceAtLeast(0)

        uiState = uiState.copy(
            paginationResult = paginationResult,
            currentPage = newCurrentPage,
            totalPages = paginationResult.pages.size
        )
    }

    fun goToHome(context: Context) {
        saveCurrentPageIfChanged(context, uiState.currentPage)
        cachedElements = null
        cachedTextStyle = null
        foundCheckpointIndices.clear()
        foundCheckpointIndicesState = emptySet()
        checkpointIndicesState = emptyList()
        currentBoxId = ""
        lastSavedPage = -1
        uiState = TxtReaderUiState()
    }

    fun stopTimer(context: Context) {
        timerJob?.cancel()
        timerJob = null
        if (currentBoxId.isNotEmpty() && remainingSeconds != null) {
            TimerContractStore.saveRemainingSeconds(context, currentBoxId, remainingSeconds!!)
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }

    private fun startTimer(context: Context, boxId: String) {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (remainingSeconds != null && remainingSeconds!! > 0) {
                delay(1000)
                if (hasSwipeControl) {
                    val timeSinceLastSwipe = (System.currentTimeMillis() - lastSwipeTime) / 1000
                    if (timeSinceLastSwipe > 300) { isTimerPaused = true; continue }
                    else isTimerPaused = false
                }
                if (!isTimerPaused && !isHandControlPaused && !isScreenPaused &&
                    remainingSeconds != null && remainingSeconds!! > 0
                ) {
                    val newSeconds = remainingSeconds!! - 1
                    remainingSeconds = newSeconds
                    TimerContractStore.saveRemainingSeconds(context, boxId, newSeconds)
                }
            }
            if (remainingSeconds != null && remainingSeconds!! == 0L) {
                TimerContractStore.saveRemainingSeconds(context, boxId, 0L)
            }
        }
    }

    private fun saveCurrentPageIfChanged(context: Context, newPage: Int) {
        if (currentBoxId.isNotEmpty() && newPage != lastSavedPage) {
            val paginationResult = uiState.paginationResult
            if (paginationResult != null && newPage < paginationResult.pages.size) {
                val charIndex = paginationResult.pages[newPage].startIndex
                CheckpointIndexStore.saveCharIndex(context, currentBoxId, charIndex)
                CheckpointIndexStore.saveCurrentPage(context, currentBoxId, newPage)
                CheckpointIndexStore.saveTotalPages(context, currentBoxId, paginationResult.pages.size)
                lastSavedPage = newPage
            }
        }
    }
}

data class TxtReaderUiState(
    val isLoading: Boolean = false,
    val paginationResult: PaginationResult? = null,
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val error: String? = null
)
