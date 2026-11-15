package com.itsm.domain.user;

/** Whether a technician can currently take new work. */
public enum AvailabilityStatus {
    AVAILABLE,
    BUSY,
    ON_LEAVE,
    OFFLINE;

    public boolean canTakeWork() {
        return this == AVAILABLE || this == BUSY;
    }
}
