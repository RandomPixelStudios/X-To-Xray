package com.xtoxray.network;

public final class HandshakeState {
    private static final HandshakeState INSTANCE = new HandshakeState();
    private volatile boolean serverHasMod;
    private volatile boolean hasReceivedHandshake;
    private volatile int ticksSinceConnect;

    private HandshakeState() {
    }

    public static HandshakeState getInstance() {
        return INSTANCE;
    }

    public boolean isServerHasMod() {
        return serverHasMod && hasReceivedHandshake;
    }

    public boolean hasReceivedHandshake() {
        return hasReceivedHandshake;
    }

    public int getTicksSinceConnect() {
        return ticksSinceConnect;
    }

    public void setServerHasMod(boolean serverHasMod) {
        this.serverHasMod = serverHasMod;
        this.hasReceivedHandshake = true;
        this.ticksSinceConnect = 0;
    }

    public void setHasReceivedHandshake(boolean hasReceivedHandshake) {
        this.hasReceivedHandshake = hasReceivedHandshake;
    }

    public void tick() {
        ticksSinceConnect++;
    }

    public void resetOnConnect() {
        this.serverHasMod = false;
        this.hasReceivedHandshake = false;
        this.ticksSinceConnect = 0;
    }
}