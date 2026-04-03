package com.saas.decopython

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.RecyclerView
import com.saas.decopython.databinding.PageCodeEditorBinding
import com.saas.decopython.databinding.PageExecutionLogBinding

class CodeEditorPagerAdapter(
    private val onCodeChanged: (String) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var codeText: String = ""
    private var logText: String = ""
    private var editorHolder: EditorViewHolder? = null
    private var logHolder: LogViewHolder? = null

    override fun getItemCount(): Int = 2

    override fun getItemViewType(position: Int): Int = position

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            PAGE_EDITOR -> {
                val binding = PageCodeEditorBinding.inflate(inflater, parent, false)
                EditorViewHolder(binding, onCodeChanged).also {
                    editorHolder = it
                }
            }

            else -> {
                val binding = PageExecutionLogBinding.inflate(inflater, parent, false)
                LogViewHolder(binding).also {
                    logHolder = it
                }
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is EditorViewHolder -> holder.bind(codeText)
            is LogViewHolder -> holder.bind(logText)
        }
    }

    fun setCodeText(value: String) {
        codeText = value
        editorHolder?.bind(value)
    }

    fun setLogText(value: String) {
        logText = value
        logHolder?.bind(value)
    }

    private class EditorViewHolder(
        private val binding: PageCodeEditorBinding,
        onCodeChanged: (String) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.codeEditorInput.doAfterTextChanged {
                onCodeChanged(it?.toString().orEmpty())
            }
        }

        fun bind(value: String) {
            if (binding.codeEditorInput.text?.toString() != value) {
                binding.codeEditorInput.setText(value)
                binding.codeEditorInput.setSelection(binding.codeEditorInput.text?.length ?: 0)
            }
        }
    }

    private class LogViewHolder(
        private val binding: PageExecutionLogBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(value: String) {
            binding.executionLogText.text = value
        }
    }

    companion object {
        const val PAGE_EDITOR = 0
        const val PAGE_LOG = 1
    }
}
