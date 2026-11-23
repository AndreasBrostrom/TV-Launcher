package app.tvshared

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import java.util.concurrent.ExecutorService

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

fun Context.toast(text: Int) = toast(getString(text))

fun Context.toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()

/** A menu of labels and what they do. */
fun Activity.showMenu(title: String, items: List<Pair<String, () -> Unit>>) {
    AlertDialog.Builder(this)
        .setTitle(title)
        .setItems(items.map { it.first }.toTypedArray()) { _, index -> items[index].second() }
        .show()
}

/** Asks before something drastic. Cancel has focus. */
fun Activity.confirm(question: String, yes: Int = android.R.string.ok, onYes: () -> Unit) {
    AlertDialog.Builder(this)
        .setMessage(question)
        .setPositiveButton(yes) { _, _ -> onYes() }
        .setNegativeButton(android.R.string.cancel, null)
        .show()
        .getButton(AlertDialog.BUTTON_NEGATIVE).requestFocus()
}

/** Asks for a name; blank ones are ignored. */
fun Activity.askName(title: Int, current: String, onName: (String) -> Unit) {
    val input = EditText(this).apply {
        setText(current)
        setSingleLine()
        setSelection(text.length)
    }
    AlertDialog.Builder(this)
        .setTitle(title)
        .setView(input)
        .setPositiveButton(android.R.string.ok) { _, _ ->
            input.text.toString().trim().takeIf(String::isNotEmpty)?.let(onName)
        }
        .setNegativeButton(android.R.string.cancel, null)
        .show()
}

fun LinearLayout.addDivider(side: Int, top: Int, bottom: Int) {
    val line = View(context).apply { setBackgroundColor(context.getColor(R.color.card)) }
    val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dp(1))
    params.setMargins(context.dp(side), context.dp(top), context.dp(side), context.dp(bottom))
    addView(line, params)
}

/** Runs [work] on [executor], then [then] on the main thread unless the screen is gone. */
fun <T> Activity.inBackground(executor: ExecutorService, work: () -> T, then: (T) -> Unit) {
    if (executor.isShutdown) return
    executor.execute {
        val result = work()
        runOnUiThread { if (!isDestroyed) then(result) }
    }
}
