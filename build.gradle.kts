plugins {
    id("java")
    `maven-publish`
    id("net.minecrell.plugin-yml.paper") version "0.6.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "dev.plex"
version = "1.0-SNAPSHOT"
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
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT")
    library("net.dv8tion:JDA:5.0.0-beta.20") {
        exclude(module = "opus-java")
    }
    implementation("org.bstats:bstats-base:3.0.2")
    implementation("org.bstats:bstats-bukkit:3.0.2")
}

paper {
    name = "Futura"
    version = project.version.toString()
    main = "dev.plex.futura.Futura"
    apiVersion = "1.20"
    authors = listOf("Telesphoreo", "Taah")
    description = "Discord plugin bridge for Minecraft"
    website = "https://plex.us.org"
    loader = "dev.plex.futura.util.FuturaLibraryManager"
    generateLibrariesJson = true
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
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
        relocate("org.bstats", "dev.plex")
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
