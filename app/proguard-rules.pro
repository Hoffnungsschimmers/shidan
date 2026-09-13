# 味笺 R8 / ProGuard 规则
#
# Release 构建已开启 isMinifyEnabled 与 isShrinkResources。以下规则用于保证
# 反射驱动的框架（Room、Hilt、Compose、Coil）在压缩后仍能正常工作。
# 改动本文件后必须执行 assembleRelease 验证，不能只依赖 Debug 构建通过。

# ---------------------------------------------------------------- Room
# Room 生成的实现类通过反射按名称查找，实体字段名与数据库列名需保持一致。
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# ---------------------------------------------------------------- Hilt / Dagger
# Hilt 在编译期生成代码并经反射注入，组件与入口点不可被裁剪。
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep,allowobfuscation,allowshrinking interface dagger.hilt.internal.GeneratedComponent
-keepnames @dagger.hilt.android.lifecycle.HiltViewModel class * extends androidx.lifecycle.ViewModel

# ---------------------------------------------------------------- 数据模型
# 枚举常量名是数据库中的持久化编码（见 Converters），必须保留；
# 实体类被 Room 反射读取，同样不可混淆。
-keepclassmembers enum com.fanji.mealnote.data.local.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keep class com.fanji.mealnote.data.local.** { *; }

# ---------------------------------------------------------------- Kotlin
-dontwarn kotlin.**
-dontwarn kotlinx.**

# ---------------------------------------------------------------- Coil
-keep class coil.** { *; }
-dontwarn coil.**

# ---------------------------------------------------------------- 调试属性
# 便于崩溃上报中定位问题；若接入混淆映射上传可移除。
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*, SourceFile, LineNumberTable
