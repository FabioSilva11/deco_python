package com.saas.decopython

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayoutMediator
import com.saas.decopython.databinding.ActivityCodeEditorBinding
import org.json.JSONObject
import java.io.File
import kotlin.concurrent.thread

class CodeEditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCodeEditorBinding
    private lateinit var pagerAdapter: CodeEditorPagerAdapter
    private lateinit var filePath: String
    private lateinit var packagesRoot: String
    private var currentCode: String = ""
    private var currentLog: String = ""
    private var isCompiling = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        filePath = intent.getStringExtra(EXTRA_FILE_PATH).orEmpty()
        if (filePath.isBlank()) {
            finish()
            return
        }

        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }
        packagesRoot = File(filesDir, "python_packages").absolutePath

        binding = ActivityCodeEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.app_name)
        supportActionBar?.subtitle = filePath.substringAfterLast('/')
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        pagerAdapter = CodeEditorPagerAdapter { newCode ->
            currentCode = newCode
        }
        binding.codePager.adapter = pagerAdapter

        TabLayoutMediator(binding.codeTabs, binding.codePager) { tab, position ->
            tab.text = if (position == CodeEditorPagerAdapter.PAGE_EDITOR) {
                getString(R.string.editor_tab)
            } else {
                getString(R.string.log_tab)
            }
        }.attach()

        loadFileContent()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_code_editor, menu)
        menu.findItem(R.id.action_compile)?.apply {
            isEnabled = !isCompiling
            title = if (isCompiling) {
                getString(R.string.compiling_action)
            } else {
                getString(R.string.compile_action)
            }
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_compile -> {
                compileCode()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onPause() {
        super.onPause()
        saveCurrentCodeSilently()
    }

    private fun loadFileContent() {
        runCatching {
            pythonModule().callAttr("read_text_file", filePath).toString()
        }.onSuccess { content ->
            currentCode = content
            pagerAdapter.setCodeText(content)
            currentLog = getString(R.string.log_waiting_message)
            pagerAdapter.setLogText(currentLog)
        }.onFailure { error ->
            currentCode = ""
            pagerAdapter.setCodeText("")
            currentLog = getString(R.string.file_read_error, error.toMessage())
            pagerAdapter.setLogText(currentLog)
        }
    }

    private fun compileCode() {
        if (isCompiling) return

        isCompiling = true
        invalidateOptionsMenu()
        currentLog = getString(R.string.compiling_log_message)
        pagerAdapter.setLogText(currentLog)
        binding.codePager.setCurrentItem(CodeEditorPagerAdapter.PAGE_LOG, true)

        val codeToRun = currentCode

        thread {
            val result = runCatching {
                pythonModule().callAttr("write_text_file", filePath, codeToRun)
                pythonModule().callAttr("run_python_code", filePath, codeToRun, packagesRoot).toString()
            }

            runOnUiThread {
                isCompiling = false
                invalidateOptionsMenu()

                result.onSuccess { rawJson ->
                    val json = JSONObject(rawJson)
                    currentLog = json.optString("output", getString(R.string.log_empty_output))
                    pagerAdapter.setLogText(currentLog)

                    Snackbar.make(
                        binding.root,
                        if (json.optBoolean("success")) {
                            getString(R.string.compile_success_message)
                        } else {
                            getString(R.string.compile_error_message)
                        },
                        Snackbar.LENGTH_LONG,
                    ).show()
                }.onFailure { error ->
                    currentLog = getString(R.string.compile_exception_message, error.toMessage())
                    pagerAdapter.setLogText(currentLog)
                    Snackbar.make(
                        binding.root,
                        getString(R.string.compile_error_message),
                        Snackbar.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    private fun saveCurrentCodeSilently() {
        runCatching {
            pythonModule().callAttr("write_text_file", filePath, currentCode)
        }
    }

    private fun pythonModule() = Python.getInstance().getModule("code_runner")

    private fun Throwable.toMessage(): String {
        return message?.takeIf { it.isNotBlank() } ?: "erro desconhecido"
    }

    companion object {
        private const val EXTRA_FILE_PATH = "extra_file_path"

        fun createIntent(context: Context, filePath: String): Intent {
            return Intent(context, CodeEditorActivity::class.java).apply {
                putExtra(EXTRA_FILE_PATH, filePath)
            }
        }
    }
}
