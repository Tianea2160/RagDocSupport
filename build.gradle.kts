import org.springframework.boot.gradle.tasks.run.BootRun

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.ktlint)
    `java-test-fixtures`
}

ktlint {
    version.set(libs.versions.ktlint.core)
}

group = "org.tianea"
version = "0.0.1-SNAPSHOT"
description = "RagDocSupport"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
    maven { url = uri("https://repo.spring.io/milestone") }
}

dependencies {
    // Spring Boot
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.kotlin.reflect)
    implementation(libs.jackson.module.kotlin)

    // Spring AI - MCP Server
    implementation(libs.spring.ai.starter.mcp.server.webmvc)

    // Spring AI - Ollama Embedding
    implementation(libs.spring.ai.starter.model.ollama)

    // Qdrant Java Client
    implementation(libs.qdrant.client)

    // Thymeleaf + htmx
    implementation(libs.spring.boot.starter.thymeleaf)
    implementation(libs.webjars.htmx)

    // SQLite + JPA (task persistence)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.sqlite.jdbc)
    implementation(libs.hibernate.community.dialects)

    // HTML Parsing
    implementation(libs.jsoup)

    // YAML parsing for doc-sources.yml
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.spring.boot.starter.actuator)

    // Spring Boot Docker Compose Support (dev only)
    developmentOnly(libs.spring.boot.docker.compose)

    // Netty native DNS resolver for macOS Apple Silicon
    runtimeOnly(libs.netty.resolver.dns.native.macos) {
        artifact {
            classifier = "osx-aarch_64"
        }
    }

    testFixturesImplementation(libs.kotlin.reflect)

    testImplementation(testFixtures(project))
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.mockk)
    testImplementation(libs.kotest.assertions.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}

dependencyManagement {
    imports {
        mavenBom(libs.spring.ai.bom.get().toString())
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("--enable-preview")
}

tasks.withType<Test>().configureEach {
    jvmArgs("--enable-preview")
}

tasks.named<BootRun>("bootRun") {
    jvmArgs("--enable-preview")
}

val benchmarkTask = tasks.register<Test>("benchmark") {
    description = "Run RAG benchmark tests (requires Qdrant + Ollama)"
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform {
        includeTags("benchmark")
    }
}

tasks.withType<Test> {
    if (this != benchmarkTask.get()) {
        useJUnitPlatform {
            excludeTags("benchmark")
        }
    }
}
