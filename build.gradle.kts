plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.24"
    id("org.jetbrains.intellij.platform") version "2.1.0"
}

group = "uk.suff"
version = "1.0-SNAPSHOT"


repositories {
    mavenCentral()
    intellijPlatform{
        defaultRepositories()
        intellijDependencies()
    }
}

intellijPlatform.pluginVerification.ides{
    ide("IU-2024.2.3")
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "21"
    }

    patchPluginXml {
        // java 21 is required from 242+ so to be able to support latest this is as low as we can go
        // todo: look into separate build or module or something for lower versions
        sinceBuild.set("242")
        untilBuild.set("243.*") // gonna need to update this as versions release
    }

    runIde {
        jvmArgs = listOf(
            "-XX:+UnlockDiagnosticVMOptions"
        )
    }
    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }

    buildSearchableOptions { enabled = false }

    prepareSandbox {
        doFirst {
            delete(fileTree(defaultDestinationDirectory))
        }
    }
}

dependencies {
    implementation("org.eclipse.jetty:jetty-server:11.0.20")
    implementation("org.eclipse.jetty:jetty-servlet:11.0.20")
    implementation("org.eclipse.jetty.websocket:websocket-jetty-server:11.0.20")
    implementation("org.eclipse.jetty.websocket:websocket-jetty-client:11.0.20")
    implementation("org.json:json:20231013")
    intellijPlatform {
        intellijIdeaUltimate("2024.2.3")
        bundledPlugins("JavaScript")
        instrumentationTools()
        pluginVerifier()
    }

}
