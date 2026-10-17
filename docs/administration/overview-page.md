# The Overview page

The Overview page is the first page after login. It shows the state of the server as a grid of cards, and it updates by itself.

## Requirements

- The account needs the permission `overview.view` to see the built-in cards.
- Cards that come from modules need the permission the module declares for them. A module without a widget adds no cards.
- An account without any visible card sees a note that says so.

## Built-in cards

| Card | Shows |
| --- | --- |
| **Players** | Players online and the maximum, with a bar for the share of slots in use |
| **Uptime** | Time since the server started, and the start time |
| **TPS** | Ticks per second from 0 to 20 with a bar and a verdict: *Running smoothly* (18 or more), *Slightly lagging* (15 to 18), *Lagging* (below 15), *Not responding* (0) |
| **Storage** | Used and total space of the drive the server runs on, and the free space |
| **CPU usage** | CPU load of the server process and of the whole machine, with a graph |
| **Memory** | Java heap in use, maximum, allocated and free, with a bar and a graph |
| **Server** | Software (Paper, Spigot, …), Minecraft version, port, online mode, number of plugins, build |
| **System** | Operating system, version, architecture, processors, physical memory, maximum heap, Java version and vendor, JVM, time zone |

Some values depend on the system. A Java runtime that cannot report the CPU load makes the CPU card say so, and a system without disk information shows a dash on the Storage card.

## Update rate

While the page is open and the browser tab is visible, the browser asks the server for new samples every couple of seconds. The server keeps a short history in memory, so the graphs are filled right after you open the page. When no built-in card is on screen, the page does not ask for metrics at all.

If the connection is lost, the status next to the title changes to *Reconnecting…* and the page keeps trying. *No access* means the account lacks `overview.view`.

## Show or hide cards

Press **Customize** to switch single cards on and off, or **Show all** to bring back everything. The choice is stored **in your browser**, per account name, and applies to the built-in cards and to the cards of modules. New cards that appear later (for example after you install a module) are shown by default.

Because the choice lives in the browser, it does not follow you to another browser or computer.

## Card sizes

Cards come in three sizes: small (one column), medium (two columns) and large (full width). The grid has four columns on wide screens, two on tablets and one on phones.

## Cards from modules

Modules can add cards. They appear in the customize list under the name of the module. If a card fails to load, only that card shows an error message and the others keep working. Developers: see [Widgets](../developers/widgets.md).
