/**
 * @description: HMCL3项目设置文件，包含所有子项目配置
 */
rootProject.name = "HMCL3"
include(
    "HMCL",
    "HMCLCore",
    "HMCLTransformerDiscoveryService",
    "McPatchClient"  // 新增McPatchClient子项目
)

val minecraftLibraries = listOf("HMCLTransformerDiscoveryService")

for (library in minecraftLibraries) {
    project(":$library").projectDir = file("minecraft/libraries/$library")
}

// 设置McPatchClient项目目录
project(":McPatchClient").projectDir = file("McPatchClient")