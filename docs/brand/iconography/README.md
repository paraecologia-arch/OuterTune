# XENOWAVE iconography

This directory is the canonical source for the XENOWAVE functional icon system. The approved PNG boards in `docs/brand/references/` are frozen visual references. Each exported function has a 24 × 24 transparent SVG master and Android VectorDrawable resource.

- Canvas: 24 × 24, transparent background.
- Stroke: 1.9 units, within the approved 1.75–2 range.
- Terminals and joins: round.
- Structure color: `#E2E8F0`; selected states use XENOWAVE blue with cyan signal glow.
- Violet is available only as the restricted `accent` state.
- Runtime states, glow, mirroring, accessibility, 24 dp visual bounds, and the 48 dp minimum interactive bounds are applied by `XenoIcon`.
- Related states intentionally share geometry families so their meaning remains stable.
- The GitHub mark remains untouched as a third-party brand asset.

## Integration status

- 129 function masters, each with a matching `app/src/main/res/drawable/xeno_icon_*.xml` VectorDrawable; the mapping is one-to-one and is enforced by name.
- 288 call sites render through `XenoIcon`, which applies the tint, the selected cyan signal glow, RTL mirroring and the accessibility contract (`contentDescription`, 24 dp visual bounds inside 48 dp interactive bounds).
- 12 masters were re-derived from the board that actually defines them because they previously aliased a different approved glyph: `add_circle_outline`, `remove_circle_outline`, `indeterminate_check_box`, `content_cut`, `edit_off`, `playlist_add`, `library_add`, `playlist_remove`, `library_add_check`, `queue_music`, `volume_up`, `confirmation_number`.
- Masters whose glyph is only defined on the isolated official vectors are still aliases of a neighbouring function and are listed under "Held" in `../XENOWAVE_IMPLEMENTATION_INVENTORY.md`.

## Launcher, themed and splash derivatives

`xenowave_launcher_master.png` is the only approved source for the launcher, themed and splash rasters. The derivatives are produced from it by cropping to the artwork bounds, scaling the artwork into the required safe zone, compositing on `#0B1020` where a filled surface is needed, and quantising losslessly. Nothing is redrawn.

| Derivative | Resource | Content fraction |
|---|---|---|
| Adaptive foreground | `mipmap-{m,h,xh,xxh,xxxh}dpi/xeno_launcher_foreground.png` | 66/108 of the 108 dp canvas |
| Themed monochrome layer | `mipmap-{m,h,xh,xxh,xxxh}dpi/xeno_launcher_monochrome.png` | 66/108, alpha only, from the bright structure of the master |
| Legacy launcher icon (API 24-25) | `mipmap-{m,h,xh,xxh,xxxh}dpi/xeno_launcher.png` | 62 percent on a rounded `#0B1020` square |
| Legacy round launcher icon | `mipmap-{m,h,xh,xxh,xxxh}dpi/xeno_launcher_round.png` | 58 percent inside a `#0B1020` circle |
| Splash (API 31+) | `drawable-{m,h,xh,xxh,xxxh}dpi/xenowave_splash_icon.png` | 192/288 of the 288 dp canvas, transparent |
| Store icon | `app/src/main/ic_launcher-playstore.png`, `fastlane/metadata/android/en-US/images/icon.png` | 68 percent on a `#0B1020` 512 x 512 canvas |
