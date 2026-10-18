plugins {
    java
}

group = "dev.simpleteams"
version = "1.0.0"

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // Bump this to the newest 26.3 build listed at https://papermc.io/downloads/paper
    compileOnly("io.papermc.paper:paper-api:26.3.build.143-beta")
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}
