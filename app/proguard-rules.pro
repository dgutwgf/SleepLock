# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep data classes
-keep class com.sleeplock.data.entity.** { *; }

# Keep Room entities
-keep class * extends androidx.room.Entity { *; }

# Keep DAO interfaces
-keep interface com.sleeplock.data.dao.** { *; }

# Keep services
-keep class com.sleeplock.service.** { *; }

# Keep receivers
-keep class com.sleeplock.receiver.** { *; }
