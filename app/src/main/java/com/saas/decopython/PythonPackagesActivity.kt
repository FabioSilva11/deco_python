package com.saas.decopython

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ListView
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.saas.decopython.databinding.ActivityPythonPackagesBinding
import com.saas.decopython.databinding.DialogFolderInputBinding
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.concurrent.thread

class PythonPackagesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPythonPackagesBinding
    private lateinit var packagesListView: ListView
    private lateinit var emptyStateTextView: TextView
    private lateinit var packagesAdapter: PythonPackageAdapter
    private val packageItems = mutableListOf<PythonPackageEntry>()
    private val packageNames = mutableListOf<String>()
    private var isDownloading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }

        binding = ActivityPythonPackagesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.python_packages_title)
        supportActionBar?.subtitle = getString(R.string.python_packages_subtitle)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        packagesListView = findViewById(R.id.python_packages_list_view)
        emptyStateTextView = findViewById(R.id.python_packages_empty_text)
        packagesAdapter = PythonPackageAdapter(this, packageItems)
        packagesListView.adapter = packagesAdapter
        packagesListView.setOnItemLongClickListener { _, _, position, _ ->
            val packageName = packageNames.getOrNull(position) ?: return@setOnItemLongClickListener false
            showPackageActionsDialog(packageName)
            true
        }

        binding.fab.setOnClickListener {
            showDownloadDialog()
        }

        refreshPackages()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_python_packages, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_refresh_packages -> {
                refreshPackages()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun refreshPackages() {
        runCatching {
            val rawJson = pythonModule()
                .callAttr("list_runtime_packages", packagesRoot().absolutePath)
                .toString()

            val parsed = parsePackages(rawJson)
            packageItems.clear()
            packageItems.addAll(parsed)
            packageNames.clear()
            packageNames.addAll(parsed.map { it.name })
            packagesAdapter.notifyDataSetChanged()

            if (parsed.isEmpty()) {
                emptyStateTextView.text = getString(R.string.python_packages_empty)
                emptyStateTextView.visibility = View.VISIBLE
            } else {
                emptyStateTextView.visibility = View.GONE
            }
        }.onFailure { error ->
            packageItems.clear()
            packageNames.clear()
            packagesAdapter.notifyDataSetChanged()
            emptyStateTextView.text = getString(R.string.python_packages_error, error.toMessage())
            emptyStateTextView.visibility = View.VISIBLE
        }
    }

    private fun parsePackages(rawJson: String): List<PythonPackageEntry> {
        val jsonArray = JSONArray(rawJson)
        return buildList {
            for (index in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(index)
                add(
                    PythonPackageEntry(
                        name = item.getString("name"),
                        version = item.getString("version"),
                    )
                )
            }
        }
    }

    private fun showDownloadDialog() {
        if (isDownloading) return

        val dialogBinding = DialogFolderInputBinding.inflate(layoutInflater)
        dialogBinding.folderNameLayout.hint = getString(R.string.python_package_input_hint)
        var latestQuery = ""
        var latestResolvedRequirement: String? = null
        var requestCounter = 0
        var pendingResolve: Runnable? = null

        val dialog = MaterialAlertDialogBuilder(
            this,
            R.style.ThemeOverlay_DecoPython_MaterialAlertDialog,
        )
            .setTitle(R.string.python_package_add_title)
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.folder_name_negative, null)
            .setPositiveButton(R.string.python_package_download_action, null)
            .create()

        dialogBinding.folderNameInput.doAfterTextChanged {
            val query = it?.toString()?.trim().orEmpty()
            latestQuery = query
            latestResolvedRequirement = null
            dialogBinding.folderNameLayout.error = null

            pendingResolve?.let(dialogBinding.root::removeCallbacks)
            if (query.isBlank()) {
                dialogBinding.folderNameLayout.helperText = null
                return@doAfterTextChanged
            }

            dialogBinding.folderNameLayout.helperText =
                getString(R.string.python_package_resolving_hint)

            val localRequestId = ++requestCounter
            val runnable = Runnable {
                thread {
                    val result = runCatching {
                        pythonModule().callAttr("resolve_runtime_package", query).toString()
                    }

                    runOnUiThread {
                        if (!dialog.isShowing || localRequestId != requestCounter) {
                            return@runOnUiThread
                        }

                        result.onSuccess { rawJson ->
                            val resolved = JSONObject(rawJson)
                            latestResolvedRequirement = resolved.getString("resolved_requirement")
                            dialogBinding.folderNameLayout.helperText = getString(
                                R.string.python_package_resolved_hint,
                                resolved.getString("name"),
                                resolved.getString("version"),
                            )
                        }.onFailure {
                            latestResolvedRequirement = null
                            dialogBinding.folderNameLayout.helperText =
                                getString(R.string.python_package_not_resolved_hint)
                        }
                    }
                }
            }

            pendingResolve = runnable
            dialogBinding.root.postDelayed(runnable, 400)
        }

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val requirement = dialogBinding.folderNameInput.text?.toString()?.trim().orEmpty()
                if (requirement.isBlank()) {
                    dialogBinding.folderNameLayout.error = getString(R.string.python_package_input_required)
                    return@setOnClickListener
                }

                dialogBinding.folderNameLayout.error = null
                dialog.dismiss()
                downloadPackage(
                    latestResolvedRequirement.takeIf { latestQuery == requirement } ?: requirement
                )
            }
        }

        dialog.show()
    }

    private fun downloadPackage(requirement: String) {
        isDownloading = true
        Snackbar.make(
            binding.root,
            getString(R.string.python_package_download_started, requirement),
            Snackbar.LENGTH_LONG,
        ).setAnchorView(R.id.fab).show()

        thread {
            val result = runCatching {
                pythonModule().callAttr(
                    "install_runtime_package",
                    packagesRoot().absolutePath,
                    requirement,
                ).toString()
            }

            runOnUiThread {
                isDownloading = false
                result.onSuccess { rawJson ->
                    val item = org.json.JSONObject(rawJson)
                    refreshPackages()
                    Snackbar.make(
                        binding.root,
                        getString(
                            R.string.python_package_download_success,
                            item.getString("name"),
                            item.getString("version"),
                        ),
                        Snackbar.LENGTH_LONG,
                    ).setAnchorView(R.id.fab).show()
                }.onFailure { error ->
                    Snackbar.make(
                        binding.root,
                        getString(R.string.python_package_download_error, error.toMessage()),
                        Snackbar.LENGTH_LONG,
                    ).setAnchorView(R.id.fab).show()
                }
            }
        }
    }

    private fun showPackageActionsDialog(packageName: String) {
        val actions = arrayOf(getString(R.string.python_package_remove_action))

        MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_DecoPython_MaterialAlertDialog)
            .setTitle(packageName)
            .setItems(actions) { _, which ->
                if (which == 0) {
                    removePackage(packageName)
                }
            }
            .show()
    }

    private fun removePackage(packageName: String) {
        thread {
            val result = runCatching {
                pythonModule().callAttr(
                    "uninstall_runtime_package",
                    packagesRoot().absolutePath,
                    packageName,
                )
            }

            runOnUiThread {
                result.onSuccess {
                    refreshPackages()
                    Snackbar.make(
                        binding.root,
                        getString(R.string.python_package_remove_success, packageName),
                        Snackbar.LENGTH_LONG,
                    ).setAnchorView(R.id.fab).show()
                }.onFailure { error ->
                    Snackbar.make(
                        binding.root,
                        getString(R.string.python_package_remove_error, error.toMessage()),
                        Snackbar.LENGTH_LONG,
                    ).setAnchorView(R.id.fab).show()
                }
            }
        }
    }

    private fun packagesRoot(): File = File(filesDir, "python_packages")

    private fun pythonModule() = Python.getInstance().getModule("package_manager")

    private fun Throwable.toMessage(): String {
        return message?.takeIf { it.isNotBlank() } ?: "erro desconhecido"
    }

    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, PythonPackagesActivity::class.java)
        }
    }
}
