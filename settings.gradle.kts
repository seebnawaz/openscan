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
        mavenCentral()  // OpenCV 4.11.0 is published here: org.opencv:opencv
    }
}
rootProject.name = "OpenScan"
include(":app")
