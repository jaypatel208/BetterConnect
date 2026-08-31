package dev.jay.betterconnect.feature.onboarding

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jay.betterconnect.core.ble.SpecialAccess

@Composable
fun OnboardingRoute(onGranted: () -> Unit, viewModel: OnboardingViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    OnboardingScreen(state = state, onAction = viewModel::onAction, onGranted = onGranted)
}

@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    onAction: (OnboardingAction) -> Unit,
    onGranted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = context as? Activity

    val requiredLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val stillPermanentlyDenied = activity?.let { act ->
            state.requiredPermissions.filter {
                grants[it] != true && !act.shouldShowRequestPermissionRationale(it)
            }.toSet()
        } ?: emptySet()
        onAction(OnboardingAction.RequiredPermissionsResult(grants, stillPermanentlyDenied))
    }

    val enhancedLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants -> onAction(OnboardingAction.EnhancedPermissionsResult(grants)) }

    val callScreeningLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { onAction(OnboardingAction.RefreshSpecialAccess) }

    LaunchedEffect(Unit) {
        if (!state.allRequiredGranted && state.permanentlyDenied.isEmpty()) {
            requiredLauncher.launch(state.requiredPermissions.toTypedArray())
        }
    }

    // Notification access has no runtime dialog - only re-checking on resume detects it.
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnAction = rememberUpdatedState(onAction)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                currentOnAction.value(OnboardingAction.RefreshSpecialAccess)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state.allRequiredGranted) {
        if (state.allRequiredGranted) onGranted()
    }

    if (state.hasPermanentDenial) {
        PermanentDenialDialog(
            missing = state.permanentlyDenied,
            onOpenSettings = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData("package:${context.packageName}".toUri()),
                )
            },
        )
        return
    }

    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Set up Better Connect", style = MaterialTheme.typography.headlineSmall)
        Text(
            "The cluster link needs Bluetooth, location and notification permissions to work " +
                "at all.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        Button(
            onClick = { requiredLauncher.launch(state.requiredPermissions.toTypedArray()) },
        ) { Text("Grant required permissions") }

        Text(
            "Optional: call and message alerts on the cluster",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 32.dp, bottom = 8.dp),
        )
        Text(
            "These enable missed-call and message alerts on the cluster display. You can " +
                "skip this and enable it later in Settings.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
        OutlinedButton(
            modifier = Modifier.padding(top = 12.dp),
            onClick = { enhancedLauncher.launch(state.enhancedPermissions.toTypedArray()) },
        ) { Text("Grant phone state & contacts") }
        OutlinedButton(
            modifier = Modifier.padding(top = 8.dp),
            onClick = { context.startActivity(SpecialAccess.notificationAccessSettingsIntent()) },
        ) {
            Text(
                if (state.notificationAccessGranted) {
                    "Notification access on"
                } else {
                    "Enable notification access"
                },
            )
        }
        OutlinedButton(
            modifier = Modifier.padding(top = 8.dp),
            onClick = {
                SpecialAccess.callScreeningRoleRequestIntent(context)
                    ?.let(callScreeningLauncher::launch)
            },
        ) {
            Text(
                if (state.callScreeningGranted) {
                    "Caller ID role held"
                } else {
                    "Enable caller ID & spam apps role"
                },
            )
        }
    }
}

@Composable
private fun PermanentDenialDialog(missing: Set<String>, onOpenSettings: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Permission needed") },
        text = {
            Text(
                "Better Connect can't work without " +
                    missing.joinToString { it.substringAfterLast('.') } +
                    ". Grant it from app settings.",
            )
        },
        confirmButton = { Button(onClick = onOpenSettings) { Text("Open settings") } },
    )
}
