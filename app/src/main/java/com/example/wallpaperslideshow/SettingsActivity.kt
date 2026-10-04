package com.example.wallpaperslideshow

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setTitle(R.string.settings_title)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settingsContainer, SettingsFragment())
                .commit()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}

class SettingsFragment : PreferenceFragmentCompat() {

    private val pickFolder =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                try {
                    requireContext().contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Throwable) {
                }
                Prefs.setFolderUri(requireContext(), uri.toString())
                updateFolderSummary()
            }
        }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        PreferenceManager.setDefaultValues(requireContext(), R.xml.preferences, false)
        setPreferencesFromResource(R.xml.preferences, rootKey)

        findPreference<Preference>(KEY_PICK_FOLDER)?.setOnPreferenceClickListener {
            pickFolder.launch(null)
            true
        }
    }

    override fun onResume() {
        super.onResume()
        updateFolderSummary()
    }

    private fun updateFolderSummary() {
        val pref = findPreference<Preference>(KEY_CURRENT_FOLDER) ?: return
        val uriString = Prefs.folderUri(requireContext())
        if (uriString.isNullOrEmpty()) {
            pref.summary = getString(R.string.no_folder_selected)
            return
        }
        val name = try {
            DocumentFile.fromTreeUri(requireContext(), Uri.parse(uriString))?.name
        } catch (_: Throwable) {
            null
        }
        pref.summary = name ?: uriString
    }

    private companion object {
        const val KEY_PICK_FOLDER = "pref_pick_folder"
        const val KEY_CURRENT_FOLDER = "folder_uri"
    }
}
