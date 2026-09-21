package dev.helpmycity.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.helpmycity.data.auth.AuthService
import dev.helpmycity.data.session.UserSession
import dev.helpmycity.data.sync.SyncEngine
import dev.helpmycity.data.sync.SyncStatus
import dev.helpmycity.domain.model.User
import dev.helpmycity.ui.AppBootstrapper
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

/**
 * App-wide session state: who is signed in, and whether their data has left the
 * device. Owned above the navigation host so it survives moving between screens.
 *
 * The only place both halves are injected: signing out ends an *account*, and
 * everything on screen is about a *user*.
 */
@KoinViewModel
class SessionViewModel(
    session: UserSession,
    private val authService: AuthService,
    syncEngine: SyncEngine,
    bootstrapper: AppBootstrapper,
) : ViewModel() {

    val currentUser: StateFlow<User?> = session.currentUser
    val syncStatus: StateFlow<SyncStatus> = syncEngine.status

    init {
        bootstrapper.start(viewModelScope)
    }

    fun signOut() {
        viewModelScope.launch { authService.signOut() }
    }
}
