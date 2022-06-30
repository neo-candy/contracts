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
import io.neow3j.devpack.annotations.SupportedStandard;
import io.neow3j.devpack.constants.CallFlags;
import io.neow3j.devpack.constants.FindOptions;
import io.neow3j.devpack.constants.NativeContract;
import io.neow3j.devpack.constants.NeoStandard;
import io.neow3j.devpack.contracts.ContractManagement;
import io.neow3j.devpack.contracts.GasToken;
import io.neow3j.devpack.contracts.OracleContract;
import io.neow3j.devpack.contracts.StdLib;
import io.neow3j.devpack.events.Event3Args;
import io.neow3j.devpack.events.Event4Args;
import io.neow3j.devpack.events.Event5Args;
import io.neow3j.devpack.events.Event7Args;

@ManifestExtra(key = "name", value = "CandeFi Contract")
@ManifestExtra(key = "author", value = "NeoCandy")
@ManifestExtra(key = "description", value = "CandeFi NFT Collection")
@ManifestExtra(key = "email", value = "hello@neocandy.io")
@ManifestExtra(key = "source", value = "tba")
@Permission(contract = "*", methods = { "onNEP11Payment", "transfer" })
@Permission(nativeContract = NativeContract.ContractManagement)
@Permission(nativeContract = NativeContract.OracleContract)
@Permission(contract = "0xd094715400b84a1b4396df3c7015ab0bd60baf03", methods = "*")
@SupportedStandard(neoStandard = NeoStandard.NEP_11)
public class CandefiNFT {

    // EVENTS

    @DisplayName("Transfer")
    private static Event4Args<Hash160, Hash160, Integer, ByteString> onTransfer;

    @DisplayName("Payment")
    private static Event3Args<Hash160, Integer, Object> onPayment;

    @DisplayName("OptionMinted")
    private static Event7Args<Hash160, ByteString, Integer, Integer, Integer, Integer, Integer> onOptionMinted;

    @DisplayName("Exercised")
    private static Event5Args<Hash160, ByteString, Integer, Integer, Integer> onExercise;

    @DisplayName("Burn")
    private static Event3Args<Hash160, ByteString, Integer> onBurn;

    @DisplayName("Debug")
    private static Event4Args<Object, Object, Object, Object> onDebug;

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
    private static final String VDOT = "Vdot";
    private static final String CREATED = "Created";
    private static final String EXERCISED = "Exercised";
    private static final String REAL_VALUE = "Real Value";
    private static final String START_VALUE = "Start Value";
    private static final String VI = "Vi";
    private static final String SAFE = "Safe";

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

    private static final StorageContext ctx = Storage.getStorageContext();
    private static int ORACLE_SUCCESS_RESPONSE_CODE = 0;

    // STORAGE KEYS
    private static final byte[] ownerkey = Helper.toByteArray((byte) 1);
    private static final byte[] candyContractHashKey = Helper.toByteArray((byte) 2);
    private static final byte[] tokensOfKey = Helper.toByteArray((byte) 3);
    private static final byte[] imageBaseUriKey = Helper.toByteArray((byte) 4);
    private static final byte[] currentSupplyKey = Helper.toByteArray((byte) 5);
    private static final byte[] isPausedKey = Helper.toByteArray((byte) 6);
    private static final byte[] oracleEndpointKey = Helper.toByteArray((byte) 9);
    private static final byte[] writerOfKey = Helper.toByteArray((byte) 10);
    private static final byte[] minStakeKey = Helper.toByteArray((byte) 11);
    private static final byte[] protocolFeeKey = Helper.toByteArray((byte) 12);
    private static final byte[] rentfuseScriptHashKey = Helper.toByteArray((byte) 13);

    // STORAGE MAPS
    private static final StorageMap tokens = new StorageMap(ctx, Helper.toByteArray((byte) 101));
    private static final StorageMap balances = new StorageMap(ctx, Helper.toByteArray((byte) 102));
    private static final StorageMap ownerOfMap = new StorageMap(ctx, Helper.toByteArray((byte) 103));
    private static final StorageMap immutableTokenProperties = new StorageMap(ctx, (byte) 104);
    private static final StorageMap earnings = new StorageMap(ctx, (byte) 105);
    private static final StorageMap writerOfMap = new StorageMap(ctx, (byte) 106);
    private static final StorageMap exercised = new StorageMap(ctx, (byte) 107);
    private static final StorageMap stakes = new StorageMap(ctx, (byte) 109);

    @io.neow3j.devpack.annotations.Struct
    static class Request {
        public int type;
        public int strike;
        public int vdot; // value decline over time in ms
        public int value;
        public int vi;
        public boolean safe;
        // Rentfuse properties
        public int paymentTokenAmount;
        int minDuration;
        int maxDuration;
        int collateral;
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
        mint(from, req, stake);
        onPayment.fire(from, amount, data);
    }

    /* READ ONLY */

    @Safe
    public static Hash160 contractOwner() {
        return new Hash160(Storage.get(ctx.asReadOnly(), ownerkey));
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
        return Storage.getIntOrZero(ctx.asReadOnly(), currentSupplyKey);
    }

    @Safe
    public static boolean isPaused() {
        return Storage.getInt(ctx.asReadOnly(), isPausedKey) == 1;
    }

    @Safe
    public static Iterator<ByteString> tokensOf(Hash160 owner) {
        return (Iterator<ByteString>) Storage.find(ctx.asReadOnly(), createStorageMapPrefix(owner, tokensOfKey),
                (byte) (FindOptions.KeysOnly | FindOptions.RemovePrefix));
    }

    @Safe
    public static Iterator<ByteString> tokensOfWriter(Hash160 owner) {
        return (Iterator<ByteString>) Storage.find(ctx.asReadOnly(), createStorageMapPrefix(owner, writerOfKey),
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
    public static List<String> tokensOfWriterJson(Hash160 owner) throws Exception {
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
    public static Hash160 writerOf(ByteString tokenId) {
        ByteString owner = writerOfMap.get(tokenId);
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
        attributes.add(getAttributeMap(STAKE, stakeOf(tokenId)));
        attributes.add(getAttributeMap(STRIKE, tokenProps.strike));
        attributes.add(getAttributeMap(WRITER, tokenProps.writer));
        attributes.add(getAttributeMap(OWNER, ownerOf(tokenProps.tokenId)));
        attributes.add(getAttributeMap(VDOT, tokenProps.vdot));
        attributes.add(getAttributeMap(CREATED, tokenProps.created));
        attributes.add(getAttributeMap(EXERCISED, exercised(tokenId)));
        attributes.add(getAttributeMap(REAL_VALUE,
                determineValue(stakeOf(tokenId), tokenProps, 0)));
        attributes.add(getAttributeMap(START_VALUE, tokenProps.value));
        attributes.add(getAttributeMap(VI, tokenProps.vi));
        attributes.add(getAttributeMap(SAFE, tokenProps.safe));

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
        attributes.add(getAttributeMap(STAKE, stakeOf(tokenId)));
        attributes.add(getAttributeMap(STRIKE, tokenProps.strike));
        attributes.add(getAttributeMap(WRITER, StdLib.base64Encode(tokenProps.writer)));
        attributes.add(getAttributeMap(OWNER, StdLib.base64Encode(ownerOf(tokenProps.tokenId).toByteString())));
        attributes.add(getAttributeMap(VDOT, tokenProps.vdot));
        attributes.add(getAttributeMap(CREATED, tokenProps.created));
        attributes.add(getAttributeMap(EXERCISED, exercised(tokenId)));
        attributes.add(getAttributeMap(REAL_VALUE,
                determineValue(stakeOf(tokenId), tokenProps, 0)));
        attributes.add(getAttributeMap(START_VALUE, tokenProps.value));
        attributes.add(getAttributeMap(VI, tokenProps.vi));
        attributes.add(getAttributeMap(SAFE, tokenProps.safe));

        p.put(ATTRIBUTES, attributes);

        return StdLib.jsonSerialize(p);
    }

    @Safe
    public static String oracleEndpoint() {
        return Storage.getString(ctx.asReadOnly(), oracleEndpointKey);
    }

    @Safe
    public static int earningsOf(Hash160 account) {
        return earnings.getIntOrZero(account.toByteArray());
    }

    // TODO: remove and handle this with a backend, block parser, db
    @Safe
    public static String earnings() {
        List<String> earningList = new List<>();
        Iterator<Struct<ByteString, ByteString>> list = earnings.find(FindOptions.RemovePrefix);
        while (list.next()) {
            Map<String, Integer> earningsForAddress = new Map<>();
            String address = StdLib.base64Encode(list.get().key);
            earningsForAddress.put(address, earningsOf(new Hash160(list.get().key)));
            earningList.add(StdLib.jsonSerialize(earningsForAddress));
        }
        return StdLib.jsonSerialize(earningList);
    }

    @Safe
    public static int minStake() {
        return Storage.getInt(ctx.asReadOnly(), minStakeKey);
    }

    @Safe
    public static int protocolFee() {
        return Storage.getInt(ctx.asReadOnly(), protocolFeeKey);
    }

    @Safe
    public static boolean exercised(ByteString tokenId) {
        Boolean result = exercised.getBoolean(tokenId);
        return result != null ? result : false;
    }

    @Safe
    public static int stakeOf(ByteString tokenId) {
        return stakes.getInt(tokenId);
    }

    @Safe
    public static Hash160 rentfuseContract() {
        return new Hash160(Storage.get(ctx.asReadOnly(), rentfuseScriptHashKey));
    }

    /* READ & WRITE */

    public static void cancelListing(ByteString tokenId) throws Exception {
        int listingId = (int) Contract.call(rentfuseContract(), "getListingIdFromNft", CallFlags.ReadOnly,
                new Object[] { Runtime.getExecutingScriptHash(), tokenId });
        Contract.call(rentfuseContract(), "closeListing", CallFlags.All, new Object[] { listingId });
        burn(tokenId);
    }

    private static void burn(ByteString tokenId) throws Exception {
        assert (Runtime.getInvocationCounter() == 1);
        Hash160 owner = ownerOf(tokenId);
        Hash160 writer = writerOf(tokenId);

        if (owner != writer) {
            throw new Exception("burn_ownerNotWriter");
        }

        if (!Runtime.checkWitness(owner)) {
            throw new Exception("burn_noAuth");
        }

        ownerOfMap.delete(tokenId);
        writerOfMap.delete(tokenId);
        new StorageMap(ctx, createStorageMapPrefix(owner, tokensOfKey)).delete(tokenId);
        new StorageMap(ctx, createStorageMapPrefix(owner, writerOfKey)).delete(tokenId);
        immutableTokenProperties.delete(tokenId);
        tokens.delete(tokenId);
        int transferAmount = stakeOf(tokenId);
        safeTransfer(Runtime.getExecutingScriptHash(), candyContract(), writer,
                transferAmount);
        if (exercised(tokenId)) {
            increaseEarnings(owner, transferAmount);
        }
        exercised.delete(tokenId);
        stakes.delete(tokenId);
        onBurn.fire(writer, tokenId, transferAmount);

    }

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

    public static void exercise(ByteString tokenId) throws Exception {
        Hash160 owner = new Hash160(ownerOfMap.get(tokenId));
        Hash160 writer = new Hash160(writerOfMap.get(tokenId));
        if (writer == owner) {
            throw new Exception("exercise_writerCantExercise");
        }
        if (!Runtime.checkWitness(owner)) {
            throw new Exception("exercise_noAuth");
        }
        if (exercised(tokenId)) {
            throw new Exception("exercise_alreadyExercised");
        }
        ByteString properties = immutableTokenProperties.get(tokenId);
        if (properties == null) {
            throw new Exception("exercise_tokenDoesNotExist");
        }
        oracle("oracleResponse", tokenId);
    }

    public static void oracleResponse(String url, Object userData, int responseCode, ByteString response)
            throws Exception {

        assert (Runtime.getInvocationCounter() == 1);
        if (responseCode != ORACLE_SUCCESS_RESPONSE_CODE) {
            throw new Exception("oracleExercise_noSuccessResponse");
        }
        if (Runtime.getCallingScriptHash() != OracleContract.getHash()) {
            throw new Exception("exerciseCall_onlyOracle");
        }
        ByteString tokenId = (ByteString) userData;
        if (exercised(tokenId)) {
            throw new Exception("oracleExercise_alreadyExercised");
        }
        TokenProperties properties = (TokenProperties) StdLib.deserialize(immutableTokenProperties.get(tokenId));
        if (properties == null) {
            throw new Exception("oracleExercise_tokenDoesNotExist");
        }
        Map<String, Object> result = (Map<String, Object>) StdLib
                .jsonDeserialize(response.toString());

        int oraclePrice = decimalStringToInt((String) result.get("price"));
        if (!canExercise(oraclePrice, properties.strike, properties.type,
                properties.safe)) {
            throw new Exception("oracleExercise_cantExercise");
        }
        int stake = stakeOf(tokenId);
        Hash160 owner = new Hash160(ownerOfMap.get(tokenId));

        int transferAmount = determineValue(stake, properties, oraclePrice);
        if (transferAmount <= 0) {
            throw new Exception("oracleExercise_zeroValue");
        }
        if (stake - transferAmount < 0) {
            throw new Exception("oracleExercise_stakeValueMismatch");
        }

        exercised.put(tokenId, 1);
        stakes.put(tokenId, stake - transferAmount);

        safeTransfer(Runtime.getExecutingScriptHash(), candyContract(), owner,
                transferAmount);

        increaseEarnings(owner, transferAmount);

        onExercise.fire(owner, tokenId, properties.type, transferAmount,
                properties.strike);

    }

    /* UTIL */

    private static void listOnRentfuse(ByteString tokenId, Request data) throws Exception {
        onDebug.fire(data.paymentTokenAmount, data.minDuration, data.maxDuration, data.collateral);
        Object[] listingData = new Object[] { 1, GasToken.getHash(),
                data.paymentTokenAmount,
                data.minDuration, data.maxDuration, data.collateral, false, 0 };
        boolean result = transfer(rentfuseContract(), tokenId, listingData);
        if (!result) {
            throw new Exception("listOnRentfuse_nep11TransferFail");
        }

    }

    private static boolean canExercise(int oraclePrice, int strike, int type, boolean safe) {
        return !safe || type == TYPE_CALL && oraclePrice > strike || type == TYPE_PUT && oraclePrice < strike;
    }

    private static int determineValue(int stake, TokenProperties props, int oraclePrice) {
        int timeDelta = Runtime.getTime() - props.created;
        int priceDelta = oraclePrice != 0 ? oraclePrice - props.strike : 0;
        int result = props.value - (props.vdot * timeDelta) + (priceDelta * props.vi);
        return result < 0 ? 0 : (result > stake) ? stake : result;
    }

    private static byte[] createStorageMapPrefix(Hash160 owner, byte[] prefix) {
        return Helper.concat(prefix, owner.toByteArray());
    }

    private static void mint(Hash160 writer, Request data, int stake) throws Exception {
        if (data.strike <= 0) {
            throw new Exception("mint_invalidStrike");
        }
        if (stake <= 0) {
            throw new Exception("mint_invalidStake");
        }
        if (data.vdot < 0) {
            throw new Exception("mint_invalidVdot");
        }
        if (data.type != TYPE_CALL && data.type != TYPE_PUT) {
            throw new Exception("mint_invalidType");
        }
        if (data.value < 0 || data.value > stake) {
            throw new Exception("mint_invalidValue");
        }
        if (data.vi < 0) {
            throw new Exception("mint_invalidVi");
        }

        // TODO: check rentfuse values?

        ByteString tokenId = nextTokenId();
        Map<String, Object> properties = new Map<>();
        properties.put(TOKEN_ID, tokenId);
        if (data.type == TYPE_CALL) {
            properties.put(NAME, "Candefi Call NFT");
        } else {
            properties.put(NAME, "Candefi Put NFT");
        }
        properties.put(DESC, "Candefi NFT collection");
        properties.put(TOKEN_URI, "");
        properties.put(IMAGE, getImageBaseURI());
        properties.put(STAKE, stake);
        properties.put(REAL_VALUE, data.value);
        properties.put(STRIKE, data.strike);
        properties.put(TYPE, data.type);
        properties.put(WRITER, writer.toByteString());
        properties.put(VDOT, data.vdot);
        properties.put(CREATED, Runtime.getTime());
        properties.put(VI, data.vi);
        properties.put(SAFE, data.safe);

        incrementCurrentSupplyByOne();
        saveProperties(properties, tokenId);
        tokens.put(tokenId, 1);
        ownerOfMap.put(tokenId, writer);
        writerOfMap.put(tokenId, writer);
        new StorageMap(ctx, createStorageMapPrefix(writer, tokensOfKey)).put(tokenId, 1);
        new StorageMap(ctx, createStorageMapPrefix(writer, writerOfKey)).put(tokenId, 1);
        incrementBalanceByOne(writer);
        listOnRentfuse(tokenId, data);
        onOptionMinted.fire(writer, tokenId, data.strike, stake, data.vdot, data.type, data.vi);
    }

    private static void increaseEarnings(Hash160 account, int amount) {
        int earned = earningsOf(account);
        earnings.put(account.toByteArray(), amount + earned);
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
        // TODO: refactor, make more generic
        String endpoint = Storage.getString(ctx, oracleEndpointKey) + "NEOUSDT";
        OracleContract.request(endpoint, null, callback, userData, 20000000);
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
        if (!properties.containsKey(VDOT)) {
            throw new Exception("saveProperties_missingVdot");
        }
        if (!properties.containsKey(CREATED)) {
            throw new Exception("saveProperties_missingCreated");
        }
        if (!properties.containsKey(REAL_VALUE)) {
            throw new Exception("saveProperties_missingValue");
        }
        if (!properties.containsKey(VI)) {
            throw new Exception("saveProperties_missingVi");
        }
        if (!properties.containsKey(SAFE)) {
            throw new Exception("saveProperties_missingSafe");
        }

        String name = (String) properties.get(NAME);
        String desc = (String) properties.get(DESC);
        String img = (String) properties.get(IMAGE);
        String uri = (String) properties.get(TOKEN_URI);
        int strike = (int) properties.get(STRIKE);
        int stake = (int) properties.get(STAKE);
        int type = (int) properties.get(TYPE);
        int vdot = (int) properties.get(VDOT);
        int created = (int) properties.get(CREATED);
        ByteString writer = (ByteString) properties.get(WRITER);
        int value = (int) properties.get(REAL_VALUE);
        int vi = (int) properties.get(VI);
        boolean safe = (boolean) properties.get(SAFE);

        TokenProperties tokenProps = new TokenProperties(tokenId, name, img, desc, uri, strike, type, writer,
                vdot, created, value, vi, safe);
        immutableTokenProperties.put(tokenId, StdLib.serialize(tokenProps));
        stakes.put(tokenId, stake);
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
        return "ipfs://QmPRxppAdyiPPr9QCL7VS3h2V9XUpLsp8o4U2E64bJAqg7";
        // return Storage.getString(ctx, imageBaseUriKey);
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
        if (minStake < 5000) {
            throw new Exception("updateMinStake_invalidAmount");
        }
        Storage.put(ctx, minStakeKey, minStake);
    }

    public static void updateProtocolFee(int fee) throws Exception {
        onlyOwner();
        if (fee < 0) {
            throw new Exception("updateProtocolFee_invalidAmount");
        }
        Storage.put(ctx, protocolFeeKey, fee);
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

            Hash160 rentfuse = (Hash160) arr[7];
            if (!Hash160.isValid(rentfuse)) {
                throw new Exception("deploy_invalidRentfuseHash");
            }
            Storage.put(ctx, rentfuseScriptHashKey, rentfuse);
        }
    }

    public static void update(ByteString script, String manifest) throws Exception {
        onlyOwner();
        if (script.length() == 0 && manifest.length() == 0) {
            throw new Exception("update_contractAndManifestEmpty");
        }
        ContractManagement.update(script, manifest);
    }

    @io.neow3j.devpack.annotations.Struct
    static class TokenProperties {

        public ByteString tokenId;
        public String name;
        public String image;
        public String description;
        public String tokenUri;
        public int strike;
        public int type;
        public ByteString writer;
        public int vdot;
        public int created;
        public int value;
        public int vi;
        public boolean safe;

        // add getters

        public TokenProperties(ByteString tokenId, String name, String image, String description, String tokenUri,
                int strike, int type, ByteString writer, int vdot, int created, int value, int vi, boolean safe) {
            this.tokenId = tokenId;
            this.name = name;
            this.image = image;
            this.description = description;
            this.tokenUri = tokenUri;
            this.strike = strike;
            this.type = type;
            this.writer = writer;
            this.vdot = vdot;
            this.created = created;
            this.value = value;
            this.vi = vi;
            this.safe = safe;
        }
    }

}