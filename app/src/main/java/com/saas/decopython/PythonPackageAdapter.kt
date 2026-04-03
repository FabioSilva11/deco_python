package com.saas.decopython

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView

class PythonPackageAdapter(
    context: Context,
    items: MutableList<PythonPackageEntry>,
) : ArrayAdapter<PythonPackageEntry>(context, 0, items) {

    private val inflater = LayoutInflater.from(context)

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: inflater.inflate(R.layout.item_python_package, parent, false)
        val item = getItem(position)

        view.findViewById<TextView>(R.id.package_name_text).text = item?.name.orEmpty()
        view.findViewById<TextView>(R.id.package_version_text).text =
            context.getString(R.string.package_version_format, item?.version.orEmpty())
        StorageIconHelper.applyPackageIcon(view.findViewById<ImageView>(R.id.package_icon_view))

        return view
    }
}
