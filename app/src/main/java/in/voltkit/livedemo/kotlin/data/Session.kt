package `in`.voltkit.livedemo.kotlin.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SessionStatus { Loading, Offline, GuestNeeded, Ready }

/** The guest on this phone. No account and no password: a token from the backend, kept in the Keystore. */
object Session {
    private const val TOKEN_KEY = "guest-session"
    lateinit var store: SecureStore
        private set
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    var status by mutableStateOf(SessionStatus.Loading)
        private set
    var guestOrNull by mutableStateOf<Guest?>(null)
        private set
    val guest: Guest get() = guestOrNull!!

    fun init(context: Context) {
        store = SecureStore(context.applicationContext)
        load()
    }

    fun load() {
        status = SessionStatus.Loading
        scope.launch {
            val token = withContext(Dispatchers.IO) { store.read(TOKEN_KEY) }
            if (token == null) {
                status = SessionStatus.GuestNeeded
                return@launch
            }
            Api.sessionToken = token
            try {
                guestOrNull = Api.me()
                LiveSocket.connect(token)
                status = SessionStatus.Ready
            } catch (e: ApiError) {
                if (e.status == 401) {
                    store.delete(TOKEN_KEY)
                    Api.sessionToken = null
                    status = SessionStatus.GuestNeeded
                } else {
                    // The identity is still good: this is only the network.
                    status = SessionStatus.Offline
                }
            }
        }
    }

    suspend fun start(name: String, avatar: String) {
        val (token, created) = Api.createGuest(name, avatar)
        withContext(Dispatchers.IO) { store.write(TOKEN_KEY, token) }
        Api.sessionToken = token
        guestOrNull = created
        LiveSocket.connect(token)
        status = SessionStatus.Ready
    }

    fun setGuest(guest: Guest) {
        guestOrNull = guest
    }

    suspend fun refresh() = setGuest(Api.me())

    fun forget() {
        store.delete(TOKEN_KEY)
        Api.sessionToken = null
        LiveSocket.disconnect()
        guestOrNull = null
        status = SessionStatus.GuestNeeded
    }
}
