plugins {
    kotlin("jvm")
    application
}

dependencies {
    implementation(project(":common-editor"))
    testImplementation("junit:junit:4.13.2")
}

application {
    mainClass.set("com.tdscript.benchmark.MainKt")
}
