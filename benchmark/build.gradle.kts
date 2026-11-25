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

tasks.register<JavaExec>("runOvernight") {
    group = "benchmark"
    description = "Runs the comprehensive overnight benchmark suite across all 6 dimensions"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.tdscript.benchmark.OvernightTestSuiteKt")
    systemProperties(System.getProperties().map { it.key.toString() to it.value }.toMap())
}
