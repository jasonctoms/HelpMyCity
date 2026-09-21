package dev.helpmycity.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.helpmycity.data.auth.AuthFailureReason
import dev.helpmycity.deployment.CityProfile
import dev.helpmycity.ui.components.CityMark
import dev.helpmycity.ui.components.CityPhotoBackground
import helpmycity.shared.generated.resources.Res
import helpmycity.shared.generated.resources.continue_as_guest
import helpmycity.shared.generated.resources.demo_logins_notice
import helpmycity.shared.generated.resources.demo_repo_footer
import helpmycity.shared.generated.resources.error_email_taken
import helpmycity.shared.generated.resources.error_invalid_credentials
import helpmycity.shared.generated.resources.error_invalid_email
import helpmycity.shared.generated.resources.error_network
import helpmycity.shared.generated.resources.error_unknown
import helpmycity.shared.generated.resources.error_weak_password
import helpmycity.shared.generated.resources.sign_in_action
import helpmycity.shared.generated.resources.sign_in_display_name
import helpmycity.shared.generated.resources.sign_in_email
import helpmycity.shared.generated.resources.sign_in_password
import helpmycity.shared.generated.resources.sign_in_subtitle
import helpmycity.shared.generated.resources.sign_in_subtitle_demo
import helpmycity.shared.generated.resources.sign_in_switch_to_sign_in
import helpmycity.shared.generated.resources.sign_in_switch_to_sign_up
import helpmycity.shared.generated.resources.sign_in_title
import helpmycity.shared.generated.resources.sign_up_action
import helpmycity.shared.generated.resources.sign_up_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Sign-in over whatever [dev.helpmycity.data.auth.AuthService] this
 * deployment configured.
 *
 * Validation, loading and error states are real, so an identity provider drops
 * in without touching this screen. The notice at the bottom publishes the shared
 * sign-ins of a demo deployment, and appears only when
 * [dev.helpmycity.deployment.CityProfile.demoLogins] is not empty.
 */
@Composable
fun SignInScreen(viewModel: SignInViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val city: CityProfile = koinInject()
    val uriHandler = LocalUriHandler.current

    Box(modifier = Modifier.fillMaxSize()) {
        // Heavier than the header's scrim, and flat rather than graded: this one
        // is not a band under a wordmark but a whole page behind a form, and the
        // photograph's job here is atmosphere rather than detail.
        CityPhotoBackground(
            imageUrl = city.branding.headerImageUrl,
            scrim = Brush.verticalGradient(
                0f to Color.Black.copy(alpha = 0.55f),
                1f to Color.Black.copy(alpha = 0.75f),
            ),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeContentPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CityMark(
                cityName = city.displayName,
                branding = city.branding,
                logoHeight = 56.dp,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            city.branding.tagline?.let { tagline ->
                Text(
                    text = tagline,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.82f),
                    modifier = Modifier.padding(bottom = 24.dp),
                )
            }
            // The form keeps its own surface rather than sitting on the photo:
            // labels, errors and a disabled button all need scheme colors to
            // mean what they usually mean.
            Surface(
                modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                tonalElevation = 3.dp,
                shadowElevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = stringResource(
                            if (state.mode == SignInMode.SIGN_IN) {
                                Res.string.sign_in_title
                            } else {
                                Res.string.sign_up_title
                            }
                        ),
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = stringResource(
                            if (viewModel.demoLogins.isEmpty()) {
                                Res.string.sign_in_subtitle
                            } else {
                                Res.string.sign_in_subtitle_demo
                            },
                            viewModel.cityName,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (state.mode == SignInMode.SIGN_UP) {
                        OutlinedTextField(
                            value = state.displayName,
                            onValueChange = viewModel::onDisplayNameChange,
                            label = { Text(stringResource(Res.string.sign_in_display_name)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    OutlinedTextField(
                        value = state.email,
                        onValueChange = viewModel::onEmailChange,
                        label = { Text(stringResource(Res.string.sign_in_email)) },
                        singleLine = true,
                        isError = state.error == AuthFailureReason.INVALID_EMAIL,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    OutlinedTextField(
                        value = state.password,
                        onValueChange = viewModel::onPasswordChange,
                        label = { Text(stringResource(Res.string.sign_in_password)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        isError = state.error == AuthFailureReason.WEAK_PASSWORD ||
                            state.error == AuthFailureReason.INVALID_CREDENTIALS,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    state.error?.let { reason ->
                        Text(
                            text = stringResource(reason.messageResource()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    Button(
                        onClick = viewModel::submit,
                        enabled = state.canSubmit,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (state.isSubmitting) {
                            CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                        }
                        Text(
                            stringResource(
                                if (state.mode == SignInMode.SIGN_IN) {
                                    Res.string.sign_in_action
                                } else {
                                    Res.string.sign_up_action
                                }
                            )
                        )
                    }

                    // A demo deployment publishes shared accounts and wipes
                    // its data nightly; letting visitors mint more accounts,
                    // named or anonymous, would leave real ones behind that the
                    // reset never clears. The published roles cover a resident.
                    if (viewModel.demoLogins.isEmpty()) {
                        TextButton(
                            onClick = viewModel::toggleMode,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                stringResource(
                                    if (state.mode == SignInMode.SIGN_IN) {
                                        Res.string.sign_in_switch_to_sign_up
                                    } else {
                                        Res.string.sign_in_switch_to_sign_in
                                    }
                                )
                            )
                        }

                        TextButton(
                            onClick = viewModel::continueAsGuest,
                            enabled = !state.isSubmitting,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(Res.string.continue_as_guest))
                        }
                    }

                }
            }

            // Its own card: none of this is part of signing in, and a visitor
            // should be able to tell which parts of the screen are the product.
            if (viewModel.demoLogins.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .widthIn(max = 420.dp)
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    shape = RoundedCornerShape(20.dp),
                    tonalElevation = 1.dp,
                    shadowElevation = 4.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(Res.string.demo_logins_notice),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        viewModel.demoLogins.forEach { login ->
                            OutlinedButton(
                                onClick = { viewModel.useDemoLogin(login) },
                                enabled = !state.isSubmitting,
                                shape = MaterialTheme.shapes.small,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp),
                            ) {
                                Text(
                                    text = login.label,
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                // The tap fills the password in; printing it
                                // here would only cost a second line.
                                Text(
                                    text = login.email,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.End,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }

                        TextButton(
                            onClick = { uriHandler.openUri(SOURCE_URL) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = stringResource(Res.string.demo_repo_footer),
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Shown under the demo's published sign-ins. */
private const val SOURCE_URL = "https://github.com/jasonctoms/HelpMyCity"

private fun AuthFailureReason.messageResource() = when (this) {
    AuthFailureReason.INVALID_EMAIL -> Res.string.error_invalid_email
    AuthFailureReason.WEAK_PASSWORD -> Res.string.error_weak_password
    AuthFailureReason.INVALID_CREDENTIALS -> Res.string.error_invalid_credentials
    AuthFailureReason.EMAIL_ALREADY_REGISTERED -> Res.string.error_email_taken
    AuthFailureReason.NETWORK_UNAVAILABLE -> Res.string.error_network
    AuthFailureReason.UNKNOWN -> Res.string.error_unknown
}
