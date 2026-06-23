plugins {
    java
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "io.livelattice"
version = "0.1.0"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.apache.kafka:kafka-clients")
    implementation("io.minio:minio:8.5.17")
    implementation("org.apache.xmlgraphics:batik-transcoder:1.18")
    implementation("org.apache.xmlgraphics:batik-codec:1.18")
    implementation("org.apache.xmlgraphics:batik-dom:1.18")
    implementation("org.apache.xmlgraphics:batik-svg-dom:1.18")
    implementation("org.apache.xmlgraphics:batik-util:1.18")
    implementation("org.apache.xmlgraphics:batik-ext:1.18")
    implementation("org.apache.xmlgraphics:fop-core:2.10")
    implementation("org.apache.poi:poi-ooxml:5.4.0")
    implementation("org.apache.tika:tika-core:3.3.0")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.mockito:mockito-core")
    testImplementation("org.testcontainers:junit-jupiter:1.21.3")
    testImplementation("org.testcontainers:minio:1.21.3")
    testImplementation("org.testcontainers:kafka:1.21.3")
    testImplementation("org.testcontainers:postgresql:1.21.3")
    testImplementation("org.awaitility:awaitility:4.3.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform {
        if (project.hasProperty("excludeTags")) {
            excludeTags(project.property("excludeTags") as String)
        }
    }
}

springBoot {
    mainClass.set("io.livelattice.importexport.ImportExportApplication")
}
