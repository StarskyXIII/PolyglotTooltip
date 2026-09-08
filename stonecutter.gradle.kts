plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "1.21.1-neoforge"

stonecutter parameters {
    val loader = current.project.substringAfterLast('-')
    constants {
        match(loader, "fabric", "neoforge", "forge")
    }
}
