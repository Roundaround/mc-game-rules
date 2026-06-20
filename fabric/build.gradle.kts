plugins {
  id("me.roundaround.allay")
}

repositories {
  exclusiveContent {
    forRepository { mavenLocal() }
    filter { includeModuleByRegex("me\\.roundaround", "trove(-.+)?") }
  }
}

allay {
  modrinth {
    dependencies {
      required("fabric-api")
    }
  }
}

dependencies {
  libBundle(platform(libs.trove.bom))
  libBundle(libs.trove.fabric.core)
  libBundle(libs.trove.gui)
  libBundle(libs.trove.network)

  gametestImplementation(platform(libs.trove.bom))
  gametestImplementation(libs.trove.fabric.gametest)
}
