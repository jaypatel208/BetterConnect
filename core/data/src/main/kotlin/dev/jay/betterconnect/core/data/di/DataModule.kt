package dev.jay.betterconnect.core.data.di

import dev.jay.betterconnect.core.data.SwitchableTransport
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.jay.betterconnect.core.domain.DiagLog
import dev.jay.betterconnect.core.domain.SequenceRunner
import dev.jay.betterconnect.core.ble.BleScanner
import dev.jay.betterconnect.core.link.ClusterTransport
import dev.jay.betterconnect.core.link.DemoCapableTransport
import dev.jay.betterconnect.core.link.DeviceScanner
import dev.jay.betterconnect.core.link.WriteScheduler
import dev.jay.betterconnect.core.protocol.TbtEncoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    /** Application-lifetime scope for the link and the heartbeat. */
    @Provides
    @Singleton
    fun provideAppScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideEncoder(): TbtEncoder = TbtEncoder()

    @Provides
    @Singleton
    fun provideDemoCapableTransport(switchable: SwitchableTransport): DemoCapableTransport = switchable

    @Provides
    @Singleton
    fun provideTransport(transport: DemoCapableTransport): ClusterTransport = transport

    @Provides
    @Singleton
    fun provideScheduler(transport: ClusterTransport): WriteScheduler = WriteScheduler(transport)

    @Provides
    @Singleton
    fun provideRunner(scheduler: WriteScheduler, encoder: TbtEncoder): SequenceRunner =
        SequenceRunner(scheduler, encoder)

    @Provides
    @Singleton
    fun provideDiagLog(): DiagLog = DiagLog()

    @Provides
    @Singleton
    fun provideScanner(scanner: BleScanner): DeviceScanner = scanner
}
