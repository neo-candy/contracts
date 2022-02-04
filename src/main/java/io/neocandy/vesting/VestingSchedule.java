package io.neocandy.vesting;

public class VestingSchedule {
    boolean isValid; /* true if an entry exists and is valid */
    boolean isRevocable; /* true if the vesting option is revocable, false if irrevocable */
    int cliffDuration; /* Duration of the cliff, with respect to the grant start day, in ms. */
    int duration; /* Duration of the vesting, with respect to the grant start day, in ms. */
    int interval; /* Duration in ms of the vesting interval. */

    public VestingSchedule(boolean isValid, boolean isRevocable, int cliffDuration, int duration,
            int interval) {
        this.isValid = isValid;
        this.isRevocable = isRevocable;
        this.cliffDuration = cliffDuration;
        this.duration = duration;
        this.interval = interval;
    }

}
