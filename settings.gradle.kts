pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // matrix-rust-sdk artifacts when available
        maven { url = uri("https://gitlab.matrix.org/api/v4/projects/27/packages/maven") }
    }
}

rootProject.name = "MatrixTelegramClient"
include(":app")
