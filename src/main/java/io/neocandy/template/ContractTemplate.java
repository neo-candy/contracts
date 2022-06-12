package io.neocandy.template;

import io.neow3j.devpack.ByteString;
import io.neow3j.devpack.Hash160;
import io.neow3j.devpack.Helper;
import io.neow3j.devpack.Runtime;
import io.neow3j.devpack.Storage;
import io.neow3j.devpack.StorageContext;
import io.neow3j.devpack.annotations.DisplayName;
import io.neow3j.devpack.annotations.OnDeployment;
import io.neow3j.devpack.annotations.OnNEP17Payment;
import io.neow3j.devpack.annotations.Permission;
import io.neow3j.devpack.annotations.Safe;
import io.neow3j.devpack.constants.NativeContract;
import io.neow3j.devpack.contracts.ContractManagement;
import io.neow3j.devpack.events.Event3Args;

@Permission(nativeContract = NativeContract.ContractManagement)
public class ContractTemplate {

    @DisplayName("Payment")
    static Event3Args<Hash160, Integer, Object> onPayment;

    private static final StorageContext ctx = Storage.getStorageContext();

    // STORAGE KEYS
    private static final byte[] ownerKey = Helper.toByteArray((byte) 1);

    @OnNEP17Payment
    public static void onPayment(Hash160 from, int amount, Object data) throws Exception {
        onPayment.fire(from, amount, data);
    }

    /* SAFE METHODS */

    @Safe
    public static Hash160 owner() {
        return new Hash160(Storage.get(ctx, ownerKey));
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
