plugins {
    `java-gradle-plugin`
}

group = "org.alexmond"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.26.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

gradlePlugin {
    plugins {
        create("unitrack") {
            id = "org.alexmond.unitrack"
            implementationClass = "org.alexmond.unitrack.gradle.UnitrackPlugin"
            displayName = "UniTrack uploader"
            description = "Upload test/coverage reports and check the quality gate, reusing the unitrack-cli engine."
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
