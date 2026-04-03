package com.saas.decopython

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.ListView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import android.view.Menu
import android.view.MenuItem
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.saas.decopython.databinding.ActivityMainBinding
import com.saas.decopython.databinding.DialogFolderInputBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var foldersListView: ListView
    private lateinit var foldersAdapter: FolderListAdapter
    private val displayItems = mutableListOf<String>()
    private val folderNames = mutableListOf<String>()
    private var showingFolderEntries = false

    private val allFilesAccessLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            refreshFolderList()
        }

    private val legacyStoragePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            refreshFolderList()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.app_name)
        supportActionBar?.subtitle = null

        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }

        foldersListView = findViewById(R.id.python_list_view)
        foldersAdapter = FolderListAdapter(this, displayItems)
        foldersListView.adapter = foldersAdapter
        foldersListView.setOnItemClickListener { _, _, position, _ ->
            if (!showingFolderEntries || position !in folderNames.indices) {
                return@setOnItemClickListener
            }

            openFolder(folderNames[position])
        }
        foldersListView.setOnItemLongClickListener { _, _, position, _ ->
            if (!showingFolderEntries || position !in folderNames.indices) {
                return@setOnItemLongClickListener false
            }

            showFolderActionsDialog(folderNames[position])
            true
        }

        binding.fab.setOnClickListener {
            if (hasStorageAccess()) {
                showFolderInputDialog(
                    titleRes = R.string.folder_name_title,
                    positiveButtonRes = R.string.folder_name_positive,
                ) { folderName ->
                    createFolder(folderName)
                }
            } else {
                requestStorageAccess()
            }
        }

        refreshFolderList()

        if (!hasStorageAccess()) {
            requestStorageAccess()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onResume() {
        super.onResume()
        refreshFolderList()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                refreshFolderList()
                true
            }
            R.id.action_storage_access -> {
                requestStorageAccess()
                true
            }
            R.id.action_python_packages -> {
                startActivity(PythonPackagesActivity.createIntent(this))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun refreshFolderList() {
        if (!hasStorageAccess()) {
            updateList(
                listOf(
                    getString(R.string.storage_permission_denied),
                    getString(R.string.tap_fab_after_permission),
                ),
                areFolders = false,
            )
            return
        }

        runCatching {
            val folders = pythonModule()
                .callAttr("list_directories", ensureBaseDirectory())
                .asList()
                .map { it.toString() }

            if (folders.isEmpty()) {
                updateList(listOf(getString(R.string.empty_folder_list)), areFolders = false)
            } else {
                updateList(folders, areFolders = true)
            }
        }.onFailure { error ->
            updateList(
                listOf(getString(R.string.base_folder_error, error.toMessage())),
                areFolders = false,
            )
        }
    }

    private fun updateList(items: List<String>, areFolders: Boolean) {
        showingFolderEntries = areFolders
        foldersAdapter.setFolderMode(areFolders)
        folderNames.clear()
        if (areFolders) {
            folderNames.addAll(items)
        }

        displayItems.clear()
        displayItems.addAll(items)
        foldersAdapter.notifyDataSetChanged()
    }

    private fun showFolderInputDialog(
        titleRes: Int,
        positiveButtonRes: Int,
        initialValue: String = "",
        onConfirm: (String) -> Unit,
    ) {
        val dialogBinding = DialogFolderInputBinding.inflate(layoutInflater)
        dialogBinding.folderNameInput.setText(initialValue)
        dialogBinding.folderNameInput.setSelection(dialogBinding.folderNameInput.text?.length ?: 0)

        val dialog = MaterialAlertDialogBuilder(
            this,
            R.style.ThemeOverlay_DecoPython_MaterialAlertDialog,
        )
            .setTitle(titleRes)
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.folder_name_negative, null)
            .setPositiveButton(positiveButtonRes, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val folderName = dialogBinding.folderNameInput.text?.toString()?.trim().orEmpty()
                if (folderName.isBlank()) {
                    dialogBinding.folderNameLayout.error = getString(R.string.folder_name_required)
                    return@setOnClickListener
                }

                dialogBinding.folderNameLayout.error = null
                onConfirm(folderName)
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun createFolder(folderName: String) {
        if (!hasStorageAccess()) {
            requestStorageAccess()
            return
        }

        runCatching {
            val createdPath = pythonModule().callAttr(
                "create_folder",
                ensureBaseDirectory(),
                folderName,
            ).toString()

            refreshFolderList()
            openDirectory(createdPath)

            Snackbar.make(
                binding.root,
                getString(R.string.folder_created, createdPath),
                Snackbar.LENGTH_LONG,
            ).setAnchorView(R.id.fab).show()
        }.onFailure { error ->
            Snackbar.make(
                binding.root,
                getString(R.string.folder_creation_error, error.toMessage()),
                Snackbar.LENGTH_LONG,
            ).setAnchorView(R.id.fab).show()
        }
    }

    private fun showFolderActionsDialog(folderName: String) {
        val actions = arrayOf(
            getString(R.string.rename_folder_action),
            getString(R.string.delete_folder_action),
        )

        MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_DecoPython_MaterialAlertDialog)
            .setTitle(folderName)
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> showFolderInputDialog(
                        titleRes = R.string.rename_folder_title,
                        positiveButtonRes = R.string.rename_folder_positive,
                        initialValue = folderName,
                    ) { newName ->
                        renameFolder(folderName, newName)
                    }
                    1 -> showDeleteFolderDialog(folderName)
                }
            }
            .show()
    }

    private fun renameFolder(oldName: String, newName: String) {
        runCatching {
            val renamedPath = pythonModule().callAttr(
                "rename_folder",
                ensureBaseDirectory(),
                oldName,
                newName,
            ).toString()

            refreshFolderList()

            Snackbar.make(
                binding.root,
                getString(R.string.folder_renamed, renamedPath),
                Snackbar.LENGTH_LONG,
            ).setAnchorView(R.id.fab).show()
        }.onFailure { error ->
            Snackbar.make(
                binding.root,
                getString(R.string.folder_rename_error, error.toMessage()),
                Snackbar.LENGTH_LONG,
            ).setAnchorView(R.id.fab).show()
        }
    }

    private fun showDeleteFolderDialog(folderName: String) {
        MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_DecoPython_MaterialAlertDialog)
            .setTitle(R.string.delete_folder_title)
            .setMessage(getString(R.string.delete_folder_message, folderName))
            .setNegativeButton(R.string.folder_name_negative, null)
            .setPositiveButton(R.string.delete_folder_positive) { _, _ ->
                deleteFolder(folderName)
            }
            .show()
    }

    private fun deleteFolder(folderName: String) {
        runCatching {
            pythonModule().callAttr("delete_folder", ensureBaseDirectory(), folderName)

            refreshFolderList()

            Snackbar.make(
                binding.root,
                getString(R.string.folder_deleted, folderName),
                Snackbar.LENGTH_LONG,
            ).setAnchorView(R.id.fab).show()
        }.onFailure { error ->
            Snackbar.make(
                binding.root,
                getString(R.string.folder_delete_error, error.toMessage()),
                Snackbar.LENGTH_LONG,
            ).setAnchorView(R.id.fab).show()
        }
    }

    private fun requestStorageAccess() {
        if (hasStorageAccess()) {
            refreshFolderList()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            openAllFilesAccessSettings()
            return
        }

        val missingPermissions = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        ).filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            refreshFolderList()
        } else {
            legacyStoragePermissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    private fun openAllFilesAccessSettings() {
        val appSettingsIntent = Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:$packageName"),
        )
        val fallbackIntent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        val intent = if (appSettingsIntent.resolveActivity(packageManager) != null) {
            appSettingsIntent
        } else {
            fallbackIntent
        }

        allFilesAccessLauncher.launch(intent)
    }

    private fun hasStorageAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun pythonModule() = Python.getInstance().getModule("list_provider")

    private fun ensureBaseDirectory(): String {
        return pythonModule()
            .callAttr("ensure_base_dir", getBaseDirectory().absolutePath)
            .toString()
    }

    @Suppress("DEPRECATION")
    private fun getBaseDirectory(): File {
        return File(Environment.getExternalStorageDirectory(), "DecoPython")
    }

    private fun openFolder(folderName: String) {
        openDirectory(File(ensureBaseDirectory(), folderName).absolutePath)
    }

    private fun openDirectory(directoryPath: String) {
        startActivity(FolderContentActivity.createIntent(this, directoryPath))
    }

    private fun Throwable.toMessage(): String {
        return message?.takeIf { it.isNotBlank() } ?: "erro desconhecido"
    }
}
