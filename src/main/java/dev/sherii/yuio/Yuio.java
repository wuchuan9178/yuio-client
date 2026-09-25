package dev.sherii.yuio;

import com.example.addon.AddonTemplate;
import org.slf4j.Logger;

// MaceMissAura 要的日志, 就这点东西
public final class Yuio {
    public static final Companion Companion = new Yuio.Companion();

    private Yuio() {
    }

    public static final class Companion {
        public Logger getLOG() {
            return AddonTemplate.LOG;
        }
    }
}
