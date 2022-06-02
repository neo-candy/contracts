package io.neocandy.defi;

import io.neow3j.devpack.Hash160;
import io.neow3j.devpack.annotations.Struct;

@Struct
public class Position {

    public Hash160 token;
    public int fee;
    public int stake;
    public int expiration;
    public int strike;

    public Position(Hash160 token, int fee, int stake, int expiration, int strike) {
        this.token = token;
        this.fee = fee;
        this.stake = stake;
        this.expiration = expiration;
        this.strike = strike;
    }

}
