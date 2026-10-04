package com.example.wallpaperslideshow

import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile

class MainActivity : AppCompatActivity() {

    private lateinit var folderLabel: TextView

    private val pickFolder =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Throwable) {
                    // Some providers do not support persistable grants.
                }
                Prefs.setFolderUri(this, uri.toString())
                updateFolderLabel()
                Toast.makeText(this, R.string.folder_saved, Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        folderLabel = findViewById(R.id.folderLabel)

        findViewById<Button>(R.id.btnPickFolder).setOnClickListener {
            pickFolder.launch(null)
        }

        findViewById<Button>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<Button>(R.id.btnSetWallpaper).setOnClickListener {
            if (Prefs.folderUri(this) == null) {
                Toast.makeText(this, R.string.pick_folder_first, Toast.LENGTH_LONG).show()
            } else {
                openLiveWallpaperChooser()
            }
        }

        updateFolderLabel()
    }

    override fun onResume() {
        super.onResume()
        updateFolderLabel()
    }

    private fun updateFolderLabel() {
        val uriString = Prefs.folderUri(this)
        if (uriString.isNullOrEmpty()) {
            folderLabel.text = getString(R.string.no_folder_selected)
            return
        }
        val name = try {
            DocumentFile.fromTreeUri(this, Uri.parse(uriString))?.name
        } catch (_: Throwable) {
            null
        }
        folderLabel.text = name ?: uriString
    }

    private fun openLiveWallpaperChooser() {
        val direct = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
            putExtra(
                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                ComponentName(this@MainActivity, SlideshowWallpaperService::class.java)
            )
        }
        try {
            startActivity(direct)
            return
        } catch (_: ActivityNotFoundException) {
        }

        try {
            startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
            return
        } catch (_: ActivityNotFoundException) {
        }

        try {
            startActivity(Intent(Intent.ACTION_SET_WALLPAPER))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.no_wallpaper_picker, Toast.LENGTH_LONG).show()
        }
    }
}
