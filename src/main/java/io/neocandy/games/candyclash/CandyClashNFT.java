package io.neocandy.games.candyclash;

import io.neow3j.devpack.ByteString;
import io.neow3j.devpack.Contract;
import io.neow3j.devpack.Hash160;
import io.neow3j.devpack.Helper;
import io.neow3j.devpack.Iterator;
import io.neow3j.devpack.List;
import io.neow3j.devpack.Map;
import io.neow3j.devpack.Runtime;
import io.neow3j.devpack.Storage;
import io.neow3j.devpack.StorageContext;
import io.neow3j.devpack.StorageMap;
import io.neow3j.devpack.Iterator.Struct;
import io.neow3j.devpack.annotations.DisplayName;
import io.neow3j.devpack.annotations.ManifestExtra;
import io.neow3j.devpack.annotations.Permission;
import io.neow3j.devpack.annotations.OnDeployment;
import io.neow3j.devpack.annotations.OnNEP17Payment;
import io.neow3j.devpack.annotations.Safe;
import io.neow3j.devpack.constants.CallFlags;
import io.neow3j.devpack.constants.FindOptions;
import io.neow3j.devpack.contracts.ContractManagement;
import io.neow3j.devpack.contracts.GasToken;
import io.neow3j.devpack.contracts.StdLib;
import io.neow3j.devpack.events.Event1Arg;
import io.neow3j.devpack.events.Event2Args;
import io.neow3j.devpack.events.Event3Args;
import io.neow3j.devpack.events.Event4Args;

import static io.neow3j.devpack.StringLiteralHelper.stringToInt;

@ManifestExtra(key = "name", value = "CandyClashNFT Contract")
@ManifestExtra(key = "author", value = "NeoCandy")
@ManifestExtra(key = "description", value = "CandyClash NFT Collection")
@ManifestExtra(key = "email", value = "hello@neocandy.io")
@Permission(contract = "*", methods = { "totalVillainCandiesStaked", "onNEP11Payment", "transfer" })
@Permission(contract = "0xfffdc93764dbaddd97c48f252a53ea4643faa3fd", methods = "*")
public class CandyClashNFT {

    @DisplayName("Debug")
    private static Event1Arg<Object> onDebug;

    @DisplayName("Mint")
    private static Event2Args<Hash160, ByteString> onMint;

    @DisplayName("Transfer")
    static Event4Args<Hash160, Hash160, Integer, ByteString> onTransfer;

    @DisplayName("onPayment")
    static Event3Args<Hash160, Integer, Object> onPayment;

    // DEFAULT METADATA
    private static final String NAME = "name";
    private static final String DESC = "description";
    private static final String IMAGE = "image";
    private static final String TOKEN_URI = "tokenURI";
    private static final String TOKEN_ID = "tokenId";

    // CUSTOM METADATA
    private static final String SUGAR = "Sugar";
    private static final String GENERATION = "Generation";
    private static final String BONUS = "Claim Bonus";
    private static final String TYPE = "Type";
    private static final String TYPE_VILLAIN = "Villain";
    private static final String TYPE_VILLAGER = "Villager";

    // GM related properties
    private static final String PROPERTIES = "properties";
    private static final String PROPERTY_HAS_LOCKED = "has_locked";
    private static final String PROPERTY_TYPE = "type";
    private static final int PROPERTY_GAME_TYPE = 4;
    // NFT attributes
    private static final String ATTRIBUTES = "attributes";
    private static final String ATTRIBUTE_TRAIT_TYPE = "trait_type";
    private static final String ATTRIBUTE_VALUE = "value";
    private static final String ATTRIBUTE_DISPLAY_TYPE = "display_type";
    // ROYALTIES
    private static final String ROYALTIES_ADDRESS = "address";
    private static final String ROYALTIES_VALUE = "value";

    private static final StorageContext ctx = Storage.getStorageContext();

    // STORAGE KEYS
    private static final byte[] ownerkey = Helper.toByteArray((byte) 1);
    private static final byte[] totalSupplyKey = Helper.toByteArray((byte) 2);
    private static final byte[] gasPriceKey = Helper.toByteArray((byte) 4);
    private static final byte[] candyPriceKey = Helper.toByteArray((byte) 5);
    private static final byte[] candyHashKey = Helper.toByteArray((byte) 6);
    private static final byte[] tokensOfKey = Helper.toByteArray((byte) 24);
    private static final byte[] imageBaseUriKey = Helper.toByteArray((byte) 27);
    private static final byte[] maxTokensAmountKey = Helper.toByteArray((byte) 28);
    private static final byte[] maxGenesisAmountKey = Helper.toByteArray((byte) 29);
    private static final byte[] isPausedKey = Helper.toByteArray((byte) 30);
    private static final byte[] royaltiesReceiverKey = Helper.toByteArray((byte) 31);
    private static final byte[] royaltiesAmountKey = Helper.toByteArray((byte) 32);
    private static final byte[] maxMintAmountKey = Helper.toByteArray((byte) 33);
    private static final byte[] stakingContractKey = Helper.toByteArray((byte) 34);

    // STORAGE MAPS
    private static final StorageMap tokens = new StorageMap(ctx, Helper.toByteArray((byte) 3));
    private static final StorageMap balances = new StorageMap(ctx, Helper.toByteArray((byte) 20));
    private static final StorageMap ownerOfMap = new StorageMap(ctx, Helper.toByteArray((byte) 4));
    private static final StorageMap propertiesNameMap = new StorageMap(ctx, (byte) 12);
    private static final StorageMap propertiesDescriptionMap = new StorageMap(ctx, (byte) 13);
    private static final StorageMap propertiesImageMap = new StorageMap(ctx, (byte) 14);
    private static final StorageMap propertiesTokenURIMap = new StorageMap(ctx, (byte) 15);
    private static final StorageMap propertiesSugarMap = new StorageMap(ctx, (byte) 16);
    private static final StorageMap propertiesTypeMap = new StorageMap(ctx, (byte) 17);
    private static final StorageMap propertiesClaimBonusMap = new StorageMap(ctx, (byte) 18);
    private static final StorageMap villainCandies = new StorageMap(ctx, (byte) 40);
    private static final StorageMap villagerCandies = new StorageMap(ctx, (byte) 19);

    /*
     * @OnNEP17Payment
     * public static void onPayment(Hash160 from, int amount, Object data) throws
     * Exception {
     * assert (!isPaused()) : "isPaused";
     * assert (stakingContract() != null) : "missing staking contract";
     * assert (totalSupply() < maxTokensAmount()) : "sold out";
     * Hash160 token = Runtime.getCallingScriptHash();
     * assert (token == GasToken.getHash() || token == candyContract()) :
     * "invalid token";
     * if (token == GasToken.getHash()) {
     * 
     * int gasPrice = gasPrice();
     * int times = amount / gasPrice;
     * assert (totalSupply() + times <= maxGenesisAmount()) : "not available";
     * assert (times <= maxMintAmount()) : "mint amount reached";
     * assert (times > 0 && amount % gasPrice == 0) : "invalid amount";
     * for (int i = 0; i < times; i++) {
     * mint(from, 0);
     * }
     * } else {
     * int candyPrice = candyPrice();
     * int times = amount / candyPrice;
     * assert (totalSupply() > maxGenesisAmount() && totalSupply() + times <=
     * maxTokensAmount()) : "not available";
     * assert (times <= maxMintAmount()) : "mint amount reached";
     * assert (times > 0 && amount % candyPrice == 0) : "invalid amount";
     * for (int i = 0; i < times; i++) {
     * mint(from, 1);
     * }
     * }
     * onPayment.fire(from, amount, data);
     * }
     */

    @OnNEP17Payment
    public static void onPayment(Hash160 from, int amount, Object data) throws Exception {
        assert (!isPaused()) : "isPaused";
        assert (stakingContract() != null) : "missing staking contract";
        assert (totalSupply() < maxTokensAmount()) : "sold out";
        Hash160 token = Runtime.getCallingScriptHash();
        assert (token == GasToken.getHash() || token == candyContract()) : "invalid token";
        if (token == GasToken.getHash()) {
            assert (totalSupply() < maxGenesisAmount()) : "not available";
            assert (amount == gasPrice()) : "invalid amount";
            mint(from, "0");
        } else {
            assert (totalSupply() >= maxGenesisAmount() && totalSupply() < maxTokensAmount()) : "not available";
            assert (amount == candyPrice()) : "invalid amount";
            mint(from, "1");
        }
        onPayment.fire(from, amount, data);
    }

    @Safe
    public static Hash160 contractOwner() {
        return new Hash160(Storage.get(ctx, ownerkey));
    }

    @Safe
    public static String getRoyalties() {
        String receiverAddress = Storage.getString(ctx, royaltiesReceiverKey);
        int amount = Storage.getInt(ctx, royaltiesAmountKey);
        Map<String, Object> map = new Map<>();
        map.put(ROYALTIES_ADDRESS, receiverAddress);
        map.put(ROYALTIES_VALUE, StdLib.jsonSerialize(amount));
        Object[] arr = new Object[] { map };
        return StdLib.jsonSerialize(arr);
    }

    @Safe
    public static String symbol() {
        return "CLASH";
    }

    @Safe
    public static int decimals() {
        return 0;
    }

    @Safe
    public static int totalSupply() {
        return Storage.getIntOrZero(ctx, totalSupplyKey);
    }

    @Safe
    public static int balanceOf(Hash160 owner) {
        return getBalanceOf(owner);
    }

    @Safe
    public static int maxGenesisAmount() {
        return Storage.getInt(ctx, maxGenesisAmountKey);
    }

    @Safe
    public static int maxTokensAmount() {
        return Storage.getInt(ctx, maxTokensAmountKey);
    }

    @Safe
    public static boolean isPaused() {
        return Storage.getInt(ctx, isPausedKey) == 1 ? true : false;
    }

    @Safe
    public static int gasPrice() {
        return Storage.getInt(ctx, gasPriceKey);
    }

    @Safe
    public static int candyPrice() {
        return Storage.getInt(ctx, candyPriceKey);
    }

    @Safe
    public static Iterator<ByteString> tokensOf(Hash160 owner) {
        return (Iterator<ByteString>) Storage.find(
                ctx.asReadOnly(),
                createTokensOfPrefix(owner),
                FindOptions.RemovePrefix);
    }

    @Safe
    public static List<String> tokensOfJson(Hash160 owner) throws Exception {
        Iterator<Struct<ByteString, ByteString>> iterator = (Iterator<Struct<ByteString, ByteString>>) Storage.find(
                ctx.asReadOnly(),
                createTokensOfPrefix(owner),
                FindOptions.RemovePrefix);
        List<String> tokens = new List();
        while (iterator.next()) {
            ByteString result = (ByteString) iterator.get().key;
            tokens.add(propertiesJson(result));
        }
        return tokens;
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

    @Safe
    public static Hash160 ownerOf(ByteString tokenId) {
        ByteString owner = ownerOfMap.get(tokenId);
        if (owner == null) {
            return null;
        }
        return new Hash160(owner);
    }

    private static void mint(Hash160 owner, String gen) throws Exception {
        incrementTotalSupplyByOne();
        int totalSupply = totalSupply();
        String ts = StdLib.jsonSerialize(totalSupply);
        ByteString tokenId = new ByteString(totalSupply);
        Map<String, String> properties = new Map<>();
        properties.put(NAME, "CandyClash Candy #" + ts);
        properties.put(DESC, "CandyClash Candy NFT. Stake to earn $CANDY.");
        properties.put(IMAGE, getImageBaseURI() + "/" + ts + ".png");
        properties.put(TOKEN_URI, "");
        properties.put(GENERATION, gen);
        // there is a 10% chance that a new gen 1 mint can be stolen
        if (gen == "1") {
            boolean steal = Runtime.getRandom() % 10 == 0;
            if (steal) {
                Hash160 newOwner = randomVillainCandyOwner();
                owner = newOwner != null ? newOwner : owner;
            }
        }
        boolean isEvil = Runtime.getRandom() % 10 == 0;
        if (isEvil) {
            properties.put(SUGAR, "1"); // RANDOMIZE
            properties.put(TYPE, TYPE_VILLAIN);
            villainCandies.put(tokenId, owner);
        } else {
            properties.put(SUGAR, "4"); // RANDOMIZE
            int randBonus = randomBonusAmount();
            String bonus = StdLib.jsonSerialize(randBonus);
            properties.put(BONUS, bonus);
            properties.put(TYPE, TYPE_VILLAGER);
            villagerCandies.put(tokenId, owner);
        }

        updateProperties(properties, tokenId);
        tokens.put(tokenId, tokenId);
        ownerOfMap.put(tokenId, owner);
        new StorageMap(ctx, createTokensOfPrefix(owner)).put(tokenId, 1);
        incrementBalanceByOne(owner);

        onMint.fire(owner, tokenId);
    }

    private static int randomBonusAmount() {
        int rand = randomNumberUntil(100);
        int bonus = 0;
        if (rand == 1) {
            bonus = 100;
        } else if (rand < 7) {
            bonus = 50;
        } else if (rand < 21) {
            bonus = 35;
        } else if (rand < 31) {
            bonus = 20;
        } else if (rand < 51) {
            bonus = 10;
        }
        return bonus;
    }

    private static int randomNumberUntil(int max) {
        Helper.assertTrue(max > 1);
        return (Runtime.getRandom() & 0xFFFFFFFF) % max;
    }

    private static Hash160 randomVillainCandyOwner() throws Exception {
        int rand = randomNumberUntil(totalVillainCandiesStaked());
        Iterator<ByteString> iter = villainCandies.find(FindOptions.ValuesOnly);
        int count = 0;
        while (iter.next()) {
            if (count == rand) {
                return new Hash160(iter.get());
            }
            count++;
        }
        // when no villain candy is staked at this moment
        return null;
    }

    private static int totalVillainCandiesStaked() {
        return (int) Contract.call(stakingContract(), "totalVillainCandiesStaked", CallFlags.All, new Object[0]);
    }

    private static Hash160 stakingContract() {
        return new Hash160(Storage.get(ctx, stakingContractKey));
    }

    private static byte[] createTokensOfPrefix(Hash160 owner) {
        return Helper.concat(tokensOfKey, owner.toByteArray());
    }

    private static Hash160 candyContract() {
        return new Hash160(Storage.get(ctx, candyHashKey));
    }

    private static void updateProperties(Map<String, String> properties, ByteString tokenId) {
        assert properties.containsKey(NAME) : "missing name";
        String tokenName = properties.get(NAME);
        propertiesNameMap.put(tokenId, tokenName);

        assert properties.containsKey(DESC) : "missing desc";
        String tokenDesc = properties.get(DESC);
        propertiesDescriptionMap.put(tokenId, tokenDesc);

        assert properties.containsKey(IMAGE) : "missing img";
        String tokenImg = properties.get(IMAGE);
        propertiesImageMap.put(tokenId, tokenImg);

        assert properties.containsKey(TOKEN_URI) : "missing uri";
        String tokenUri = properties.get(TOKEN_URI);
        propertiesTokenURIMap.put(tokenId, tokenUri);

        assert properties.containsKey(SUGAR) : "missing sugar";
        String sugarValue = properties.get(SUGAR);
        propertiesSugarMap.put(tokenId, sugarValue);

        assert properties.containsKey(TYPE) : "missing type";
        String tokenType = properties.get(TYPE);
        propertiesTypeMap.put(tokenId, tokenType);

        if (tokenType == TYPE_VILLAGER) {
            assert properties.containsKey(BONUS) : "missing claimBonus";
            String bonus = properties.get(BONUS);
            propertiesClaimBonusMap.put(tokenId, bonus);
        }

    }

    @Safe
    public static Iterator<Iterator.Struct<ByteString, ByteString>> tokens() {
        return (Iterator<Iterator.Struct<ByteString, ByteString>>) tokens.find(FindOptions.RemovePrefix);
    }

    @Safe
    public static Map<String, Object> properties(ByteString tokenId) throws Exception {
        Map<String, Object> p = new Map<>();
        String tokenName = propertiesNameMap.getString(tokenId);
        if (tokenName == null) {
            throw new Exception("This token id does not exist.");
        }

        p.put(NAME, tokenName);
        ByteString tokenDescription = propertiesDescriptionMap.get(tokenId);
        if (tokenDescription != null) {
            p.put(DESC, tokenDescription.toString());
        }
        ByteString tokenImage = propertiesImageMap.get(tokenId);
        if (tokenImage != null) {
            p.put(IMAGE, tokenImage.toString());
        }
        ByteString tokenURI = propertiesTokenURIMap.get(tokenId);
        if (tokenURI != null) {
            p.put(TOKEN_URI, tokenURI.toString());
        }
        Map<String, Object> propsMap = new Map<>();
        propsMap.put(PROPERTY_HAS_LOCKED, false);
        propsMap.put(PROPERTY_TYPE, PROPERTY_GAME_TYPE);
        p.put(PROPERTIES, propsMap);

        List<Map<String, String>> attributes = new List<>();

        ByteString type = propertiesTypeMap.get(tokenId);
        if (type != null) {
            attributes.add(getAttributeMap(TYPE, type.toString()));
        }
        ByteString sugar = propertiesSugarMap.get(tokenId);
        if (sugar != null) {
            attributes.add(getAttributeMap(SUGAR, sugar.toString()));
        }
        ByteString claimBonus = propertiesClaimBonusMap.get(tokenId);
        if (claimBonus != null) {
            attributes.add(getAttributeMap(BONUS, claimBonus.toString() + "%"));
        }
        p.put(ATTRIBUTES, attributes);

        return p;
    }

    @Safe
    public static String getTypeOfToken(ByteString tokenId) {
        return propertiesTypeMap.get(tokenId).toString();
    }

    @Safe
    public static String getSugarOfToken(ByteString tokenId) {
        return propertiesSugarMap.getString(tokenId);
    }

    @Safe
    public static String propertiesJson(ByteString tokenId) throws Exception {
        Map<String, Object> p = new Map<>();
        String tokenName = propertiesNameMap.getString(tokenId);
        if (tokenName == null) {
            throw new Exception("This token id does not exist.");
        }
        p.put(TOKEN_ID, tokenId.toInt());
        p.put(NAME, tokenName);
        ByteString tokenDescription = propertiesDescriptionMap.get(tokenId);
        if (tokenDescription != null) {
            p.put(DESC, tokenDescription.toString());
        }
        ByteString tokenImage = propertiesImageMap.get(tokenId);
        if (tokenImage != null) {
            p.put(IMAGE, tokenImage.toString());
        }
        ByteString tokenURI = propertiesTokenURIMap.get(tokenId);
        if (tokenURI != null) {
            p.put(TOKEN_URI, tokenURI.toString());
        }
        Map<String, Object> propsMap = new Map<>();
        propsMap.put(PROPERTY_HAS_LOCKED, false);
        propsMap.put(PROPERTY_TYPE, PROPERTY_GAME_TYPE);
        p.put(PROPERTIES, propsMap);

        List<Map<String, String>> attributes = new List<>();

        ByteString type = propertiesTypeMap.get(tokenId);
        if (type != null) {
            attributes.add(getAttributeMap(TYPE, type.toString()));
        }
        ByteString sugar = propertiesSugarMap.get(tokenId);
        if (sugar != null) {
            attributes.add(getAttributeMap(SUGAR, sugar.toString()));
        }
        ByteString claimBonus = propertiesClaimBonusMap.get(tokenId);
        if (claimBonus != null) {
            attributes.add(getAttributeMap(BONUS, claimBonus.toString() + "%"));
        }
        p.put(ATTRIBUTES, attributes);
        return StdLib.jsonSerialize(p);
    }

    private static Map<String, String> getAttributeMap(String trait, String value) {
        Map<String, String> m = new Map<>();
        m.put(ATTRIBUTE_TRAIT_TYPE, trait);
        m.put(ATTRIBUTE_VALUE, value);
        m.put(ATTRIBUTE_DISPLAY_TYPE, "");
        return m;
    }

    // TODO: dont need anymore
    private static int maxMintAmount() {
        return Storage.getInt(ctx, maxMintAmountKey);
    }

    private static void incrementBalanceByOne(Hash160 owner) {
        balances.put(owner.toByteArray(), getBalanceOf(owner) + 1);
    }

    private static void decrementBalanceByOne(Hash160 owner) {
        balances.put(owner.toByteArray(), getBalanceOf(owner) - 1);
    }

    private static String getImageBaseURI() {
        return Storage.getString(ctx, imageBaseUriKey);
    }

    private static int getBalanceOf(Hash160 owner) {
        if (balances.get(owner.toByteArray()) == null) {
            return 0;
        }
        return balances.get(owner.toByteArray()).toInt();
    }

    private static void incrementTotalSupplyByOne() {
        int updatedTotalSupply = Storage.getInt(ctx, totalSupplyKey) + 1;
        Storage.put(ctx, totalSupplyKey, updatedTotalSupply);
    }

    private static void onlyOwner() {
        assert Runtime.checkWitness(contractOwner()) : "onlyOwner";
    }

    public static String getVillagerCandies(int from, int size) {
        Iterator<Struct<ByteString, ByteString>> iterator = villagerCandies.find(FindOptions.RemovePrefix);
        List<Integer> result = new List<>();
        int count = 0;
        while (iterator.next() && result.size() != size) {
            if (count >= from) {
                result.add(iterator.get().key.toInt());
            }
            count++;
        }
        return StdLib.jsonSerialize(result);
    }

    public static String getVillainCandies(int from, int size) {
        Iterator<Struct<ByteString, ByteString>> iterator = villainCandies.find(FindOptions.RemovePrefix);
        List<Integer> result = new List<>();
        int count = 0;
        while (iterator.next() && result.size() != size) {
            if (count >= from) {
                result.add(iterator.get().key.toInt());
            }
            count++;
        }
        return StdLib.jsonSerialize(result);
    }

    /* OWNER ONLY METHODS */

    public static void getAvailableGas(int amount) {
        onlyOwner();
        GasToken.transfer(Runtime.getExecutingScriptHash(), contractOwner(), amount, null);
    }

    public static void getAvailableCandy(int amount) {
        onlyOwner();
        Contract.call(candyContract(), "transfer", CallFlags.All,
                new Object[] { Runtime.getExecutingScriptHash(), contractOwner(), amount, null });
    }

    public static void updateStakingContract(Hash160 contract) {
        onlyOwner();
        Storage.put(ctx, stakingContractKey, contract);
    }

    public static void updateGasPrice(int amount) {
        onlyOwner();
        Storage.put(ctx, gasPriceKey, amount);
    }

    public static void updateCandyPrice(int amount) {
        onlyOwner();
        Storage.put(ctx, candyPriceKey, amount);
    }

    public static void updateImageBaseURI(String uri) {
        onlyOwner();
        Storage.put(ctx, imageBaseUriKey, uri);
    }

    public static void updatePause(boolean paused) {
        onlyOwner();
        Storage.put(ctx, isPausedKey, paused ? 1 : 0);
    }

    /* CONTRACT MANAGEMENT */

    @OnDeployment
    public static void deploy(Object data, boolean update) {
        if (!update) {
            Storage.put(ctx, totalSupplyKey, 0);
            Object[] arr = (Object[]) data;

            Hash160 owner = (Hash160) arr[0];
            Helper.assertTrue(Hash160.isValid(owner));
            Storage.put(ctx, ownerkey, owner);

            int gasPrice = (int) arr[1];
            Helper.assertTrue(gasPrice >= 1_00000000);
            Storage.put(ctx, gasPriceKey, gasPrice);

            int candyPrice = (int) arr[2];
            Helper.assertTrue(candyPrice >= stringToInt("500000000000"));
            Storage.put(ctx, candyPriceKey, candyPrice);

            Hash160 candyHash = (Hash160) arr[3];
            Helper.assertTrue(Hash160.isValid(candyHash));
            Storage.put(ctx, candyHashKey, candyHash);

            String imageBaseURI = (String) arr[4];
            Helper.assertTrue(imageBaseURI.length() > 0);
            Storage.put(ctx, imageBaseUriKey, imageBaseURI);

            int maxTokensAmount = (int) arr[5];
            Helper.assertTrue(maxTokensAmount > 0);
            Storage.put(ctx, maxTokensAmountKey, maxTokensAmount);

            int maxGenesisAmount = (int) arr[6];
            Helper.assertTrue(maxTokensAmount > 0 && maxGenesisAmount < maxTokensAmount);
            Storage.put(ctx, maxGenesisAmountKey, maxGenesisAmount);

            Storage.put(ctx, isPausedKey, (int) arr[7]);

            String royaltiesReceiverAddress = (String) arr[8];
            Helper.assertTrue(royaltiesReceiverAddress.length() > 0);
            Storage.put(ctx, royaltiesReceiverKey, royaltiesReceiverAddress);

            int royaltiesAmount = (int) arr[9];
            Helper.assertTrue(royaltiesAmount > 0);
            Storage.put(ctx, royaltiesAmountKey, royaltiesAmount);

            int maxMintAmount = (int) arr[10];
            Helper.assertTrue(maxMintAmount > 0 && maxMintAmount < 100);
            Storage.put(ctx, maxMintAmountKey, maxMintAmount);
        }
    }

    public static void update(ByteString script, String manifest) throws Exception {
        onlyOwner();
        assert (script.length() != 0 || manifest.length() != 0)
                : "The new contract script and manifest must not be empty.";

        ContractManagement.update(script, manifest);
    }

}