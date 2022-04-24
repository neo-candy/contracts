package io.neocandy.tokens.nep17;

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
import io.neow3j.devpack.constants.NativeContract;
import io.neow3j.devpack.contracts.ContractManagement;
import io.neow3j.devpack.events.Event3Args;

@ManifestExtra(key = "name", value = "Sugar Token")
@ManifestExtra(key = "author", value = "Neo Candy")
@ManifestExtra(key = "description", value = "Sugar Token Contract")
@Permission(contract = "*", methods = "onNEP17Payment")
@Permission(nativeContract = NativeContract.ContractManagement)
@SupportedStandards("NEP-17")
public class Sugar {

    @DisplayName("Transfer")
    static Event3Args<Hash160, Hash160, Integer> onTransfer;

    private static final int DECIMALS = 8;
    private static final int BURN_RATE_PERCENT = 2;

    private static final byte[] PREFIX_ASSET = new byte[] { 0x01 };
    private static final byte[] TOTAL_SUPPLY_KEY = new byte[] { 0x02 };
    private static final byte[] OWNER_KEY = new byte[] { 0x03 };

    private static final StorageContext sc = Storage.getStorageContext();
    private static final StorageMap assetMap = new StorageMap(sc, PREFIX_ASSET);

    public static String symbol() {
        return "SUGAR";
    }

    public static int decimals() {
        return DECIMALS;
    }

    public static int totalSupply() {
        return Storage.getInt(sc, TOTAL_SUPPLY_KEY);
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
                    "Invalid sender signature. The sender of the tokens needs to be the signing account.");
        }
        if (getBalance(from) < amount) {
            return false;
        }
        if (from != to && amount != 0) {
            int burnAmount = amount * BURN_RATE_PERCENT / 100;
            if (burnAmount > 0) {
                burn(from, burnAmount);
            }
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
            Storage.put(sc, TOTAL_SUPPLY_KEY, 0);
            Storage.put(sc, OWNER_KEY, owner);
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

    private static void burn(Hash160 from, int amount) throws Exception {
        int balance = balanceOf(from);
        if (balance >= amount) {
            deductFromBalance(from, amount);
            int ts = totalSupply();
            Storage.put(sc, TOTAL_SUPPLY_KEY, ts - amount);
        } else {
            throw new Exception("Not enough balance to burn");
        }
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