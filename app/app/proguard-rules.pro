# Keep BouncyCastle (used by reflection)
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# Keep Nordic BLE
-keep class no.nordicsemi.** { *; }
-dontwarn no.nordicsemi.**

# Keep our pairing model classes (serialized)
-keepclassmembers class com.celox.segway.core.data.** { *; }
-keep @kotlinx.serialization.Serializable class com.celox.segway.** { *; }

# Standard Kotlin / Compose / Hilt / Room rules are bundled.
