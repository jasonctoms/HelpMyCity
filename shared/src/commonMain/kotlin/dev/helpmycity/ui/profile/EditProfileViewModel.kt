package dev.helpmycity.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.helpmycity.data.session.UserSession
import dev.helpmycity.domain.repository.ProfileOutcome
import dev.helpmycity.domain.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

data class EditProfileFormState(
    val displayName: String = "",
    val email: String = "",
    val isSaving: Boolean = false,
    /** False until the user has been read, so the form never starts blank over real data. */
    val isLoaded: Boolean = false,
    /** A guest, or nobody: there is no directory record behind this form. */
    val isEditable: Boolean = true,
) {
    val canSave: Boolean get() = isEditable && !isSaving && displayName.isNotBlank()
}

/**
 * Editing your own name and address, and nobody else's -- roles and areas are
 * the admin screen's business, and this form never shows them.
 */
@KoinViewModel
class EditProfileViewModel(
    private val session: UserSession,
    private val users: UserRepository,
) : ViewModel() {

    private val _form = MutableStateFlow(EditProfileFormState())
    val form: StateFlow<EditProfileFormState> = _form.asStateFlow()

    /** Set when a save was refused; cleared on the next attempt. */
    private val _saveError = MutableStateFlow<ProfileOutcome?>(null)
    val saveError: StateFlow<ProfileOutcome?> = _saveError.asStateFlow()

    init {
        // Read once, not observed: a form that rewrote itself would lose what
        // was typed into it.
        val user = session.currentUser.value
        _form.value = EditProfileFormState(
            displayName = user?.displayName.orEmpty(),
            email = user?.email.orEmpty(),
            isLoaded = true,
            isEditable = user?.isGuest == false,
        )
    }

    fun onDisplayNameChange(value: String) =
        _form.update { it.copy(displayName = value) }.also { _saveError.value = null }

    fun onEmailChange(value: String) =
        _form.update { it.copy(email = value) }.also { _saveError.value = null }

    /** Calls [onSaved] once the change is recorded, and also when nothing changed. */
    fun save(onSaved: () -> Unit) {
        val state = _form.value
        if (!state.canSave) return
        _saveError.value = null
        _form.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            val outcome = users.updateProfile(
                actor = session.currentUser.value,
                displayName = state.displayName,
                email = state.email,
            )
            _form.update { it.copy(isSaving = false) }
            when (outcome) {
                ProfileOutcome.Saved, ProfileOutcome.NoChanges -> onSaved()
                else -> _saveError.value = outcome
            }
        }
    }
}
