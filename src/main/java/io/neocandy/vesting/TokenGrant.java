package io.neocandy.vesting;

import io.neow3j.devpack.Hash160;

public class TokenGrant {
    boolean isActive; /* true if this vesting entry is active and in-effect entry. */
    boolean wasRevoked; /* true if this vesting schedule was revoked. */
    int startDay; /* Start day of the grant, in days since the UNIX epoch (start of day). */
    int amount; /* Total number of tokens that vest. */
    Hash160 vestingLocation; /* Address of wallet that is holding the vesting schedule. */
    Hash160 grantor; /* Grantor that made the grant */

    public TokenGrant(boolean isActive, boolean wasRevoked, int startDay, int amount, Hash160 vestingLocation,
            Hash160 grantor) {
        this.isActive = isActive;
        this.wasRevoked = wasRevoked;
        this.startDay = startDay;
        this.amount = amount;
        this.vestingLocation = vestingLocation;
        this.grantor = grantor;
    }
}
