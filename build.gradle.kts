plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

// Sundial is a CORE mod: FML must load sundial.asm.Plugin BEFORE any mod class
// so its ASM transformer can patch WorldServer/WorldClient.tick(). The gtnhgradle
// convention did not emit the manifest attributes from gradle.properties
// (coreModClass), so they are set explicitly here.
tasks.jar {
    manifest {
        attributes(
            "FMLCorePlugin" to "sundial.asm.Plugin",
            "FMLCorePluginContainsFMLMod" to "true"
        )
    }
}