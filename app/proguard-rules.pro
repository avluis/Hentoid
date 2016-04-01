# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in ${sdk.dir}/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:

-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

-keepattributes JavascriptInterface

-keep public class me.devsaki.hentoid.activities.HitomiActivity$PageLoadListener
-keep public class * implements me.devsaki.hentoid.HitomiActivity$PageLoadListener
-keepclassmembers class me.devsaki.hentoid.activities.HitomiActivity$PageLoadListener {
   <methods>;
}

-keep public class me.devsaki.hentoid.activities.TsuminoActivity$PageLoadListener
-keep public class * implements me.devsaki.hentoid.TsuminoActivity$PageLoadListener
-keepclassmembers class me.devsaki.hentoid.activities.TsuminoActivity$PageLoadListener {
   <methods>;
}