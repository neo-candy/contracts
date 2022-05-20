package io.neocandy.discord;

import io.neow3j.devpack.ByteString;
import io.neow3j.devpack.Contract;
import io.neow3j.devpack.Hash160;
import io.neow3j.devpack.Helper;
import io.neow3j.devpack.Iterator;
import io.neow3j.devpack.List;
import io.neow3j.devpack.Runtime;
import io.neow3j.devpack.Storage;
import io.neow3j.devpack.StorageContext;
import io.neow3j.devpack.StorageMap;
import io.neow3j.devpack.Iterator.Struct;
import io.neow3j.devpack.annotations.DisplayName;
import io.neow3j.devpack.annotations.ManifestExtra;
import io.neow3j.devpack.annotations.OnDeployment;
import io.neow3j.devpack.annotations.OnNEP11Payment;
import io.neow3j.devpack.annotations.OnNEP17Payment;
import io.neow3j.devpack.annotations.Permission;
import io.neow3j.devpack.annotations.Safe;
import io.neow3j.devpack.constants.CallFlags;
import io.neow3j.devpack.constants.FindOptions;
import io.neow3j.devpack.constants.NativeContract;
import io.neow3j.devpack.contracts.ContractManagement;
import io.neow3j.devpack.contracts.LedgerContract;
import io.neow3j.devpack.events.Event1Arg;
import io.neow3j.devpack.events.Event3Args;

@Permission(contract = "*", methods = { "transfer" })
@Permission(nativeContract = NativeContract.ContractManagement)
public class DiscordStaking {

    @DisplayName("onPayment")
    static Event3Args<Hash160, Integer, Object> onPayment;

    private static final StorageContext ctx = Storage.getStorageContext();
    private static final byte[] ownerKey = Helper.toByteArray((byte) 1);
    private static final byte[] candyContractKey = Helper.toByteArray((byte) 2);

    /* STORAGE MAPS */
    private static StorageMap lockups = new StorageMap(ctx, Helper.toByteArray((byte) 3));
    private static StorageMap stake = new StorageMap(ctx, Helper.toByteArray((byte) 3));

    @Safe
    public static Hash160 owner() {
        return new Hash160(Storage.get(ctx, ownerKey));
    }

    @Safe
    public static Hash160 candyContract() {
        return new Hash160(Storage.get(ctx, candyContractKey));
    }

    @OnNEP17Payment
    public static void onPayment(Hash160 from, int amount, Object data) throws Exception {
        if (Runtime.getCallingScriptHash() != candyContract()) {
            throw new Exception("onlyCandy");
        }
        onPayment.fire(from, amount, data);
    }

    /* CONTRACT MANAGEMENT */

    public static void update(ByteString script, String manifest) throws Exception {
        onlyOwner();
        ContractManagement.update(script, manifest);
    }

    private static void onlyOwner() throws Exception {
        if (!Runtime.checkWitness(owner())) {
            throw new Exception("onlyOwner");
        }
    }

    @OnDeployment
    public static void deploy(Object data, boolean update) {
        Object[] arr = (Object[]) data;
        if (!update) {
            Storage.put(ctx, ownerKey, (Hash160) arr[0]);
            Storage.put(ctx, candyContractKey, (Hash160) arr[1]);

        }
    }

}
