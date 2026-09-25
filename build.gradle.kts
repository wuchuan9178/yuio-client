plugins {
    id("net.fabricmc.fabric-loom-remap") version "1.14.7"
}

base {
    archivesName = "yuio"
    version = "0.1.0"
    group = "com.example"
}

loom {
    accessWidenerPath = file("src/main/resources/yuio.accesswidener")
}

repositories {
    maven {
        name = "阿里云"
        url = uri("https://maven.aliyun.com/repository/public")
    }
    maven {
        name = "阿里云Gradle"
        url = uri("https://maven.aliyun.com/repository/gradle-plugin")
    }
    maven {
        name = "Fabric"
        url = uri("https://maven.fabricmc.net/")
    }
    maven {
        name = "meteor-maven"
        url = uri("https://maven.meteordev.org/releases")
    }
    maven {
        name = "meteor-maven-snapshots"
        url = uri("https://maven.meteordev.org/snapshots")
    }
    mavenCentral()
}

dependencies {
    minecraft("com.mojang:minecraft:1.21.11")
    mappings("net.fabricmc:yarn:1.21.11+build.3:v2")
    modImplementation("net.fabricmc:fabric-loader:0.18.1")
    modImplementation("meteordevelopment:meteor-client:1.21.11-SNAPSHOT")
}

tasks {
    processResources {
        val propertyMap = mapOf(
            "version" to project.version,
            "mc_version" to "1.21.11"
        )
        inputs.properties(propertyMap)
        filteringCharset = "UTF-8"
        filesMatching("fabric.mod.json") {
            expand(propertyMap)
        }
    }

    jar {
        from("LICENSE") {
            rename { "${it}_${base.archivesName.get()}" }
        }
    }

    java {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release = 21
        options.compilerArgs.add("-Xlint:deprecation")
        options.compilerArgs.add("-Xlint:unchecked")
    }
}
