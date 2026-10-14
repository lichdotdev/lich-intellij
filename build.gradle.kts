import org.jetbrains.changelog.Changelog
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    kotlin("jvm") version "2.4.21"
    id("org.jetbrains.intellij.platform") version "2.19.0"
    id("org.jetbrains.changelog") version "2.5.0"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

// Built against, and verified on, the oldest IDE sinceBuild admits.
val oldestSupportedIde = "2024.3.7"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation(project(":core"))

    intellijPlatform {
        // Built against IntelliJ IDEA Community. The plugin depends on the platform alone, not on any language's
        // support, so other JetBrains IDEs can install it, untested.
        intellijIdeaCommunity(oldestSupportedIde)
        // Not on the compile classpath by default; the patch builder the Commit view's "Create Patch" uses lives here.
        bundledModule("intellij.platform.vcs.impl")
        pluginVerifier()
        zipSigner()
        testFramework(TestFrameworkType.Platform)
    }
}

intellijPlatform {
    pluginConfiguration {
        // The Marketplace's "What's new": this version's CHANGELOG.md section, or [Unreleased] before it is cut.
        changeNotes = providers.gradleProperty("pluginVersion").map { pluginVersion ->
            with(changelog) {
                renderItem(
                    (getOrNull(pluginVersion) ?: getUnreleased()).withHeader(false).withEmptySections(false),
                    Changelog.OutputType.HTML,
                )
            }
        }
        ideaVersion {
            sinceBuild = "243"
            // Open-ended: the platform APIs used here are long-stable, and
            // pluginVerification below is what says when a release stops working.
            untilBuild = provider { null }
        }
    }
    pluginVerification {
        // The two ends of the supported range, not recommended()'s seven: each IDE is gigabytes extracted, and seven
        // fill a CI runner's disk (and filled a laptop's). The newest is bumped by hand; see docs/ceilings.md.
        ides {
            create(IntelliJPlatformType.IntellijIdeaCommunity, oldestSupportedIde)
            create(IntelliJPlatformType.IntellijIdea, "2026.2.3")
        }
    }
    // Read from the environment only at signing and publishing time, so a local build needs none of them.
    // .github/workflows/release.yml passes them from the repository secrets.
    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        // The oldest IDE supported (sinceBuild 243) runs on the Kotlin stdlib it bundles, 2.0: a call into anything
        // newer compiles here and fails there with NoSuchMethodError.
        apiVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0
        // Platform interfaces built with DefaultImpls (ToolWindowFactory among them) otherwise get a bridge in every
        // implementing class for each default method, @Internal ones included, and verifyPlugin fails on those.
        jvmDefault = org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode.NO_COMPATIBILITY
    }
}

changelog {
    repositoryUrl = "https://github.com/lichdotdev/lich-intellij"
    versionPrefix = "v"
    // Keep a Changelog lists only the sections a release has; the cut leaves [Unreleased] empty, not six empty headings.
    groups.empty()
}
