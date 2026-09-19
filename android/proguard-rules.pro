# LibGDX - P1 修复：防止 release 包混淆导致闪退
-verbose
-dontwarn com.badlogic.**
-dontwarn com.mistbound.**
-dontwarn android.**

-keep class com.badlogic.** { *; }
-keep class com.badlogic.gdx.backends.android.** { *; }
-keep class com.badlogic.gdx.graphics.g2d.** { *; }
-keep class com.badlogic.gdx.audio.** { *; }

-keep class com.mistbound.** { *; }

# libGDX 通过反射加载，保持默认构造
-keepclasseswithmembers class * {
    public <init>(...);
}

# 防止 R 被混淆
-keep class **.R$* { *; }
