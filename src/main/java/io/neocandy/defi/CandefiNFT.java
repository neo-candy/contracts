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
import io.neow3j.devpack.annotations.ContractSourceCode;
import io.neow3j.devpack.annotations.DisplayName;
import io.neow3j.devpack.annotations.ManifestExtra;
import io.neow3j.devpack.annotations.Permission;
import io.neow3j.devpack.annotations.OnDeployment;
import io.neow3j.devpack.annotations.OnNEP17Payment;
import io.neow3j.devpack.annotations.Safe;
import io.neow3j.devpack.annotations.SupportedStandard;
import io.neow3j.devpack.constants.CallFlags;
import io.neow3j.devpack.constants.FindOptions;
import io.neow3j.devpack.constants.NeoStandard;
import io.neow3j.devpack.contracts.ContractManagement;
import io.neow3j.devpack.contracts.GasToken;
import io.neow3j.devpack.contracts.OracleContract;
import io.neow3j.devpack.contracts.StdLib;
import io.neow3j.devpack.events.Event1Arg;
import io.neow3j.devpack.events.Event3Args;
import io.neow3j.devpack.events.Event4Args;
import io.neow3j.devpack.events.Event5Args;
import io.neow3j.devpack.events.Event7Args;

@ManifestExtra(key = "Author", value = "NeoCandy")
@ManifestExtra(key = "Description", value = "CandeFi NFT Collection")
@ManifestExtra(key = "Email", value = "hello@neocandy.io")
@ManifestExtra(key = "Website", value = "https://neocandy.io")
@Permission(contract = "*", methods = "*")
@SupportedStandard(neoStandard = NeoStandard.NEP_11)
@ContractSourceCode("https://github.com/neo-candy/contracts")
@DisplayName("CandefiNFT")
@SuppressWarnings("unchecked")
public class CandefiNFT {

    // EVENTS

    @DisplayName("Transfer")
    private static Event4Args<Hash160, Hash160, Integer, ByteString> onTransfer;

    @DisplayName("Payment")
    private static Event3Args<Hash160, Integer, Object> onPayment;

    @DisplayName("Mint")
    private static Event7Args<Hash160, ByteString, Integer, Integer, Integer, Integer, Integer> onMint;

    @DisplayName("Exercise")
    private static Event5Args<Hash160, ByteString, Integer, Integer, Integer> onExercise;

    @DisplayName("Burn")
    private static Event3Args<Hash160, ByteString, Integer> onBurn;

    @DisplayName("Debug")
    private static Event4Args<Object, Object, Object, Object> onDebug;

    @DisplayName("Error")
    private static Event1Arg<String> onError;

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
    private static final String DEPRECIATION = "Depreciation";
    private static final String VOLATILITY = "Volatility";
    private static final String CREATED = "Created";
    private static final String EXERCISED = "Exercised";
    private static final String VALUE = "Value";
    private static final String SAFE = "Safe";
    private static final String RENTING_START = "Renting Start";

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
    private static final byte[] ownerkey = Helper.toByteArray((byte) 5);
    private static final byte[] candyContractHashKey = Helper.toByteArray((byte) 6);
    private static final byte[] tokensOfKey = Helper.toByteArray((byte) 7);
    private static final byte[] imageBaseUriKey = Helper.toByteArray((byte) 8);
    private static final byte[] currentSupplyKey = Helper.toByteArray((byte) 9);
    private static final byte[] isPausedKey = Helper.toByteArray((byte) 10);
    private static final byte[] oracleEndpointKey = Helper.toByteArray((byte) 11);
    private static final byte[] writerOfKey = Helper.toByteArray((byte) 12);
    private static final byte[] candyMinStakeKey = Helper.toByteArray((byte) 13);
    private static final byte[] candyProtocolFeeKey = Helper.toByteArray((byte) 14);
    private static final byte[] rentfuseScriptHashKey = Helper.toByteArray((byte) 15);
    private static final byte[] totalSupplyKey = Helper.toByteArray((byte) 16);

    // STORAGE MAPS
    private static final StorageMap tokens = new StorageMap(ctx, Helper.toByteArray((byte) 101));
    private static final StorageMap balances = new StorageMap(ctx, Helper.toByteArray((byte) 102));
    private static final StorageMap ownerOfMap = new StorageMap(ctx, Helper.toByteArray((byte) 103));
    private static final StorageMap immutableTokenProperties = new StorageMap(ctx, (byte) 104);
    private static final StorageMap earnings = new StorageMap(ctx, (byte) 105);
    private static final StorageMap writerOfMap = new StorageMap(ctx, (byte) 106);
    private static final StorageMap exercised = new StorageMap(ctx, (byte) 107);
    private static final StorageMap stakes = new StorageMap(ctx, (byte) 109);
    private static final StorageMap rentingStartedAt = new StorageMap(ctx, (byte) 110);

    public static void onNEP11RentingStarted(Hash160 lender, Hash160 borrower, ByteString rentingId, ByteString tokenId,
            Object[] data) {
        onlyRentfuse();
        rentingStartedAt.put(tokenId, Runtime.getTime());
    }

    public static void onNEP11RentingFinished(Hash160 lender, Hash160 borrower, ByteString rentingId,
            ByteString tokenId, Object[] data) {
        onlyRentfuse();
        // burn(tokenId);
    }

    public static void onNEP11RentingRevoked(Hash160 lender, Hash160 borrower, ByteString rentingId, ByteString tokenId,
            Object[] data) {
        onlyRentfuse();
        // burn(tokenId);
    }

    @OnNEP17Payment
    public static void onPayment(Hash160 from, int amount, Object data) throws Exception {
        if (isPaused()) {
            fireErrorAndAbort("onPayment_isPaused");
        }
        if (amount <= 0) {
            fireErrorAndAbort("onPayment_invalidAmount");
        }
        Hash160 token = Runtime.getCallingScriptHash();

        if (token != candyContract()) {
            fireErrorAndAbort("onPayment_onlyCandy");
        }
        if (data == null) {
            fireErrorAndAbort("onPayment_dataMissing");
        }
        MintRequest req = (MintRequest) data;
        if (amount - protocolFee() < minStake()) {
            fireErrorAndAbort("onPayment_invalidMinStake");
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
    public static int totalSupply() {
        return Storage.getInt(ctx.asReadOnly(), totalSupplyKey);
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
    public static Iterator<ByteString> tokensOfWriter(Hash160 writer) throws Exception {
        if (!Hash160.isValid(writer)) {
            throw new Exception("tokensOf_invalidHash");
        }
        return (Iterator<ByteString>) Storage.find(ctx.asReadOnly(), createStorageMapPrefix(writer, writerOfKey),
                (byte) (FindOptions.KeysOnly | FindOptions.RemovePrefix));
    }

    @Safe
    public static List<String> tokensOfJson(Hash160 owner) throws Exception {
        if (!Hash160.isValid(owner)) {
            throw new Exception("tokensOfJson_invalidHash");
        }
        Iterator<Struct<ByteString, ByteString>> iterator = (Iterator<Struct<ByteString, ByteString>>) Storage.find(
                ctx.asReadOnly(),
                createStorageMapPrefix(owner, tokensOfKey),
                FindOptions.RemovePrefix);
        List<String> tokens = new List<>();
        while (iterator.next()) {
            ByteString result = (ByteString) iterator.get().key;
            tokens.add(propertiesJson(result));
        }
        return tokens;
    }

    @Safe
    public static List<String> tokensOfWriterJson(Hash160 writer) throws Exception {
        if (!Hash160.isValid(writer)) {
            throw new Exception("tokensOfWriterJson_invalidHash");
        }
        Iterator<Struct<ByteString, ByteString>> iterator = (Iterator<Struct<ByteString, ByteString>>) Storage.find(
                ctx.asReadOnly(),
                createStorageMapPrefix(writer, writerOfKey),
                FindOptions.RemovePrefix);
        List<String> tokens = new List<>();
        while (iterator.next()) {
            ByteString result = (ByteString) iterator.get().key;
            tokens.add(propertiesJson(result));
        }
        return tokens;
    }

    @Safe
    public static Hash160 ownerOf(ByteString tokenId) throws Exception {
        if (!isValidTokenId(tokenId)) {
            throw new Exception("ownerOf_invalidTokenId");
        }
        ByteString owner = ownerOfMap.get(tokenId);
        if (owner == null) {
            return null;
        }
        return new Hash160(owner);
    }

    @Safe
    public static Hash160 writerOf(ByteString tokenId) throws Exception {
        if (!isValidTokenId(tokenId)) {
            throw new Exception("writerOf_invalidTokenId");
        }
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
        if (!isValidTokenId(tokenId)) {
            throw new Exception("properties_invalidTokenId");
        }
        TokenProperties tokenProps = (TokenProperties) StdLib
                .deserialize(immutableTokenProperties.get(tokenId));
        Map<String, Object> p = new Map<>();

        if (tokenProps == null) {
            throw new Exception("properties_tokenDoesNotExist");
        }
        p.put(TOKEN_ID, tokenProps.getTokenId());
        p.put(NAME, tokenProps.getName());
        p.put(DESC, tokenProps.getDescription());
        p.put(IMAGE, tokenProps.getImage());
        p.put(TOKEN_URI, tokenProps.getTokenUri());

        Map<String, Object> properties = new Map<>();
        properties.put(PROPERTY_HAS_LOCKED, false);
        properties.put(PROPERTY_TYPE, PROPERTY_GAME_TYPE);
        p.put(PROPERTIES, properties);

        List<Map<String, Object>> attributes = new List<>();
        attributes.add(getAttributeMap(TYPE, tokenProps.getType()));
        attributes.add(getAttributeMap(STAKE, stakeOf(tokenId)));
        attributes.add(getAttributeMap(STRIKE, tokenProps.getStrike()));
        attributes.add(getAttributeMap(WRITER, tokenProps.getWriter()));
        attributes.add(getAttributeMap(OWNER, ownerOf(tokenProps.getTokenId())));
        attributes.add(getAttributeMap(DEPRECIATION, tokenProps.getDepreciation()));
        attributes.add(getAttributeMap(CREATED, tokenProps.getCreated()));
        attributes.add(getAttributeMap(EXERCISED, isExercised(tokenId)));
        attributes.add(getAttributeMap(VALUE, tokenProps.getValue()));
        attributes.add(getAttributeMap(VOLATILITY, tokenProps.getVolatility()));
        attributes.add(getAttributeMap(SAFE, tokenProps.isSafe()));
        attributes.add(getAttributeMap(RENTING_START, rentingStartedAt.getInt(tokenId)));

        p.put(ATTRIBUTES, attributes);

        return p;
    }

    @Safe
    public static String propertiesJson(ByteString tokenId) throws Exception {
        if (!isValidTokenId(tokenId)) {
            throw new Exception("propertiesJson_invalidTokenId");
        }
        TokenProperties tokenProps = (TokenProperties) StdLib
                .deserialize(immutableTokenProperties.get(tokenId));
        Map<String, Object> p = new Map<>();

        if (tokenProps == null) {
            throw new Exception("propertiesJson_tokenDoesNotExist");
        }
        p.put(TOKEN_ID, tokenProps.getTokenId());
        p.put(NAME, tokenProps.getName());
        p.put(DESC, tokenProps.getDescription());
        p.put(IMAGE, tokenProps.getImage());
        p.put(TOKEN_URI, tokenProps.getTokenUri());

        Map<String, Object> properties = new Map<>();
        properties.put(PROPERTY_HAS_LOCKED, false);
        properties.put(PROPERTY_TYPE, PROPERTY_GAME_TYPE);
        p.put(PROPERTIES, properties);

        List<Map<String, Object>> attributes = new List<>();
        attributes.add(getAttributeMap(TYPE, tokenProps.getType()));
        attributes.add(getAttributeMap(STAKE, stakeOf(tokenId)));
        attributes.add(getAttributeMap(STRIKE, tokenProps.getStrike()));
        attributes.add(getAttributeMap(WRITER, StdLib.base64Encode(tokenProps.getWriter())));
        attributes.add(getAttributeMap(OWNER, StdLib.base64Encode(ownerOf(tokenProps.getTokenId()).toByteString())));
        attributes.add(getAttributeMap(DEPRECIATION, tokenProps.getDepreciation()));
        attributes.add(getAttributeMap(CREATED, tokenProps.getCreated()));
        attributes.add(getAttributeMap(EXERCISED, isExercised(tokenId)));
        attributes.add(getAttributeMap(VALUE, tokenProps.getValue()));
        attributes.add(getAttributeMap(VOLATILITY, tokenProps.getVolatility()));
        attributes.add(getAttributeMap(SAFE, tokenProps.isSafe()));
        attributes.add(getAttributeMap(RENTING_START, rentingStartedAt.getInt(tokenId)));

        p.put(ATTRIBUTES, attributes);

        return StdLib.jsonSerialize(p);
    }

    @Safe
    public static String oracleEndpoint() {
        return Storage.getString(ctx.asReadOnly(), oracleEndpointKey);
    }

    @Safe
    public static int earningsOf(Hash160 account) throws Exception {
        if (!Hash160.isValid(account)) {
            throw new Exception("earningsOf_invalidHash");
        }
        return earnings.getIntOrZero(account.toByteArray());
    }

    // TODO: remove this method, because of limitations its better to handle it
    // off-chain
    @Safe
    public static String earnings() throws Exception {
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
        return Storage.getInt(ctx.asReadOnly(), candyMinStakeKey);
    }

    @Safe
    public static int protocolFee() {
        return Storage.getInt(ctx.asReadOnly(), candyProtocolFeeKey);
    }

    @Safe
    public static boolean isExercised(ByteString tokenId) throws Exception {
        if (!isValidTokenId(tokenId)) {
            throw new Exception("isExercised_invalidTokenId");
        }
        Boolean result = exercised.getBoolean(tokenId);
        return result != null ? result : false;
    }

    @Safe
    public static int stakeOf(ByteString tokenId) throws Exception {
        if (!isValidTokenId(tokenId)) {
            throw new Exception("stakeOf_invalidTokenId");
        }
        return stakes.getIntOrZero(tokenId);
    }

    @Safe
    public static Hash160 rentfuseContract() {
        return new Hash160(Storage.get(ctx.asReadOnly(), rentfuseScriptHashKey));
    }

    /* READ & WRITE */

    public static void closeListing(ByteString tokenId) throws Exception {
        if (isPaused()) {
            fireErrorAndAbort("closeListing_isPaused");
        }
        if (!isValidTokenId(tokenId)) {
            fireErrorAndAbort("closeListing_invalidTokenId");
        }
        if (tokens.get(tokenId) == null) {
            fireErrorAndAbort("closeListing_tokenDoesNotExist");
        }

        int rentfuseListingId = (int) Contract.call(rentfuseContract(), "getListingIdFromNft", CallFlags.ReadOnly,
                new Object[] { Runtime.getExecutingScriptHash(), tokenId });
        if (rentfuseListingId == 0) {
            fireErrorAndAbort("closeListing_listingNotFound");
        }
        Contract.call(rentfuseContract(), "closeListing", CallFlags.All, new Object[] { rentfuseListingId });
        burn(tokenId);
    }

    public static boolean transfer(Hash160 to, ByteString tokenId, Object data) throws Exception {
        if (!Hash160.isValid(to) || Hash160.zero() == to) {
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
        if (!isValidTokenId(tokenId)) {
            fireErrorAndAbort("exercise_invalidTokenId");
        }
        if (tokens.get(tokenId) == null) {
            fireErrorAndAbort("exercise_tokenDoesNotExist");
        }
        Hash160 owner = new Hash160(ownerOfMap.get(tokenId));
        Hash160 writer = new Hash160(writerOfMap.get(tokenId));
        if (writer == owner) {
            fireErrorAndAbort("exercise_writerCantExercise");
        }
        if (!Runtime.checkWitness(owner)) {
            fireErrorAndAbort("exercise_noAuth");
        }
        if (isExercised(tokenId)) {
            fireErrorAndAbort("exercise_isExercised");
        }
        oracle("oracleResponse", tokenId);
    }

    public static void oracleResponse(String url, Object userData, int responseCode, ByteString response)
            throws Exception {
        assert (Runtime.getInvocationCounter() == 1);
        if (responseCode != ORACLE_SUCCESS_RESPONSE_CODE) {
            fireErrorAndAbort("oracleResponse_noSuccessResponse");
        }
        if (Runtime.getCallingScriptHash() != OracleContract.getHash()) {
            fireErrorAndAbort("oracleResponse_onlyOracle");
        }
        ByteString tokenId = (ByteString) userData;

        if (isExercised(tokenId)) {
            fireErrorAndAbort("oracleResponse_alreadyExercised");
        }
        TokenProperties properties = (TokenProperties) StdLib.deserialize(immutableTokenProperties.get(tokenId));
        if (properties == null) {
            fireErrorAndAbort("oracleResponse_tokenDoesNotExist");
        }
        Map<String, Object> result = (Map<String, Object>) StdLib
                .jsonDeserialize(response.toString());

        int oraclePrice = decimalStringToInt((String) result.get("price"));
        if (!canExercise(oraclePrice, properties.getStrike(), properties.getType(),
                properties.isSafe())) {
            fireErrorAndAbort("oracleResponse_cantExercise");
        }
        int stake = stakeOf(tokenId);
        Hash160 owner = new Hash160(ownerOfMap.get(tokenId));
        int rentingStarted = rentingStartedAt.getInt(tokenId);
        int transferAmount = determineValue(stake, properties, oraclePrice, rentingStarted);
        if (transferAmount <= 0) {
            fireErrorAndAbort("oracleResponse_zeroValue");
        }
        if (stake - transferAmount < 0) {
            fireErrorAndAbort("oracleResponse_stakeValueMismatch");
        }
        exercised.put(tokenId, 1);
        stakes.put(tokenId, stake - transferAmount);

        safeTransfer(Runtime.getExecutingScriptHash(), candyContract(), owner,
                transferAmount);

        increaseEarnings(owner, transferAmount);

        onExercise.fire(owner, tokenId, properties.getType(), transferAmount,
                properties.getStrike());
    }

    /* UTIL */

    private static void burn(ByteString tokenId) throws Exception {
        assert (Runtime.getInvocationCounter() == 1);
        Hash160 owner = ownerOf(tokenId);
        Hash160 writer = writerOf(tokenId);

        if (owner != writer) {
            fireErrorAndAbort("burn_ownerNotWriter");
        }

        if (!Runtime.checkWitness(owner)) {
            fireErrorAndAbort("burn_noAuth");
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
        if (isExercised(tokenId)) {
            increaseEarnings(owner, transferAmount);
        }
        exercised.delete(tokenId);
        stakes.delete(tokenId);
        onBurn.fire(writer, tokenId, transferAmount);
        onTransfer.fire(writer, null, 1, tokenId);
    }

    private static boolean isValidTokenId(ByteString tokenId) {
        return tokenId.length() < 65;
    }

    private static void listOnRentfuse(ByteString tokenId, MintRequest data) throws Exception {
        onDebug.fire(data.paymentTokenAmount, data.minDurationInMinutes, data.maxDurationInMinutes, data.collateral);
        Object[] listingData = new Object[] { 1, GasToken.getHash(),
                data.paymentTokenAmount,
                data.minDurationInMinutes, data.maxDurationInMinutes, data.collateral, false, 0 };
        boolean result = transfer(rentfuseContract(), tokenId, listingData);
        if (!result) {
            fireErrorAndAbort("listOnRentfuse_nep11TransferFail");
        }
    }

    private static boolean canExercise(int oraclePrice, int strike, int type, boolean safe) {
        if (oraclePrice <= 0) {
            fireErrorAndAbort("canExercise_invalidOraclePrice");
        }
        return !safe || type == TYPE_CALL && oraclePrice > strike || type == TYPE_PUT && oraclePrice < strike;
    }

    private static int determineValue(int stake, TokenProperties props, int oraclePrice, int rentingStarted) {
        if (stake <= 0 || oraclePrice <= 0 || rentingStarted <= 0) {
            fireErrorAndAbort("determineValue_invalidValues");
        }
        int timeDelta = Runtime.getTime() - rentingStarted;
        int priceDelta = oraclePrice != 0 ? oraclePrice - props.getStrike() : 0;
        int result = props.getValue() - (props.getDepreciation() * timeDelta) + (priceDelta * props.getVolatility());
        return result < 0 ? 0 : (result > stake) ? stake : result;
    }

    private static byte[] createStorageMapPrefix(Hash160 owner, byte[] prefix) {
        return Helper.concat(prefix, owner.toByteArray());
    }

    private static void mint(Hash160 writer, MintRequest data, int stake) throws Exception {
        if (data.strike <= 0) {
            fireErrorAndAbort("mint_invalidStrike");
        }
        if (stake <= 0) {
            fireErrorAndAbort("mint_invalidStake");
        }
        if (data.depreciation < 0) {
            fireErrorAndAbort("mint_invalidVdot");
        }
        if (data.type != TYPE_CALL && data.type != TYPE_PUT) {
            fireErrorAndAbort("mint_invalidType");
        }
        if (data.value < 0 || data.value > stake) {
            fireErrorAndAbort("mint_invalidValue");
        }
        if (data.volatility < 0) {
            fireErrorAndAbort("mint_invalidVi");
        }

        // TODO: check rentfuse values?

        ByteString tokenId = nextTokenId();
        Map<String, Object> properties = new Map<>();
        properties.put(TOKEN_ID, tokenId);
        if (data.type == TYPE_CALL) {
            properties.put(NAME, "Candefi Call");
        } else {
            properties.put(NAME, "Candefi Put");
        }
        properties.put(DESC, "Candefi NFT collection");
        properties.put(TOKEN_URI, "");
        properties.put(IMAGE, getImageBaseURI());
        properties.put(STAKE, stake);
        properties.put(VALUE, data.value);
        properties.put(STRIKE, data.strike);
        properties.put(TYPE, data.type);
        properties.put(WRITER, writer.toByteString());
        properties.put(DEPRECIATION, data.depreciation);
        properties.put(CREATED, Runtime.getTime());
        properties.put(VOLATILITY, data.volatility);
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
        onMint.fire(writer, tokenId, data.strike, stake, data.depreciation, data.type, data.volatility);
        onTransfer.fire(null, writer, 1, tokenId);
    }

    private static void increaseEarnings(Hash160 account, int amount) throws Exception {
        if (amount <= 0) {
            fireErrorAndAbort("increaseEarnings_invalidAmount");
        }
        int earned = earningsOf(account);
        earnings.put(account.toByteArray(), earned + amount);
    }

    private static void safeTransfer(Hash160 from, Hash160 token, Hash160 to, int amount) {
        boolean result = (boolean) Contract.call(token, "transfer", CallFlags.All,
                new Object[] { from, to, amount, null });
        if (!result) {
            fireErrorAndAbort("safeTransfer_transferFail");
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

    private static void saveProperties(Map<String, Object> properties, ByteString tokenId) {

        if (!properties.containsKey(NAME)) {
            fireErrorAndAbort("saveProperties_missingName");
        }
        if (!properties.containsKey(DESC)) {
            fireErrorAndAbort("saveProperties_missingDescription");
        }
        if (!properties.containsKey(IMAGE)) {
            fireErrorAndAbort("saveProperties_missingImage");
        }
        if (!properties.containsKey(TOKEN_URI)) {
            fireErrorAndAbort("saveProperties_missingTokenUri");
        }
        if (!properties.containsKey(STRIKE)) {
            fireErrorAndAbort("saveProperties_missingStrike");
        }
        if (!properties.containsKey(STAKE)) {
            fireErrorAndAbort("saveProperties_missingStake");
        }
        if (!properties.containsKey(TYPE)) {
            fireErrorAndAbort("saveProperties_missingType");
        }
        if (!properties.containsKey(WRITER)) {
            fireErrorAndAbort("saveProperties_missingWriter");
        }
        if (!properties.containsKey(DEPRECIATION)) {
            fireErrorAndAbort("saveProperties_missingVdot");
        }
        if (!properties.containsKey(CREATED)) {
            fireErrorAndAbort("saveProperties_missingCreated");
        }
        if (!properties.containsKey(VALUE)) {
            fireErrorAndAbort("saveProperties_missingValue");
        }
        if (!properties.containsKey(VOLATILITY)) {
            fireErrorAndAbort("saveProperties_missingVi");
        }
        if (!properties.containsKey(SAFE)) {
            fireErrorAndAbort("saveProperties_missingSafe");
        }

        String name = (String) properties.get(NAME);
        String desc = (String) properties.get(DESC);
        String img = (String) properties.get(IMAGE);
        String uri = (String) properties.get(TOKEN_URI);
        int strike = (int) properties.get(STRIKE);
        int stake = (int) properties.get(STAKE);
        int type = (int) properties.get(TYPE);
        int vdot = (int) properties.get(DEPRECIATION);
        int created = (int) properties.get(CREATED);
        ByteString writer = (ByteString) properties.get(WRITER);
        int value = (int) properties.get(VALUE);
        int vi = (int) properties.get(VOLATILITY);
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

    private static void fireErrorAndAbort(String msg) {
        onError.fire(msg);
        Helper.abort();
    }

    /* PERMISSION CHECKS */

    private static void onlyOwner() {
        if (!Runtime.checkWitness(contractOwner())) {
            fireErrorAndAbort("onlyOwner");
        }
    }

    private static void onlyRentfuse() {
        if (!Runtime.checkWitness(rentfuseContract())) {
            fireErrorAndAbort("onlyRentfuse");
        }
    }

    /* OWNER ONLY METHODS */

    public static void updateImageBaseURI(String uri) {
        onlyOwner();
        if (uri.length() < 5) {
            fireErrorAndAbort("updateImageBaseURI_invalidUri");
        }
        Storage.put(ctx, imageBaseUriKey, uri);
    }

    public static void updatePause(boolean paused) {
        onlyOwner();
        Storage.put(ctx, isPausedKey, paused ? 1 : 0);
    }

    public static void updateCandyMinStake(int candyMinStake) {
        onlyOwner();
        if (candyMinStake < 5000_000000000L) {
            fireErrorAndAbort("updateCandyMinStake_invalidMinStake");
        }
        Storage.put(ctx, candyMinStakeKey, candyMinStake);
    }

    public static void updateCandyProtocolFee(int candyFee) {
        onlyOwner();
        if (candyFee < 0) {
            fireErrorAndAbort("updateCandyProtocolFee_invalidCandyFee");
        }
        Storage.put(ctx, candyProtocolFeeKey, candyFee);
    }

    /* CONTRACT MANAGEMENT */

    @OnDeployment
    public static void deploy(Object data, boolean update) {
        if (!update) {
            Object[] arr = (Object[]) data;
            Hash160 owner = (Hash160) arr[0];
            if (!Hash160.isValid(owner) || owner == Hash160.zero()) {
                fireErrorAndAbort("deploy_invalidOwner");
            }
            Storage.put(ctx, ownerkey, owner);

            Hash160 candyHash = (Hash160) arr[1];
            if (!Hash160.isValid(candyHash) || candyHash == Hash160.zero()) {
                fireErrorAndAbort("deploy_invalidCandyHash");
            }
            Storage.put(ctx, candyContractHashKey, candyHash);

            String imageBaseURI = (String) arr[2];
            if (imageBaseURI.length() < 5) {
                fireErrorAndAbort("deploy_invalidImageBaseURI");
            }
            Storage.put(ctx, imageBaseUriKey, imageBaseURI);

            String oracleEndpoint = (String) arr[3];
            if (oracleEndpoint.length() < 5) {
                fireErrorAndAbort("deploy_invalidOracleEndpoint");
            }
            Storage.put(ctx, oracleEndpointKey, oracleEndpoint);

            int candyProtocolFee = (int) arr[4];
            if (candyProtocolFee < 0) {
                fireErrorAndAbort("deploy_invalidCandyProtocolFee");
            }
            Storage.put(ctx, candyProtocolFeeKey, candyProtocolFee);

            int candyMinStake = (int) arr[5];
            if (candyMinStake < 5000_000000000L) {
                fireErrorAndAbort("deploy_invalidCandyMinStake");
            }
            Storage.put(ctx, candyMinStakeKey, candyMinStake);

            Hash160 rentfuse = (Hash160) arr[6];
            if (!Hash160.isValid(rentfuse) || rentfuse == Hash160.zero()) {
                fireErrorAndAbort("deploy_invalidRentfuseHash");
            }
            Storage.put(ctx, rentfuseScriptHashKey, rentfuse);
            Storage.put(ctx, isPausedKey, 1);
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