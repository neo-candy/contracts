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
import io.neow3j.devpack.constants.NativeContract;
import io.neow3j.devpack.contracts.ContractManagement;
import io.neow3j.devpack.contracts.StdLib;
import io.neow3j.devpack.events.Event1Arg;
import io.neow3j.devpack.events.Event2Args;
import io.neow3j.devpack.events.Event3Args;
import io.neow3j.devpack.events.Event4Args;

import static io.neow3j.devpack.StringLiteralHelper.stringToInt;
import static io.neocandy.games.candyclash.Utils.createStorageMapPrefix;
import static io.neocandy.games.candyclash.Utils.randomNumber;

@ManifestExtra(key = "name", value = "CandyClashNFT Contract")
@ManifestExtra(key = "author", value = "NeoCandy")
@ManifestExtra(key = "description", value = "CandyClash NFT Collection")
@ManifestExtra(key = "email", value = "hello@neocandy.io")
@Permission(contract = "*", methods = { "totalVillainCandiesStaked", "onNEP11Payment", "transfer" })
@Permission(nativeContract = NativeContract.ContractManagement)
public class CandyClashNFT {

    // EVENTS

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
    private static final String ORIGIN = "Origin";
    private static final String LEVEL = "Level";
    private static final String AGE = "Age";

    // METADATA VALUES
    private static final String TYPE_VILLAIN = "Villain";
    private static final String TYPE_VILLAGER = "Villager";
    private static final String ORIGIN_SWEETGLEN = "Sweetglen";
    private static final String ORIGIN_CANEMOR = "Canemor";
    private static final String GEN_0 = "0";
    private static final String GEN_1 = "1";

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
    private static final byte[] nftPriceKey = Helper.toByteArray((byte) 3);
    private static final byte[] candyContractHashKey = Helper.toByteArray((byte) 4);
    private static final byte[] tokensOfKey = Helper.toByteArray((byte) 5);
    private static final byte[] imageBaseUriKey = Helper.toByteArray((byte) 6);
    private static final byte[] maxTokensAmountKey = Helper.toByteArray((byte) 7);
    private static final byte[] maxGenesisAmountKey = Helper.toByteArray((byte) 8);
    private static final byte[] isPausedKey = Helper.toByteArray((byte) 9);
    private static final byte[] royaltiesReceiverKey = Helper.toByteArray((byte) 10);
    private static final byte[] royaltiesAmountKey = Helper.toByteArray((byte) 11);
    private static final byte[] maxMintAmountKey = Helper.toByteArray((byte) 12);
    private static final byte[] stakingContractHashKey = Helper.toByteArray((byte) 13);
    private static final byte[] xpTableKey = Helper.toByteArray((byte) 14);
    private static final byte[] pricePerXpKey = Helper.toByteArray((byte) 15);
    private static final byte[] stolenNftsKey = Helper.toByteArray((byte) 16);
    private static final byte[] lostNftsKey = Helper.toByteArray((byte) 17);

    // STORAGE MAPS
    private static final StorageMap tokens = new StorageMap(ctx, Helper.toByteArray((byte) 101));
    private static final StorageMap balances = new StorageMap(ctx, Helper.toByteArray((byte) 102));
    private static final StorageMap ownerOfMap = new StorageMap(ctx, Helper.toByteArray((byte) 103));
    private static final StorageMap sugarValues = new StorageMap(ctx, (byte) 104);
    private static final StorageMap bonusValues = new StorageMap(ctx, (byte) 105);
    private static final StorageMap levelValues = new StorageMap(ctx, (byte) 106);
    private static final StorageMap ageValues = new StorageMap(ctx, (byte) 107);
    private static final StorageMap villains = new StorageMap(ctx, (byte) 108);
    private static final StorageMap villagers = new StorageMap(ctx, (byte) 109);
    private static final StorageMap immutableTokenProperties = new StorageMap(ctx, (byte) 110);

    @OnNEP17Payment
    public static void onPayment(Hash160 from, int amount, Object data) throws Exception {
        if (isPaused()) {
            throw new Exception("onPayment_isPaused");
        }
        if (stakingContract() == null) {
            throw new Exception("onPayment_missingStakingContract");
        }
        if (totalSupply() >= maxTokensAmount()) {
            throw new Exception("onPayment_soldOut");
        }
        Hash160 token = Runtime.getCallingScriptHash();

        if (token != candyContract()) {
            throw new Exception("onPayment_onlyCandy");
        }
        if (amount != nftPrice()) {
            throw new Exception("onPayment_invalidCandyAmount");
        }

        String generation = GEN_0;
        if (totalSupply() >= maxGenesisAmount()) {
            generation = GEN_1;
        }
        mint(from, generation);
        burn(amount);
        onPayment.fire(from, amount, data);
    }

    /* READ ONLY */

    @Safe
    public static Hash160 contractOwner() {
        return new Hash160(Storage.get(ctx, ownerkey));
    }

    /**
     * Required by NFT marketplaces that support royalties.
     * 
     * @return royalties data with receiverAddress and royaltiesAmount.
     */
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
        return Storage.getInt(ctx, isPausedKey) == 1;
    }

    @Safe
    public static int nftPrice() {
        return Storage.getInt(ctx, nftPriceKey);
    }

    @Safe
    public static Iterator<ByteString> tokensOf(Hash160 owner) {
        return (Iterator<ByteString>) Storage.find(
                ctx.asReadOnly(),
                createStorageMapPrefix(owner, tokensOfKey),
                FindOptions.RemovePrefix);
    }

    @Safe
    public static List<String> tokensOfJson(Hash160 owner) throws Exception {
        Iterator<Struct<ByteString, ByteString>> iterator = (Iterator<Struct<ByteString, ByteString>>) Storage.find(
                ctx.asReadOnly(),
                createStorageMapPrefix(owner, tokensOfKey),
                FindOptions.RemovePrefix);
        List<String> tokens = new List();
        while (iterator.next()) {
            ByteString result = (ByteString) iterator.get().key;
            tokens.add(propertiesJson(result));
        }
        return tokens;
    }

    @Safe
    public static Hash160 ownerOf(ByteString tokenId) {
        ByteString owner = ownerOfMap.get(tokenId);
        if (owner == null) {
            return null;
        }
        return new Hash160(owner);
    }

    @Safe
    public static Iterator<Iterator.Struct<ByteString, ByteString>> tokens() {
        return (Iterator<Iterator.Struct<ByteString, ByteString>>) tokens.find(FindOptions.RemovePrefix);
    }

    @Safe
    public static Map<String, Object> properties(ByteString tokenId) throws Exception {
        ImmutableTokenProperties iTokenProps = (ImmutableTokenProperties) StdLib
                .deserialize(immutableTokenProperties.get(tokenId));
        Map<String, Object> p = new Map<>();

        if (iTokenProps == null) {
            throw new Exception("properties_tokenDoesNotExist");
        }

        p.put(NAME, iTokenProps.getName());
        p.put(DESC, iTokenProps.getDescription());
        p.put(IMAGE, iTokenProps.getImage());
        p.put(TOKEN_URI, iTokenProps.getTokenUri());

        Map<String, Object> properties = new Map<>();
        properties.put(PROPERTY_HAS_LOCKED, false);
        properties.put(PROPERTY_TYPE, PROPERTY_GAME_TYPE);
        p.put(PROPERTIES, properties);

        List<Map<String, String>> attributes = new List<>();
        attributes.add(getAttributeMap(TYPE, iTokenProps.getType()));
        attributes.add(getAttributeMap(GENERATION, iTokenProps.getGeneration()));
        attributes.add(getAttributeMap(ORIGIN, iTokenProps.getOrigin()));

        attributes.add(getAttributeMap(SUGAR, sugarValues.get(tokenId).toString()));
        attributes.add(getAttributeMap(BONUS, bonusValues.get(tokenId).toString() + "%"));
        attributes.add(getAttributeMap(AGE, ageValues.get(tokenId).toString()));
        attributes.add(getAttributeMap(LEVEL, levelValues.get(tokenId).toString()));
        p.put(ATTRIBUTES, attributes);

        return p;
    }

    @Safe
    public static String propertiesJson(ByteString tokenId) throws Exception {
        return StdLib.jsonSerialize(properties(tokenId));
    }

    /**
     * Query minted villager nfts, supports pagination.
     * 
     * @param from start index
     * @param size list size
     * @return list of minted villager nfts
     */
    @Safe
    public static String getVillagerCandies(int from, int size) {
        Iterator<Struct<ByteString, ByteString>> iterator = villagers.find(FindOptions.RemovePrefix);
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

    /**
     * Query minted villain nfts, supports pagination.
     * 
     * @param from start index
     * @param size list size
     * @return list of minted villain nfts
     */
    @Safe
    public static String getVillainCandies(int from, int size) {
        Iterator<Struct<ByteString, ByteString>> iterator = villains.find(FindOptions.RemovePrefix);
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

    @Safe
    public static int[] experienceTable() {
        return (int[]) StdLib.deserialize(Storage.get(ctx, xpTableKey));
    }

    @Safe
    public static int pricePerExperiencePoint() {
        return (int) Storage.getInt(ctx, pricePerXpKey);
    }

    /* READ & WRITE */

    public static boolean transfer(Hash160 to, ByteString tokenId, Object data) throws Exception {
        Hash160 owner = ownerOf(tokenId);
        if (owner == null) {
            throw new Exception("transfer_tokenDoesNotExist");
        }
        onlyOwner();
        ownerOfMap.put(tokenId, to.toByteArray());
        new StorageMap(ctx, createStorageMapPrefix(owner, tokensOfKey)).delete(tokenId);
        new StorageMap(ctx, createStorageMapPrefix(to, tokensOfKey)).put(tokenId, 1);

        decrementBalanceByOne(owner);
        incrementBalanceByOne(to);

        onTransfer.fire(owner, to, 1, tokenId);

        if (ContractManagement.getContract(to) != null) {
            Contract.call(to, "onNEP11Payment", CallFlags.All,
                    new Object[] { owner, 1, tokenId, data });
        }
        return true;
    }

    public static void addExperienceToToken(ByteString tokenId, int amount) throws Exception {
        Hash160 owner = ownerOf(tokenId);
        if (!Runtime.checkWitness(owner)) {
            throw new Exception("addExperienceToToken_noAuth");
        }
        int currentXp = sugarValues.getInt(tokenId);
        int[] table = experienceTable();
        int max = table[table.length - 1];
        if (currentXp + amount > max || amount <= 0) {
            throw new Exception("addExperienceToToken_invalidAmount");
        }
        int price = pricePerExperiencePoint();
        safeTransfer(owner, Runtime.getExecutingScriptHash(), amount * price);
        int newExp = currentXp + amount;
        sugarValues.put(tokenId, newExp);
        levelValues.put(tokenId, getLevelForXp(newExp));
    }

    /* UTIL */

    private static void burn(int amount) throws Exception {
        safeTransfer(Runtime.getExecutingScriptHash(), Hash160.zero(), amount);
    }

    private static void safeTransfer(Hash160 from, Hash160 to, int amount) throws Exception {
        if (!Runtime.checkWitness(from)) {
            throw new Exception("safeTransfer_noAuth");
        }
        Contract.call(candyContract(), "transfer", CallFlags.All,
                new Object[] { from, Runtime.getExecutingScriptHash(), amount, null });
    }

    private static int getLevelForXp(int xp) {
        int[] table = experienceTable();
        int level = 1;
        for (int i = 0; i < table.length; i++) {
            if (table[i] >= xp) {
                level = i;
                break;
            }
        }
        return level;
    }

    private static String generateName(boolean villain) {
        return "Sweet Marcid";
    }

    private static void mint(Hash160 owner, String gen) throws Exception {
        int totalSupply = totalSupply();
        String ts = StdLib.jsonSerialize(++totalSupply);
        ByteString tokenId = new ByteString(ts);
        Map<String, String> properties = new Map<>();
        properties.put(TOKEN_ID, ts);
        properties.put(DESC, "This candy is part of the Candyclash NFT collection.");
        properties.put(IMAGE, getImageBaseURI() + "/" + tokenId + ".png");
        properties.put(TOKEN_URI, "");
        properties.put(GENERATION, gen);

        // there is a 10% chance that a new gen 1 mint can be stolen
        if (gen == "1") {
            boolean steal = Runtime.getRandom() % 10 == 0;
            if (steal) {
                new StorageMap(ctx, createStorageMapPrefix(owner, lostNftsKey)).put(tokenId, 1);
                Hash160 newOwner = randomVillainCandyOwner();
                owner = newOwner != null ? newOwner : owner;
                new StorageMap(ctx, createStorageMapPrefix(owner, stolenNftsKey)).put(tokenId, 1);
            }
        }
        boolean isEvil = Runtime.getRandom() % 10 == 0;
        properties.put(NAME, generateName(isEvil));
        properties.put(LEVEL, "1");
        properties.put(SUGAR, "0");
        properties.put(AGE, "1"); // RANDOMIZE
        int randBonus = randomBonusClaimAmount();
        String bonus = StdLib.jsonSerialize(randBonus);
        properties.put(BONUS, bonus);
        if (isEvil) {
            properties.put(ORIGIN, ORIGIN_CANEMOR);
            properties.put(TYPE, TYPE_VILLAIN);
            villains.put(tokenId, owner);
        } else {
            properties.put(ORIGIN, ORIGIN_SWEETGLEN);
            properties.put(TYPE, TYPE_VILLAGER);
            villagers.put(tokenId, owner);
        }
        incrementTotalSupplyByOne();
        saveProperties(properties, tokenId);
        tokens.put(tokenId, tokenId);
        ownerOfMap.put(tokenId, owner);
        new StorageMap(ctx, createStorageMapPrefix(owner, tokensOfKey)).put(tokenId, 1);
        incrementBalanceByOne(owner);
        onMint.fire(owner, tokenId);
    }

    /**
     * Calculate a random number.
     * - 50% to get 0
     * - 20% to get 1
     * - 14% to get 2
     * - 10% to get 3
     * - 5% to get 4
     * - 1% to get 5
     * 
     * @return random claim bonus number between 1 and 5
     */
    private static int randomBonusClaimAmount() {
        int rand = randomNumber(100);
        int bonus = 0;
        if (rand == 0) {
            bonus = 5;
        } else if (rand < 6) {
            bonus = 4;
        } else if (rand < 16) {
            bonus = 3;
        } else if (rand < 30) {
            bonus = 2;
        } else if (rand < 50) {
            bonus = 1;
        }
        return bonus;
    }

    /**
     * Get a random number between 0 and amount of total villain NFTs staked.
     * Iterate over the staked villain NFTs and return the NFT owner at the index of
     * the random number. When no villain NFT is staked then return null
     * 
     * @return the owner hash of a random villain NFT owner
     */
    private static Hash160 randomVillainCandyOwner() {
        int rand = randomNumber(totalVillainCandiesStaked());
        Iterator<ByteString> iter = villains.find(FindOptions.ValuesOnly);
        int count = 0;
        while (iter.next()) {
            if (count == rand) {
                return new Hash160(iter.get());
            }
            count++;
        }
        return null;
    }

    private static int totalVillainCandiesStaked() {
        return (int) Contract.call(stakingContract(), "totalVillainCandiesStaked", CallFlags.All, new Object[0]);
    }

    private static Hash160 stakingContract() {
        ByteString result = Storage.get(ctx, stakingContractHashKey);
        return result != null ? new Hash160(result) : null;
    }

    private static Hash160 candyContract() {
        ByteString result = Storage.get(ctx, candyContractHashKey);
        return result != null ? new Hash160(result) : null;
    }

    private static void saveProperties(Map<String, String> properties, ByteString tokenId) throws Exception {

        if (!properties.containsKey(NAME)) {
            throw new Exception("saveProperties_missingName");
        }

        if (!properties.containsKey(DESC)) {
            throw new Exception("saveProperties_missingDescription");
        }

        if (!properties.containsKey(IMAGE)) {
            throw new Exception("saveProperties_missingImage");
        }

        if (!properties.containsKey(TOKEN_URI)) {
            throw new Exception("saveProperties_missingTokenUri");
        }

        if (!properties.containsKey(SUGAR)) {
            throw new Exception("saveProperties_missingSugar");
        }

        if (!properties.containsKey(TYPE)) {
            throw new Exception("saveProperties_missingType");
        }

        if (!properties.containsKey(GENERATION)) {
            throw new Exception("saveProperties_missingGeneration");
        }

        if (!properties.containsKey(LEVEL)) {
            throw new Exception("saveProperties_missingLevel");
        }

        if (!properties.containsKey(AGE)) {
            throw new Exception("saveProperties_missingAge");
        }

        if (!properties.containsKey(ORIGIN)) {
            throw new Exception("saveProperties_missingOrigin");
        }

        if (!properties.containsKey(BONUS)) {
            throw new Exception("saveProperties_missingBonus");
        }

        String name = properties.get(NAME);
        String desc = properties.get(DESC);
        String img = properties.get(IMAGE);
        String uri = properties.get(TOKEN_URI);
        String type = properties.get(TYPE);
        String generation = properties.get(GENERATION);
        String origin = properties.get(ORIGIN);

        ImmutableTokenProperties iTokenProps = new ImmutableTokenProperties(tokenId.toString(), name, img, desc, uri,
                type, origin,
                generation);
        immutableTokenProperties.put(tokenId, StdLib.serialize(iTokenProps));

        String sugar = properties.get(SUGAR);
        String level = properties.get(LEVEL);
        String age = properties.get(AGE);
        String bonus = properties.get(BONUS);

        sugarValues.put(tokenId, sugar);
        levelValues.put(tokenId, level);
        ageValues.put(tokenId, age);
        bonusValues.put(tokenId, bonus);
    }

    private static Map<String, String> getAttributeMap(String trait, String value) {
        Map<String, String> m = new Map<>();
        m.put(ATTRIBUTE_TRAIT_TYPE, trait);
        m.put(ATTRIBUTE_VALUE, value);
        m.put(ATTRIBUTE_DISPLAY_TYPE, "");
        return m;
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

    /* PERMISSION CHECKS */

    private static void onlyOwner() throws Exception {
        if (!Runtime.checkWitness(contractOwner())) {
            throw new Exception("onlyOwner");
        }
    }

    /* OWNER ONLY METHODS */

    public static void updateStakingContract(Hash160 contract) throws Exception {
        onlyOwner();
        Storage.put(ctx, stakingContractHashKey, contract);
    }

    public static void updateCandyPrice(int amount) throws Exception {
        onlyOwner();
        Storage.put(ctx, nftPriceKey, amount);
    }

    public static void updateImageBaseURI(String uri) throws Exception {
        onlyOwner();
        Storage.put(ctx, imageBaseUriKey, uri);
    }

    public static void updatePause(boolean paused) throws Exception {
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

            int candyPrice = (int) arr[2];
            Helper.assertTrue(candyPrice >= stringToInt("500000000000"));
            Storage.put(ctx, nftPriceKey, candyPrice);

            Hash160 candyHash = (Hash160) arr[3];
            Helper.assertTrue(Hash160.isValid(candyHash));
            Storage.put(ctx, candyContractHashKey, candyHash);

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

            int[] xpTable = (int[]) arr[11];
            Helper.assertTrue(xpTable.length > 1);
            Storage.put(ctx, xpTableKey, StdLib.serialize(xpTable));

            int pricePerXp = (int) arr[12];
            Helper.assertTrue(pricePerXp > 0);
            Storage.put(ctx, pricePerXpKey, pricePerXp);
        }
    }

    public static void update(ByteString script, String manifest) throws Exception {
        onlyOwner();
        if (script.length() == 0 && manifest.length() == 0) {
            throw new Exception("update_contractAndManifestEmpty");
        }
        ContractManagement.update(script, manifest);
    }

}