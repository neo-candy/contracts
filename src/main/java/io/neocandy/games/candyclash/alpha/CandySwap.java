package io.neocandy.games.candyclash.alpha;

import io.neow3j.devpack.ByteString;
import io.neow3j.devpack.Contract;
import io.neow3j.devpack.Hash160;
import io.neow3j.devpack.Helper;
import io.neow3j.devpack.Runtime;
import io.neow3j.devpack.Storage;
import io.neow3j.devpack.StorageContext;
import io.neow3j.devpack.StorageMap;
import io.neow3j.devpack.annotations.OnDeployment;
import io.neow3j.devpack.annotations.OnNEP17Payment;
import io.neow3j.devpack.annotations.Permission;
import io.neow3j.devpack.annotations.Safe;
import io.neow3j.devpack.annotations.Trust;
import io.neow3j.devpack.constants.CallFlags;
import io.neow3j.devpack.contracts.ContractManagement;

@Permission(contract = "*", methods = "*")
@Trust(contract = "*")
public class CandySwap {

    private static final StorageContext ctx = Storage.getStorageContext();
    private static final byte[] ownerKey = Helper.toByteArray((byte) 1);
    private static final byte[] candyContractKey = Helper.toByteArray((byte) 2);

    private static StorageMap receivers = new StorageMap(ctx, Helper.toByteArray((byte) 101));

    private static final int MS_PER_DAY = 1000 * 60 * 60 * 24;

    @OnNEP17Payment
    public static void onPayment(Hash160 from, int amount, Object data) throws Exception {
        if (Runtime.getCallingScriptHash() != candyContractHash()) {
            throw new Exception("onlyCandy");
        }
    }

    public static void swap(Hash160 receiver) throws Exception {
        if (!Runtime.checkWitness(receiver)) {
            throw new Exception("swap_noAuth");
        }
        if (candyBalance() < 50000_000000000L) {
            throw new Exception("swap_notEnoughCandy");
        }
        int result = receivers.getIntOrZero(receiver.toByteArray());
        if (Runtime.getTime() - MS_PER_DAY > result) {
            transferCandy(receiver);
            receivers.put(receiver.toByteArray(), Runtime.getTime());
        } else {
            throw new Exception("swap_onceADay");
        }
    }

    private static void transferCandy(Hash160 to) {
        Contract.call(candyContractHash(), "transfer", CallFlags.All,
                new Object[] { Runtime.getExecutingScriptHash(), to, 50000_000000000L,
                        null });
    }

    @OnDeployment
    public static void deploy(Object data, boolean update) {
        Object[] arr = (Object[]) data;
        if (!update) {
            Storage.put(ctx, ownerKey, (Hash160) arr[0]);
            Storage.put(ctx, candyContractKey, (Hash160) arr[1]);

        }
    }

    @Safe
    public static Hash160 owner() {
        return new Hash160(Storage.get(ctx, ownerKey));
    }

    @Safe
    public static int candyBalance() {
        Hash160 candyContract = candyContractHash();
        return (int) Contract.call(candyContract, "balanceOf", CallFlags.All,
                new Object[] { Runtime.getExecutingScriptHash() });
    }

    private static Hash160 candyContractHash() {
        return new Hash160(Storage.get(ctx, candyContractKey));
    }

    public static void update(ByteString script, String manifest) throws Exception {
        if (!Runtime.checkWitness(owner())) {
            throw new Exception("onlyOwner");
        }
        if (script.length() == 0 && manifest.length() == 0) {
            throw new Exception("update_contractAndManifestEmpty");
        }
        ContractManagement.update(script, manifest);
    }

}
