plugins {
    kotlin("jvm")
    application
}

application {
    mainClass.set("com.tdscript.studio.desktop.MainKt")
}

dependencies {
    implementation(project(":common-editor"))
    testImplementation("junit:junit:4.13.2")
}
