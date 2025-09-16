package com.timursarsembayev.cubesorter

import android.os.Bundle
import android.util.TypedValue
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView

class RecordsActivity : BaseDrawerActivity() {

    private val prefs by lazy { getSharedPreferences("progress", MODE_PRIVATE) }

    private fun bestTimeKey(level: Int) = "best_time_$level"
    private fun bestMovesKey(level: Int) = "best_moves_$level"
    private fun recordDateKey(level: Int) = "record_date_$level"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentWithDrawer(R.layout.activity_records)
        populateTable()
    }

    override fun onResume() {
        super.onResume()
        populateTable()
    }

    private fun populateTable() {
        val table: TableLayout = findViewById(R.id.recordsTable)
        table.removeAllViews()
        // Header
        val header = TableRow(this)
        header.addView(makeHeaderCell(getString(R.string.records_header_level)))
        header.addView(makeHeaderCell(getString(R.string.records_header_time)))
        header.addView(makeHeaderCell(getString(R.string.records_header_moves)))
        header.addView(makeHeaderCell(getString(R.string.records_header_date)))
        table.addView(header)
        // Rows
        for (lv in 1..SorterGameView.MAX_LEVEL) {
            val row = TableRow(this)
            val bt = prefs.getLong(bestTimeKey(lv), Long.MAX_VALUE)
            val bm = prefs.getInt(bestMovesKey(lv), Int.MAX_VALUE)
            val rd = prefs.getLong(recordDateKey(lv), Long.MAX_VALUE)
            row.addView(makeCell(lv.toString()))
            row.addView(makeCell(if (bt == Long.MAX_VALUE) getString(R.string.dash) else formatElapsed(bt)))
            row.addView(makeCell(if (bm == Int.MAX_VALUE) getString(R.string.dash) else bm.toString()))
            row.addView(makeCell(formatDate(rd)))
            if (lv % 2 == 0) {
                row.setBackgroundColor(getColor(R.color.records_row_alt_bg))
            }
            table.addView(row)
        }
    }

    private fun formatElapsed(ms: Long): String {
        if (ms == Long.MAX_VALUE) return "--:--.-"
        val m = (ms / 60000).toInt()
        val s = ((ms % 60000) / 1000).toInt()
        val t = ((ms % 1000) / 100).toInt()
        return String.format("%02d:%02d.%d", m, s, t)
    }

    private fun formatDate(ms: Long): String {
        if (ms == Long.MAX_VALUE) return getString(R.string.dash)
        val fmt = java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.getDefault())
        return fmt.format(java.util.Date(ms))
    }

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
    ).toInt()

    private fun makeHeaderCell(text: String): TextView {
        val tv = TextView(this)
        tv.text = text
        tv.setPadding(dp(12), dp(8), dp(12), dp(8))
        tv.setTypeface(tv.typeface, android.graphics.Typeface.BOLD)
        tv.setTextColor(getColor(R.color.primaryColor))
        tv.textSize = 14f
        return tv
    }

    private fun makeCell(text: String): TextView {
        val tv = TextView(this)
        tv.text = text
        tv.setPadding(dp(12), dp(6), dp(12), dp(6))
        tv.setTextColor(getColor(R.color.text_color))
        tv.textSize = 13f
        return tv
    }
}
