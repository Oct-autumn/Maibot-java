package org.maibot.core.modloader;

import org.maibot.core.cdi.annotation.Component;
import org.maibot.sdk.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ModManager {
    private static final Logger log = LoggerFactory.getLogger(ModManager.class);

    private static final Map<String, Mod> loadedMods = new ConcurrentHashMap<>();
    
    private ModManager() {
    }

    /**
     * 重载Mods
     */
    public void reloadMods() {

    }
}
