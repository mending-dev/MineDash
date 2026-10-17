# Installation

## 1. Get the files

You need:

- the plugin jar, named like `MineDash-<version>.jar`
- one jar per module you want, for example the Console and the Files module

Use the jars of a release, or build them from source (see [Building from source](#building-from-source) below).

## 2. Install the plugin

1. Stop the server.
2. Copy the plugin jar into the `plugins` folder of the server.
3. Start the server.

On the first start MineDash creates its folder `plugins/MineDash/` with these files:

```text
plugins/MineDash/
├── config.json     settings of the panel
├── users.json      accounts
└── modules/        put module jars here (empty on the first start)
```

The server log shows the address of the panel and, because no accounts exist yet, the password of the automatically created administrator account:

```text
[MineDash] ==================================================
[MineDash] No accounts found. Created default admin account:
[MineDash]   Username: admin
[MineDash]   Password: aB3dEf7hJk9mNp2q
[MineDash] This password is shown only once.
[MineDash] ==================================================
[MineDash] Web panel running on port 8765
```

> **Warning:** The password is shown only once. Copy it now. If you lose it, see [Lost password](../administration/users-and-permissions.md#lost-password).

## 3. Install modules

1. Copy the module jars into `plugins/MineDash/modules/`.
2. Restart the server.

New module jars are detected **when the server starts**. A module that is found for the first time is **enabled** automatically. After that you can switch it on and off on the page *Manage modules* without a restart. See [Managing modules](../administration/modules.md).

## 4. Open the panel

Open `http://<address-of-your-server>:8765` in a browser.

- On the machine that runs the server: `http://localhost:8765`
- From another computer: `http://<ip-of-the-server>:8765`, and the port has to be open in the firewall

Log in with the account from the log. Details are on the next page: [First login](first-login.md).

> **Warning:** This address uses plain HTTP, so the password travels unencrypted. That is fine on your own computer or inside a trusted network. For anything reachable from the internet, set up HTTPS first, see [Deployment](../deployment/overview.md).

## Building from source

The project is a Gradle build. You need JDK 17 or newer to run Gradle (the result still runs on Java 8) and Node.js 20.19 or newer for the frontends.

```bash
./gradlew build
```

The jars end up here:

| Jar | Location |
| --- | --- |
| Plugin | `core/build/libs/MineDash-<version>.jar` |
| Console module | `modules/console/build/libs/console-<version>.jar` |
| Files module | `modules/files/build/libs/files-<version>.jar` |

Gradle builds the frontends automatically by running `npm install` and `npm run build`, so Node.js has to be available. If you start Gradle from a program that does not know your shell `PATH` (for example IntelliJ IDEA started from the Dock), the build looks for Node.js in the usual places (Homebrew, nvm, Volta).

## Verify the installation

The repository contains a script that checks a running server from the outside:

```bash
tools/smoke-test.sh http://localhost:8765 admin '<password>'
```

It checks that anonymous requests are rejected, that foreign origins are blocked, that the security headers are set, that login, overview and the Console module work, and that logout ends the session. The Console module has to be enabled for the whole run to pass. If you use `"secureCookie": true`, pass an `https://` address.

## Uninstall

1. Stop the server.
2. Delete the plugin jar.
3. Optionally delete `plugins/MineDash/`. This removes the accounts, the settings and the data of all modules.
