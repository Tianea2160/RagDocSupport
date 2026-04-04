plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.ktlint)
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

    // HTML Parsing
    implementation(libs.jsoup)

    // Web Crawling
    implementation(libs.webmagic.core)
    implementation(libs.webmagic.extension)

    // YAML parsing for doc-sources.yml
    implementation(libs.jackson.dataformat.yaml)

    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.kotlin.test.junit5)
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

tasks.withType<Test> {
    useJUnitPlatform()
}
