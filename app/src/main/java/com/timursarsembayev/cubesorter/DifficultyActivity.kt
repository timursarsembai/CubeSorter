package com.timursarsembayev.cubesorter

import android.os.Bundle
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast

class DifficultyActivity : BaseDrawerActivity() {

    private val prefs by lazy { getSharedPreferences("progress", MODE_PRIVATE) }

    private enum class Difficulty { EASY, NORM, HARD, EXTREME }

    private lateinit var radioGroup: RadioGroup
    private lateinit var radioEasy: RadioButton
    private lateinit var radioNorm: RadioButton
    private lateinit var radioHard: RadioButton
    private lateinit var radioExtreme: RadioButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentWithDrawer(R.layout.activity_difficulty)

        radioGroup = findViewById(R.id.radioDifficulty)
        radioEasy = findViewById(R.id.radioEasy)
        radioNorm = findViewById(R.id.radioNorm)
        radioHard = findViewById(R.id.radioHard)
        radioExtreme = findViewById(R.id.radioExtreme)

        // Установим текущее ��начение
        val stored = prefs.getString("difficulty", Difficulty.NORM.name)
        val current = try { Difficulty.valueOf(stored ?: Difficulty.NORM.name) } catch (_: Exception) { Difficulty.NORM }
        when (current) {
            Difficulty.EASY -> radioEasy.isChecked = true
            Difficulty.NORM -> radioNorm.isChecked = true
            Difficulty.HARD -> radioHard.isChecked = true
            Difficulty.EXTREME -> radioExtreme.isChecked = true
        }

        // Подсказка
        findViewById<TextView>(R.id.textHint).text = getString(R.string.difficulty_apply_hint)

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val newDiff = when (checkedId) {
                R.id.radioEasy -> Difficulty.EASY
                R.id.radioHard -> Difficulty.HARD
                R.id.radioExtreme -> Difficulty.EXTREME
                else -> Difficulty.NORM
            }
            prefs.edit().putString("difficulty", newDiff.name).apply()
            Toast.makeText(this, getString(R.string.difficulty_changed), Toast.LENGTH_SHORT).show()
        }
    }
}
