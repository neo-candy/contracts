package io.neocandy;

import static io.neow3j.devpack.StringLiteralHelper.stringToInt;

import io.neow3j.devpack.ByteString;
import io.neow3j.devpack.Contract;
import io.neow3j.devpack.Hash160;
import io.neow3j.devpack.Runtime;
import io.neow3j.devpack.Storage;
import io.neow3j.devpack.StorageContext;
import io.neow3j.devpack.StorageMap;
import io.neow3j.devpack.annotations.DisplayName;
import io.neow3j.devpack.annotations.ManifestExtra;
import io.neow3j.devpack.annotations.OnDeployment;
import io.neow3j.devpack.annotations.OnVerification;
import io.neow3j.devpack.annotations.Permission;
import io.neow3j.devpack.annotations.Safe;
import io.neow3j.devpack.annotations.SupportedStandards;
import io.neow3j.devpack.constants.CallFlags;
import io.neow3j.devpack.contracts.ContractManagement;
import io.neow3j.devpack.events.Event3Args;

@ManifestExtra(key = "name", value = "Neo Candy")
@ManifestExtra(key = "author", value = "Neo Candy")
@ManifestExtra(key = "description", value = "Neo Candy Token Contract")
@Permission(contract = "*", methods = "onNEP17Payment")
@Permission(contract = "fffdc93764dbaddd97c48f252a53ea4643faa3fd")
@SupportedStandards("NEP-17")
public class NeoCandy {

    @DisplayName("Transfer")
    static Event3Args<Hash160, Hash160, Integer> onTransfer;

    private static final int INITIAL_SUPPLY = stringToInt("9000000000000000000");

    private static final int DECIMALS = 9;

    private static final byte[] PREFIX_ASSET = new byte[] { 0x01 };
    private static final byte[] TOTAL_SUPPLY_KEY = new byte[] { 0x02 };
    private static final byte[] OWNER_KEY = new byte[] { 0x03 };

    private static final StorageContext sc = Storage.getStorageContext();
    private static final StorageMap assetMap = sc.createMap(PREFIX_ASSET);

    public static String symbol() {
        return "CANDY";
    }

    public static int decimals() {
        return DECIMALS;
    }

    public static int totalSupply() {
        return Storage.getInteger(sc, TOTAL_SUPPLY_KEY);
    }

    public static boolean transfer(Hash160 from, Hash160 to, int amount, Object[] data) throws Exception {

        if (!Hash160.isValid(from) || !Hash160.isValid(to)) {
            throw new Exception("From or To address is not a valid address.");
        }
        if (amount < 0) {
            throw new Exception("The transfer amount was negative.");
        }
        if (!Runtime.checkWitness(from) && from != Runtime.getCallingScriptHash()) {
            throw new Exception(
                    "Invalid sender signature. The sender of the tokens needs to be " + "the signing account.");
        }
        if (getBalance(from) < amount) {
            return false;
        }
        if (from != to && amount != 0) {
            deductFromBalance(from, amount);
            addToBalance(to, amount);
        }

        onTransfer.fire(from, to, amount);
        if (ContractManagement.getContract(to) != null) {
            Contract.call(to, "onNEP17Payment", CallFlags.All, new Object[] { from, amount, data });
        }
        return true;
    }

    public static int balanceOf(Hash160 account) throws Exception {
        if (!Hash160.isValid(account)) {
            throw new Exception("Argument is not a valid address.");
        }
        return getBalance(account);
    }

    @OnDeployment
    public static void deploy(Object data, boolean update) throws Exception {
        if (!update) {
            if (data == null) {
                throw new Exception("Expects the owner hash as an argument but argument was null.");
            }
            Hash160 owner = (Hash160) data;
            if (!Hash160.isValid(owner)) {
                throw new Exception("Expects the owner hash as an argument but argument was not a valid Hash160.");
            }
            if (Storage.get(sc, TOTAL_SUPPLY_KEY) != null) {
                throw new Exception("Contract was already deployed.");
            }
            Storage.put(sc, TOTAL_SUPPLY_KEY, INITIAL_SUPPLY);
            Storage.put(sc, OWNER_KEY, owner);
            assetMap.put(owner.toByteArray(), INITIAL_SUPPLY);
        }
    }

    public static void update(ByteString script, String manifest) throws Exception {
        throwIfSignerIsNotOwner();
        if (script.length() == 0 && manifest.length() == 0) {
            throw new Exception("The new contract script and manifest must not be empty.");
        }
        ContractManagement.update(script, manifest);
    }

    @Safe
    public static Hash160 getOwner() {
        return new Hash160(Storage.get(sc, OWNER_KEY));
    }

    @OnVerification
    public static boolean verify() throws Exception {
        throwIfSignerIsNotOwner();
        return true;
    }

    private static void throwIfSignerIsNotOwner() throws Exception {
        if (!Runtime.checkWitness(getOwner())) {
            throw new Exception("The calling entity is not the owner of this contract.");
        }
    }

    private static void addToBalance(Hash160 key, int value) {
        assetMap.put(key.toByteArray(), getBalance(key) + value);
    }

    private static void deductFromBalance(Hash160 key, int value) {
        int oldValue = getBalance(key);
        if (oldValue == value) {
            assetMap.delete(key.toByteArray());
        } else {
            assetMap.put(key.toByteArray(), oldValue - value);
        }
    }

    private static int getBalance(Hash160 key) {
        return assetMap.get(key.toByteArray()).toIntOrZero();
    }

}