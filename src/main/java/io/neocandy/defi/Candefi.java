package io.neocandy.defi;

import io.neow3j.devpack.ByteString;
import io.neow3j.devpack.Hash160;
import io.neow3j.devpack.Helper;
import io.neow3j.devpack.Runtime;
import io.neow3j.devpack.Storage;
import io.neow3j.devpack.StorageContext;
import io.neow3j.devpack.StorageMap;
import io.neow3j.devpack.annotations.DisplayName;
import io.neow3j.devpack.annotations.OnDeployment;
import io.neow3j.devpack.annotations.OnNEP17Payment;
import io.neow3j.devpack.annotations.Safe;
import io.neow3j.devpack.contracts.ContractManagement;
import io.neow3j.devpack.contracts.CryptoLib;
import io.neow3j.devpack.contracts.StdLib;
import io.neow3j.devpack.events.Event3Args;

public class Candefi {

    @DisplayName("Payment")
    static Event3Args<Hash160, Integer, Object> onPayment;

    private static final StorageContext ctx = Storage.getStorageContext();

    private static final int SEVEN_DAYS_MS = 1000 * 60 * 60 * 24 * 7;
    private static final int THIRTY_DAYS_MS = 1000 * 60 * 60 * 24 * 30;

    /* STORAGE KEYS */
    private static final byte[] ownerKey = Helper.toByteArray((byte) 1);
    private static final byte[] candyContractKey = Helper.toByteArray((byte) 2);
    private static final byte[] minStakeKey = Helper.toByteArray((byte) 3);

    /* STORAGE MAP */
    private static final StorageMap whitelist = new StorageMap(ctx, Helper.toByteArray((byte) 101));
    private static final StorageMap positions = new StorageMap(ctx, Helper.toByteArray((byte) 102));

    @OnNEP17Payment
    public static void onPayment(Hash160 from, int amount, Object data) throws Exception {

        if (Runtime.getCallingScriptHash() != candyContract()) {
            throw new Exception("onPayment_onlyCandy");
        }

        if (amount < minStake()) {
            throw new Exception("onPayment_invalidAmount");
        }

        Object[] arr = (Object[]) data;
        Hash160 token = (Hash160) arr[0];
        if (!isWhitelisted(token)) {
            throw new Exception("onPayment_notWhitelisted");
        }

        int expiration = (int) arr[1];
        if (expiration != SEVEN_DAYS_MS || expiration != THIRTY_DAYS_MS) {
            throw new Exception("onPayment_invalidExpiration");
        }

        int fee = (int) arr[2];
        if (fee <= 0) {
            throw new Exception("onPayment_invalidFee");
        }

        int strike = (int) arr[3];
        if (strike <= 0) {
            throw new Exception("onPayment_invalidStrikePrice");
        }

        Position p = new Position(token, fee, amount, Runtime.getTime() + expiration, strike);
        positions.put(getPID(p), StdLib.serialize(p));

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
    public static Hash160[] whitelist() {
        return new Hash160[0];
    }

    /* UTILS */

    private static ByteString getPID(Position p) {
        return CryptoLib.sha256(StdLib.serialize(p));
    }

    private static boolean isWhitelisted(Hash160 token) {
        return whitelist.get(token.toByteArray()) != null;
    }

    private static Hash160 candyContract() {
        return new Hash160(Storage.get(ctx, candyContractKey));
    }

    /* OWNER ONLY METHODS */

    public static void whitelist(Hash160 token) {
        whitelist.put(token.toByteArray(), 1);
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
