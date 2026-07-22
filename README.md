# Plex Discord Bridge

A dual-mode Plex 2.0 module and standalone Paper plugin that relays chat between
Minecraft and Discord using JDA.

## Features

- Independent, bidirectional public-chat and staff-chat routes.
- Channel ID lookup with a unique channel-name fallback.
- Optional guild scoping for safe fallback-name resolution.
- Bot/webhook filtering, disabled Discord mentions, and literal insertion of
  Discord content into Adventure components.
- Folia-safe Discord-to-Minecraft scheduling.
- JDA loaded at runtime by Plex rather than shaded into the module.
- Configurable server-started and server-stopped Discord messages.
- Persistent Minecraft-to-Discord links using expiring one-time codes.
- A native Dialog API account dashboard with live Discord display names and
  MiniMessage hex styling from each member's highest colored role.
- An opt-in, batched Discord console channel with linked-player command
  execution and live server log output.
- A console-only account-link administration command.

## Build

This project targets the current Plex `2.0-SNAPSHOT` API, Java 25, and Paper
26.2. Run `./gradlew build`; the same `Module-DiscordBridge.jar` supports both
installation modes.

### Plex module mode

Copy the JAR into `plugins/Plex/modules` and fully restart the server. Plex
loads JDA through the module's `module.yml`, runs dialect-specific module
migrations, and stores links in a module-prefixed table through Plex's shared
Hikari pool and JDBI instance. A module reload is not enough the first time
because Plex resolves module libraries while Paper constructs its classpath.

### Standalone Paper mode

Copy the JAR directly into `plugins`. Paper loads the standalone entry point
from `plugin.yml` and resolves JDA, Hikari, JDBI, and the SQL drivers through
its library loader. Standalone mode owns its Hikari pool and supports SQLite,
MariaDB, and PostgreSQL through the `database` section of `config.yml`.

Install the JAR in exactly one mode; do not place copies in both `plugins` and
`plugins/Plex/modules` on the same server.

### Source layout

- `common` contains configuration, dialog UI, linking, relay services, and the
  platform abstraction shared by both runtime modes.
- `module` contains only Plex commands, listeners, lifecycle, and platform
  integration.
- `standalone` contains only the Paper entry point, commands, listeners,
  platform adapter, and owned Hikari database implementation.

Plex staff-chat capture is only available in module mode because standalone
Paper has no Plex staff-chat event. Public chat, Discord-to-permission staff
messages, lifecycle messages, and account linking work in both modes.

On first load Plex creates
`plugins/Plex/modules/Module-DiscordBridge/config.yml`. Add the bot token and
channel IDs there, then restart. The bot needs View Channel, Send Messages, and
Read Message History in each configured channel. Enable Message Content Intent
for it in the Discord Developer Portal.

In standalone mode Paper creates `plugins/PlexDiscordBridge/config.yml`
instead.

The module uses a normal bundled `config.yml`. If an older Plex build copied
Plex's own configuration into the module data folder, the bridge preserves it
as `config.yml.plex-backup` and generates the correct file on the next start.

If a channel ID is blank or cannot be resolved, the bridge tries that route's
`fallback-name`. A fallback is accepted only when it matches exactly one text
channel. Set `guild-id` to constrain this lookup. If neither value resolves,
only that route stays disabled; the module and the other route continue.

## Account linking

With `linking.enabled: true`, a player runs `/discordlink` in Minecraft to open
a native dialog dashboard. It can generate and copy a one-time code, refresh
the linked profile, show the Discord member display name in their highest
colored role's exact hex color, and unlink with confirmation. The player sends
the code in a direct message to the Discord bot; successful links are stored in
SQL. Plex mode uses Plex's configured database; standalone mode uses the
configured standalone Hikari datasource. An existing legacy `links.yml` is
imported once and renamed to `links.yml.migrated`.

Players can still use `/discordlink code`, `/discordlink status`, and, when
enabled, `/discordlink unlink` as text fallbacks. Discord messages can display
a linked Minecraft name using `linking.linked-name-format`.

The console-only `/discordlinkadmin` command supports:

- `list`
- `lookup minecraft <player>`
- `lookup discord <discord-id>`
- `code <player>`
- `link <player> <discord-id>`
- `unlink minecraft <player>`
- `unlink discord <discord-id>`

Plex registers the administrative command with a console-only command source,
so it is unavailable to players even if they have operator permissions.

## Discord console channel

The `channels.console` route is disabled by default. Set its channel ID (or a
unique fallback name), then independently enable `server-output-to-discord`
and `discord-to-server-commands`. Server output is sent in bounded batches to
avoid flooding Discord during log bursts.

A Discord command is accepted only when its author has a stored account link
and the linked Minecraft player is currently online. The command is dispatched
as that real player, so Minecraft sees the player's current name and applies
their normal permissions. It never executes with console privileges and does
not create a fake console sender. Command responses intended for the sender are
therefore shown to the player in Minecraft; the Discord channel receives the
normal server console/log stream.

Treat this channel as sensitive. Server logs can contain addresses, plugin
errors, configuration values, and other operational details, so Discord access
should be limited to trusted staff.

## Plex staff-chat API

The accompanying Plex changes add two supported integration points:

- `PlexPlayerView.staffChat()` exposes the player's current staff-chat mode.
- `StaffChatMessageEvent` covers both toggled staff chat and the direct
  `/adminchat <message>` command form. The event is cancellable and its
  Adventure message can be replaced before Plex delivers it.

The Discord module consumes that public event and has no dependency on Plex
server internals.

## Security notes

Keep the bot token only in the generated server configuration. Minecraft text
is Markdown-escaped before sending to Discord, and all outgoing allowed
mentions are disabled. Discord text is inserted as literal Adventure text, so
it cannot inject MiniMessage tags or click events.
