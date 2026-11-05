package com.autodj.tv

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.File

/**
 * Music on the TV itself: its storage and any USB drive plugged into it.
 * Uses both MediaStore queries (across all volumes) and direct filesystem scanning
 * so that USB drives are detected even if the TV OS hasn't indexed them into MediaStore.
 */
object DeviceMusic {
    private const val TAG = "DeviceMusic"
    private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "wav", "flac", "aac", "ogg", "opus", "wma", "aiff")

    val permission: String =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

    /**
     * Scans for music on internal storage and any attached USB drive / removable volume.
     * Blocking; call off the main thread.
     */
    fun scan(context: Context): List<Uri> {
        val out = LinkedHashSet<Uri>()

        // 1. Query MediaStore across all volumes
        runCatching {
            scanMediaStore(context, out)
        }.onFailure { Log.w(TAG, "MediaStore scan error: ${it.message}") }

        // 2. Direct filesystem scan for mounted USB drives and external volumes
        runCatching {
            scanStorageFilesystem(context, out)
        }.onFailure { Log.w(TAG, "Filesystem scan error: ${it.message}") }

        Log.i(TAG, "Found total ${out.size} audio tracks on device and USB storage")
        return out.toList()
    }

    private fun scanMediaStore(context: Context, out: MutableSet<Uri>) {
        val collections = mutableListOf<Uri>()
        if (Build.VERSION.SDK_INT >= 29) {
            collections += MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            runCatching {
                for (volumeName in MediaStore.getExternalVolumeNames(context)) {
                    collections += MediaStore.Audio.Media.getContentUri(volumeName)
                }
            }
        } else {
            collections += MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            collections += MediaStore.Audio.Media.INTERNAL_CONTENT_URI
        }

        // Relaxed selection: TV boxes often do not set IS_MUSIC or DURATION on USB drives
        val selection = "(${MediaStore.Audio.Media.MIME_TYPE} LIKE 'audio/%' OR ${MediaStore.Audio.Media.IS_MUSIC} != 0)"
        val projection = arrayOf(MediaStore.Audio.Media._ID)

        for (collection in collections.distinct()) {
            runCatching {
                context.contentResolver.query(
                    collection, projection, selection, null, "${MediaStore.Audio.Media.DATE_ADDED} DESC",
                )?.use { c ->
                    val idCol = c.getColumnIndex(MediaStore.Audio.Media._ID)
                    if (idCol != -1) {
                        while (c.moveToNext()) {
                            out += ContentUris.withAppendedId(collection, c.getLong(idCol))
                        }
                    }
                }
            }
        }
    }

    private fun scanStorageFilesystem(context: Context, out: MutableSet<Uri>) {
        val candidateRoots = LinkedHashSet<File>()

        // Collect removable storage roots from StorageManager
        if (Build.VERSION.SDK_INT >= 24) {
            val sm = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
            sm?.storageVolumes?.forEach { vol ->
                if (Build.VERSION.SDK_INT >= 30) {
                    vol.directory?.let { candidateRoots += it }
                }
            }
        }

        // Fallback: examine external files dirs from ContextCompat
        ContextCompat.getExternalFilesDirs(context, null).filterNotNull().forEach { file ->
            var current: File? = file
            while (current != null && current.parentFile != null) {
                val parent = current.parentFile
                if (parent?.name == "storage" || parent?.name == "mnt") {
                    candidateRoots += current
                    break
                }
                current = parent
            }
        }

        // Standard Android mount locations for USB drives
        listOf("/storage", "/mnt/media_rw", "/mnt/usb_storage", "/mnt/usb").forEach { path ->
            val dir = File(path)
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.forEach { sub ->
                    if (sub.isDirectory && sub.canRead() && sub.name != "self" && sub.name != "knox-emulated") {
                        candidateRoots += sub
                    }
                }
            }
        }

        // Add standard Music / Downloads folders if accessible
        runCatching {
            candidateRoots += Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            candidateRoots += Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        }

        // Walk directories looking for audio files
        for (root in candidateRoots) {
            if (!root.exists() || !root.canRead()) continue
            scanDirectory(root, out, maxDepth = 8)
        }
    }

    private fun scanDirectory(dir: File, out: MutableSet<Uri>, maxDepth: Int) {
        if (maxDepth <= 0 || !dir.canRead()) return
        val files = dir.listFiles() ?: return
        for (f in files) {
            if (f.isDirectory) {
                // Skip hidden folders and Android system directories
                if (!f.name.startsWith(".") && !f.name.equals("Android", ignoreCase = true)) {
                    scanDirectory(f, out, maxDepth - 1)
                }
            } else if (f.isFile && f.length() > 50_000) {
                val ext = f.extension.lowercase()
                if (ext in AUDIO_EXTENSIONS) {
                    out += Uri.fromFile(f)
                }
            }
        }
    }
}
