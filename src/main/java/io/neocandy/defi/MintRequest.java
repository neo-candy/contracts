package io.neocandy.defi;

import io.neow3j.devpack.annotations.Struct;

@Struct
public class MintRequest {
    public int type;
    public int strike;
    public int depreciation;
    public int value;
    public int volatility;
    public boolean safe;
    // Rentfuse properties
    public int paymentTokenAmount;
    int minDurationInMinutes;
    int maxDurationInMinutes;
    int collateral;
}
