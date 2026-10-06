plugins {
    java
}

group = "com.raresb"
version = "1.7.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // Matches the server: Paper 26.3 build 140
    compileOnly("io.papermc.paper:paper-api:26.3.build.140-beta")
    // Netty as bundled with the server (PackHost serves the resource pack on the game port)
    compileOnly("io.netty:netty-transport:4.2.16.Final")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-Xlint:deprecation")
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}
