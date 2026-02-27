package com.example.walletconnect.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.walletconnect.ui.theme.BpmfHuninnFontFamily
import com.example.walletconnect.utils.BoxMetadataStore
import com.example.walletconnect.utils.CheckpointIndexStore
import com.example.walletconnect.utils.FileManager
import com.example.walletconnect.utils.LocalBookStore
import org.jsoup.Jsoup
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipInputStream

private val RdBgTop       = Color(0xFFF6F9FE)
private val RdBgMid       = Color(0xFFD5DCE9)
private val RdBgBot       = Color(0xFFDEE6F2)
private val RdSurface     = Color(0xFFEDF1F8)
private val RdSurfaceLo   = Color(0xFFE8EDF5)
private val RdBorderHi    = Color(0xFFF4F7FC)
private val RdBorderLo    = Color(0xFFBDCADB)
private val RdNavy        = Color(0xFF2D3A4F)
private val RdSlate       = Color(0xFF4B6080)
private val RdTextHi      = Color(0xFF0F172A)
private val RdTextLo      = Color(0xFF8896A8)
private val RdError       = Color(0xFFEF4444)

private val RdBgBrush       = Brush.verticalGradient(listOf(RdBgTop, RdBgMid, RdBgBot))
private val RdAccentBrush   = Brush.linearGradient(listOf(Color(0xFF1E2D3D), Color(0xFF3D5166)))
private val RdBorderBrush   = Brush.linearGradient(listOf(RdBorderHi, RdBorderLo))
private val RdShadowAmbient = Color(0x22000000)
private val RdShadowSpot    = Color(0x2E000000)

private val TypeColorEpub = Color(0xFF4B6080)
private val TypeColorPdf  = Color(0xFFB45309)
private val TypeColorTxt  = Color(0xFF16A34A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadScreen(
    onBack: () -> Unit,
    onOpenBook: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var books by remember { mutableStateOf(LocalBookStore.getBooks(context)) }
    var duplicateBookName by remember { mutableStateOf<String?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult

        val contentResolver = context.contentResolver
        val mimeType = contentResolver.getType(uri) ?: ""

        var displayName = "book"
        var incomingSize = -1L
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIdx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = it.getColumnIndex(OpenableColumns.SIZE)
                if (nameIdx >= 0) displayName = it.getString(nameIdx) ?: "book"
                if (sizeIdx >= 0) incomingSize = it.getLong(sizeIdx)
            }
        }

        val fileType = when {
            mimeType == "application/epub+zip" -> "epub"
            mimeType == "application/pdf" -> "pdf"
            mimeType == "text/plain" -> "txt"
            displayName.endsWith(".epub", true) -> "epub"
            displayName.endsWith(".pdf", true) -> "pdf"
            displayName.endsWith(".txt", true) -> "txt"
            else -> return@rememberLauncherForActivityResult
        }

        val isDuplicate = books.any { existing ->
            existing.name == displayName && run {
                val savedFile = when (existing.fileType) {
                    "epub" -> FileManager.getEpubFile(context, existing.id)
                    "pdf" -> FileManager.getPdfFile(context, existing.id)
                    "txt" -> FileManager.getTxtFile(context, existing.id)
                    else -> null
                }
                savedFile != null && incomingSize >= 0 && savedFile.length() == incomingSize
            }
        }
        if (isDuplicate) {
            duplicateBookName = displayName
            return@rememberLauncherForActivityResult
        }

        val bookId = LocalBookStore.addBook(context, displayName, fileType)

        val saved = when (fileType) {
            "epub" -> FileManager.saveEpubFile(context, uri, bookId)
            "pdf" -> FileManager.savePdfFile(context, uri, bookId)
            "txt" -> FileManager.saveTxtFile(context, uri, bookId)
            else -> null
        }

        if (saved != null) {
            BoxMetadataStore.setFileType(context, bookId, fileType)
            books = LocalBookStore.getBooks(context)
        } else {
            LocalBookStore.removeBook(context, bookId)
        }
    }

    duplicateBookName?.let { dupName ->
        Dialog(onDismissRequest = { duplicateBookName = null }) {
            Box(
                modifier = Modifier
                    .width(300.dp)
                    .wrapContentHeight()
                    .shadow(
                        20.dp, RoundedCornerShape(24.dp),
                        ambientColor = RdShadowAmbient,
                        spotColor = RdShadowSpot
                    )
                    .background(RdSurface, RoundedCornerShape(24.dp))
                    .border(1.dp, RdBorderBrush, RoundedCornerShape(24.dp))
                    .padding(24.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        "Already added",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = RdTextHi
                    )
                    Text(
                        dupName,
                        fontSize = 14.sp,
                        color = RdSlate,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "A book with the same name and size already exists in your library.",
                        fontSize = 13.sp,
                        color = RdTextLo
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(
                                4.dp, RoundedCornerShape(12.dp),
                                ambientColor = RdShadowAmbient,
                                spotColor = RdShadowSpot
                            )
                            .clip(RoundedCornerShape(12.dp))
                            .background(RdAccentBrush, RoundedCornerShape(12.dp))
                            .clickable { duplicateBookName = null }
                            .padding(vertical = 13.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "OK",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(RdBgBrush)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0),
            topBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFFF6F9FE), Color(0xFFEEF3FB), Color.Transparent)
                            )
                        )
                        .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .shadow(6.dp, CircleShape, ambientColor = RdShadowAmbient, spotColor = RdShadowSpot)
                                .background(RdSurface, CircleShape)
                                .border(1.dp, RdBorderLo, CircleShape)
                                .clip(CircleShape)
                                .clickable(onClick = onBack),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = "back",
                                tint = RdTextHi,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Text(
                            text = "read",
                            fontFamily = BpmfHuninnFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = RdTextHi
                        )

                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .shadow(6.dp, CircleShape, ambientColor = RdShadowAmbient, spotColor = RdShadowSpot)
                                .background(RdSurface, CircleShape)
                                .border(1.dp, RdBorderLo, CircleShape)
                                .clip(CircleShape)
                                .clickable {
                                    filePickerLauncher.launch(
                                        arrayOf(
                                            "application/epub+zip",
                                            "application/pdf",
                                            "text/plain",
                                            "application/octet-stream"
                                        )
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "add book",
                                tint = RdTextHi,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            if (books.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Text(
                            "No books yet",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = RdSlate
                        )
                        Text(
                            "Tap + to add epub, pdf or txt",
                            fontSize = 13.sp,
                            color = RdTextLo
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(books, key = { it.id }) { book ->
                        BookCard(
                            book = book,
                            onClick = { onOpenBook(book.id) },
                            onDelete = {
                                when (book.fileType) {
                                    "epub" -> FileManager.getEpubFile(context, book.id)?.delete()
                                    "pdf" -> FileManager.getPdfFile(context, book.id)?.delete()
                                    "txt" -> FileManager.getTxtFile(context, book.id)?.delete()
                                }
                                LocalBookStore.removeBook(context, book.id)
                                books = LocalBookStore.getBooks(context)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BookCard(
    book: LocalBookStore.LocalBook,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }

    data class CachedBookData(
        val title: String,
        val author: String?,
        val currentPage: Int,
        val totalPages: Int
    )

    val cachedData = remember(book.id) {
        val bookFile = when (book.fileType) {
            "epub" -> FileManager.getEpubFile(context, book.id)
            "pdf" -> FileManager.getPdfFile(context, book.id)
            "txt" -> FileManager.getTxtFile(context, book.id)
            else -> null
        }
        val meta = bookFile?.let { resolveBookMeta(it, book.fileType) } ?: BookMeta(null, null)
        val title = meta.title
            ?: book.name
                .removeSuffix(".${book.fileType}")
                .removeSuffix(".${book.fileType.uppercase()}")
        CachedBookData(
            title = title,
            author = meta.author,
            currentPage = CheckpointIndexStore.getCurrentPage(context, book.id),
            totalPages = CheckpointIndexStore.getTotalPages(context, book.id)
        )
    }

    val bookTitle = cachedData.title
    val bookAuthor = cachedData.author
    val currentPage = cachedData.currentPage
    val totalPages = cachedData.totalPages

    val typeColor = when (book.fileType) {
        "epub" -> TypeColorEpub
        "pdf" -> TypeColorPdf
        "txt" -> TypeColorTxt
        else -> RdSlate
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                8.dp, RoundedCornerShape(20.dp),
                clip = false,
                ambientColor = RdShadowAmbient,
                spotColor = RdShadowSpot
            )
            .background(RdSurface, RoundedCornerShape(20.dp))
            .border(1.dp, RdBorderBrush, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(48.dp)
                .align(Alignment.TopStart)
                .padding(top = 20.dp)
                .background(typeColor, RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
        )

        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = bookTitle,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = RdTextHi,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (bookAuthor != null) {
                        Text(
                            text = bookAuthor,
                            fontSize = 12.sp,
                            color = RdTextLo,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .background(typeColor.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                        .border(1.dp, typeColor.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = book.fileType.uppercase(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        color = typeColor
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, RdBorderLo, Color.Transparent)
                        )
                    )
            )

            if (totalPages > 0) {
                val readProgress = (currentPage + 1).toFloat() / totalPages.toFloat()
                val percent = (readProgress * 100).toInt().coerceIn(0, 100)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Progress",
                        fontSize = 11.sp,
                        color = RdTextLo,
                        letterSpacing = 0.3.sp
                    )
                    Text(
                        text = "${currentPage + 1} / $totalPages pages  ·  $percent%",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = RdTextHi
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(RdSurfaceLo, RoundedCornerShape(2.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(readProgress.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(
                                Brush.horizontalGradient(listOf(RdSlate, RdNavy)),
                                RoundedCornerShape(2.dp)
                            )
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Pages",
                        fontSize = 11.sp,
                        color = RdTextLo,
                        letterSpacing = 0.3.sp
                    )
                    Text(
                        text = "not started",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = RdTextLo
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .shadow(
                            4.dp, RoundedCornerShape(14.dp),
                            ambientColor = RdShadowAmbient,
                            spotColor = RdShadowSpot
                        )
                        .background(RdSurface, RoundedCornerShape(14.dp))
                        .border(1.dp, RdBorderLo, RoundedCornerShape(14.dp))
                        .clickable { onClick() }
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Read",
                        color = RdSlate,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .shadow(
                            4.dp, RoundedCornerShape(14.dp),
                            ambientColor = RdShadowAmbient,
                            spotColor = RdShadowSpot
                        )
                        .background(RdSurface, RoundedCornerShape(14.dp))
                        .border(1.dp, RdBorderLo, RoundedCornerShape(14.dp))
                        .clickable { showDeleteDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "x",
                        color = RdTextLo,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }

    if (showDeleteDialog) {
        Dialog(onDismissRequest = { showDeleteDialog = false }) {
            Box(
                modifier = Modifier
                    .width(300.dp)
                    .wrapContentHeight()
                    .shadow(
                        20.dp, RoundedCornerShape(24.dp),
                        ambientColor = RdShadowAmbient,
                        spotColor = RdShadowSpot
                    )
                    .background(RdSurface, RoundedCornerShape(24.dp))
                    .border(1.dp, RdBorderBrush, RoundedCornerShape(24.dp))
                    .padding(24.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        "Delete book?",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = RdTextHi
                    )
                    Text(
                        bookTitle,
                        fontSize = 15.sp,
                        color = RdSlate,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .shadow(
                                    4.dp, RoundedCornerShape(12.dp),
                                    ambientColor = RdShadowAmbient,
                                    spotColor = RdShadowSpot
                                )
                                .background(RdSurface, RoundedCornerShape(12.dp))
                                .border(1.dp, RdBorderLo, RoundedCornerShape(12.dp))
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showDeleteDialog = false }
                                .padding(vertical = 13.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Cancel",
                                color = RdSlate,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .shadow(
                                    4.dp, RoundedCornerShape(12.dp),
                                    ambientColor = RdShadowAmbient,
                                    spotColor = RdShadowSpot
                                )
                                .clip(RoundedCornerShape(12.dp))
                                .background(RdAccentBrush, RoundedCornerShape(12.dp))
                                .clickable {
                                    showDeleteDialog = false
                                    onDelete()
                                }
                                .padding(vertical = 13.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Delete",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class BookMeta(val title: String?, val author: String?)

private fun resolveBookMeta(file: File, fileType: String): BookMeta {
    if (fileType == "epub") {
        return try {
            FileInputStream(file).use { fis ->
                ZipInputStream(fis).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (entry.name.endsWith(".opf", ignoreCase = true)) {
                            val doc = Jsoup.parse(zip.bufferedReader().readText())
                            val title = doc.select("dc|title, title").first()?.text()?.trim()
                            val author = doc.select("dc|creator, creator").first()?.text()?.trim()
                            return BookMeta(
                                title = title?.takeIf { it.isNotBlank() },
                                author = author?.takeIf { it.isNotBlank() }
                            )
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }
            BookMeta(null, null)
        } catch (_: Exception) {
            BookMeta(null, null)
        }
    }
    return BookMeta(null, null)
}
