# R8 rules for the release build. Room, kotlinx-serialization, Retrofit, OkHttp, Coil, ML Kit,
# CameraX, Glance and RevenueCat ship their own consumer rules; these cover what they cannot see.

# Stack traces from the field stay readable with the mapping file.
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod
-renamesourcefileattribute SourceFile

# kotlinx-serialization: @Serializable classes (API bodies, Navigation 3 keys, sync rows) keep
# their generated serializer and the companion that hands it out.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit service interfaces are created by reflection; keep their methods and generic types.
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# Room finds the generated <Database>_Impl by name.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
