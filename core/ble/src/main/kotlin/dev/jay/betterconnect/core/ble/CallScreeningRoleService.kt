package dev.jay.betterconnect.core.ble

import android.telecom.Call
import android.telecom.CallScreeningService

/**
 * Registered now so the "Caller ID & spam apps" role is visible and grantable from the
 * onboarding flow. Wiring a missed call into `MISSED_CALL` (`0310`) is future work - this
 * never blocks or screens a call, it only observes.
 */
class CallScreeningRoleService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        respondToCall(callDetails, CallResponse.Builder().build())
    }
}
