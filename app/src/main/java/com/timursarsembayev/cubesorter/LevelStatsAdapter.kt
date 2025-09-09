package com.timursarsembayev.cubesorter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

// Добавлено поле dateMs
data class LevelStat(val level: Int, val timeMs: Long, val moves: Int, val dateMs: Long)

class LevelStatsAdapter(
    private var items: List<LevelStat>,
    private val formatTime: (Long) -> String,
    private val formatDate: (Long) -> String,
    private val onClick: (Int) -> Unit
) : RecyclerView.Adapter<LevelStatsAdapter.VH>() {

    fun update(newItems: List<LevelStat>) {
        items = newItems
        notifyDataSetChanged()
    }

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvLevel: TextView = v.findViewById(R.id.textLv)
        val tvTime: TextView = v.findViewById(R.id.textLvTime)
        val tvMoves: TextView = v.findViewById(R.id.textLvMoves)
        val tvDate: TextView = v.findViewById(R.id.textLvDate)
        init { v.setOnClickListener { onClick(items[bindingAdapterPosition].level) } }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_level_stat, parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.tvLevel.text = item.level.toString()
        holder.tvTime.text = if (item.timeMs == Long.MAX_VALUE) "--" else formatTime(item.timeMs)
        holder.tvMoves.text = if (item.moves == Int.MAX_VALUE) "-" else item.moves.toString()
        holder.tvDate.text = if (item.dateMs == Long.MAX_VALUE) "--" else formatDate(item.dateMs)
    }
}
