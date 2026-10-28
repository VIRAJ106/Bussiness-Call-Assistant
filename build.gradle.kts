plugins {
    java
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management) apply false
    alias(libs.plugins.spotless) apply false
}

allprojects {
    group = "com.dayclose"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-parameters", "-Xlint:all"))
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        jvmArgs("-XX:+EnableDynamicAgentLoading")
    }

    // Apply Spring dependency management to all subprojects for consistent versions
    apply(plugin = "io.spring.dependency-management")
    val versionCatalog = rootProject.extensions.getByType<VersionCatalogsExtension>().named("libs")
    the<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension>().apply {
        imports {
            mavenBom(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES)
            val testcontainersVer = versionCatalog.findVersion("testcontainers").get().requiredVersion
            val otelVer = versionCatalog.findVersion("opentelemetry-bom").get().requiredVersion
            mavenBom("org.testcontainers:testcontainers-bom:$testcontainersVer")
            mavenBom("io.opentelemetry:opentelemetry-bom:$otelVer")
        }
    }
}

// Library subprojects: plain JAR, no Spring Boot plugin
configure(subprojects.filter { it.path.startsWith(":libs:") }) {
    // Libraries produce a plain JAR (no bootJar)
    dependencies {
        "testImplementation"(platform(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES))
        "testImplementation"("org.springframework.boot:spring-boot-starter-test")
    }
}

// Service subprojects: Spring Boot applications
configure(subprojects.filter { it.path.startsWith(":services:") }) {
    apply(plugin = "org.springframework.boot")

    dependencies {
        "implementation"(project(":libs:dayclose-events"))
        "implementation"(project(":libs:dayclose-dto"))
        if (path != ":services:api-gateway") {
            "implementation"(project(":libs:dayclose-web-starter"))
        }
        "testImplementation"(project(":libs:dayclose-test"))
        "testImplementation"("org.springframework.boot:spring-boot-starter-test")
    }
}
