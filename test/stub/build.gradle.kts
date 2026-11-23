// A compile-only copy of Mayor's ReachService, so LanternGraves builds without Mayor. Never shipped.
plugins { java }
repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}
dependencies { compileOnly("io.papermc.paper:paper-api:26.1.2.build.74-stable") }
java { toolchain.languageVersion.set(JavaLanguageVersion.of(25)) }
