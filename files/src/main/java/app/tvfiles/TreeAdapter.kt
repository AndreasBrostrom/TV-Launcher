package app.tvfiles

import android.content.Context
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import app.tvshared.dp

/** Shows the rows of a [FileTree], indented by depth. */
class TreeAdapter : BaseAdapter() {
    var rows: List<Row> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun getCount() = rows.size

    override fun getItem(position: Int) = rows[position]

    override fun getItemId(position: Int) = position.toLong()

    override fun getView(position: Int, recycled: View?, parent: ViewGroup): View {
        val context = parent.context
        val view = recycled ?: LayoutInflater.from(context).inflate(R.layout.row, parent, false)
        val row = rows[position]
        val entry = row.entry
        view.setPaddingRelative(context.dp(12 + 24 * row.depth), 0, context.dp(16), 0)
        view.findViewById<ImageView>(R.id.chevron).apply {
            visibility = if (entry.isFolder) View.VISIBLE else View.INVISIBLE
            rotation = if (row.expanded) 90f else 0f
        }
        view.findViewById<ImageView>(R.id.icon).setImageResource(
            when {
                entry.drive != null -> R.drawable.ic_drive
                entry.isFolder -> R.drawable.ic_folder
                else -> R.drawable.ic_file
            },
        )
        view.findViewById<TextView>(R.id.name).text = entry.drive?.label ?: entry.file.name
        view.findViewById<TextView>(R.id.size).text = when {
            entry.drive != null -> space(context, entry.drive)
            entry.isFolder -> ""
            else -> Formatter.formatShortFileSize(context, entry.size)
        }
        return view
    }

    private fun space(context: Context, drive: Drive): String =
        if (drive.total == 0L) context.getString(R.string.unknown_space)
        else context.getString(
            R.string.space,
            Formatter.formatShortFileSize(context, drive.free),
            Formatter.formatShortFileSize(context, drive.total),
        )
}
