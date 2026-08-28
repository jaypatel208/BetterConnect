package dev.jay.betterconnect.feature.onboarding

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.jay.betterconnect.core.ble.FullPermissions
import dev.jay.betterconnect.core.ble.SpecialAccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * Every Android permission-request/role-grant call lives at the Activity layer (system
 * dialogs and Settings deep-links need an Activity, not an injected Context), so this
 * ViewModel never calls them - it only tracks the outcome the Route reports back. The
 * one exception is the *initial* [PackageManager] check, which only needs a Context.
 */
data class OnboardingUiState(
    val requiredGrants: Map<String, Boolean> = emptyMap(),
    val permanentlyDenied: Set<String> = emptySet(),
    val enhancedGrants: Map<String, Boolean> = emptyMap(),
    val notificationAccessGranted: Boolean = false,
    val callScreeningGranted: Boolean = false,
) {
    val requiredPermissions: List<String> get() = FullPermissions.required
    val enhancedPermissions: List<String> get() = FullPermissions.enhanced

    val missingRequired: List<String>
        get() = requiredPermissions.filterNot { requiredGrants[it] == true }

    val allRequiredGranted: Boolean get() = missingRequired.isEmpty()
    val hasPermanentDenial: Boolean get() = permanentlyDenied.isNotEmpty()

    val missingEnhanced: List<String>
        get() = enhancedPermissions.filterNot { enhancedGrants[it] == true }
}

sealed interface OnboardingAction {
    data class RequiredPermissionsResult(
        val grants: Map<String, Boolean>,
        val permanentlyDenied: Set<String>,
    ) : OnboardingAction

    data class EnhancedPermissionsResult(val grants: Map<String, Boolean>) : OnboardingAction

    data object RefreshSpecialAccess : OnboardingAction
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(@ApplicationContext private val context: Context) :
    ViewModel() {

    private val requiredGrants = MutableStateFlow(initialGrantMap(FullPermissions.required))
    private val permanentlyDenied = MutableStateFlow<Set<String>>(emptySet())
    private val enhancedGrants = MutableStateFlow(initialGrantMap(FullPermissions.enhanced))
    private val notificationAccessGranted =
        MutableStateFlow(SpecialAccess.isNotificationAccessGranted(context))
    private val callScreeningGranted =
        MutableStateFlow(SpecialAccess.isCallScreeningRoleHeld(context))

    val uiState: StateFlow<OnboardingUiState> = combine(
        requiredGrants,
        permanentlyDenied,
        enhancedGrants,
        notificationAccessGranted,
        callScreeningGranted,
    ) { required, denied, enhanced, notificationAccess, callScreening ->
        OnboardingUiState(
            requiredGrants = required,
            permanentlyDenied = denied,
            enhancedGrants = enhanced,
            notificationAccessGranted = notificationAccess,
            callScreeningGranted = callScreening,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OnboardingUiState())

    fun onAction(action: OnboardingAction) {
        when (action) {
            is OnboardingAction.RequiredPermissionsResult -> {
                requiredGrants.update { it + action.grants }
                permanentlyDenied.value = action.permanentlyDenied
            }

            is OnboardingAction.EnhancedPermissionsResult -> {
                enhancedGrants.update { it + action.grants }
            }

            OnboardingAction.RefreshSpecialAccess -> {
                notificationAccessGranted.value = SpecialAccess.isNotificationAccessGranted(context)
                callScreeningGranted.value = SpecialAccess.isCallScreeningRoleHeld(context)
            }
        }
    }

    private fun initialGrantMap(permissions: List<String>): Map<String, Boolean> =
        permissions.associateWith {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
}
