package com.novelnest.reader

import android.app.*
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import java.util.Locale

class NovelPlaybackService : Service(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var mediaSession: MediaSessionCompat? = null
    private var ready = false
    private var currentIndex = 0
    private var voiceMode = 0
    private var paused = false
    private var pendingText: String? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        mediaSession = MediaSessionCompat(this, "NovelNest").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { resumePlayback() }
                override fun onPause() { pausePlayback() }
                override fun onSkipToNext() { nextChapter() }
                override fun onSkipToPrevious() { previousChapter() }
                override fun onStop() { stopPlayback() }
            })
            isActive = true
        }
        tts = TextToSpeech(this, this)
    }

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            tts?.language = Locale.getDefault()
            tts?.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) { updateState(PlaybackStateCompat.STATE_PLAYING) }
                override fun onDone(utteranceId: String?) {
                    if (utteranceId == "chapter") {
                        if (currentIndex < chapters.lastIndex) {
                            currentIndex++
                            speakCurrentChapter()
                        } else {
                            stopPlayback()
                        }
                    }
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) { updateState(PlaybackStateCompat.STATE_ERROR) }
            })
            pendingText?.let { speakCurrentChapter() }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                currentIndex = intent.getIntExtra(EXTRA_INDEX, currentIndex).coerceIn(0, chapters.lastIndex)
                voiceMode = intent.getIntExtra(EXTRA_VOICE_MODE, voiceMode)
                pendingText = chapters[currentIndex].second
                startForeground(NOTIFICATION_ID, buildNotification())
                if (ready) speakCurrentChapter()
                updateMetadata()
                updateState(PlaybackStateCompat.STATE_PLAYING)
            }
            ACTION_PAUSE -> pausePlayback()
            ACTION_RESUME -> resumePlayback()
            ACTION_NEXT -> nextChapter()
            ACTION_PREVIOUS -> previousChapter()
            ACTION_STOP -> stopPlayback()
        }
        return START_NOT_STICKY
    }

    private fun speakCurrentChapter() {
        if (!ready || paused) return
        val chapter = chapters[currentIndex]
        pendingText = chapter.second
        updateMetadata()
        val text = chapter.second
        val chunks = text.split(Regex("(?<=[.!?])\\s+")).chunked(35).map { it.joinToString(" ") }
        tts?.stop()
        chunks.forEachIndexed { index, chunk ->
            tts?.speak(chunk, TextToSpeech.QUEUE_ADD, null, if (index == chunks.lastIndex) "chapter" else "part_$index")
        }
        updateState(PlaybackStateCompat.STATE_PLAYING)
        val notification = buildNotification()
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    private fun pausePlayback() {
        paused = true
        tts?.stop()
        updateState(PlaybackStateCompat.STATE_PAUSED)
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
    }

    private fun resumePlayback() {
        if (paused) {
            paused = false
            speakCurrentChapter()
        } else if (ready) speakCurrentChapter()
    }

    private fun nextChapter() {
        if (currentIndex < chapters.lastIndex) currentIndex++ else currentIndex = 0
        paused = false
        speakCurrentChapter()
    }

    private fun previousChapter() {
        currentIndex = (currentIndex - 1).coerceAtLeast(0)
        paused = false
        speakCurrentChapter()
    }

    private fun stopPlayback() {
        tts?.stop()
        paused = false
        updateState(PlaybackStateCompat.STATE_STOPPED)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun updateMetadata() {
        val chapter = chapters[currentIndex]
        mediaSession?.setMetadata(MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, chapter.first)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "NovelNest")
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "Sovereign of Infinite Clones — Demo")
            .build())
    }

    private fun updateState(state: Int) {
        val actions = PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or PlaybackStateCompat.ACTION_STOP
        mediaSession?.setPlaybackState(PlaybackStateCompat.Builder()
            .setActions(actions)
            .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, if (state == PlaybackStateCompat.STATE_PLAYING) 1f else 0f)
            .build())
    }

    private fun buildNotification(): Notification {
        val sessionToken = mediaSession?.sessionToken
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(chapters[currentIndex].first)
            .setContentText("NovelNest • Text-to-speech")
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(!paused)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .addAction(android.R.drawable.ic_media_previous, "Previous", servicePendingIntent(ACTION_PREVIOUS, 21))
            .addAction(if (paused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
                if (paused) "Play" else "Pause", servicePendingIntent(if (paused) ACTION_RESUME else ACTION_PAUSE, 22))
            .addAction(android.R.drawable.ic_media_next, "Next", servicePendingIntent(ACTION_NEXT, 23))
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(sessionToken)
                .setShowActionsInCompactView(0, 1, 2))
            .build()
    }

    private fun servicePendingIntent(action: String, requestCode: Int) =
        android.app.PendingIntent.getService(this, requestCode, Intent(this, NovelPlaybackService::class.java).setAction(action),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 23) android.app.PendingIntent.FLAG_IMMUTABLE else 0)

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "NovelNest playback", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Background novel narration and lock-screen controls"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        mediaSession?.isActive = false
        mediaSession?.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_PLAY = "com.novelnest.reader.PLAY"
        const val ACTION_PAUSE = "com.novelnest.reader.PAUSE"
        const val ACTION_RESUME = "com.novelnest.reader.RESUME"
        const val ACTION_NEXT = "com.novelnest.reader.NEXT"
        const val ACTION_PREVIOUS = "com.novelnest.reader.PREVIOUS"
        const val ACTION_STOP = "com.novelnest.reader.STOP"
        const val EXTRA_INDEX = "chapter_index"
        const val EXTRA_VOICE_MODE = "voice_mode"
        private const val CHANNEL_ID = "novelnest_playback"
        private const val NOTIFICATION_ID = 401

        val chapters = listOf(
            "Chapter 1 — The First Awakening" to
                "The rain fell over the quiet city as Arin discovered a strange light inside his palm. It shimmered like a tiny star, then split into two identical sparks. Each spark carried a memory of its own. Arin took a breath and wondered whether this was the beginning of something extraordinary. By sunrise, he had learned one rule: every clone could train, and every lesson returned to him.",
            "Chapter 2 — A Thousand Possibilities" to
                "Arin sent one clone to the library and another to the training hall. A third stayed home to study the mysterious mark. Hours passed, and knowledge flowed back into his mind. The power was not limitless, but it grew with patience. For the first time, the future seemed larger than the walls around him.",
            "Chapter 3 — The Hidden Cost" to
                "At dusk, the mark dimmed. Arin felt the exhaustion of every clone at once. Power had a price, and careless ambition could destroy him. He sat beneath the old lantern and made a promise: he would learn the rules before chasing strength. Somewhere beyond the rooftops, another light answered his own."
        )
    }
}
