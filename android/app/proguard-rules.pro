# LINKO release rules.
# Keep Kotlin serialization metadata used by the update/control-plane models.
-keepattributes *Annotation*,InnerClasses,EnclosingMethod,Signature
-keep class kotlinx.serialization.** { *; }
