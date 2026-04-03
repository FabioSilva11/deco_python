package com.saas.decopython

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.chaquo.python.Python
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.saas.decopython.databinding.ActivityFolderContentBinding
import com.saas.decopython.databinding.DialogFolderInputBinding
import org.json.JSONArray
import java.io.File

class FolderContentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFolderContentBinding
    private lateinit var entriesListView: ListView
    private lateinit var emptyStateTextView: TextView
    private lateinit var entriesAdapter: StorageEntryAdapter
    private val entries = mutableListOf<StorageEntry>()
    private lateinit var currentDirectoryPath: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        currentDirectoryPath = intent.getStringExtra(EXTRA_DIRECTORY_PATH).orEmpty()
        if (currentDirectoryPath.isBlank()) {
            finish()
            return
        }

        binding = ActivityFolderContentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.app_name)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        entriesListView = findViewById(R.id.folder_content_list_view)
        emptyStateTextView = findViewById(R.id.empty_state_text)
        entriesAdapter = StorageEntryAdapter(this, entries)
        entriesListView.adapter = entriesAdapter

        entriesListView.setOnItemClickListener { _, _, position, _ ->
            val entry = entries.getOrNull(position) ?: return@setOnItemClickListener
            if (entry.isDirectory) {
                openDirectory(File(currentDirectoryPath, entry.name).absolutePath)
            } else {
                openFile(File(currentDirectoryPath, entry.name).absolutePath)
            }
        }

        entriesListView.setOnItemLongClickListener { _, _, position, _ ->
            val entry = entries.getOrNull(position) ?: return@setOnItemLongClickListener false
            showEntryActionsDialog(entry)
            true
        }

        binding.fab.setOnClickListener {
            showCreateOptionsDialog()
        }

        refreshEntries()
    }

    override fun onResume() {
        super.onResume()
        refreshEntries()
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.menu_folder_content, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_refresh_content -> {
                refreshEntries()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun refreshEntries() {
        runCatching {
            val rawJson = pythonModule()
                .callAttr("list_entries", currentDirectoryPath)
                .toString()

            val parsedEntries = parseEntries(rawJson)
            entries.clear()
            entries.addAll(parsedEntries)
            entriesAdapter.notifyDataSetChanged()

            if (parsedEntries.isEmpty()) {
                emptyStateTextView.text = getString(R.string.empty_content_list)
                emptyStateTextView.visibility = View.VISIBLE
            } else {
                emptyStateTextView.visibility = View.GONE
            }
        }.onFailure { error ->
            emptyStateTextView.text = getString(R.string.content_load_error, error.toMessage())
            emptyStateTextView.visibility = View.VISIBLE
            entries.clear()
            entriesAdapter.notifyDataSetChanged()
        }
    }

    private fun parseEntries(rawJson: String): List<StorageEntry> {
        val jsonArray = JSONArray(rawJson)
        return buildList {
            for (index in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(index)
                add(
                    StorageEntry(
                        name = item.getString("name"),
                        isDirectory = item.getBoolean("is_dir"),
                    )
                )
            }
        }
    }

    private fun showCreateOptionsDialog() {
        val actions = arrayOf(
            getString(R.string.create_folder),
            getString(R.string.create_file),
        )

        MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_DecoPython_MaterialAlertDialog)
            .setTitle(R.string.create_inside_folder)
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> showNameInputDialog(
                        titleRes = R.string.folder_name_title,
                        hintRes = R.string.folder_name_hint,
                        positiveButtonRes = R.string.folder_name_positive,
                    ) { name ->
                        createFolder(name)
                    }
                    1 -> showNameInputDialog(
                        titleRes = R.string.file_name_title,
                        hintRes = R.string.file_name_hint,
                        positiveButtonRes = R.string.create_file_positive,
                    ) { name ->
                        createFile(name)
                    }
                }
            }
            .show()
    }

    private fun showEntryActionsDialog(entry: StorageEntry) {
        val actions = arrayOf(
            getString(R.string.rename_folder_action),
            getString(R.string.delete_folder_action),
        )

        MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_DecoPython_MaterialAlertDialog)
            .setTitle(entry.name)
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> showNameInputDialog(
                        titleRes = R.string.rename_entry_title,
                        hintRes = if (entry.isDirectory) {
                            R.string.folder_name_hint
                        } else {
                            R.string.file_name_hint
                        },
                        positiveButtonRes = R.string.rename_folder_positive,
                        initialValue = entry.name,
                    ) { newName ->
                        renameEntry(entry, newName)
                    }
                    1 -> showDeleteEntryDialog(entry)
                }
            }
            .show()
    }

    private fun showDeleteEntryDialog(entry: StorageEntry) {
        MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_DecoPython_MaterialAlertDialog)
            .setTitle(R.string.delete_entry_title)
            .setMessage(getString(R.string.delete_entry_message, entry.name))
            .setNegativeButton(R.string.folder_name_negative, null)
            .setPositiveButton(R.string.delete_folder_positive) { _, _ ->
                deleteEntry(entry)
            }
            .show()
    }

    private fun showNameInputDialog(
        titleRes: Int,
        hintRes: Int,
        positiveButtonRes: Int,
        initialValue: String = "",
        onConfirm: (String) -> Unit,
    ) {
        val dialogBinding = DialogFolderInputBinding.inflate(layoutInflater)
        dialogBinding.folderNameLayout.hint = getString(hintRes)
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
                val value = dialogBinding.folderNameInput.text?.toString()?.trim().orEmpty()
                if (value.isBlank()) {
                    dialogBinding.folderNameLayout.error = getString(R.string.item_name_required)
                    return@setOnClickListener
                }

                dialogBinding.folderNameLayout.error = null
                onConfirm(value)
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun createFolder(folderName: String) {
        runCatching {
            pythonModule().callAttr("create_folder", currentDirectoryPath, folderName)
            refreshEntries()
            Snackbar.make(
                binding.root,
                getString(R.string.folder_created_inside, folderName),
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

    private fun createFile(fileName: String) {
        runCatching {
            val createdPath = pythonModule().callAttr("create_file", currentDirectoryPath, fileName)
                .toString()
            refreshEntries()
            openFile(createdPath)
            Snackbar.make(
                binding.root,
                getString(R.string.file_created, fileName),
                Snackbar.LENGTH_LONG,
            ).setAnchorView(R.id.fab).show()
        }.onFailure { error ->
            Snackbar.make(
                binding.root,
                getString(R.string.file_creation_error, error.toMessage()),
                Snackbar.LENGTH_LONG,
            ).setAnchorView(R.id.fab).show()
        }
    }

    private fun renameEntry(entry: StorageEntry, newName: String) {
        runCatching {
            pythonModule().callAttr("rename_entry", currentDirectoryPath, entry.name, newName)
            refreshEntries()
            Snackbar.make(
                binding.root,
                getString(R.string.entry_renamed, newName),
                Snackbar.LENGTH_LONG,
            ).setAnchorView(R.id.fab).show()
        }.onFailure { error ->
            Snackbar.make(
                binding.root,
                getString(R.string.entry_rename_error, error.toMessage()),
                Snackbar.LENGTH_LONG,
            ).setAnchorView(R.id.fab).show()
        }
    }

    private fun deleteEntry(entry: StorageEntry) {
        runCatching {
            pythonModule().callAttr("delete_entry", currentDirectoryPath, entry.name)
            refreshEntries()
            Snackbar.make(
                binding.root,
                getString(R.string.entry_deleted, entry.name),
                Snackbar.LENGTH_LONG,
            ).setAnchorView(R.id.fab).show()
        }.onFailure { error ->
            Snackbar.make(
                binding.root,
                getString(R.string.entry_delete_error, error.toMessage()),
                Snackbar.LENGTH_LONG,
            ).setAnchorView(R.id.fab).show()
        }
    }

    private fun openDirectory(path: String) {
        startActivity(createIntent(this, path))
    }

    private fun openFile(path: String) {
        startActivity(CodeEditorActivity.createIntent(this, path))
    }

    private fun pythonModule() = Python.getInstance().getModule("list_provider")

    private fun Throwable.toMessage(): String {
        return message?.takeIf { it.isNotBlank() } ?: "erro desconhecido"
    }

    companion object {
        private const val EXTRA_DIRECTORY_PATH = "extra_directory_path"

        fun createIntent(context: Context, directoryPath: String): Intent {
            return Intent(context, FolderContentActivity::class.java).apply {
                putExtra(EXTRA_DIRECTORY_PATH, directoryPath)
            }
        }
    }
}
