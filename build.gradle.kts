plugins {
    java
}

repositories {
    maven {
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
    maven {
        url = uri("https://nexus.telesphoreo.me/repository/plex/")
    }
    mavenCentral()
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
    compileOnly("dev.plex:api:2.0-SNAPSHOT")
    compileOnly("net.dv8tion:JDA:6.5.0")
    compileOnly("com.zaxxer:HikariCP:7.1.0")
    compileOnly("org.jdbi:jdbi3-core:3.54.0")
    compileOnly("org.xerial:sqlite-jdbc:3.53.2.0")
    compileOnly("org.mariadb.jdbc:mariadb-java-client:3.5.9")
    compileOnly("org.postgresql:postgresql:42.7.13")

}

group = "dev.plex"
version = "1.0.0"
description = "Discord chat bridge module for Plex"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.jar {
    archiveBaseName.set("Module-DiscordBridge")
    archiveVersion.set("")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = Charsets.UTF_8.name()
}

tasks.withType<Javadoc>().configureEach {
    options.encoding = Charsets.UTF_8.name()
}

tasks.processResources {
    filteringCharset = Charsets.UTF_8.name()
}
