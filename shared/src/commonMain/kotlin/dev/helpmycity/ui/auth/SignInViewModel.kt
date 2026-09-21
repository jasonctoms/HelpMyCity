package dev.helpmycity.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.helpmycity.data.auth.AuthFailureReason
import dev.helpmycity.data.auth.AuthResult
import dev.helpmycity.data.auth.AuthService
import dev.helpmycity.deployment.CityProfile
import dev.helpmycity.deployment.DemoLogin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

data class SignInUiState(
    val mode: SignInMode = SignInMode.SIGN_IN,
    val displayName: String = "",
    val email: String = "",
    val password: String = "",
    val isSubmitting: Boolean = false,
    val error: AuthFailureReason? = null,
) {
    val canSubmit: Boolean
        get() = !isSubmitting && email.isNotBlank() && password.isNotBlank()
}

enum class SignInMode { SIGN_IN, SIGN_UP }

@KoinViewModel
class SignInViewModel(
    private val authService: AuthService,
    city: CityProfile,
) : ViewModel() {

    /** Named so the sign-in copy reads as this deployment's, not a template's. */
    val cityName: String = city.displayName

    /**
     * Shared sign-ins to publish on the screen, empty for a real deployment.
     * These are genuine accounts on the configured identity provider, so the
     * notice naming them is an invitation rather than a disclaimer.
     */
    val demoLogins: List<DemoLogin> = city.demoLogins

    private val _uiState = MutableStateFlow(SignInUiState())
    val uiState: StateFlow<SignInUiState> = _uiState.asStateFlow()

    fun onDisplayNameChange(value: String) = _uiState.update { it.copy(displayName = value, error = null) }
    fun onEmailChange(value: String) = _uiState.update { it.copy(email = value, error = null) }
    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value, error = null) }

    /**
     * Fills the form from a published demo account.
     *
     * Switches back to sign-in as well: the accounts already exist, so leaving
     * the form in sign-up mode would fail on an address that is taken.
     */
    fun useDemoLogin(login: DemoLogin) = _uiState.update {
        it.copy(
            mode = SignInMode.SIGN_IN,
            email = login.email,
            password = login.password,
            error = null,
        )
    }

    fun toggleMode() = _uiState.update {
        it.copy(
            mode = if (it.mode == SignInMode.SIGN_IN) SignInMode.SIGN_UP else SignInMode.SIGN_IN,
            error = null,
        )
    }

    fun submit() {
        val state = _uiState.value
        if (!state.canSubmit) return
        _uiState.update { it.copy(isSubmitting = true, error = null) }

        viewModelScope.launch {
            val result = when (state.mode) {
                SignInMode.SIGN_IN -> authService.signIn(state.email.trim(), state.password)
                SignInMode.SIGN_UP ->
                    authService.signUp(state.displayName.trim(), state.email.trim(), state.password)
            }
            // On success the session flow flips and the app navigates away, so
            // only the failure path needs to touch this state.
            _uiState.update {
                it.copy(
                    isSubmitting = false,
                    error = (result as? AuthResult.Failure)?.reason,
                )
            }
        }
    }

    fun continueAsGuest() {
        _uiState.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            authService.continueAsGuest()
            _uiState.update { it.copy(isSubmitting = false) }
        }
    }
}
