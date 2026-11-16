# R8 / ProGuard Configuration for Compose Runtime Protection
-dontobfuscate
-dontoptimize

# Retain Compose runtime classes
-keep class androidx.compose.** { *; }
-keep interface androidx.compose.** { *; }

# Retain Coroutines & Data Models
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}
