package com.novelnest.reader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private var chapterIndex = 0
    private var isPlaying = false
    private lateinit var chapterTitle: TextView
    private lateinit var chapterBody: TextView
    private lateinit var status: TextView
    private lateinit var playButton: Button
    private lateinit var voiceSpinner: Spinner
    private val chapters = NovelPlaybackService.chapters

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermission()
        buildUi()
        renderChapter()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun buildUi() {
        val scroll = ScrollView(this)
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 28)
            setBackgroundColor(0xFF17251F.toInt())
        }
        scroll.addView(page)
        setContentView(scroll)

        page.addView(TextView(this).apply {
            text = "NOVELNEST"
            textSize = 13f
            letterSpacing = .18f
            setTextColor(0xFFD5B77A.toInt())
        })
        page.addView(TextView(this).apply {
            text = "Your world of web novels"
            textSize = 25f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 8, 0, 22)
        })

        val novel = TextView(this).apply {
            text = "Sovereign of Infinite Clones"
            textSize = 21f
            setTextColor(0xFFFFFFFF.toInt())
        }
        page.addView(novel)
        page.addView(TextView(this).apply {
            text = "DEMO STORY • Fantasy / Action"
            textSize = 12f
            setTextColor(0xFFD5B77A.toInt())
            setPadding(0, 5, 0, 18)
        })

        chapterTitle = TextView(this).apply {
            textSize = 19f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 8, 0, 12)
        }
        page.addView(chapterTitle)
        chapterBody = TextView(this).apply {
            textSize = 18f
            setTextColor(0xFFE6E9E5.toInt())
            setLineSpacing(7f, 1f)
            setPadding(0, 0, 0, 20)
        }
        page.addView(chapterBody)

        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        nav.addView(makeButton("Previous") { moveChapter(-1) }, LinearLayout.LayoutParams(0, -2, 1f))
        nav.addView(makeButton("Next") { moveChapter(1) }, LinearLayout.LayoutParams(0, -2, 1f))
        page.addView(nav)

        page.addView(TextView(this).apply {
            text = "Narration voice"
            textSize = 15f
            setTextColor(0xFFD5B77A.toInt())
            setPadding(0, 22, 0, 8)
        })
        voiceSpinner = Spinner(this)
        val voices = listOf("Phone's built-in voice", "Natural voice (installed TTS engines)")
        voiceSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, voices)
        voiceSpinner.setSelection(0)
        page.addView(voiceSpinner)

        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        playButton = makeButton("▶ Listen") { togglePlayback() }
        controls.addView(playButton, LinearLayout.LayoutParams(0, -2, 1f))
        controls.addView(makeButton("■ Stop") { sendPlaybackAction(NovelPlaybackService.ACTION_STOP) }, LinearLayout.LayoutParams(0, -2, 1f))
        page.addView(controls)

        status = TextView(this).apply {
            text = "Lock your screen after pressing Listen. Playback controls appear in the media notification."
            textSize = 13f
            setTextColor(0xFFB8C4BC.toInt())
            setPadding(0, 15, 0, 4)
        }
        page.addView(status)
        page.addView(makeButton("Auto-play next chapter: ON") {
            Toast.makeText(this, "Auto-next is enabled", Toast.LENGTH_SHORT).show()
        }.apply { isEnabled = false; alpha = .8f })
    }

    private fun makeButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        setOnClickListener { action() }
    }

    private fun renderChapter() {
        val chapter = chapters[chapterIndex]
        chapterTitle.text = chapter.first
        chapterBody.text = chapter.second
    }

    private fun moveChapter(delta: Int) {
        chapterIndex = (chapterIndex + delta).coerceIn(0, chapters.lastIndex)
        renderChapter()
        if (isPlaying) startChapterPlayback()
    }

    private fun togglePlayback() {
        if (isPlaying) {
            sendPlaybackAction(NovelPlaybackService.ACTION_PAUSE)
            isPlaying = false
            playButton.text = "▶ Listen"
            status.text = "Paused"
        } else {
            startChapterPlayback()
        }
    }

    private fun startChapterPlayback() {
        val intent = Intent(this, NovelPlaybackService::class.java).apply {
            action = NovelPlaybackService.ACTION_PLAY
            putExtra(NovelPlaybackService.EXTRA_INDEX, chapterIndex)
            putExtra(NovelPlaybackService.EXTRA_VOICE_MODE, voiceSpinner.selectedItemPosition)
        }
        ContextCompat.startForegroundService(this, intent)
        isPlaying = true
        playButton.text = "Ⅱ Pause"
        status.text = "Playing aloud. You can lock the screen."
    }

    private fun sendPlaybackAction(action: String) {
        val intent = Intent(this, NovelPlaybackService::class.java).apply { this.action = action }
        if (action == NovelPlaybackService.ACTION_STOP) {
            isPlaying = false
            playButton.text = "▶ Listen"
            status.text = "Stopped"
        }
        startService(intent)
    }
}
