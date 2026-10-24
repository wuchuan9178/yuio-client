package com.example.addon.utils.eps;

import java.util.concurrent.ThreadLocalRandom;

public final class MathUtils {

    private MathUtils() {
    }

    public static int getRandom(int min, int max) {
        if (min >= max) return min;
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    public static float getRandom(float min, float max) {
        if (min >= max) return min;
        return (float) ThreadLocalRandom.current().nextDouble(min, max);
    }

    public static double getRandom(double min, double max) {
        if (min >= max) return min;
        return ThreadLocalRandom.current().nextDouble(min, max);
    }
}
