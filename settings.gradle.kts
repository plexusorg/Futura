rootProject.name = "plex-discordbridge"

// Compile against a local Plex checkout until the staff-chat API is published.
// Override with -Dplex.checkout=/path/to/Plex on another machine.
val plexCheckout = providers.systemProperty("plex.checkout")
    .orElse("${System.getProperty("user.home")}/IdeaProjects/Plex")
    .get()
includeBuild(plexCheckout)
