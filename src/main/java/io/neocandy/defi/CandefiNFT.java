package io.neocandy.defi;

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
import io.neow3j.devpack.contracts.NeoToken;
import io.neow3j.devpack.contracts.OracleContract;
import io.neow3j.devpack.contracts.StdLib;
import io.neow3j.devpack.events.Event2Args;
import io.neow3j.devpack.events.Event3Args;
import io.neow3j.devpack.events.Event4Args;
import io.neow3j.devpack.events.Event5Args;
import io.neow3j.devpack.events.Event6Args;

@ManifestExtra(key = "name", value = "CandeFi Contract")
@ManifestExtra(key = "author", value = "NeoCandy")
@ManifestExtra(key = "description", value = "CandeFi NFT Collection")
@ManifestExtra(key = "email", value = "hello@neocandy.io")
@Permission(contract = "*", methods = { "onNEP11Payment", "transfer" })
@Permission(nativeContract = NativeContract.ContractManagement)
@Permission(nativeContract = NativeContract.OracleContract)
public class CandefiNFT {

    // EVENTS

    @DisplayName("Mint")
    private static Event2Args<Hash160, ByteString> onMint;

    @DisplayName("Transfer")
    private static Event4Args<Hash160, Hash160, Integer, ByteString> onTransfer;

    @DisplayName("Payment")
    private static Event3Args<Hash160, Integer, Object> onPayment;

    @DisplayName("OptionMinted")
    private static Event5Args<Hash160, ByteString, Integer, Integer, Integer> onOptionMinted;

    @DisplayName("Exercised")
    private static Event5Args<Hash160, ByteString, Integer, Integer, Integer> onExercise;

    // DEFAULT METADATA
    private static final String NAME = "name";
    private static final String DESC = "description";
    private static final String IMAGE = "image";
    private static final String TOKEN_URI = "tokenURI";
    private static final String TOKEN_ID = "tokenId";

    // CUSTOM METADATA
    private static final String STRIKE = "Strike";
    private static final String STAKE = "Stake";
    private static final String TYPE = "Type";
    private static final String WRITER = "Writer";
    private static final String OWNER = "Owner";

    // CUSTOM METADATA VALUES
    private static final int TYPE_CALL = 1;
    private static final int TYPE_PUT = 2;

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
    private static final byte[] candyContractHashKey = Helper.toByteArray((byte) 2);
    private static final byte[] tokensOfKey = Helper.toByteArray((byte) 3);
    private static final byte[] imageBaseUriKey = Helper.toByteArray((byte) 4);
    private static final byte[] currentSupplyKey = Helper.toByteArray((byte) 5);
    private static final byte[] isPausedKey = Helper.toByteArray((byte) 6);
    private static final byte[] royaltiesReceiverKey = Helper.toByteArray((byte) 7);
    private static final byte[] royaltiesAmountKey = Helper.toByteArray((byte) 8);
    private static final byte[] oracleEndpointKey = Helper.toByteArray((byte) 9);
    private static final byte[] writerOfKey = Helper.toByteArray((byte) 10);
    private static final byte[] minStakeKey = Helper.toByteArray((byte) 11);
    private static final byte[] protocolFeeKey = Helper.toByteArray((byte) 12);

    // STORAGE MAPS
    private static final StorageMap tokens = new StorageMap(ctx, Helper.toByteArray((byte) 101));
    private static final StorageMap balances = new StorageMap(ctx, Helper.toByteArray((byte) 102));
    private static final StorageMap ownerOfMap = new StorageMap(ctx, Helper.toByteArray((byte) 103));
    private static final StorageMap immutableTokenProperties = new StorageMap(ctx, (byte) 104);
    private static final StorageMap claims = new StorageMap(ctx, (byte) 105);

    @io.neow3j.devpack.annotations.Struct
    static class Request {
        public int type;
        public int strike;
    }

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
        if (data == null) {
            throw new Exception("onPayment_dataMissing");
        }
        Request req = (Request) data;
        if (amount - protocolFee() < minStake()) {
            throw new Exception("onPayment_invalidMinStake");
        }
        int stake = amount - protocolFee();
        if (req.type == TYPE_CALL) {
            ByteString tokenId = mint(from, req.strike, stake, TYPE_CALL);
            onOptionMinted.fire(from, tokenId, req.strike, stake, TYPE_CALL);
        } else if (req.type == TYPE_PUT) {
            ByteString tokenId = mint(from, req.strike, stake, TYPE_PUT);
            onOptionMinted.fire(from, tokenId, req.strike, stake, TYPE_PUT);
        } else
            throw new Exception("onPayment_invalidType");
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
        return "CDEFI";
    }

    @Safe
    public static int decimals() {
        return 0;
    }

    @Safe
    public static int balanceOf(Hash160 owner) {
        return getBalanceOf(owner);
    }

    @Safe
    public static int currentSupply() {
        return Storage.getIntOrZero(ctx, currentSupplyKey);
    }

    @Safe
    public static boolean isPaused() {
        return Storage.getInt(ctx, isPausedKey) == 1;
    }

    @Safe
    public static Iterator<Struct<ByteString, ByteString>> tokensOf(Hash160 owner) {
        return (Iterator<Struct<ByteString, ByteString>>) Storage.find(
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
    public static List<String> writerOfJson(Hash160 owner) throws Exception {
        Iterator<Struct<ByteString, ByteString>> iterator = (Iterator<Struct<ByteString, ByteString>>) Storage.find(
                ctx.asReadOnly(),
                createStorageMapPrefix(owner, writerOfKey),
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
        attributes.add(getAttributeMap(TYPE, tokenProps.type));
        attributes.add(getAttributeMap(STAKE, tokenProps.stake));
        attributes.add(getAttributeMap(STRIKE, tokenProps.strike));
        attributes.add(getAttributeMap(WRITER, tokenProps.writer));

        p.put(ATTRIBUTES, attributes);

        return p;
    }

    @Safe
    public static String propertiesJson(ByteString tokenId) throws Exception {
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
        attributes.add(getAttributeMap(TYPE, tokenProps.type));
        attributes.add(getAttributeMap(STAKE, tokenProps.stake));
        attributes.add(getAttributeMap(STRIKE, tokenProps.strike));
        attributes.add(getAttributeMap(WRITER, StdLib.base64Encode(tokenProps.writer)));
        attributes.add(getAttributeMap(OWNER, StdLib.base64Encode(ownerOf(tokenProps.tokenId).toByteString())));

        p.put(ATTRIBUTES, attributes);

        return StdLib.jsonSerialize(p);
    }

    @Safe
    public static String oracleEndpoint() {
        return Storage.getString(ctx, oracleEndpointKey);
    }

    @Safe
    public static int claimsOf(Hash160 account) {
        return claims.getIntOrZero(account.toByteArray());
    }

    @Safe
    public static Iterator claims() {
        return claims.find(FindOptions.RemovePrefix);
    }

    @Safe
    public static int minStake() {
        return Storage.getInt(ctx, minStakeKey);
    }

    @Safe
    public static int protocolFee() {
        return Storage.getInt(ctx, protocolFeeKey);
    }

    /* READ & WRITE */

    public static boolean transfer(Hash160 to, ByteString tokenId, Object data) throws Exception {
        Hash160 owner = ownerOf(tokenId);
        if (owner == null) {
            throw new Exception("transfer_tokenDoesNotExist");
        }
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

    public static void exercise(ByteString tokenId) throws Exception {
        Hash160 owner = new Hash160(ownerOfMap.get(tokenId));
        if (!Runtime.checkWitness(owner)) {
            throw new Exception("exercise_noAuth");
        }
        oracle("exerciseCall", tokenId);
    }

    public static void exerciseCall(String url, Object userData, int responseCode, ByteString response)
            throws Exception {
        if (Runtime.getCallingScriptHash() != OracleContract.getHash()) {
            throw new Exception("exerciseCall_onlyOracle");
        }
        ByteString tokenId = (ByteString) userData;
        TokenProperties properties = (TokenProperties) StdLib.deserialize(immutableTokenProperties.get(tokenId));

        Map<String, Object> result = (Map<String, Object>) StdLib
                .jsonDeserialize(response.toString());

        int oraclePrice = decimalStringToInt((String) result.get("price"));
        Hash160 owner = new Hash160(ownerOfMap.get(tokenId));
        if (canExercise(oraclePrice, properties.strike, properties.type)) {
            safeTransfer(Runtime.getExecutingScriptHash(), NeoToken.getHash(), owner, properties.stake);
            increaseClaims(owner, properties.stake);
            onExercise.fire(owner, tokenId, properties.type, properties.stake, properties.strike);
        }
    }

    /* UTIL */

    private static byte[] createStorageMapPrefix(Hash160 owner, byte[] prefix) {
        return Helper.concat(prefix, owner.toByteArray());
    }

    private static ByteString mint(Hash160 writer, int strike, int stake, int type) throws Exception {
        if (strike <= 0) {
            throw new Exception("mint_invalidStrike");
        }
        if (stake <= 0) {
            throw new Exception("mint_invalidStake");
        }
        ByteString tokenId = nextTokenId();
        Map<String, Object> properties = new Map<>();
        properties.put(TOKEN_ID, tokenId);
        properties.put(NAME, "Name Placeholder");
        properties.put(DESC, "Description Placeholder");
        properties.put(TOKEN_URI, "");
        properties.put(IMAGE, getImageBaseURI());
        properties.put(STAKE, stake);
        properties.put(STRIKE, strike);
        properties.put(TYPE, TYPE_CALL);
        properties.put(WRITER, writer.toByteString());

        incrementCurrentSupplyByOne();
        saveProperties(properties, tokenId);
        tokens.put(tokenId, 1);
        ownerOfMap.put(tokenId, writer);
        new StorageMap(ctx, createStorageMapPrefix(writer, tokensOfKey)).put(tokenId, 1);
        new StorageMap(ctx, createStorageMapPrefix(writer, writerOfKey)).put(tokenId, 1);
        incrementBalanceByOne(writer);
        onMint.fire(writer, tokenId);
        return tokenId;
    }

    private static void increaseClaims(Hash160 account, int amount) {
        int claim = claimsOf(account);
        claims.put(account.toByteArray(), amount + claim);
    }

    private static boolean canExercise(int oraclePrice, int strike, int type) {
        return type == TYPE_CALL && oraclePrice > strike || type == TYPE_PUT && oraclePrice < strike;
    }

    private static void safeTransfer(Hash160 from, Hash160 token, Hash160 to, int amount) throws Exception {
        boolean result = (boolean) Contract.call(token, "transfer", CallFlags.All,
                new Object[] { from, to, amount, null });
        if (!result) {
            throw new Exception("safeTransfer_transferFail");
        }
    }

    private static int decimalStringToInt(String price) {
        String[] split = StdLib.stringSplit(price, ".");
        return StdLib.atoi(split[0] + split[1], 10);
    }

    private static void oracle(String callback, Object userData) {
        String endpoint = Storage.getString(ctx, oracleEndpointKey) + "NEOUSDT";
        OracleContract.request(endpoint, null, callback, userData, 30000000);
    }

    private static ByteString nextTokenId() {
        int currentSupply = currentSupply();
        String cs = StdLib.itoa(++currentSupply, 10);
        return new ByteString(cs);
    }

    private static Hash160 candyContract() {
        ByteString result = Storage.get(ctx, candyContractHashKey);
        return result != null ? new Hash160(result) : null;
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

        if (!properties.containsKey(STRIKE)) {
            throw new Exception("saveProperties_missingStrike");
        }

        if (!properties.containsKey(STAKE)) {
            throw new Exception("saveProperties_missingStake");
        }

        if (!properties.containsKey(TYPE)) {
            throw new Exception("saveProperties_missingType");
        }

        if (!properties.containsKey(WRITER)) {
            throw new Exception("saveProperties_missingWriter");
        }

        String name = (String) properties.get(NAME);
        String desc = (String) properties.get(DESC);
        String img = (String) properties.get(IMAGE);
        String uri = (String) properties.get(TOKEN_URI);
        int strike = (int) properties.get(STRIKE);
        int stake = (int) properties.get(STAKE);
        int type = (int) properties.get(TYPE);
        ByteString writer = (ByteString) properties.get(WRITER);

        TokenProperties tokenProps = new TokenProperties(tokenId, name, img, desc, uri, strike, stake, type, writer);
        immutableTokenProperties.put(tokenId, StdLib.serialize(tokenProps));
    }

    private static Map<String, Object> getAttributeMap(String trait, Object value) {
        Map<String, Object> m = new Map<>();
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

    private static void incrementCurrentSupplyByOne() {
        int updatedCurrentSupply = Storage.getIntOrZero(ctx, currentSupplyKey) + 1;
        Storage.put(ctx, currentSupplyKey, updatedCurrentSupply);
    }

    /* PERMISSION CHECKS */

    private static void onlyOwner() throws Exception {
        if (!Runtime.checkWitness(contractOwner())) {
            throw new Exception("onlyOwner");
        }
    }

    /* OWNER ONLY METHODS */

    public static void updateImageBaseURI(String uri) throws Exception {
        onlyOwner();
        Storage.put(ctx, imageBaseUriKey, uri);
    }

    public static void updatePause(boolean paused) throws Exception {
        onlyOwner();
        Storage.put(ctx, isPausedKey, paused ? 1 : 0);
    }

    public static void updateMinStake(int minStake) throws Exception {
        onlyOwner();
        if (minStake < 100) {
            throw new Exception("updateMinStake_invalidAmount");
        }
        Storage.put(ctx, minStakeKey, minStake);
    }

    public static void updateProtocolFee(int fee) throws Exception {
        onlyOwner();
        if (fee < 0) {
            throw new Exception("updateProtocolFee_invalidAmount");
        }
        Storage.put(ctx, minStakeKey, fee);
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
            Storage.put(ctx, ownerkey, owner);

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

            Storage.put(ctx, isPausedKey, (int) arr[3]);

            /*
             * String royaltiesReceiverAddress = (String) arr[3];
             * if (royaltiesReceiverAddress.length() == 0) {
             * throw new Exception("deploy_royaltiesReceiverAddress ");
             * }
             * Storage.put(ctx, royaltiesReceiverKey, royaltiesReceiverAddress);
             * 
             * int royaltiesAmount = (int) arr[4];
             * if (royaltiesAmount <= 0) {
             * throw new Exception("deploy_royaltiesAmount");
             * }
             * Storage.put(ctx, royaltiesAmountKey, royaltiesAmount);
             */

            String oracleEndpoint = (String) arr[4];
            if (oracleEndpoint.length() == 0) {
                throw new Exception("deploy_oracleEndpoint");
            }
            Storage.put(ctx, oracleEndpointKey, oracleEndpoint);

            int protocolFee = (int) arr[5];
            if (protocolFee < 0) {
                throw new Exception("deploy_protocolFee");
            }
            Storage.put(ctx, protocolFeeKey, protocolFee);

            int minStake = (int) arr[6];
            // too low value, does not make sense
            if (minStake < 100) {
                throw new Exception("deploy_minStake");
            }
            Storage.put(ctx, minStakeKey, minStake);
        }
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
        public int stake;
        public int strike;
        public int type;
        public ByteString writer;

        public TokenProperties(ByteString tokenId, String name, String image, String description, String tokenUri,
                int stake, int strike, int type, ByteString writer) {
            this.tokenId = tokenId;
            this.name = name;
            this.image = image;
            this.description = description;
            this.tokenUri = tokenUri;
            this.stake = stake;
            this.strike = strike;
            this.type = type;
            this.writer = writer;
        }
    }

}