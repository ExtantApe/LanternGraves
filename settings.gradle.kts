rootProject.name = "LanternGraves"

// A compile-only copy of Mayor's ReachService, so LanternGraves builds without Mayor. Never shipped.
include("mayor-stub")
project(":mayor-stub").projectDir = file("test/stub")
