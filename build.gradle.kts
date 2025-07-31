plugins {
    id("java")
}

group = "vn.com.fecredit.util"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.sun.mail:javax.mail:1.6.2")
    implementation("net.java.dev.jna:jna:5.14.0")
// JNA for WMI access
    implementation("net.java.dev.jna:jna-platform:5.14.0")
// JNA platform for Windows-specific APIs
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}