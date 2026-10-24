package com.example.addon.events;

import meteordevelopment.orbit.ICancellable;

public class PlayerTickEvent {

    public static final class Pre extends PlayerTickEvent implements ICancellable {

        private boolean cancelled;

        @Override
        public void setCancelled(boolean cancelled) {
            this.cancelled = cancelled;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }

    public static final class Post extends PlayerTickEvent {
    }
}
