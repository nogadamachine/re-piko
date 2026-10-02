group = "crimera"

patches {
    about {
        name = "re-piko"
        description = "Instagram 449.0.0.52.84용 비공식 Piko 패치입니다. Instagram 기능을 한국어로 제공합니다."
        source = "https://github.com/nogadamachine/re-piko"
        author = "re-piko contributors; based on crimera/piko"
        contact = "na"
        website = "https://github.com/nogadamachine/re-piko"
        license = "GNU General Public License v3.0"
    }
}

dependencies {
    compileOnly("com.github.REAndroid:ARSCLib:a28c6fb2a7")

    // Used by JsonGenerator.
    implementation(libs.gson)

    implementation(libs.morphe.patches.library)
}

tasks {
    register<JavaExec>("checkStringResources") {
        description = "Checks resource strings for invalid formatting"

        dependsOn(compileKotlin)

        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("app.morphe.util.resource.CheckStringKt")
    }

    register<JavaExec>("generatePatchesList") {
        description = "Build patch with patch list"

        dependsOn(build)

        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("app.morphe.util.PatchListGeneratorKt")
    }
    // Used by gradle-semantic-release-plugin.
    publish {
        dependsOn("generatePatchesList")
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs = listOf("-Xcontext-parameters")
    }
}

// IG448 port-only JVM audit. This does not test the app on Android.
tasks.register<JavaExec>("verify448Selector") {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("app.crimera.patches.instagram.filter.story.StoryAppendSelectorTestKt")
    args(rootProject.file("port-audit/448_story_parser.tsv").absolutePath)
}
