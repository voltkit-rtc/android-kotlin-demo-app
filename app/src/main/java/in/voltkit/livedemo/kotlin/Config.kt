package `in`.voltkit.livedemo.kotlin

/** The backend. The same one as VoltKit Native and VoltKit Flutter: only the language differs. */
object Config {
    val apiUrl: String = BuildConfig.API_URL.trimEnd('/')

    /** Gift animations may be paths on the backend or full URLs on a CDN. */
    fun assetUrl(pathOrUrl: String): String =
        if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) pathOrUrl else "$apiUrl$pathOrUrl"
}

object Voltkit {
    /** The app's name. Its twins are VoltKit Native (React Native) and VoltKit Flutter. */
    const val APP_NAME = "VoltKit Kotlin"
    const val APP_SUFFIX = "Kotlin"
    const val SITE = "https://voltkit.in"
    const val KOTLIN_PAGE = "https://voltkit.in/rtc/android"
    const val KOTLIN_DOCS = "https://voltkit.in/docs/android-sdk"
    const val QUICKSTART = "https://voltkit.in/docs/quickstart"
    const val TAGLINE = "Connect the world in one click."
    const val POWERED_BY = "Powered by VoltKit RTC"
    const val MADE_WITH = "Made with Kotlin"
    const val DEMO_LINE = "Kotlin demo app from VoltKit"
    const val BUILD = "Build your own live streaming, audio and video app with VoltKit"
}
