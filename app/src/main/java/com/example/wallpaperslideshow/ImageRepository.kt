package com.example.wallpaperslideshow

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

/**
 * Walks a SAF tree (the folder the user picked) and collects every image
 * it can find - including images inside sub-folders.
 */
object ImageRepository {

    private val EXTENSIONS = setOf(
        "jpg", "jpeg", "png", "webp", "bmp", "gif", "heic", "heif", "avif"
    )

    fun listImages(context: Context, treeUri: Uri?): List<Uri> {
        if (treeUri == null) return emptyList()
        val root = try {
            DocumentFile.fromTreeUri(context, treeUri)
        } catch (t: Throwable) {
            null
        } ?: return emptyList()

        val out = ArrayList<Uri>(128)
        collect(root, out)
        out.sortBy { it.toString() }   // stable, predictable order
        return out
    }

    private fun collect(dir: DocumentFile, out: MutableList<Uri>) {
        val children = try {
            dir.listFiles()
        } catch (t: Throwable) {
            return
        }
        for (child in children) {
            when {
                child.isDirectory -> collect(child, out)
                isImage(child)    -> out.add(child.uri)
            }
        }
    }

    private fun isImage(file: DocumentFile): Boolean {
        val type = file.type
        if (type != null && type.startsWith("image/")) return true
        val name = file.name?.lowercase() ?: return false
        val dot = name.lastIndexOf('.')
        if (dot < 0) return false
        return name.substring(dot + 1) in EXTENSIONS
    }
}
