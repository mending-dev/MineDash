# Console module

A live view of the server console in the browser, with a command line to run commands.

- Module id: `console`
- Requires MineDash with module API version 1 and Java 8 or newer
- Nothing else has to be installed

## Features

- Live log of the server: new lines appear as they are written
- The last 1000 lines are available as history when you open the page
- Time stamp and level for each line, warnings and errors are coloured
- Command input with the history of your commands (arrow up and down)
- **Clear** empties the view in your browser (it does not touch the server log)
- The status of the connection is shown on the page and reconnects by itself

## Permissions

| Permission | Allows |
| --- | --- |
| `console.view` | See the live log |
| `console.execute` | Run commands on the server |

- Permissions do not include each other. Someone who should read the log **and** run commands needs both.
- With `console.view` but without `console.execute`, the command line is disabled and says *Read-only: missing permission console.execute*.
- `console.*` grants both, `*` grants everything.

```json
{ "username": "helper", "password": "change-me", "permissions": ["overview.view", "console.view"] }
```

> **`console.execute` is a powerful permission.** Commands run with the rights of the server console. They can stop the server, change operators, and with some plugins run system commands. Give it only to people you would trust with console access.

## How commands run

- A command is sent over HTTPS (or HTTP) to the module and then run **as the server console** on the main server thread.
- A leading `/` is removed, so `say hello` and `/say hello` do the same.
- A command has to be one line and at most 1000 characters.
- The result of a command appears in the log like any other output, after the audit line described below.
- **Audit:** every executed command is written to the server log as `<username> executed: <command>`. It shows up in the console and in `latest.log`.

## Log lines

The log shows what the server writes through its logger. Output that a program writes directly to the process output without the logger may not appear. Lines have a level (`INFO`, `WARN`, `ERROR`, …), a time and the message.

## Limits

| Limit | Value |
| --- | --- |
| History kept on the server | 1000 lines |
| Lines kept in the browser | 2000 |
| Command length | 1000 characters |
| Connections per account | 20 (shared with all WebSocket features) |

## Installing and removing

The module is a jar in `plugins/MineDash/modules/`. Disable it on the **Modules** page, or remove the jar, to turn the console off for everyone. See [Modules](../administration/modules.md).

## When it does not work

| Symptom | Cause |
| --- | --- |
| The page stays on *Connecting…* or *Disconnected* | A proxy that does not pass WebSockets, see [Reverse proxy basics](../deployment/reverse-proxy.md) |
| *Forbidden* or no log | The account has no `console.view` |
| The command line is grey | The account has no `console.execute` |
| Commands are accepted but nothing happens | The command does not exist, or it prints to the player instead of the console. Check the log. |

More in [Troubleshooting](../reference/troubleshooting.md).

## For developers

The module is a small example of a real module with a WebSocket channel and a protected endpoint. The source is in `modules/console`. Protocol:

- WebSocket `/api/ws?module=console&channel=logs`: first message `{"type":"history","entries":[…]}`, then `{"type":"log","entry":{…}}` for each line. The client sends `ping` and gets `{"type":"pong"}`.
- `POST /api/modules/console/command` with `{"command":"…"}` and permission `console.execute`. Answer `204`.
- A log entry has the fields `id`, `time` (milliseconds since 1970), `level`, `logger`, `message`.
