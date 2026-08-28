package dev.jay.betterconnect.feature.onboarding

import android.Manifest
import app.cash.turbine.test
import dev.jay.betterconnect.core.ble.FullPermissions
import dev.jay.betterconnect.core.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OnboardingViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun viewModel() = OnboardingViewModel(RuntimeEnvironment.getApplication())

    @Test
    fun `nothing granted on first launch reports every required permission missing`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            val state = awaitItem()
            assertFalse(state.allRequiredGranted)
            assertTrue(FullPermissions.required.all { it in state.missingRequired })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a partial grant only clears the permissions actually granted`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitItem()

            val partial = FullPermissions.required.associateWith { false } +
                (FullPermissions.required.first() to true)
            vm.onAction(OnboardingAction.RequiredPermissionsResult(partial, emptySet()))

            val state = awaitItem()
            assertFalse(state.allRequiredGranted)
            assertTrue(FullPermissions.required.first() !in state.missingRequired)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `every required permission granted clears the missing list`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitItem()

            val allGranted = FullPermissions.required.associateWith { true }
            vm.onAction(OnboardingAction.RequiredPermissionsResult(allGranted, emptySet()))

            val state = awaitItem()
            assertTrue(state.allRequiredGranted)
            assertTrue(state.missingRequired.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a permanent denial is tracked separately from an ordinary missing grant`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitItem()

            val denied = setOf(Manifest.permission.ACCESS_FINE_LOCATION)
            vm.onAction(
                OnboardingAction.RequiredPermissionsResult(
                    FullPermissions.required.associateWith { false },
                    denied,
                ),
            )

            val state = awaitItem()
            assertTrue(state.hasPermanentDenial)
            assertTrue(Manifest.permission.ACCESS_FINE_LOCATION in state.permanentlyDenied)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `enhanced permissions never block required-permission state`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitItem()

            vm.onAction(
                OnboardingAction.RequiredPermissionsResult(
                    FullPermissions.required.associateWith { true },
                    emptySet(),
                ),
            )
            val afterRequired = awaitItem()
            assertTrue(afterRequired.allRequiredGranted)

            vm.onAction(
                OnboardingAction.EnhancedPermissionsResult(
                    mapOf(Manifest.permission.READ_PHONE_STATE to true),
                ),
            )
            val afterEnhanced = awaitItem()
            assertTrue(
                "enhanced results must not affect the required gate",
                afterEnhanced.allRequiredGranted,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }
}
