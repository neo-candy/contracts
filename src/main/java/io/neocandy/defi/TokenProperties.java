package io.neocandy.defi;

import io.neow3j.devpack.ByteString;
import io.neow3j.devpack.annotations.Struct;

@Struct
public class TokenProperties {
    private ByteString tokenId;
    private String name;
    private String image;
    private String description;
    private String tokenUri;
    private int strike;
    private int type;
    private ByteString writer;
    private int depreciation;
    private int created;
    private int value;
    private int volatility;
    private boolean safe;

    public TokenProperties(ByteString tokenId, String name, String image, String description, String tokenUri,
            int strike, int type, ByteString writer, int depreciation, int created, int value, int volatility,
            boolean safe) {
        this.tokenId = tokenId;
        this.name = name;
        this.image = image;
        this.description = description;
        this.tokenUri = tokenUri;
        this.strike = strike;
        this.type = type;
        this.writer = writer;
        this.depreciation = depreciation;
        this.created = created;
        this.value = value;
        this.volatility = volatility;
        this.safe = safe;
    }

    public ByteString getTokenId() {
        return tokenId;
    }

    public String getName() {
        return name;
    }

    public String getImage() {
        return image;
    }

    public String getDescription() {
        return description;
    }

    public String getTokenUri() {
        return tokenUri;
    }

    public int getStrike() {
        return strike;
    }

    public int getType() {
        return type;
    }

    public ByteString getWriter() {
        return writer;
    }

    public int getDepreciation() {
        return depreciation;
    }

    public int getCreated() {
        return created;
    }

    public int getValue() {
        return value;
    }

    public int getVolatility() {
        return volatility;
    }

    public boolean isSafe() {
        return safe;
    }

}
