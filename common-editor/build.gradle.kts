plugins {
    kotlin("jvm")
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

sourceSets {
    main {
        kotlin.srcDirs(
            "src/main/kotlin",
            "/home/anton/TDModules/modules/core-tdscript/src/main/kotlin"
        )
    }
}
