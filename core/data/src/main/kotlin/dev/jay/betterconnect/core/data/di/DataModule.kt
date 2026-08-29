package dev.jay.betterconnect.core.data.di

import android.content.Context
import android.content.pm.PackageManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.jay.betterconnect.core.ble.BleScanner
import dev.jay.betterconnect.core.ble.DeviceLocationSource
import dev.jay.betterconnect.core.data.DataStoreDeviceRepository
import dev.jay.betterconnect.core.data.RideLog
import dev.jay.betterconnect.core.data.RoutesApiRepository
import dev.jay.betterconnect.core.data.SwitchableTransport
import dev.jay.betterconnect.core.domain.DiagLog
import dev.jay.betterconnect.core.domain.LocationFixSource
import dev.jay.betterconnect.core.domain.RoutesRepository
import dev.jay.betterconnect.core.domain.SequenceRunner
import dev.jay.betterconnect.core.link.ClusterTransport
import dev.jay.betterconnect.core.link.ControlPump
import dev.jay.betterconnect.core.link.DemoCapableTransport
import dev.jay.betterconnect.core.link.DeviceRepository
import dev.jay.betterconnect.core.link.DeviceScanner
import dev.jay.betterconnect.core.link.GeneralScheduler
import dev.jay.betterconnect.core.link.WriteScheduler
import dev.jay.betterconnect.core.protocol.TbtEncoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.io.File
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    /** Application-lifetime scope for the link and the heartbeat. */
    @Provides
    @Singleton
    fun provideAppScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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
    fun provideControlPump(transport: ClusterTransport): ControlPump = ControlPump(transport)

    @Provides
    @Singleton
    fun provideGeneralScheduler(transport: ClusterTransport, controlPump: ControlPump): GeneralScheduler =
        GeneralScheduler(transport, controlPump.acks)

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

    @Provides
    @Singleton
    fun provideDeviceRepository(impl: DataStoreDeviceRepository): DeviceRepository = impl

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient()

    /**
     * Read from the same manifest meta-data tag (`com.google.android.geo.API_KEY`) the Maps
     * SDK itself reads, so one key entered in `secrets.properties` covers both the map and
     * the Routes API call - no separate BuildConfig field to keep in sync. Empty (not
     * missing) when unset, so a keyless build never crashes here.
     */
    @Provides
    @Named("mapsApiKey")
    fun provideMapsApiKey(@ApplicationContext context: Context): String = runCatching {
        val info = context.packageManager.getApplicationInfo(
            context.packageName,
            PackageManager.GET_META_DATA,
        )
        info.metaData?.getString("com.google.android.geo.API_KEY")
    }.getOrNull().orEmpty()

    @Provides
    @Singleton
    fun provideRoutesRepository(impl: RoutesApiRepository): RoutesRepository = impl

    @Provides
    @Singleton
    fun provideLocationFixSource(@ApplicationContext context: Context): LocationFixSource =
        LocationFixSource { DeviceLocationSource.fixes(context) }

    @Provides
    @Singleton
    fun provideRideLog(@ApplicationContext context: Context): RideLog =
        RideLog(File(context.filesDir, "ride-logs"))
}
