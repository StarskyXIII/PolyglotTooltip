plugins {
    java
    id("net.neoforged.moddev") version "2.0.141"
}

group = "com.starskyxiii"
version = "0.1.0"
base.archivesName = "polyglot-name-audit-1.21.1-neoforge"

neoForge {
    version = "21.1.249"
    mods { register("polyglotnameaudit") { sourceSet(sourceSets.main.get()) } }
}

repositories { mavenCentral() }
dependencies {
    compileOnly(files("libs/clientdevbridge-1.21.1-neoforge-1.0.0-171.jar"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
tasks.test { useJUnitPlatform() }
java.toolchain.languageVersion = JavaLanguageVersion.of(21)
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 21
}
