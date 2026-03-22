package com.verite.tmr

import android.content.Context
import android.net.Uri
import android.util.Log
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.apache.poi.xwpf.extractor.XWPFWordExtractor
import org.apache.poi.xwpf.usermodel.XWPFDocument

object DocumentTextExtractor {
    private const val TAG = "DocumentExtractor"
    private var isPdfBoxInitialized = false

    fun init(context: Context) {
        if (!isPdfBoxInitialized) {
            try {
                PDFBoxResourceLoader.init(context.applicationContext)
                isPdfBoxInitialized = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize PDFBox", e)
            }
        }
    }

    fun extractText(context: Context, uri: Uri): String? {
        val resolver = context.contentResolver
        val mimeType = resolver.getType(uri) ?: ""
        val uriString = uri.toString().lowercase()
        
        return try {
            when {
                mimeType.contains("pdf") || uriString.endsWith(".pdf") -> {
                    extractFromPdf(context, uri)
                }
                mimeType.contains("wordprocessingml.document") || uriString.endsWith(".docx") -> {
                    extractFromDocx(context, uri)
                }
                mimeType.contains("text/plain") || uriString.endsWith(".txt") -> {
                    resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }
                else -> {
                    Log.w(TAG, "Unsupported file type: $mimeType. Attempting raw text extraction.")
                    // Fallback to text parsing if possible
                    resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Extraction failed: ${e.message}", e)
            null
        }
    }

    private fun extractFromPdf(context: Context, uri: Uri): String? {
        init(context)
        return context.contentResolver.openInputStream(uri)?.use { inputStream ->
            PDDocument.load(inputStream).use { document ->
                val stripper = PDFTextStripper()
                val text = stripper.getText(document)
                // Returning extracted content
                text
            }
        }
    }

    private fun extractFromDocx(context: Context, uri: Uri): String? {
        return context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val document = XWPFDocument(inputStream)
            val extractor = XWPFWordExtractor(document)
            val text = extractor.text
            extractor.close()
            text
        }
    }
}
