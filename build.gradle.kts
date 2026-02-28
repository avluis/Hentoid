plugins {
    id("com.android.application") version ("9.0.1") apply false
    id("com.google.gms.google-services") version ("4.4.1") apply false
    id("io.objectbox") version "5.1.0" apply false
    id("org.sonarqube") version "7.2.2.6593"
    id("com.google.firebase.crashlytics") version "3.0.6" apply false
    // Kept for ObjectBox while they still use kapt (see https://github.com/objectbox/objectbox-java/issues/1075)
    id("com.android.legacy-kapt") version ("9.0.0") apply false
    id("com.mikepenz.aboutlibraries.plugin") version "13.2.1" apply false
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven { url=uri("https://jitpack.io") } // AppIntro
        maven { url=uri("https://plugins.gradle.org/m2/") } // Sonarcloud
    }
}