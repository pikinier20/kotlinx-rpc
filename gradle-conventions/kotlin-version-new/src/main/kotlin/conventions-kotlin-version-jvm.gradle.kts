/*
 * Copyright 2023-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import util.projectLanguageVersion

configure<KotlinJvmProjectExtension> {
    compilerOptions(projectLanguageVersion)
}
