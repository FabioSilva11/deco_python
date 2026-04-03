package com.saas.decopython

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView

class FolderListAdapter(
    context: Context,
    items: MutableList<String>,
) : ArrayAdapter<String>(context, 0, items) {

    private val inflater = LayoutInflater.from(context)
    private var folderMode = true

    fun setFolderMode(isFolderMode: Boolean) {
        folderMode = isFolderMode
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: inflater.inflate(R.layout.item_folder, parent, false)
        val folderName = getItem(position).orEmpty()

        view.findViewById<TextView>(R.id.folder_name_text).text = folderName
        StorageIconHelper.applyRootIcon(
            view.findViewById<ImageView>(R.id.folder_icon_view),
            folderMode,
        )
        return view
    }
}
