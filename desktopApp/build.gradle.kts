plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
}

dependencies {
    implementation(project(":common-editor"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material)
    testImplementation("junit:junit:4.13.2")
}

compose.desktop {
    application {
        mainClass = "com.tdscript.studio.desktop.MainKt"
        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb, org.jetbrains.compose.desktop.application.dsl.TargetFormat.AppImage)
            packageName = "tdscript-studio"
            packageVersion = "1.1.0"
        }
    }
}
