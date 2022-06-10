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
import io.neow3j.devpack.annotations.OnDeployment;
import io.neow3j.devpack.annotations.OnNEP17Payment;
import io.neow3j.devpack.annotations.Permission;
import io.neow3j.devpack.annotations.Safe;
import io.neow3j.devpack.constants.CallFlags;
import io.neow3j.devpack.constants.FindOptions;
import io.neow3j.devpack.contracts.ContractManagement;
import io.neow3j.devpack.contracts.CryptoLib;
import io.neow3j.devpack.contracts.OracleContract;
import io.neow3j.devpack.contracts.StdLib;
import io.neow3j.devpack.events.Event3Args;

@Permission(contract = "*", methods = "*")
public class Candefi {

    @DisplayName("Payment")
    static Event3Args<Hash160, Integer, Object> onPayment;

    private static final StorageContext ctx = Storage.getStorageContext();

    private static final int SEVEN_DAYS_MS = 1000 * 60 * 60 * 24 * 7;
    private static final int THIRTY_DAYS_MS = 1000 * 60 * 60 * 24 * 30;
    private static final String ACTION_OPEN_POSITION = "openPosition";
    private static final String ACTION_BUY_POSITION = "buyPosition";
    private static final int BELOW_STRIKE = 0;
    private static final int ABOVE_STRIKE = 1;

    /* STORAGE KEYS */
    private static final byte[] ownerKey = Helper.toByteArray((byte) 1);
    private static final byte[] candyContractKey = Helper.toByteArray((byte) 2);
    private static final byte[] minStakeKey = Helper.toByteArray((byte) 3);
    private static final byte[] apiEndpointKey = Helper.toByteArray((byte) 4);

    /* STORAGE MAP */
    private static final StorageMap whitelist = new StorageMap(ctx, Helper.toByteArray((byte) 101));
    private static final StorageMap positions = new StorageMap(ctx, Helper.toByteArray((byte) 102));
    private static final StorageMap buyers = new StorageMap(ctx, Helper.toByteArray((byte) 103));

    class OraclePriceResponse {
        public int price;
        public int mins;
    }

    @OnNEP17Payment
    public static void onPayment(Hash160 from, int amount, Object data) throws Exception {

        if (Runtime.getCallingScriptHash() != candyContract()) {
            throw new Exception("onPayment_onlyCandy");
        }

        if (amount < minStake()) {
            throw new Exception("onPayment_invalidAmount");
        }

        Object[] arr = (Object[]) data;
        String action = (String) arr[0];
        if (action == ACTION_OPEN_POSITION) {
            Hash160 token = (Hash160) arr[1];
            int expiration = (int) arr[2];
            int fee = (int) arr[3];
            int strike = (int) arr[4];
            int direction = (int) arr[5];
            openPosition(from, amount, token, expiration, fee, strike, direction);
        } else if (action == ACTION_BUY_POSITION) {
            ByteString positionId = (ByteString) arr[1];
            buyPosition(positionId, amount, from);
        } else {
            throw new Exception("onPayment_invalidAction");
        }
        onPayment.fire(from, amount, data);
    }

    /* SAFE METHODS */

    @Safe
    public static Hash160 owner() {
        return new Hash160(Storage.get(ctx, ownerKey));
    }

    @Safe
    public static int minStake() {
        return Storage.getInt(ctx, minStakeKey);
    }

    @Safe
    public static Hash160[] getWhitelist() {
        return new Hash160[0];
    }

    @Safe
    public static String apiEndpoint() {
        return Storage.getString(ctx, apiEndpointKey);
    }

    @Safe
    public static List<String> positions() {
        List<String> pos = new List();
        Iterator<Struct<ByteString, ByteString>> iterator = (Iterator<Struct<ByteString, ByteString>>) positions
                .find(FindOptions.RemovePrefix);
        while (iterator.next()) {
            Position result = (Position) StdLib.deserialize(iterator.get().value);
            pos.add(StdLib.jsonSerialize(result));
        }
        return pos;

    }

    /* PUBLIC WRITE */

    public static void closePosition(ByteString positionId) throws Exception {
        ByteString positionResult = positions.get(positionId);
        if (positionResult == null) {
            throw new Exception("buyPosition_noPositionFound");
        }
        ByteString buyerResult = buyers.get(positionId);

        Position position = (Position) StdLib.deserialize(positionResult);
        Hash160 caller = Runtime.getCallingScriptHash();
        if (!Runtime.checkWitness(caller)) {
            throw new Exception("buyPosition_callerNotSigner");
        }
        if (position.creator == caller) {
            if (buyerResult == null) {
                positions.delete(positionId);
                safeTransfer(position.token, position.creator, position.stake * 90 / 100);
            } else if (position.expiration >= Runtime.getTime()) {
                positions.delete(positionId);
                buyers.delete(positionId);
                safeTransfer(position.token, position.creator, position.stake);
            } else {
                throw new Exception("closePosition_expirationNotReached");
            }
        } else if (caller == new Hash160(buyerResult)) {
            if (position.expiration < Runtime.getTime()) {
                _closePosition(position);
            } else {
                throw new Exception("closePosition_positionExpired");
            }
        } else {
            throw new Exception("closePosition_noAuth");
        }
    }

    public static void _closePosition(Position p) {
        String s = whitelist.getString(p.token.toByteArray());
        String endpoint = Storage.getString(ctx, apiEndpointKey) + s;
        OracleContract.request(endpoint, null, "callback", p, 30000000);
    }

    public static void resolvePosition(String url, Object userData, int responseCode, ByteString response)
            throws Exception {
        if (Runtime.getCallingScriptHash() != OracleContract.getHash()) {
            throw new Exception("resolvePosition_onlyOracle");
        }
        Position p = (Position) userData;
        Storage.put(ctx, "tmp", response);
        Map<String, Object> result = (Map<String, Object>) StdLib
                .jsonDeserialize(Storage.getString(ctx, "tmp"));
        int priceResponse = decimalStringToInt((String) result.get("price"));
        int strike = p.strike;
        ByteString positionId = getPositionId(p);
        Hash160 buyer = new Hash160(buyers.get(positionId));
        if (p.direction == ABOVE_STRIKE && priceResponse > strike
                || p.direction == BELOW_STRIKE && priceResponse < strike) {
            positions.delete(positionId);
            buyers.delete(positionId);
            safeTransfer(p.token, buyer, p.stake);
        }

        Storage.put(ctx, "response", response);
    }

    /* UTILS */

    private static int decimalStringToInt(String price) {
        String[] split = StdLib.stringSplit(price, ".");
        return StdLib.atoi(split[0] + split[1], 10);
    }

    private static void safeTransfer(Hash160 token, Hash160 to, int amount) throws Exception {
        Contract.call(token, "transfer", CallFlags.All,
                new Object[] { Runtime.getExecutingScriptHash(), to, amount, null });
    }

    private static void buyPosition(ByteString positionId, int fee, Hash160 buyer) throws Exception {
        ByteString result = positions.get(positionId);
        if (result == null) {
            throw new Exception("buyPosition_noPositionFound");
        }

        if (buyers.get(positionId) != null) {
            throw new Exception("buyPosition_notAvailable");
        }

        buyers.put(positionId, buyer.toByteArray());

        Position position = (Position) StdLib.deserialize(result);

        if (position.fee != fee) {
            throw new Exception("buyPosition_invalidFee");
        }
        // TODO: in the future the stake amount depends on when the buyer bought the
        // position!
        // Buying the position halfway through allows only 50% of the stake to be
        // earned?

    }

    private static void openPosition(Hash160 from, int amount, Hash160 token, int expiration, int fee, int strike,
            int direction)
            throws Exception {
        if (!isWhitelisted(token)) {
            throw new Exception("onPayment_notWhitelisted");
        }
        if (expiration != SEVEN_DAYS_MS || expiration != THIRTY_DAYS_MS) {
            throw new Exception("onPayment_invalidExpiration");
        }

        if (fee <= 0) {
            throw new Exception("onPayment_invalidFee");
        }

        if (strike <= 0) {
            throw new Exception("onPayment_invalidStrikePrice");
        }
        if (direction != ABOVE_STRIKE && direction != BELOW_STRIKE) {
            throw new Exception("onPayment_invalidDirectionInteger");
        }

        Position p = new Position(from, token, fee, amount, Runtime.getTime() + expiration, strike, direction);
        positions.put(getPositionId(p), StdLib.serialize(p));
    }

    private static ByteString getPositionId(Position p) {
        return CryptoLib.sha256(StdLib.serialize(p));
    }

    private static boolean isWhitelisted(Hash160 token) {
        return whitelist.get(token.toByteArray()) != null;
    }

    private static Hash160 candyContract() {
        return new Hash160(Storage.get(ctx, candyContractKey));
    }

    /* OWNER ONLY METHODS */

    public static void whitelist(Hash160 token, String symbol) throws Exception {
        onlyOwner();
        whitelist.put(token.toByteArray(), symbol);
    }

    public static void updateMinStake(int minStake) throws Exception {
        onlyOwner();
        if (minStake <= 0) {
            throw new Exception("updateMinStake_invalidMinStake");
        }
        Storage.put(ctx, minStakeKey, minStake);
    }

    /* PERMISSION CHECKS */

    private static void onlyOwner() throws Exception {
        if (!Runtime.checkWitness(owner())) {
            throw new Exception("onlyOwner");
        }
    }

    /* CONTRACT MANGEMENT */

    @OnDeployment
    public static void deploy(Object data, boolean update) throws Exception {
        Object[] arr = (Object[]) data;
        if (!update) {
            Hash160 owner = (Hash160) arr[0];
            if (!Hash160.isValid(owner)) {
                throw new Exception("deploy_invalidOwner");
            }
            Storage.put(ctx, ownerKey, owner);

            Hash160 candyContract = (Hash160) arr[1];
            if (!Hash160.isValid(candyContract)) {
                throw new Exception("deploy_invalidCandyContract");
            }
            Storage.put(ctx, candyContractKey, candyContract);

            int minStake = (int) arr[2];
            if (minStake <= 0) {
                throw new Exception("deploy_invalidMinStake");
            }
            Storage.put(ctx, minStakeKey, minStake);

            String apiEndpoint = (String) arr[3];
            Storage.put(ctx, apiEndpointKey, apiEndpoint);
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
