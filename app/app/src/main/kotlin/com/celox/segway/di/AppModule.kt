package com.celox.segway.di

import android.content.Context
import com.celox.segway.core.ble.BleScanner
import com.celox.segway.core.ble.GattClient
import com.celox.segway.core.data.AppDatabase
import com.celox.segway.core.data.AppDatabaseProvider
import com.celox.segway.core.data.TrackDao
import com.celox.segway.core.data.VehicleDao
import com.celox.segway.core.util.BleLog
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun gattClient(
        @ApplicationContext context: Context,
        bleLog: BleLog
    ): GattClient = GattClient(context, bleLog)

    @Provides @Singleton
    fun bleScanner(@ApplicationContext context: Context): BleScanner = BleScanner(context)

    @Provides @Singleton
    fun database(provider: AppDatabaseProvider): AppDatabase = provider.db

    @Provides
    fun vehicleDao(db: AppDatabase): VehicleDao = db.vehicleDao()

    @Provides
    fun trackDao(db: AppDatabase): TrackDao = db.trackDao()
}
