plugins {
  id("me.roundaround.allay")
}

allay {
  displayName.set("Game Rules")
  description.set("Modify game rules in your existing worlds.")
  authors.set(listOf("Roundaround"))
  license.set("MIT")
  homepage.set("https://modrinth.com/mod/game-rules")
  repository.set("https://github.com/Roundaround/mc-game-rules")
  issues.set("https://github.com/Roundaround/mc-game-rules/issues")

  gametest {
    // Acknowledge the Minecraft EULA for the throwaway worlds the headless
    // server game test spins up.
    eula.set(true)
  }

  modrinth {
    projectId.set("game-rules")
  }

  curseforge {
    projectId.set(1292156)
  }

  release {
    versionType.set("release")
    minecraftVersions("26.2")
    changelogDir.set(file("changelogs"))
  }
}
