# Hostile Neural Networks 26.2 port status

This source bundle is an **unverified port in progress**. It is not a playable release. The project still has 26.2 compilation errors; do not copy its output into a mods folder until `gradlew.bat clean build` succeeds and the client and server have been exercised.

## Changes in this revision

- Bundled the supplied Placebo 26.2 development JAR in `libs/` and referenced it directly from Gradle.
- Adapted the machine item handlers to Placebo's transactional inventory interface, preserving menu access and automation restrictions.
- Moved machine energy to NeoForge's transactional `SimpleEnergyHandler` with compatibility accessors for existing menus.
- Registered NeoForge 26.2 energy and item capabilities for the Sim Chamber, Loot Fabricator, Data Center, and its mode-specific I/O ports.
- Moved inventory drops to block-entity `preRemoveSideEffects` and migrated the machine save hooks to `ValueInput`/`ValueOutput`.
- Updated several screen constructors and tooltips for 26.2's UI extraction API.

## Remaining work

The supplied build log reported 245 compile errors before this revision. The patch addresses clusters of these errors, but it has not been compiled because this workspace only has Java 17 and cannot download the Minecraft/NeoForge build artifacts. Remaining API migrations include UI input callbacks, item/entity codecs, command permissions, renderer and JEI integration. The custom animated model preview and tint registration also need restoration and runtime testing.

On a Windows installation with JDK 25, open this project and run `gradlew.bat clean compileJava --console=plain`. The next compiler output is the authoritative list of remaining errors. Build both a client and a dedicated server once compilation succeeds.

The original source is under the MIT license in `LICENSE`.
