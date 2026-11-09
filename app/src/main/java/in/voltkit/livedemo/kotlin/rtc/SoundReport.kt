package `in`.voltkit.livedemo.kotlin.rtc

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import com.voltkit.rtc.StreamClient
import com.voltkit.rtc.VoltKitRtc
import `in`.voltkit.livedemo.kotlin.EchoSetting
import `in`.voltkit.livedemo.kotlin.Voltkit
import `in`.voltkit.livedemo.kotlin.ui.ToastKind
import `in`.voltkit.livedemo.kotlin.ui.toast
import kotlinx.coroutines.CancellationException

/** Where call sound plays right now, as Android has it. */
@Suppress("DEPRECATION")
private fun route(audio: AudioManager): String {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val device = audio.communicationDevice ?: return "none"
        return when (device.type) {
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "loudspeaker"
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "earpiece"
            AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_USB_HEADSET -> "wired headset"
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLE_HEADSET -> "Bluetooth headset"
            else -> "other (type ${device.type})"
        }
    }
    return when {
        audio.isBluetoothScoOn -> "Bluetooth headset"
        audio.isWiredHeadsetOn -> "wired headset"
        audio.isSpeakerphoneOn -> "loudspeaker"
        else -> "earpiece"
    }
}

/**
 * Copies what VoltKit asks for when someone reports an echo or other sound trouble: the phone, its Android version, where the
 * sound plays and how loud, which echo canceller this app runs, and the SDK's whole diagnostics snapshot. The person then pastes
 * it into a message, with a few words about what they heard. The same report is in VoltKit Flutter and VoltKit Native.
 */
suspend fun copySoundReport(context: Context, client: StreamClient, loudspeaker: Boolean) {
    try {
        val diagnostics = client.getDiagnostics()
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val echoLines = diagnostics.log.filter { it.event.contains("echo", ignoreCase = true) }.map { "${it.event}: ${it.detail}" }
        val report = buildString {
            appendLine("${Voltkit.APP_NAME} sound report")
            appendLine("Phone: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine(
                "Sound from: ${route(audio)} (the app asked for the ${if (loudspeaker) "loudspeaker" else "earpiece"}), call volume " +
                    "${audio.getStreamVolume(AudioManager.STREAM_VOICE_CALL)} of ${audio.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)}",
            )
            appendLine("SDK: in.voltkit:voltkit-rtc ${VoltKitRtc.VERSION}")
            appendLine(
                "Echo canceller: " + if (EchoSetting.phoneCanceller(context)) {
                    "the phone's (hardwareEchoCanceler = true, the SDK's default)"
                } else {
                    "WebRTC's (hardwareEchoCanceler = false, echoSuppression = auto)"
                },
            )
            echoLines.forEach { appendLine(it) }
            appendLine()
            appendLine("Diagnostics:")
            append(diagnostics.toJson())
        }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("${Voltkit.APP_NAME} sound report", report))
        toast("Sound report copied. Paste it in a message to VoltKit, with what you heard.", ToastKind.Success)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        toast("Could not make the sound report.", ToastKind.Error)
    }
}
