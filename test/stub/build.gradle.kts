// A compile-only copy of Posse's ReachService, so LanternGraves builds without Posse. Never shipped.
plugins { java }
repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}
dependencies { compileOnly("io.papermc.paper:paper-api:26.1.2.build.74-stable") }
java { toolchain.languageVersion.set(JavaLanguageVersion.of(25)) }
