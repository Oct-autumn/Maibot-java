package org.maibot.core.modloader;

import org.maibot.sdk.mod.Mod;

public record LoadedMod(
  Mod modInstance,
  ModClassLoader modClassLoader
) {
}
