plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm {
        jvmToolchain(8)
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":runtime"))
                implementation(project(":runtime-server"))
                implementation(project(":transport-ktor"))

                implementation("io.ktor:ktor-server-core:2.3.4")
                implementation("io.ktor:ktor-server-websockets:2.3.4")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
                implementation("org.jetbrains.kotlin:kotlin-reflect:1.9.0")
            }
        }
    }
}