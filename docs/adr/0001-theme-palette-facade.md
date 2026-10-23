# 0001. Theme Palette Strategy and Facade Architecture

## Context and Decision

The P2P mod UI relies entirely on procedural vector rendering via `P2PTheme`, using over a dozen color tokens referenced across screens, container menus, dialogs, widgets, and offline rendering harnesses. We introduced a second (Dark) theme mirroring Apple's iOS Dark Mode system palette. To avoid invasive refactoring of dozens of static call sites while enabling zero-restart runtime switching and NeoForge client config synchronization, we adopted a Strategy Pattern for color palettes coupled with a dynamic `P2PTheme` static facade.

## Considered Options

- **Instance Injection**: Passing a `ThemeContext` or `P2PTheme` instance down through every Screen and Widget constructor. Rejected due to extreme invasiveness and code churn across both the base mod and addon screens.
- **Dynamic Facade + Mutable Static Forwarding**: `P2PTheme` holds the current `ThemePalette` strategy; public color tokens are kept in sync on theme change without `final`, preserving backward compatibility for compile-time syntax while dynamically repainting on the next frame. Accepted.
