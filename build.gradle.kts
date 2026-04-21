plugins {
    id("java")
    id("org.springframework.boot") version "3.5.13"
    id("io.spring.dependency-management") version "1.1.7"
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
}

tasks.test {
    useJUnitPlatform()
}