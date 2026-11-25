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

tasks.register<JavaExec>("runSystemBenchmark") {
    group = "benchmark"
    description = "Runs the comprehensive system benchmark suite (Cold Start, Throughput, Memory, Security, Faults, Conformance)"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.tdscript.benchmark.OvernightTestSuiteKt")
    systemProperties(System.getProperties().map { it.key.toString() to it.value }.toMap())
}

tasks.register<JavaExec>("runOvernight") {
    group = "benchmark"
    description = "Alias for runSystemBenchmark"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.tdscript.benchmark.OvernightTestSuiteKt")
    systemProperties(System.getProperties().map { it.key.toString() to it.value }.toMap())
}
