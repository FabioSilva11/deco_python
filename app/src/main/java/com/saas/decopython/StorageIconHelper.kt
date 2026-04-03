package com.saas.decopython

import android.content.res.ColorStateList
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.mikepenz.iconics.IconicsDrawable

object StorageIconHelper {

    private val codeExtensions = setOf(
        "c", "cpp", "cs", "css", "go", "h", "hpp", "html", "java", "js", "json", "kt",
        "kts", "md", "php", "py", "rb", "rs", "sh", "sql", "toml", "ts", "tsx", "txt",
        "xml", "yaml", "yml",
    )
    private val imageExtensions = setOf("bmp", "gif", "heic", "jpeg", "jpg", "png", "svg", "webp")
    private val audioExtensions = setOf("aac", "flac", "m4a", "mp3", "ogg", "wav")
    private val videoExtensions = setOf("3gp", "avi", "mkv", "mov", "mp4", "webm")
    private val archiveExtensions = setOf("7z", "bz2", "gz", "rar", "tar", "xz", "zip")
    private val spreadsheetExtensions = setOf("csv", "ods", "xls", "xlsx")
    private val documentExtensions = setOf("doc", "docx", "odt")
    private val presentationExtensions = setOf("odp", "ppt", "pptx")

    fun applyRootIcon(target: ImageView, isFolderMode: Boolean) {
        val iconKey = if (isFolderMode) "faw-folder" else "faw-info-circle"
        val colorRes = if (isFolderMode) R.color.icon_folder_color else R.color.icon_generic_color
        applyIcon(target, iconKey, colorRes)
    }

    fun applyEntryIcon(target: ImageView, entry: StorageEntry) {
        val iconKey = when {
            entry.isDirectory -> "faw-folder"
            extensionOf(entry.name) == "pdf" -> "faw-file-pdf"
            extensionOf(entry.name) in spreadsheetExtensions -> "faw-file-excel"
            extensionOf(entry.name) in documentExtensions -> "faw-file-word"
            extensionOf(entry.name) in presentationExtensions -> "faw-file-powerpoint"
            extensionOf(entry.name) in imageExtensions -> "faw-file-image"
            extensionOf(entry.name) in audioExtensions -> "faw-file-audio"
            extensionOf(entry.name) in videoExtensions -> "faw-file-video"
            extensionOf(entry.name) in archiveExtensions -> "faw-file-archive"
            extensionOf(entry.name) in codeExtensions -> "faw-file-code"
            else -> "faw-file-alt"
        }

        val colorRes = when {
            entry.isDirectory -> R.color.icon_folder_color
            extensionOf(entry.name) in imageExtensions ||
                extensionOf(entry.name) in audioExtensions ||
                extensionOf(entry.name) in videoExtensions -> R.color.icon_document_color
            extensionOf(entry.name) == "pdf" ||
                extensionOf(entry.name) in spreadsheetExtensions ||
                extensionOf(entry.name) in documentExtensions ||
                extensionOf(entry.name) in presentationExtensions ||
                extensionOf(entry.name) in archiveExtensions ||
                extensionOf(entry.name) in codeExtensions -> R.color.icon_folder_color
            else -> R.color.icon_generic_color
        }

        applyIcon(target, iconKey, colorRes)
    }

    private fun applyIcon(target: ImageView, iconKey: String, colorRes: Int) {
        val color = ContextCompat.getColor(target.context, colorRes)

        target.setImageDrawable(
            IconicsDrawable(target.context, iconKey).apply {
                val density = target.resources.displayMetrics.density
                sizeXPx = (20 * density).toInt()
                sizeYPx = (20 * density).toInt()
                paddingPx = density.toInt()
                colorList = ColorStateList.valueOf(color)
            }
        )
    }

    private fun extensionOf(fileName: String): String {
        return fileName.substringAfterLast('.', "").lowercase()
    }
}
