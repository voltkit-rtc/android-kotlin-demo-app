package `in`.voltkit.livedemo.kotlin.rtc

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import `in`.voltkit.livedemo.kotlin.data.Session
import `in`.voltkit.livedemo.kotlin.ui.C
import `in`.voltkit.livedemo.kotlin.ui.GhostButton
import `in`.voltkit.livedemo.kotlin.ui.GradientButton
import `in`.voltkit.livedemo.kotlin.ui.Icon
import `in`.voltkit.livedemo.kotlin.ui.Ion
import `in`.voltkit.livedemo.kotlin.ui.SheetHost
import `in`.voltkit.livedemo.kotlin.ui.SheetScope
import `in`.voltkit.livedemo.kotlin.ui.T
import `in`.voltkit.livedemo.kotlin.ui.ToastKind
import `in`.voltkit.livedemo.kotlin.ui.V
import `in`.voltkit.livedemo.kotlin.ui.toast
import kotlinx.coroutines.CompletableDeferred

/** The system permission dialog, as a suspend call. Registered by the activity before it starts. */
object PermissionAsker {
    private var activity: ComponentActivity? = null
    private var launcher: ActivityResultLauncher<Array<String>>? = null
    private var pending: CompletableDeferred<Map<String, Boolean>>? = null

    fun register(activity: ComponentActivity) {
        this.activity = activity
        launcher = activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            pending?.complete(it)
            pending = null
        }
    }

    fun unregister(activity: ComponentActivity) {
        if (this.activity === activity) {
            this.activity = null
            launcher = null
            pending?.complete(emptyMap())
            pending = null
        }
    }

    fun granted(permission: String): Boolean {
        val a = activity ?: return false
        return ContextCompat.checkSelfPermission(a, permission) == PackageManager.PERMISSION_GRANTED
    }

    /** After a refusal: true when the system will not ask again, so only Settings can change it. */
    fun blocked(permission: String): Boolean {
        val a = activity ?: return false
        return !granted(permission) && !ActivityCompat.shouldShowRequestPermissionRationale(a, permission)
    }

    suspend fun request(permissions: List<String>): Map<String, Boolean> {
        val missing = permissions.filterNot { granted(it) }
        if (missing.isEmpty()) return permissions.associateWith { true }
        val launch = launcher ?: return permissions.associateWith { granted(it) }
        val result = CompletableDeferred<Map<String, Boolean>>().also { pending = it }
        launch.launch(missing.toTypedArray())
        val answers = result.await()
        return permissions.associateWith { it !in missing || answers[it] == true }
    }

    fun openSettings() {
        val a = activity ?: return
        a.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", a.packageName, null)))
    }
}

private const val CONSENT_KEY = "media-consent-v1"

/**
 * Nothing goes on air without this: the app explains what happens once, then the system asks. Viewers are never asked, because
 * they publish nothing.
 */
suspend fun askMediaConsent(type: String): Boolean {
    if (Session.store.read(CONSENT_KEY) == null) {
        val yes = SheetHost.ask<Boolean>(id = "consent-sheet") { ConsentSheet(type) }
        if (yes != true) return false
        Session.store.write(CONSENT_KEY, "1")
    }
    val kinds = buildList {
        if (type == "video") add(Manifest.permission.CAMERA to "camera")
        add(Manifest.permission.RECORD_AUDIO to "microphone")
    }
    val result = PermissionAsker.request(kinds.map { it.first })
    // Only so the call's notification shows. The call works without it.
    if (Build.VERSION.SDK_INT >= 33) PermissionAsker.request(listOf(Manifest.permission.POST_NOTIFICATIONS))

    val refused = kinds.filter { result[it.first] != true }
    if (refused.isEmpty()) return true
    val names = refused.joinToString(" and ") { it.second }
    if (refused.any { PermissionAsker.blocked(it.first) }) {
        toast("Allow the $names in Settings to go on air.", ToastKind.Error)
        PermissionAsker.openSettings()
    } else {
        toast("The $names ${if (refused.size > 1) "are" else "is"} needed to go on air.", ToastKind.Error)
    }
    return false
}

@Composable
private fun SheetScope.ConsentSheet(type: String) {
    val video = type == "video"
    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(if (video) Ion.videocam else Ion.mic, 38.dp, C.magenta)
        Spacer(Modifier.height(10.dp))
        T("Going on air", V.Title, align = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        T(
            "Everyone in the room or call sees and hears you live. Your ${if (video) "camera and microphone are" else "microphone is"} only " +
                "on while you are on stage, and you can switch ${if (video) "them" else "it"} off at any time. This app does not record you.",
            color = C.textDim,
            align = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        GradientButton("I understand", { close(true) }, Modifier.fillMaxWidth(), id = "consent-accept")
        Spacer(Modifier.height(10.dp))
        GhostButton("Not now", { close(false) }, Modifier.fillMaxWidth())
    }
}
