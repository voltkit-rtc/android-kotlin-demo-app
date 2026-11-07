# The SVGA player reads its protobuf classes through Wire.
-keep class com.opensource.svgaplayer.proto.** { *; }
-keep class com.squareup.wire.** { *; }
-dontwarn com.squareup.wire.**
-dontwarn org.jetbrains.annotations.**
