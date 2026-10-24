package app.downly.android

import android.graphics.Color
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.MediaController
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import app.downly.ui.stringsFor

/** Built-in player for finished videos and music (plays what Android can decode). */
class PlayerActivity : ComponentActivity() {
    private lateinit var video: VideoView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent?.data
        if (uri == null) {
            finish()
            return
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        video = VideoView(this)
        val controls = MediaController(this)
        controls.setAnchorView(video)
        video.setMediaController(controls)
        root.addView(video, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT, Gravity.CENTER))

        val titleView = TextView(this).apply {
            text = intent?.getStringExtra(EXTRA_TITLE).orEmpty()
            setTextColor(Color.WHITE)
            textSize = 14f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setBackgroundColor(0x88000000.toInt())
            setPadding(32, 16, 32, 16)
        }
        root.addView(titleView, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.TOP))
        setContentView(root)

        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, 0, bars.right, bars.bottom)
            titleView.setPadding(32, bars.top + 16, 32, 16)
            insets
        }

        video.setOnPreparedListener { it.isLooping = false; video.start() }
        video.setOnCompletionListener { controls.show(0) }
        video.setOnErrorListener { _, _, _ ->
            val strings = stringsFor((application as DownlyApplication).controller.settings.value.language)
            Toast.makeText(this, strings.more.filesOpenFailed, Toast.LENGTH_LONG).show()
            finish()
            true
        }
        video.setVideoURI(uri)
        video.requestFocus()
    }

    override fun onPause() {
        if (::video.isInitialized) video.pause()
        super.onPause()
    }

    override fun onDestroy() {
        if (::video.isInitialized) video.stopPlayback()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_TITLE = "title"
    }
}
