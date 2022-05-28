package io.neocandy.games.candyclash;

import io.neow3j.devpack.ByteString;
import io.neow3j.devpack.annotations.Struct;

@Struct
public class ImmutableTokenProperties {

    ByteString tokenId;
    String name;
    String image;
    String description;
    String tokenUri;
    String type;
    String origin;
    int generation;

    public ImmutableTokenProperties(ByteString tokenId, String name, String image, String description, String tokenUri,
            String type,
            String origin, int generation) {
        this.tokenId = tokenId;
        this.name = name;
        this.image = image;
        this.description = description;
        this.tokenUri = tokenUri;
        this.type = type;
        this.origin = origin;
        this.generation = generation;
    }

    public ByteString getTokenId() {
        return this.tokenId;
    }

    public String getName() {
        return this.name;
    }

    public String getImage() {
        return this.image;
    }

    public String getDescription() {
        return this.description;
    }

    public String getTokenUri() {
        return this.tokenUri;
    }

    public String getOrigin() {
        return this.origin;
    }

    public int getGeneration() {
        return this.generation;
    }

    public String getType() {
        return this.type;
    }

}
