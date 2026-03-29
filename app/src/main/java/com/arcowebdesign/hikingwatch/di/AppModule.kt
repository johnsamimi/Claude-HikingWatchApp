package com.arcowebdesign.hikingwatch.di

import android.content.Context
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
    @Provides @Singleton
    fun provideDb(@ApplicationContext ctx: Context): HikingDatabase =
        Room.databaseBuilder(ctx, HikingDatabase::class.java, "hiking_db")
            .fallbackToDestructiveMigration().build()

    @Provides @Singleton fun provideSessionDao(db: HikingDatabase): SessionDao = db.sessionDao()
    @Provides @Singleton fun provideWaypointDao(db: HikingDatabase): WaypointDao = db.waypointDao()

    @Provides @Singleton
    fun provideFusedLocation(@ApplicationContext ctx: Context): FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(ctx)
}
