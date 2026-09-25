package dev.sherii.yuio;

import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import org.slf4j.Logger;

// 原版 AModule 拖着通知系统一堆东西, 砍掉, 只留壳
public abstract class AModule extends Module {
    protected final Logger LOG = Yuio.Companion.getLOG();

    public AModule(Category category, String name, String desc) {
        super(category, name, desc);
    }
}
