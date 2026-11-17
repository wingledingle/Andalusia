# MediaPipe uses reflection/JNI and protobuf-lite.
-keep class com.google.mediapipe.** { *; }
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.mediapipe.**
-dontwarn com.google.protobuf.**
-dontwarn javax.annotation.**
# Brushes are persisted with Java serialization.
-keep class com.silpa.engine.BrushSettings { *; }
-keep enum com.silpa.engine.** { *; }
-keepclassmembers class * implements java.io.Serializable { static final long serialVersionUID; private static final java.io.ObjectStreamField[] serialPersistentFields; !static !transient <fields>; private void writeObject(java.io.ObjectOutputStream); private void readObject(java.io.ObjectInputStream); java.lang.Object writeReplace(); java.lang.Object readResolve(); }
-keep enum com.silpa.feature.studio.Tool { *; }
