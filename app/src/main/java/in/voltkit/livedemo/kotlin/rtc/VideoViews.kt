package `in`.voltkit.livedemo.kotlin.rtc

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.SurfaceTexture
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.voltkit.rtc.RemoteTrack
import com.voltkit.rtc.StreamClient
import com.voltkit.rtc.VoltKitRtc
import org.webrtc.EglBase
import org.webrtc.EglRenderer
import org.webrtc.GlRectDrawer
import org.webrtc.VideoFrame
import org.webrtc.VideoSink
import org.webrtc.VideoTrack
import java.util.concurrent.CountDownLatch

/**
 * A video on a TextureView. It draws like any other view, so a card can round its corners and overlays sit on top, which a
 * SurfaceViewRenderer cannot do. It shares the SDK's EGL context, so frames need no copy, and it fills its view the way the
 * other apps' cards do (cropped, not letterboxed).
 */
@SuppressLint("ViewConstructor")
class VideoTextureView(context: Context) : TextureView(context), VideoSink, TextureView.SurfaceTextureListener {
    private val renderer = EglRenderer("voltkit-video")
    private var released = false

    init {
        surfaceTextureListener = this
        // A video is never see-through. With an alpha channel that the drawer fills, and the view marked opaque, no GPU can
        // blend the room's background into the picture (flutter_webrtc's renderer had exactly that bug under Impeller).
        setOpaque(true)
        renderer.init(VoltKitRtc.eglBaseContext, EglBase.CONFIG_RGBA, GlRectDrawer())
    }

    fun setMirror(mirror: Boolean) = renderer.setMirror(mirror)

    override fun onFrame(frame: VideoFrame) {
        if (!released) renderer.onFrame(frame)
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        renderer.createEglSurface(surface)
        aspect(width, height)
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = aspect(width, height)

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        val done = CountDownLatch(1)
        renderer.releaseEglSurface { done.countDown() }
        done.await()
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

    /** Crops the picture to fill the view. */
    private fun aspect(width: Int, height: Int) {
        if (width > 0 && height > 0) renderer.setLayoutAspectRatio(width.toFloat() / height)
    }

    fun release() {
        if (released) return
        released = true
        renderer.release()
    }
}

/**
 * Someone else's camera. attachVideo tells the SDK how big it is drawn and whether it can be seen, so this view receives the
 * copy that fits, and none while hidden.
 */
@Composable
fun RemoteVideo(client: StreamClient, track: RemoteTrack, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val view = remember(track.producerId) { VideoTextureView(context) }
    DisposableEffect(view) {
        val attachment = client.attachVideo(track, view, true)
        onDispose {
            attachment.detach()
            view.release()
        }
    }
    AndroidView(factory = { view }, modifier = modifier)
}

/** Your own camera. */
@Composable
fun LocalVideo(track: VideoTrack, mirror: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val view = remember { VideoTextureView(context) }
    DisposableEffect(track) {
        track.addSink(view)
        onDispose {
            // The SDK may have disposed the track already (the camera released while off).
            runCatching { track.removeSink(view) }
        }
    }
    DisposableEffect(view) { onDispose { view.release() } }
    AndroidView(factory = { view }, modifier = modifier, update = { it.setMirror(mirror) })
}
