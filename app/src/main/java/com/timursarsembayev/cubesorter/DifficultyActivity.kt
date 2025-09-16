package com.timursarsembayev.cubesorter

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class DifficultyActivity : BaseDrawerActivity() {

    private val prefs by lazy { getSharedPreferences("progress", MODE_PRIVATE) }

    private enum class Difficulty { EASY, NORM, HARD, EXTREME }

    private lateinit var containerEasy: LinearLayout
    private lateinit var containerNorm: LinearLayout
    private lateinit var containerHard: LinearLayout
    private lateinit var containerExtreme: LinearLayout

    private var current: Difficulty = Difficulty.NORM

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentWithDrawer(R.layout.activity_difficulty)

        containerEasy = findViewById(R.id.containerEasy)
        containerNorm = findViewById(R.id.containerNorm)
        containerHard = findViewById(R.id.containerHard)
        containerExtreme = findViewById(R.id.containerExtreme)

        // Установим текущее значение из настроек
        val stored = prefs.getString("difficulty", Difficulty.NORM.name)
        current = try { Difficulty.valueOf(stored ?: Difficulty.NORM.name) } catch (_: Exception) { Difficulty.NORM }
        updateSelection()

        // Подсказка
        findViewById<TextView>(R.id.textHint).text = getString(R.string.difficulty_apply_hint)

        containerEasy.setOnClickListener { onDifficultyPicked(Difficulty.EASY) }
        containerNorm.setOnClickListener { onDifficultyPicked(Difficulty.NORM) }
        containerHard.setOnClickListener { onDifficultyPicked(Difficulty.HARD) }
        containerExtreme.setOnClickListener { onDifficultyPicked(Difficulty.EXTREME) }
    }

    private fun onDifficultyPicked(newDiff: Difficulty) {
        if (current == newDiff) return
        current = newDiff
        prefs.edit().putString("difficulty", current.name).apply()
        updateSelection()
        Toast.makeText(this, getString(R.string.difficulty_changed), Toast.LENGTH_SHORT).show()
    }

    private fun updateSelection() {
        containerEasy.isSelected = current == Difficulty.EASY
        containerNorm.isSelected = current == Difficulty.NORM
        containerHard.isSelected = current == Difficulty.HARD
        containerExtreme.isSelected = current == Difficulty.EXTREME
    }
}
