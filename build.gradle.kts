
import com.github.spotbugs.snom.Confidence
import com.github.spotbugs.snom.Effort
import com.github.spotbugs.snom.SpotBugsTask

plugins {
    id("java")
    id("checkstyle")
    id("org.springframework.boot") version "3.5.13"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.github.spotbugs") version "6.5.1"
    id("com.diffplug.spotless") version "8.3.0"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}
extra["tomcat.version"] = "10.1.54"
extra["snakeyaml.version"] = "2.6"

dependencies {
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation("org.postgresql:postgresql:42.7.10")
    implementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    implementation("org.mindrot:jbcrypt:0.4")
    implementation("io.jsonwebtoken:jjwt-api:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.13.0")
    implementation("org.eclipse.angus:angus-mail:2.0.5")
    implementation("org.jsmpp:jsmpp:3.0.1")
}

tasks.test {
    useJUnitPlatform()
    systemProperty("spring.profiles.active", "test")
}

// Spotbugz
spotbugs {
    ignoreFailures.set(false)
    showStackTraces.set(true)
    showProgress.set(true)
    effort.set(Effort.DEFAULT)
    reportLevel.set(Confidence.DEFAULT)
}

tasks.withType<SpotBugsTask>().configureEach {
    excludeFilter.set(layout.projectDirectory.file("config/spotbugs/excludeFilter.xml"))
    reports.create("html") {
        required.set(true)
        outputLocation.set(layout.buildDirectory.file("reports/spotbugs/$name.html"))
    }
    reports.create("xml") {
        required.set(false)
    }
}

// Spotless
spotless {
    java {
        target("src/main/java/**/*.java", "src/test/java/**/*.java")
        googleJavaFormat()
        trimTrailingWhitespace()
        endWithNewline()
    }

    kotlinGradle {
        target("*.gradle.kts")
        ktlint()
    }
}

// Checkstyle
checkstyle {
    toolVersion = "10.12.4"
}

tasks.withType<Checkstyle>().configureEach {
    reports {
        xml.required.set(false)
        html.required.set(true)
    }
}
