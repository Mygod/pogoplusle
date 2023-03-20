# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable
-dontobfuscate

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Parcel reading may lookup/validate the parcel and creator via their
# inner-class relationship. Ensure the attributes are kept and the
# inner/outer relationship is soft pinned. The 'allowshrinking' option
# allows the classes to be removed if unused, but otherwise their attributes
# are retained.
-keepattributes EnclosingClass,InnerClasses
#-keep,allowshrinking,allowobfuscation class * implements android.os.Parcelable {}
#-keep,allowshrinking,allowobfuscation class * implements android.os.Parcelable$Creator {}
