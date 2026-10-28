<p align="center">
  <img src="src/main/resources/assets/client_stacker/logo.svg" width="128" alt="Client Stacker logo">
</p>

<h1 align="center">Client Stacker</h1>

<p align="center">Use compact resources for crafting and villager trading.</p>

<p align="center">
  <a href="LICENSE.md"><img src="https://img.shields.io/badge/license-MIT-22c55e" alt="License: MIT"></a>
</p>

Client Stacker is a client-side mod for Fabric and NeoForge. It prepares materials
through normal crafting, including emeralds stored as blocks. Servers do not need
the mod installed.

## Use

- In your inventory, open **Conversions** to unpack one block or prepare a chosen
  quantity. Open a crafting table to compact a material or all eligible resources.
- With a villager, use **Trade once** or **Trade to limit**. Set spending, retained
  value, and output limits in **Settings**. Converting blocks briefly closes and
  reopens the same villager. Leave one hand empty for reopening.
- Protect inventory slots or keep per-material reserves under **Conversions**.
  Named and other modified stacks are excluded. Escape cancels future work;
  completed crafts and purchases stay completed.

Shortcuts start unbound and can be assigned in Minecraft's controls. Compression
keeps 16 loose items by default. Automatic compression on opening a table is off.

## Compatibility

Build targets cover every stable Minecraft release from 1.21 through 26.3, for
Fabric and NeoForge. Install the jar matching your exact game version and loader;
Fabric also needs Fabric API. The mod belongs in the client's `mods` folder.

[Villager Trading Plus](https://modrinth.com/mod/trading-plus) is optional. Known
versions use Client Stacker for trades that need conversion or its limits, preserving
VTP's Shift settings and Ctrl bypass. Settings shows the installed version and
integration status. Unknown versions keep their own behavior.

There is no published release yet. All 34 targets pass build and startup checks.
Gameplay tests cover Fabric 1.21.11 with and without VTP 1.3.0. Gameplay on other
targets and dedicated multiplayer still need validation before release. VTP has no
NeoForge artifact for 1.21.9, 1.21.10, or 26.x at present.

If the server changes an inventory or does not confirm an action, automation stops.
Check the cursor, grid, and payment slots before retrying. Server plugins that
suppress command-completion responses can prevent confirmation; no command is
executed. Settings are stored in `config/client-stacker.json`.

## Development

See [CONTRIBUTING.md](CONTRIBUTING.md) for setup, testing, and review requirements.
Build a development jar with `./gradlew :1.21.11-fabric:build` (or use
`gradlew.bat` on Windows). Output is in `versions/1.21.11-fabric/build/libs/`;
use the jar without `-sources` in its name.
Report reproducible problems through [issues](https://github.com/ohne-b/client-stacker/issues),
including Minecraft, loader, mod versions, and a short set of steps.

## License

[MIT](LICENSE.md), copyright 2026 ohne-b. Not affiliated with Mojang or Microsoft.
