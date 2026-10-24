package com.example.addon.events;

import net.minecraft.util.PlayerInput;

public final class KeyboardInputEvent {

    private float forwardImpulse;
    private float leftImpulse;

    private boolean jump;
    private boolean shift;
    private boolean sprint;

    public KeyboardInputEvent(float forwardImpulse, float leftImpulse, boolean jump, boolean shift, boolean sprint) {
        this.forwardImpulse = forwardImpulse;
        this.leftImpulse = leftImpulse;
        this.jump = jump;
        this.shift = shift;
        this.sprint = sprint;
    }

    public float getForward() {
        return forwardImpulse;
    }

    public void setForward(float forwardImpulse) {
        this.forwardImpulse = forwardImpulse;
    }

    public float getStrafe() {
        return leftImpulse;
    }

    public void setStrafe(float leftImpulse) {
        this.leftImpulse = leftImpulse;
    }

    public boolean isJump() {
        return jump;
    }

    public void setJump(boolean jump) {
        this.jump = jump;
    }

    public boolean isShift() {
        return shift;
    }

    public boolean isSprint() {
        return sprint;
    }

    public PlayerInput toNewInput() {
        return new PlayerInput(
                forwardImpulse > 0.0f,
                forwardImpulse < 0.0f,
                leftImpulse > 0.0f,
                leftImpulse < 0.0f,
                jump,
                shift,
                sprint
        );
    }
}
