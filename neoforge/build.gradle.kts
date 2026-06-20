plugins {
  id("me.roundaround.allay")
}

repositories {
  exclusiveContent {
    forRepository { mavenLocal() }
    filter { includeModuleByRegex("me\\.roundaround", "trove(-.+)?") }
  }
}

dependencies {
  libBundle(platform(libs.trove.bom))
  libBundle(libs.trove.neoforge.core)
  libBundle(libs.trove.gui)
  libBundle(libs.trove.network)

  gametestImplementation(platform(libs.trove.bom))
  gametestImplementation(libs.trove.neoforge.gametest)
}
