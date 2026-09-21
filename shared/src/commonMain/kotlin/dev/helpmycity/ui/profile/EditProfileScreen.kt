package dev.helpmycity.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.helpmycity.domain.repository.ProfileOutcome
import helpmycity.shared.generated.resources.Res
import helpmycity.shared.generated.resources.edit_cancel
import helpmycity.shared.generated.resources.edit_profile_email
import helpmycity.shared.generated.resources.edit_profile_email_hint
import helpmycity.shared.generated.resources.edit_profile_name
import helpmycity.shared.generated.resources.edit_profile_not_editable
import helpmycity.shared.generated.resources.edit_profile_save
import helpmycity.shared.generated.resources.error_email_in_use
import helpmycity.shared.generated.resources.error_invalid_email
import helpmycity.shared.generated.resources.error_name_required
import helpmycity.shared.generated.resources.error_profile_missing
import org.jetbrains.compose.resources.stringResource

/**
 * Your own name and address.
 *
 * Nothing about role or areas is here: those are assigned, not chosen, and
 * showing them on a form you can type into would suggest otherwise.
 */
@Composable
fun EditProfileScreen(
    viewModel: EditProfileViewModel,
    onSaved: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val saveError by viewModel.saveError.collectAsStateWithLifecycle()

    if (!form.isLoaded) return

    if (!form.isEditable) {
        Text(
            text = stringResource(Res.string.edit_profile_not_editable),
            modifier = modifier.padding(24.dp),
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = form.displayName,
                onValueChange = viewModel::onDisplayNameChange,
                label = { Text(stringResource(Res.string.edit_profile_name)) },
                singleLine = true,
                isError = saveError == ProfileOutcome.NameRequired,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = form.email,
                onValueChange = viewModel::onEmailChange,
                label = { Text(stringResource(Res.string.edit_profile_email)) },
                supportingText = { Text(stringResource(Res.string.edit_profile_email_hint)) },
                singleLine = true,
                isError = saveError == ProfileOutcome.InvalidEmail ||
                    saveError == ProfileOutcome.EmailTaken,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            saveError?.let { outcome ->
                Text(
                    text = stringResource(outcome.messageResource()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Button(
                onClick = { viewModel.save(onSaved) },
                enabled = form.canSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.edit_profile_save))
            }

            TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.edit_cancel))
            }
        }
    }
}

private fun ProfileOutcome.messageResource() = when (this) {
    ProfileOutcome.NameRequired -> Res.string.error_name_required
    ProfileOutcome.InvalidEmail -> Res.string.error_invalid_email
    ProfileOutcome.EmailTaken -> Res.string.error_email_in_use
    // Saved and NoChanges never reach here: both leave the screen.
    else -> Res.string.error_profile_missing
}
