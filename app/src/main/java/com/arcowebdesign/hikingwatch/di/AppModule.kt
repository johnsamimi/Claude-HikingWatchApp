package com.arcowebdesign.hikingwatch.di

import android.content.Context
import androidx.health.services.client.HealthServices
import androidx.health.services.client.HealthServicesClient
import androidx.room.Room
import com.arcowebdesign.hikingwatch.data.db.*
import com.google.android.gms.location.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideHikingDatabase(@ApplicationContext context: Context): HikingDatabase =
        Room.databaseBuilder(
            context,
            HikingDatabase::class.java,
            "hiking_database"
        )
        .fallbackToDestructiveMigration()
        .build()

    @Provides
    @Singleton
    fun provideSessionDao(db: HikingDatabase): SessionDao = db.sessionDao()

    @Provides
    @Singleton
    fun provideWaypointDao(db: HikingDatabase): WaypointDao = db.waypointDao()

    @Provides
    @Singleton
    fun provideFusedLocationClient(
        @ApplicationContext context: Context
    ): FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    @Provides
    @Singleton
    fun provideHealthServicesClient(
        @ApplicationContext context: Context
    ): HealthServicesClient = HealthServices.getClient(context)
}
