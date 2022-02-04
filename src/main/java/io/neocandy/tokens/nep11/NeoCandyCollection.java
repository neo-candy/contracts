package io.neocandy.tokens.nep11;

import io.neow3j.devpack.ByteString;
import io.neow3j.devpack.Contract;
import io.neow3j.devpack.Hash160;
import io.neow3j.devpack.Helper;
import io.neow3j.devpack.Iterator;
import io.neow3j.devpack.Map;
import io.neow3j.devpack.Runtime;
import io.neow3j.devpack.Storage;
import io.neow3j.devpack.StorageContext;
import io.neow3j.devpack.StorageMap;
import io.neow3j.devpack.annotations.DisplayName;
import io.neow3j.devpack.annotations.ManifestExtra;
import io.neow3j.devpack.annotations.Permission;
import io.neow3j.devpack.annotations.OnDeployment;
import io.neow3j.devpack.annotations.Safe;
import io.neow3j.devpack.constants.CallFlags;
import io.neow3j.devpack.constants.FindOptions;
import io.neow3j.devpack.contracts.ContractManagement;
import io.neow3j.devpack.events.Event3Args;
import io.neow3j.devpack.events.Event4Args;

@ManifestExtra(key = "name", value = "NeoCandy Collection")
@ManifestExtra(key = "author", value = "NeoCandy")
@ManifestExtra(key = "description", value = "NeoCandy NFT Collection")
@Permission(contract = "*", methods = "*")
public class NeoCandyCollection {

    static final StorageContext ctx = Storage.getStorageContext();

    static final byte[] ownerkey = Helper.toByteArray((byte) 1);
    static final byte[] totalSupplyKey = Helper.toByteArray((byte) 2);

    static final byte[] registryPrefix = Helper.toByteArray((byte) 3);
    static final StorageMap registryMap = new StorageMap(ctx, registryPrefix);

    static final byte[] ownerOfKey = Helper.toByteArray((byte) 4);
    static final StorageMap ownerOfMap = new StorageMap(ctx, ownerOfKey);

    static final StorageMap contractMap = new StorageMap(ctx, (byte) 5);

    static final String propName = "name";
    static final String propDescription = "description";
    static final String propImage = "image";
    static final String propTokenURI = "tokenURI";

    static final StorageMap propertiesNameMap = new StorageMap(ctx, (byte) 12);
    static final StorageMap propertiesDescriptionMap = new StorageMap(ctx, (byte) 13);
    static final StorageMap propertiesImageMap = new StorageMap(ctx, (byte) 14);
    static final StorageMap propertiesTokenURIMap = new StorageMap(ctx, (byte) 15);

    static final byte[] balanceKey = Helper.toByteArray((byte) 20);
    static final StorageMap balanceMap = new StorageMap(ctx, balanceKey);

    static final byte[] tokensOfKey = Helper.toByteArray((byte) 24);

    @OnDeployment
    public static void deploy(Object data, boolean update) {
        if (!update) {
            Storage.put(ctx, ownerkey, (Hash160) data);
            contractMap.put(totalSupplyKey, 0);
        }
    }

    @DisplayName("Transfer")
    static Event4Args<Hash160, Hash160, Integer, ByteString> onTransfer;

    @Safe
    public static Hash160 contractOwner() {
        return new Hash160(Storage.get(ctx, ownerkey));
    }

    @Safe
    public static String symbol() {
        return "CANDY";
    }

    @Safe
    public static int decimals() {
        return 0;
    }

    @Safe
    public static int totalSupply() {
        return contractMap.getInt(totalSupplyKey);
    }

    @Safe
    public static int balanceOf(Hash160 owner) {
        return getBalanceOf(owner);
    }

    @Safe
    public static Iterator<ByteString> tokensOf(Hash160 owner) {
        return (Iterator<ByteString>) Storage.find(
                ctx.asReadOnly(),
                createTokensOfPrefix(owner),
                FindOptions.RemovePrefix);
    }

    public static boolean transfer(Hash160 to, ByteString tokenId, Object data) throws Exception {
        Hash160 owner = ownerOf(tokenId);
        assert owner != null : "This token id does not exist";
        assert Runtime.checkWitness(owner) : "No authorization";

        ownerOfMap.put(tokenId, to.toByteArray());
        new StorageMap(ctx, createTokensOfPrefix(owner)).delete(tokenId);
        new StorageMap(ctx, createTokensOfPrefix(to)).put(tokenId, 1);

        decrementBalanceByOne(owner);
        incrementBalanceByOne(to);

        onTransfer.fire(owner, to, 1, tokenId);

        if (ContractManagement.getContract(to) != null) {
            Contract.call(to, "onNEP11Payment", CallFlags.All,
                    new Object[] { owner, 1, tokenId, data });
        }
        return true;
    }

    private static byte[] createTokensOfPrefix(Hash160 owner) {
        return Helper.concat(tokensOfKey, owner.toByteArray());
    }

    @Safe
    public static Hash160 ownerOf(ByteString tokenId) {
        ByteString owner = ownerOfMap.get(tokenId);
        if (owner == null) {
            return null;
        }
        return new Hash160(owner);
    }

    @DisplayName("Mint")
    private static Event3Args<Hash160, ByteString, Map<String, String>> onMint;

    public static void mint(Hash160 owner, ByteString tokenId, Map<String, String> properties)
            throws Exception {

        assert Runtime.checkWitness(contractOwner()) : "No authorization";
        assert registryMap.get(tokenId) == null : "This token id already exists";

        assert properties.containsKey(propName) : "The properties must contain a value for the key `name`";

        String tokenName = properties.get(propName);
        propertiesNameMap.put(tokenId, tokenName);

        if (properties.containsKey(propDescription)) {
            String description = properties.get(propDescription);
            propertiesDescriptionMap.put(tokenId, description);
        }
        if (properties.containsKey(propImage)) {
            String image = properties.get(propImage);
            propertiesImageMap.put(tokenId, image);
        }
        if (properties.containsKey(propTokenURI)) {
            String tokenURI = properties.get(propTokenURI);
            propertiesTokenURIMap.put(tokenId, tokenURI);
        }

        registryMap.put(tokenId, tokenId);
        ownerOfMap.put(tokenId, owner.toByteArray());
        new StorageMap(ctx, createTokensOfPrefix(owner)).put(tokenId, 1);

        incrementBalanceByOne(owner);
        incrementTotalSupplyByOne();
        onMint.fire(owner, tokenId, properties);
    }

    @Safe
    public static Iterator<Iterator.Struct<ByteString, ByteString>> tokens() {
        return (Iterator<Iterator.Struct<ByteString, ByteString>>) registryMap.find(FindOptions.RemovePrefix);
    }

    @Safe
    public static Map<String, String> properties(ByteString tokenId) throws Exception {
        Map<String, String> p = new Map<>();
        ByteString tokenName = propertiesNameMap.get(tokenId);
        if (tokenName == null) {
            throw new Exception("This token id does not exist.");
        }

        p.put(propName, tokenName.toString());
        ByteString tokenDescription = propertiesDescriptionMap.get(tokenId);
        if (tokenDescription != null) {
            p.put(propDescription, tokenDescription.toString());
        }
        ByteString tokenImage = propertiesImageMap.get(tokenId);
        if (tokenImage != null) {
            p.put(propImage, tokenImage.toString());
        }
        ByteString tokenURI = propertiesTokenURIMap.get(tokenId);
        if (tokenURI != null) {
            p.put(propTokenURI, tokenURI.toString());
        }
        return p;
    }

    private static void incrementBalanceByOne(Hash160 owner) {
        balanceMap.put(owner.toByteArray(), getBalanceOf(owner) + 1);
    }

    private static void decrementBalanceByOne(Hash160 owner) {
        balanceMap.put(owner.toByteArray(), getBalanceOf(owner) - 1);
    }

    private static int getBalanceOf(Hash160 owner) {
        if (balanceMap.get(owner.toByteArray()) == null) {
            return 0;
        }
        return balanceMap.get(owner.toByteArray()).toInt();
    }

    private static void incrementTotalSupplyByOne() {
        int updatedTotalSupply = contractMap.getInt(totalSupplyKey) + 1;
        contractMap.put(totalSupplyKey, updatedTotalSupply);
    }

    private static void decrementTotalSupplyByOne() {
        int updatedTotalSupply = contractMap.getInt(totalSupplyKey) - 1;
        contractMap.put(totalSupplyKey, updatedTotalSupply);
    }

    public static boolean burn(ByteString tokenId) throws Exception {
        Hash160 owner = ownerOf(tokenId);
        assert owner != null : "This token id does not exist";
        assert Runtime.checkWitness(owner) : "No authorization";

        registryMap.delete(tokenId);
        propertiesNameMap.delete(tokenId);
        propertiesDescriptionMap.delete(tokenId);
        propertiesImageMap.delete(tokenId);
        propertiesTokenURIMap.delete(tokenId);
        ownerOfMap.delete(tokenId);
        new StorageMap(ctx, createTokensOfPrefix(owner)).delete(tokenId);
        decrementBalanceByOne(owner);
        decrementTotalSupplyByOne();
        return true;
    }

    public static boolean destroy() {
        if (!Runtime.checkWitness(contractOwner())) {
            return false;
        }
        ContractManagement.destroy();
        return true;
    }

    public static void update(ByteString script, String manifest) throws Exception {
        assert Runtime.checkWitness(contractOwner()) : "No authorization";
        if (script.length() == 0 && manifest.length() == 0) {
            throw new Exception("The new contract script and manifest must not be empty.");
        }
        ContractManagement.update(script, manifest);
    }

}