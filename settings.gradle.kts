rootProject.name = "LanternGraves"

// A compile-only copy of Posse's ReachService, so LanternGraves builds without Posse. Never shipped.
include("posse-stub")
project(":posse-stub").projectDir = file("test/stub")
