# P2P Client UI

The visual presentation and immediate-mode vector interface for the P2P network management system.

## Language

**ThemeMode**:
The user's active visual theme selection (LIGHT or DARK).
_Avoid_: Skin, style

**ThemePalette**:
The contract defining all semantic and functional color tokens for an iOS-inspired vector theme.
_Avoid_: ColorScheme, ThemeColors, ThemeSpec

**Procedural Vector UI**:
A texture-free GUI rendering pipeline where components, cards, wells, and controls are generated on-the-fly via scanline rounded-rect math.
_Avoid_: GUI bitmap, mod texture

**P2PTheme Facade**:
The global static entry point providing active palette tokens, geometry metrics, and drawing primitives while dynamically forwarding to the active palette.
_Avoid_: Theme Singleton, Context Injector
