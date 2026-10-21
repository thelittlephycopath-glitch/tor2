import java.util.Properties

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    val localProperties = Properties().also { properties ->
        val file = File(rootDir, "local.properties")
        if (file.exists()) {
            file.inputStream().use { inputStream ->
                properties.load(inputStream)
            }
        }
    }

    val hasCIJobToken = System.getenv("CI_JOB_TOKEN") != null
    val hasLocalMaven = System.getenv("ENFORCE_LOCAL_MAVEN") == "1"
    val hasGitLabPrivateToken = localProperties.getProperty("gitLabPrivateToken") != null

    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()

        // Include onionmasq from available maven repos
        if (hasGitLabPrivateToken && !hasLocalMaven) {
            maven {
                url = uri("https://gitlab.torproject.org/api/v4/projects/1192/packages/maven")
                name = "GitLab Private"
                credentials(HttpHeaderCredentials::class.java) {
                    name = "Private-Token"
                    value = localProperties.getProperty("gitLabPrivateToken")
                }
                authentication {
                    register("header", HttpHeaderAuthentication::class.java)
                }
                content {
                    includeModule("org.torproject", "onionmasq")
                }
            }
        } else if (hasCIJobToken && !hasLocalMaven) {
            maven {
                url = uri("https://gitlab.torproject.org/api/v4/projects/1192/packages/maven")
                name = "GitLab CI"
                credentials(HttpHeaderCredentials::class.java) {
                    name = "Job-Token"
                    value = System.getenv("CI_JOB_TOKEN")
                }
                authentication {
                    register("header", HttpHeaderAuthentication::class.java)
                }
                content {
                    includeModule("org.torproject", "onionmasq")
                }
            }
        } else {
            println("WARNING: Including onionmasq from local maven repository!")
            mavenLocal {
                content {
                    includeModule("org.torproject", "onionmasq")
                }
            }
        }
    }
}
rootProject.name = "Tor VPN"
include(":app")
