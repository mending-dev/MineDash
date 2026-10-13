// Shared by every project that ships a "frontend" folder (a Vite project).
// Registers npmInstall and buildFrontend. Build output: <project>/frontend/dist

val frontendDir = file("frontend")
val isWindows = System.getProperty("os.name").lowercase().contains("win")
val home: String = System.getProperty("user.home")

// IntelliJ started from the Dock/Start menu does not inherit the shell PATH,
// so common Node install locations are added explicitly
val extraPaths = mutableListOf("/opt/homebrew/bin", "/usr/local/bin", "$home/.volta/bin")
file("$home/.nvm/versions/node").listFiles()
    ?.sortedByDescending { it.name }
    ?.forEach { extraPaths.add(File(it, "bin").path) }
val separator = File.pathSeparator
val fullPath = (listOfNotNull(System.getenv("PATH")) + extraPaths).joinToString(separator)

// Runs npm through the user's login shell so it is found like in a terminal
fun npmCommand(vararg args: String): List<String> {
    val line = "npm " + args.joinToString(" ")
    return if (isWindows) {
        listOf("cmd", "/c", line)
    } else {
        listOf(System.getenv("SHELL") ?: "/bin/sh", "-l", "-c", line)
    }
}

val npmInstall = tasks.register<Exec>("npmInstall") {
    group = "frontend"
    workingDir = frontendDir
    environment("PATH", fullPath)
    commandLine(npmCommand("install"))
    inputs.files(fileTree(frontendDir) { include("package.json", "package-lock.json") })
    // Marker file written by npm, much cheaper to track than the whole node_modules folder
    outputs.file(frontendDir.resolve("node_modules/.package-lock.json"))
}

tasks.register<Exec>("buildFrontend") {
    group = "frontend"
    dependsOn(npmInstall)
    workingDir = frontendDir
    environment("PATH", fullPath)
    commandLine(npmCommand("run", "build"))
    inputs.files(fileTree(frontendDir) { exclude("node_modules/**", "dist/**") })
    outputs.dir(frontendDir.resolve("dist"))
}