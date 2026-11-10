plugins {
    java
}

group = "com.extantape"
version = "0.1.2"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.onarandombox.com/multiverse-releases/")
}

val paperApi = "io.papermc.paper:paper-api:26.1.2.build.74-stable"
val mvInventories = "org.mvplugins.multiverse.inventories:multiverse-inventories:5.3.6"

dependencies {
    compileOnly(paperApi)
    compileOnly(mvInventories) { isTransitive = false }

    testImplementation(paperApi)
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
    options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:removal"))
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("paper-plugin.yml") { expand(props) }
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    archiveBaseName.set("LanternGraves")
    from("LICENSE")
}

// A tiny plugin for the bot tests only (test/), never shipped: it kills a tagged player as they log out.
val testHook by sourceSets.creating {
    compileClasspath += configurations.compileClasspath.get()
}
tasks.register<Jar>("hookJar") {
    from(testHook.output)
    archiveFileName.set("LanternGravesTestHook.jar")
    destinationDirectory.set(layout.buildDirectory.dir("test-hook"))
}
