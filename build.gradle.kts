plugins {
    kotlin("jvm") version "1.8.0"
    application

}

group = "org.production"
version = "40.0"

repositories {
    mavenCentral()
}
//android {
//    sourceSets {
//        getByName("main") {
//            java.srcDir("src/main/kotlin")
//        }
//    }
//}
//buildscript {
//    repositories {
//        mavenCentral()
//    }
//    dependencies {
//        classpath("com.android.tools.build:gradle:0.xx.y")
//    }
//}
//
//allprojects {
//    repositories {
//        mavenCentral()
//    }
//}

dependencies {
    testImplementation(kotlin("test"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.4.0")
    implementation("com.google.code.gson:gson:2.10")
    implementation("org.apache.commons:commons-math3:3.6.1")


}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(11)

}
application {
    mainClass.set("MainKt")
}