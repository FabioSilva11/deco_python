package com.saas.decopython

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView

class StorageEntryAdapter(
    context: Context,
    items: MutableList<StorageEntry>,
) : ArrayAdapter<StorageEntry>(context, 0, items) {

    private val inflater = LayoutInflater.from(context)

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: inflater.inflate(R.layout.item_storage_entry, parent, false)
        val entry = getItem(position)

        view.findViewById<TextView>(R.id.entry_name_text).text = entry?.name.orEmpty()
        view.findViewById<TextView>(R.id.entry_type_text).text = context.getString(
            if (entry?.isDirectory == true) R.string.entry_type_folder else R.string.entry_type_file,
        )
        entry?.let {
            StorageIconHelper.applyEntryIcon(view.findViewById<ImageView>(R.id.entry_icon_view), it)
        }

        return view
    }
}
