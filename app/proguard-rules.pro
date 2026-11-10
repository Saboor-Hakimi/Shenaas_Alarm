# MediaPipe ships no consumer rules. Its JNI layer calls back into Java classes by name and its
# protobuf-lite messages are read reflectively, so keep both intact.
-keep class com.google.mediapipe.** { *; }
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.mediapipe.**
-dontwarn com.google.protobuf.**
# Optional MediaPipe dependency for on-device LLMs, not used here.
-dontwarn com.google.ai.edge.litertlm.**
-dontwarn com.google.auto.value.**
-dontwarn javax.lang.model.**
-dontwarn javax.annotation.processing.**
