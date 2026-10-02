# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *

# Hilt
-keep class dagger.hilt.** { *; }
-keep @dagger.hilt.android.HiltAndroidApp class *
-keep @dagger.hilt.android.lifecycle.HiltViewModel class *

# Kotlin coroutines
-keepclassmembers class kotlinx.coroutines.** { *; }

# Gson (retrofit)
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }

# DataStore
-keep class androidx.datastore.** { *; }

# Vico charts
-keep class com.patrykandpatrick.vico.** { *; }
