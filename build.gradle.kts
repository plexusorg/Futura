import net.minecrell.pluginyml.paper.PaperPluginDescription

plugins {
    id("java")
    `maven-publish`
    id("de.eldoria.plugin-yml.paper") version "0.9.0"
    id("com.gradleup.shadow") version "9.6.1"
}

group = "dev.plex"
version = "3.0-SNAPSHOT"
description = "Futura"

repositories {
    mavenCentral()
    maven {
        name = "papermc-repo"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
    maven {
        name = "sonatype"
        url = uri("https://oss.sonatype.org/content/groups/public/")
    }
    maven {
        name = "telesphoreo-repo"
        url = uri("https://nexus.telesphoreo.me/repository/plex/")
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
    compileOnly("dev.plex:api:2.0-SNAPSHOT")

    compileOnly("org.apache.logging.log4j:log4j-api:2.25.2")
    compileOnly("org.apache.logging.log4j:log4j-core:2.25.2")

    implementation("net.dv8tion:JDA:6.5.0")
    implementation("org.bstats:bstats-base:3.2.1")
    implementation("org.bstats:bstats-bukkit:3.2.1")
}

paper {
    name = "Futura"
    version = project.version.toString()
    main = "dev.plex.futura.FuturaPlugin"
    apiVersion = "1.20"
    authors = listOf("Telesphoreo", "Taah", "NotInSync")
    description = "Discord plugin bridge for Minecraft"
    website = "https://plex.us.org"

    serverDependencies {
        register("Plex") {
            required = false
            load = PaperPluginDescription.RelativeLoadOrder.BEFORE
        }
    }
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks {
    compileJava {
        options.encoding = Charsets.UTF_8.name()
    }
    javadoc {
        options.encoding = Charsets.UTF_8.name()
    }
    processResources {
        filteringCharset = Charsets.UTF_8.name()
    }

    build {
        dependsOn(shadowJar)
    }

    jar {
        enabled = false
    }

    shadowJar {
        archiveBaseName.set("Futura")
        archiveClassifier.set("")
        relocate("org.bstats", "dev.plex.futura.libs.bstats")
        relocate("net.dv8tion", "dev.plex.futura.libs.jda")
    }
}

publishing {
    repositories {
        maven {
            val releasesRepoUrl = uri("https://nexus.telesphoreo.me/repository/plex-releases/")
            val snapshotsRepoUrl = uri("https://nexus.telesphoreo.me/repository/plex-snapshots/")
            url = if (rootProject.version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl
            credentials {
                username = System.getenv("plexUser")
                password = System.getenv("plexPassword")
            }
        }
    }
    publications {
        create<MavenPublication>("maven") {
            pom.withXml {
                val dependenciesNode = asNode().appendNode("dependencies")
                configurations.getByName("library").allDependencies.configureEach {
                    dependenciesNode.appendNode("dependency")
                            .appendNode("groupId", group).parent()
                            .appendNode("artifactId", name).parent()
                            .appendNode("version", version).parent()
                            .appendNode("scope", "provided").parent()
                }
                configurations.getByName("implementation").allDependencies.configureEach {
                    dependenciesNode.appendNode("dependency")
                            .appendNode("groupId", group).parent()
                            .appendNode("artifactId", name).parent()
                            .appendNode("version", version).parent()
                            .appendNode("scope", "provided").parent()
                }
            }
            artifacts.artifact(tasks.shadowJar)
        }
    }
}
