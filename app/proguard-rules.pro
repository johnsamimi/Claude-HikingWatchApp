# OSMDroid
-keep class org.osmdroid.** { *; }
-keep interface org.osmdroid.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers class * {
    @androidx.room.* <methods>;
}

# Hilt
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager

# Health Services
-keep class androidx.health.services.** { *; }

# Keep data classes
-keep class com.arcowebdesign.hikingwatch.domain.model.** { *; }
-keep class com.arcowebdesign.hikingwatch.data.db.** { *; }
