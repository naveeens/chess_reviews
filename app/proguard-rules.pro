# Keep all Kotlin serialization and chess data models
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}

-keep class wrklabs.chess.model.** { *; }
-keep class wrklabs.chess.classifier.** { *; }
-keep class wrklabs.chess.engine.** { *; }
