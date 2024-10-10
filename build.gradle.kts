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
        sinceBuild.set("IU-232.*") // needs updating
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
        intellijIdeaUltimate("2024.2.0.1")
        bundledPlugins("JavaScript")
        instrumentationTools()
    }

}
