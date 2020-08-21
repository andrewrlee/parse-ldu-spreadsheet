plugins {
    kotlin("jvm") version "1.4.0"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.apache.poi:poi:3.17")
    implementation("org.apache.poi:poi-ooxml:3.17")
    implementation ("com.beust:klaxon:5.4")

    testImplementation("junit:junit:4.13")
    testImplementation(kotlin("test-junit"))
}