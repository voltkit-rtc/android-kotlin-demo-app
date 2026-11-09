package `in`.voltkit.livedemo.kotlin

import android.app.Application
import android.content.Context
import com.voltkit.rtc.RtcOptions
import com.voltkit.rtc.VoltKitRtc
import `in`.voltkit.livedemo.kotlin.data.Session

class VoltKitApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Before any client, as the SDK requires: which echo canceller this run uses. See EchoSetting.
        VoltKitRtc.initialize(this, RtcOptions(hardwareEchoCanceler = EchoSetting.phoneCanceller(this)))
        Session.init(this)
    }
}

/**
 * Which echo canceller to use. This SDK uses the phone's own by default. On some phones it does not cope with the loudspeaker
 * (the other person hears themselves), and VoltKit's docs say to try such a phone both ways. With the phone's canceller off,
 * WebRTC's is used, and from SDK 0.9.17 it gets a loudspeaker tuning on phones whose own canceller is Android's software one
 * (`RtcOptions.echoSuppression`, `auto` by default). The setting is in Profile, and applies the next time the app starts.
 */
object EchoSetting {
    private const val PREFS = "settings"
    private const val KEY = "echo-canceller"

    fun phoneCanceller(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "phone") != "webrtc"

    fun set(context: Context, phone: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, if (phone) "phone" else "webrtc").apply()
    }
}
