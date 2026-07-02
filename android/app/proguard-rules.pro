# Vosk uses JNA for JNI bindings — keep its classes and native bindings intact.
-keep class org.vosk.** { *; }
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** { *; }
-dontwarn java.awt.**
-dontwarn org.vosk.**
-dontwarn com.sun.jna.**

# kotlinx.serialization
-keepclassmembers class **$$serializer { *; }
-keepclassmembers @kotlinx.serialization.Serializable class * { *; }
-keep,includedescriptorclasses class com.vibeflow.mobile.**$$serializer { *; }
