plugins {
    alias(libs.plugins.conventions.kmp)
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":krpc-runtime:krpc-runtime-api"))
            }
        }
    }
}
