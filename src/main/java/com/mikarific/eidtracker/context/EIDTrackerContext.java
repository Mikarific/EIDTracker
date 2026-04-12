package com.mikarific.eidtracker.context;

public class EIDTrackerContext {
    public static boolean fixNonPlayerTriggeredCollidingEntityIds = false;

    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    public static void push() {
        DEPTH.set(DEPTH.get() + 1);
    }

    public static void pop() {
        int newDepth = DEPTH.get() - 1;
        if (newDepth <= 0) {
            DEPTH.remove();
        } else {
            DEPTH.set(newDepth);
        }
    }

    public static int getDepth() {
        return DEPTH.get();
    }

    public static boolean isPlayerAction() {
        return DEPTH.get() > 0;
    }
}
