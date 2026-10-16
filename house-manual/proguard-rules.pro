# Room, kotlinx-serialization and ML Kit ship consumer rules; keep generated DB and serializers.
-keep class * extends androidx.room.RoomDatabase
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * { kotlinx.serialization.KSerializer serializer(...); }
-dontwarn com.google.mlkit.**
