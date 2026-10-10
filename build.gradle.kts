import org.jetbrains.changelog.Changelog
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    kotlin("jvm") version "2.0.21"
    id("org.jetbrains.intellij.platform") version "2.2.1"
    id("org.jetbrains.changelog") version "2.2.1"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation(project(":core"))

    intellijPlatform {
        // Built against the Community edition and runs in every IDE built on the
        // same platform (IDEA, PyCharm, WebStorm, GoLand, CLion, Rider, ...): the
        // plugin depends on the platform alone, not on any language's support.
        intellijIdeaCommunity("2024.2.5")
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
            sinceBuild = "242"
            // Open-ended: the platform APIs used here are long-stable, and
            // pluginVerification below is what says when a release stops working.
            untilBuild = provider { null }
        }
    }
    pluginVerification {
        ides {
            recommended()
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
}

changelog {
    repositoryUrl = "https://github.com/omartelo/lich-intellij"
    versionPrefix = "v"
    // Keep a Changelog lists only the sections a release has; the cut leaves [Unreleased] empty, not six empty headings.
    groups.empty()
}
