val drillPluginId: String by settings
rootProject.name = "$drillPluginId-plugin"

apply(from = "plugins.settings.gradle.kts")

include(":api")
include(":agent-api")
include(":admin-part")
include(":agent-part")
include(":plugin-runner")
