# Only unused Material icon implementations may be removed from the debug APK.
# Keep every other app/dependency class and member, including JNI/reflection entry
# points. Do not optimize or rename code; retain annotations and debug metadata.
-dontoptimize
-dontobfuscate
-keepattributes *
-keep class !androidx.compose.material.icons.**,** { *; }

# Commons Compress/Lang ship optional JVM adapters for formats that this app does
# not use. These exact classes were already absent from the unshrunk debug APK;
# RomArchive uses java.util.zip and SevenZFile, with the bundled XZ implementation.
-dontwarn com.github.luben.zstd.BufferPool
-dontwarn com.github.luben.zstd.Zstd
-dontwarn com.github.luben.zstd.ZstdInputStream
-dontwarn com.github.luben.zstd.ZstdOutputStream
-dontwarn java.lang.invoke.MethodHandleProxies
-dontwarn org.brotli.dec.BrotliInputStream
-dontwarn org.objectweb.asm.AnnotationVisitor
-dontwarn org.objectweb.asm.Attribute
-dontwarn org.objectweb.asm.ClassReader
-dontwarn org.objectweb.asm.ClassVisitor
-dontwarn org.objectweb.asm.FieldVisitor
-dontwarn org.objectweb.asm.Label
-dontwarn org.objectweb.asm.MethodVisitor
-dontwarn org.objectweb.asm.Type
