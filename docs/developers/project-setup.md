# Project setup

Three ways to set up a module project. All three produce the same kind of jar.

| Way | Use when |
| --- | --- |
| [Inside the MineDash repository](#inside-the-minedash-repository) | You build MineDash yourself, or you contribute a module to it |
| [Standalone project](#standalone-project) | The module lives in its own repository and only needs the MineDash jar |
| [Backend only](#backend-only-modules) | The module has no browser part |

## Tools

| Tool | Version | For |
| --- | --- | --- |
| JDK | 17 or newer to run Gradle 9. The code compiles to Java 8 bytecode. | Backend |
| Node.js | 20.19 or newer | Frontend build with Vite 7 |
| A test server | Spigot or Paper with MineDash installed | Trying it out |

**Why Java 8 bytecode?** MineDash runs on Minecraft servers from 1.8 (Java 8) to the newest versions. A module built with `--release 8` loads everywhere. Use a higher release only if you do not care about old servers.

**Why compile against Spigot API 1.8.8?** So you cannot call a method that old servers lack. If your module needs newer Bukkit API, check it at runtime.

## Inside the MineDash repository

This is the setup of the [Quickstart](quickstart.md). Layout of a module folder:

```text
modules/<name>/
├── build.gradle.kts
├── frontend/                  optional, a Vite project
│   ├── package.json
│   ├── tsconfig.json
│   ├── vite.config.ts
│   └── src/
└── src/main/
    ├── java/…                 backend
    └── resources/
        ├── module.json
        └── icon.svg           optional
```

How the build works:

1. `settings.gradle.kts` includes every folder in `modules/` that has a `build.gradle.kts`.
2. `gradle/frontend.gradle.kts` adds two tasks to the module: `npmInstall` and `buildFrontend`. They run `npm` in `frontend/` through your login shell, so the Node you use in a terminal is used.
3. `processResources` copies `frontend/dist` into the jar under `web/` and replaces `${version}` in `module.json`.
4. `compileOnly(project(":api"))` is the only MineDash dependency. **Never depend on `:core`.** The core is internal and can change at any time.

Commands:

```bash
./gradlew :modules:<name>:jar          # build one module
./gradlew build                        # build everything
```

The result is `modules/<name>/build/libs/<name>-<version>.jar`.

A module without a frontend leaves out the `frontend/` folder and everything about `buildFrontend` in `build.gradle.kts`:

```kotlin
dependencies {
    compileOnly(project(":api"))
    compileOnly("org.spigotmc:spigot-api:1.8.8-R0.1-SNAPSHOT")
}

tasks.processResources {
    val version = project.version
    inputs.property("version", version)
    filesMatching("module.json") {
        expand("version" to version)
    }
}
```

## Standalone project

Your module in its own repository. MineDash does not publish its API to a Maven repository, but the **API classes are inside the MineDash plugin jar** (`dev.mending.minedash.api`, not relocated). You compile against that jar.

> This setup is described from the structure of the build. It has not been run on every machine. If a step differs on your system, the idea stays the same: compile against the MineDash jar and the Spigot API, copy the Vite output to `web/` in the jar.

Layout:

```text
my-module/
├── build.gradle.kts
├── settings.gradle.kts
├── libs/MineDash-<version>.jar      the plugin jar, only needed to compile
├── frontend/…                       as above
└── src/main/…
```

`settings.gradle.kts`:

```kotlin
rootProject.name = "my-module"
```

`build.gradle.kts`:

```kotlin
plugins {
    `java-library`
}

group = "com.example"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
}

// The bungeecord-chat snapshot required by spigot-api 1.8.8 no longer exists
configurations.all {
    exclude(group = "net.md-5", module = "bungeecord-chat")
}

dependencies {
    compileOnly(files("libs/MineDash-1.0.0-SNAPSHOT.jar"))
    compileOnly("org.spigotmc:spigot-api:1.8.8-R0.1-SNAPSHOT")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(8)
}

tasks.processResources {
    val version = project.version
    inputs.property("version", version)
    filesMatching("module.json") {
        expand("version" to version)
    }

    // Build the frontend first with "npm run build" in frontend/, then it is bundled under /web
    from("frontend/dist") {
        into("web")
    }
}
```

Build:

```bash
cd frontend && npm install && npm run build && cd ..
./gradlew jar
```

You can run `npm` from Gradle with an `Exec` task like the one in `gradle/frontend.gradle.kts` of the MineDash repository, if you want a single command.

Get the plugin jar from a release, or build it from the repository: `./gradlew :core:shadowJar` creates `MineDash-<version>.jar` in `core/build/libs/`.

## Backend-only modules

Leave out `frontend` in `module.json` and the whole `frontend/` folder. The module can still offer routes, listeners and data for other modules.

## Using Bukkit in your module

Your jar sees the classes of Bukkit and of MineDash's API through the server and plugin class loaders. In your own code you can use `Bukkit`, events, and so on as in any plugin. Register listeners with `context.getPlugin()` as owner so they are removed with MineDash:

```java
Bukkit.getPluginManager().registerEvents(listener, context.getPlugin());
```

Unregister them in `onDisable`, because a module can be switched off while the server runs:

```java
HandlerList.unregisterAll(listener);
```

## Using libraries

- **MineDash's own libraries** (Javalin, Jetty, Gson, jBCrypt, SLF4J, Kotlin) are relocated and **not available to modules**. Use `context.toJson` and `context.fromJson` for JSON.
- **Your own libraries**: bundle them into your jar (a "fat jar", for example with the Gradle Shadow plugin). Relocate them if they could clash with other plugins.
- Libraries that the server provides (Bukkit, Log4j) are `compileOnly`.

## Module class loader

Each module gets its own class loader whose parent is the MineDash plugin loader. A module therefore sees:

1. its own jar,
2. the MineDash API (`dev.mending.minedash.api.*`),
3. the server classes (Bukkit and so on).

Two modules cannot see each other's classes. To share data, use routes or the Bukkit services manager.
