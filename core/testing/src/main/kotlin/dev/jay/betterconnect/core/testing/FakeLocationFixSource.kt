package dev.jay.betterconnect.core.testing

import dev.jay.betterconnect.core.domain.LocationFixSource
import dev.jay.betterconnect.core.model.LocationFix
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Lets a test push [LocationFix]s on demand instead of waiting on a real GPS. */
class FakeLocationFixSource : LocationFixSource {

    private val fixesFlow = MutableSharedFlow<LocationFix>(extraBufferCapacity = 32)

    fun emit(fix: LocationFix) {
        fixesFlow.tryEmit(fix)
    }

    override fun fixes() = fixesFlow.asSharedFlow()
}
