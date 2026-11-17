pluginManagement {
    repositories { google(); gradlePluginPortal(); mavenCentral() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "Silpa"
include(":app", ":core:design", ":engine", ":ml", ":feature:home", ":feature:studio", ":feature:bank", ":monetization")
