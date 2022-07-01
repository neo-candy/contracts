package io.neocandy.tokens.nep11.lollipop;

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
import io.neow3j.devpack.StringLiteralHelper;
import io.neow3j.devpack.Iterator.Struct;
import io.neow3j.devpack.annotations.DisplayName;
import io.neow3j.devpack.annotations.ManifestExtra;
import io.neow3j.devpack.annotations.Permission;
import io.neow3j.devpack.annotations.OnDeployment;
import io.neow3j.devpack.annotations.OnNEP17Payment;
import io.neow3j.devpack.annotations.OnVerification;
import io.neow3j.devpack.annotations.Safe;
import io.neow3j.devpack.annotations.SupportedStandard;
import io.neow3j.devpack.constants.CallFlags;
import io.neow3j.devpack.constants.FindOptions;
import io.neow3j.devpack.constants.NativeContract;
import io.neow3j.devpack.constants.NeoStandard;
import io.neow3j.devpack.contracts.ContractManagement;
import io.neow3j.devpack.contracts.StdLib;
import io.neow3j.devpack.events.Event3Args;
import io.neow3j.devpack.events.Event4Args;

import static io.neocandy.games.candyclash.CandyClashUtils.createStorageMapPrefix;

@ManifestExtra(key = "name", value = "LollipopNFT Contract")
@ManifestExtra(key = "author", value = "NeoCandy")
@ManifestExtra(key = "description", value = "Lollipop NFT Collection")
@ManifestExtra(key = "email", value = "hello@neocandy.io")
@ManifestExtra(key = "source", value = "https://github.com/neo-candy")
@Permission(contract = "*", methods = { "onNEP11Payment", "transfer" })
@Permission(nativeContract = NativeContract.ContractManagement)
@SupportedStandard(neoStandard = NeoStandard.NEP_11)
public class LollipopNFT {

    // EVENTS
    @DisplayName("Transfer")
    static Event4Args<Hash160, Hash160, Integer, ByteString> onTransfer;

    @DisplayName("Payment")
    static Event3Args<Hash160, Integer, Object> onPayment;

    // DEFAULT METADATA
    private static final String NAME = "name";
    private static final String DESC = "description";
    private static final String IMAGE = "image";
    private static final String TOKEN_URI = "tokenURI";
    private static final String TOKEN_ID = "tokenId";

    // GM related properties
    private static final String PROPERTIES = "properties";
    private static final String PROPERTY_HAS_LOCKED = "has_locked";
    private static final String PROPERTY_TYPE = "type";
    private static final int PROPERTY_GAME_TYPE = 4;

    // NFT attributes
    private static final String ATTRIBUTES = "attributes";

    private static final StorageContext ctx = Storage.getStorageContext();

    // STORAGE KEYS
    private static final byte[] ownerKey = Helper.toByteArray((byte) 5);
    private static final byte[] maxSupplyKey = Helper.toByteArray((byte) 6);
    private static final byte[] candyContractHashKey = Helper.toByteArray((byte) 7);
    private static final byte[] tokensOfKey = Helper.toByteArray((byte) 8);
    private static final byte[] imageBaseUriKey = Helper.toByteArray((byte) 9);
    private static final byte[] currentSupplyKey = Helper.toByteArray((byte) 10);
    private static final byte[] isPausedKey = Helper.toByteArray((byte) 11);
    private static final byte[] currentPriceKey = Helper.toByteArray((byte) 12);

    // STORAGE MAPS
    private static final StorageMap tokens = new StorageMap(ctx, Helper.toByteArray((byte) 101));
    private static final StorageMap balances = new StorageMap(ctx, Helper.toByteArray((byte) 102));
    private static final StorageMap ownerOfMap = new StorageMap(ctx, Helper.toByteArray((byte) 103));
    private static final StorageMap immutableTokenProperties = new StorageMap(ctx, (byte) 104);

    @OnNEP17Payment
    public static void onPayment(Hash160 from, int amount, Object data) throws Exception {
        if (isPaused()) {
            throw new Exception("onPayment_isPaused");
        }
        if (amount <= 0) {
            throw new Exception("onPayment_invalidAmount");
        }
        Hash160 token = Runtime.getCallingScriptHash();

        if (token != candyContract()) {
            throw new Exception("onPayment_onlyCandy");
        }

        if (amount != currentPrice()) {
            throw new Exception("onPayment_invalidPrice");

        }
        mint(from);
        onPayment.fire(from, amount, data);
    }

    /* READ ONLY */

    @Safe
    public static Hash160 contractOwner() {
        return new Hash160(Storage.get(ctx.asReadOnly(), ownerKey));
    }

    @Safe
    public static String symbol() {
        return "LOLLI";
    }

    @Safe
    public static int decimals() {
        return 0;
    }

    @Safe
    public static int maxSupply() {
        return Storage.getInt(ctx.asReadOnly(), maxSupplyKey);
    }

    @Safe
    public static int balanceOf(Hash160 owner) throws Exception {
        if (!Hash160.isValid(owner)) {
            throw new Exception("balanceOf_invalidHash");
        }
        return getBalanceOf(owner);
    }

    @Safe
    public static int currentSupply() {
        return Storage.getIntOrZero(ctx.asReadOnly(), currentSupplyKey);
    }

    @Safe
    public static boolean isPaused() {
        return Storage.getInt(ctx.asReadOnly(), isPausedKey) == 1;
    }

    @Safe
    public static int currentPrice() {
        return Storage.getIntOrZero(ctx.asReadOnly(), currentPriceKey);
    }

    @Safe
    public static Iterator<ByteString> tokensOf(Hash160 owner) throws Exception {
        if (!Hash160.isValid(owner)) {
            throw new Exception("tokensOf_invalidHash");
        }
        return (Iterator<ByteString>) Storage.find(ctx.asReadOnly(), createStorageMapPrefix(owner, tokensOfKey),
                (byte) (FindOptions.KeysOnly | FindOptions.RemovePrefix));
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
    public static Hash160 candyContract() {
        ByteString result = Storage.get(ctx.asReadOnly(), candyContractHashKey);
        return result != null ? new Hash160(result) : null;
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
        TokenProperties tokenProps = (TokenProperties) StdLib
                .deserialize(immutableTokenProperties.get(tokenId));
        Map<String, Object> p = new Map<>();

        if (tokenProps == null) {
            throw new Exception("properties_tokenDoesNotExist");
        }
        p.put(TOKEN_ID, tokenProps.tokenId);
        p.put(NAME, tokenProps.name);
        p.put(DESC, tokenProps.description);
        p.put(IMAGE, tokenProps.image);
        p.put(TOKEN_URI, tokenProps.tokenUri);

        Map<String, Object> properties = new Map<>();
        properties.put(PROPERTY_HAS_LOCKED, false);
        properties.put(PROPERTY_TYPE, PROPERTY_GAME_TYPE);
        p.put(PROPERTIES, properties);

        List<Map<String, Object>> attributes = new List<>();
        p.put(ATTRIBUTES, attributes);

        return p;
    }

    @Safe
    public static String propertiesJson(ByteString tokenId) throws Exception {
        return StdLib.jsonSerialize(properties(tokenId));
    }

    /* READ & WRITE */

    public static boolean transfer(Hash160 to, ByteString tokenId, Object data) throws Exception {
        if (!Hash160.isValid(to)) {
            throw new Exception("transfer_invalidHash");
        }
        if (tokenId.length() > 64) {
            throw new Exception("transfer_invalidTokenId");
        }
        Hash160 owner = ownerOf(tokenId);
        if (!Runtime.checkWitness(owner)) {
            return false;
        }
        onTransfer.fire(owner, to, 1, tokenId);
        if (owner != to) {
            ownerOfMap.put(tokenId, to.toByteArray());

            new StorageMap(ctx, createStorageMapPrefix(owner, tokensOfKey)).delete(tokenId);
            new StorageMap(ctx, createStorageMapPrefix(to, tokensOfKey)).put(tokenId, 1);

            decrementBalanceByOne(owner);
            incrementBalanceByOne(to);
        }
        if (ContractManagement.getContract(to) != null) {
            Contract.call(to, "onNEP11Payment", CallFlags.All, new Object[] { owner, 1, tokenId, data });
        }
        return true;
    }

    /* UTIL */

    private static void updateCurrentPrice() {
        int candyPrice = 0;
        int cs = currentSupply();
        if (cs == 22) {
            candyPrice = StringLiteralHelper.stringToInt("500000000000000");
            Storage.put(ctx, currentPriceKey, candyPrice);
        } else if (cs == 50) {
            candyPrice = StringLiteralHelper.stringToInt("1000000000000000");
            Storage.put(ctx, currentPriceKey, candyPrice);
        } else if (cs == 100) {
            candyPrice = StringLiteralHelper.stringToInt("1250000000000000");
            Storage.put(ctx, currentPriceKey, candyPrice);
        } else if (cs == 150) {
            candyPrice = StringLiteralHelper.stringToInt("1500000000000000");
            Storage.put(ctx, currentPriceKey, candyPrice);
        } else if (cs == 200) {
            candyPrice = StringLiteralHelper.stringToInt("2000000000000000");
            Storage.put(ctx, currentPriceKey, candyPrice);
        } else if (cs == 218) {
            candyPrice = StringLiteralHelper.stringToInt("3000000000000000");
            Storage.put(ctx, currentPriceKey, candyPrice);
        } else if (cs == 220) {
            candyPrice = StringLiteralHelper.stringToInt("4000000000000000");
            Storage.put(ctx, currentPriceKey, candyPrice);
        }
    }

    private static void mint(Hash160 owner) throws Exception {
        if (currentSupply() >= maxSupply()) {
            throw new Exception("onPayment_soldOut");
        }
        int currentSupply = currentSupply();
        String cs = StdLib.itoa(currentSupply, 10);
        ByteString tokenId = new ByteString(cs);
        Map<String, Object> properties = new Map<>();
        properties.put(TOKEN_ID, cs);
        properties.put(NAME, "NeoCandy Lollipop");
        properties.put(DESC,
                "A collection of 222 unique lollipop NFTs. Holders can participate in exclusive events, NFT claims, airdrops, giveaways, and more.");
        properties.put(TOKEN_URI, "");
        properties.put(IMAGE, getImageBaseURI() + cs + ".png");
        incrementCurrentSupplyByOne();
        updateCurrentPrice();

        saveProperties(properties, tokenId);
        tokens.put(tokenId, tokenId);
        ownerOfMap.put(tokenId, owner);
        new StorageMap(ctx, createStorageMapPrefix(owner, tokensOfKey)).put(tokenId,
                1);
        incrementBalanceByOne(owner);
        onTransfer.fire(null, owner, 1, tokenId);
    }

    private static void saveProperties(Map<String, Object> properties, ByteString tokenId) throws Exception {

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

        String name = (String) properties.get(NAME);
        String desc = (String) properties.get(DESC);
        String img = (String) properties.get(IMAGE);
        String uri = (String) properties.get(TOKEN_URI);

        TokenProperties tokenProps = new TokenProperties(tokenId, name, img, desc, uri);
        immutableTokenProperties.put(tokenId, StdLib.serialize(tokenProps));
    }

    private static void incrementBalanceByOne(Hash160 owner) {
        balances.put(owner.toByteArray(), getBalanceOf(owner) + 1);
    }

    private static void decrementBalanceByOne(Hash160 owner) {
        balances.put(owner.toByteArray(), getBalanceOf(owner) - 1);
    }

    private static String getImageBaseURI() {
        return Storage.getString(ctx.asReadOnly(), imageBaseUriKey);
    }

    private static int getBalanceOf(Hash160 owner) {
        if (balances.get(owner.toByteArray()) == null) {
            return 0;
        }
        return balances.get(owner.toByteArray()).toInt();
    }

    private static void incrementCurrentSupplyByOne() {
        int updatedCurrentSupply = currentSupply() + 1;
        Storage.put(ctx, currentSupplyKey, updatedCurrentSupply);
    }

    /* PERMISSION CHECKS */

    private static void onlyOwner() throws Exception {
        if (!Runtime.checkWitness(contractOwner())) {
            throw new Exception("onlyOwner");
        }
    }

    /* OWNER ONLY METHODS */

    public static void getCandy(int amount) throws Exception {
        onlyOwner();
        Contract.call(candyContract(), "transfer", CallFlags.All,
                new Object[] { Runtime.getExecutingScriptHash(), contractOwner(), amount, null });
    }

    public static void adminMint() throws Exception {
        onlyOwner();
        mint(contractOwner());
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
    public static void deploy(Object data, boolean update) throws Exception {
        if (!update) {
            Object[] arr = (Object[]) data;

            Hash160 owner = (Hash160) arr[0];
            if (!Hash160.isValid(owner)) {
                throw new Exception("deploy_invalidOwner");
            }
            Storage.put(ctx, ownerKey, owner);

            Hash160 candyHash = (Hash160) arr[1];
            if (!Hash160.isValid(candyHash)) {
                throw new Exception("deploy_invalidCandyHash");
            }
            Storage.put(ctx, candyContractHashKey, candyHash);

            String imageBaseURI = (String) arr[2];
            if (imageBaseURI.length() == 0) {
                throw new Exception("deploy_invalidImageBaseURI");
            }
            Storage.put(ctx, imageBaseUriKey, imageBaseURI);
            Storage.put(ctx, maxSupplyKey, 222);
            Storage.put(ctx, isPausedKey, 1);

            for (int i = 0; i < 22; i++) {
                mint(contractOwner());
            }
        }
    }

    @OnVerification
    public static boolean verify() {
        return Runtime.checkWitness(contractOwner());
    }

    public static void update(ByteString script, String manifest) throws Exception {
        onlyOwner();
        if (script.length() == 0 && manifest.length() == 0) {
            throw new Exception("update_contractAndManifestEmpty");
        }
        ContractManagement.update(script, manifest);
    }

    static class TokenProperties {

        public ByteString tokenId;
        public String name;
        public String image;
        public String description;
        public String tokenUri;

        public TokenProperties(ByteString tokenId, String name, String image, String description, String tokenUri) {
            this.tokenId = tokenId;
            this.name = name;
            this.image = image;
            this.description = description;
            this.tokenUri = tokenUri;
        }
    }

}