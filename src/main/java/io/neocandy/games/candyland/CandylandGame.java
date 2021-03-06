package io.neocandy.games.candyland;

import io.neow3j.devpack.ByteString;
import io.neow3j.devpack.Hash160;
import io.neow3j.devpack.Helper;
import io.neow3j.devpack.Iterator;
import io.neow3j.devpack.List;
import io.neow3j.devpack.Runtime;
import io.neow3j.devpack.Storage;
import io.neow3j.devpack.StorageContext;
import io.neow3j.devpack.StorageMap;
import io.neow3j.devpack.Iterator.Struct;
import io.neow3j.devpack.annotations.ManifestExtra;
import io.neow3j.devpack.annotations.OnDeployment;
import io.neow3j.devpack.annotations.Permission;
import io.neow3j.devpack.annotations.Safe;
import io.neow3j.devpack.constants.FindOptions;
import io.neow3j.devpack.contracts.ContractManagement;
import io.neow3j.devpack.contracts.StdLib;

@ManifestExtra(key = "author", value = "NeoCandy")
@ManifestExtra(key = "description", value = "Candyland Game Contract")
@Permission(contract = "0xfffdc93764dbaddd97c48f252a53ea4643faa3fd", methods = "*")
public class CandylandGame {

    private static final byte[] OWNER_KEY = Helper.toByteArray((byte) 1);
    private static final byte[] USERNAMES_PREFIX = Helper.toByteArray((byte) 2);
    private static final byte[] USERNAME_KEY = Helper.toByteArray((byte) 3);
    private static final StorageContext ctx = Storage.getStorageContext();

    private static final StorageMap usernames = new StorageMap(ctx, USERNAMES_PREFIX);

    public static void register(String username, Hash160 owner) {
        assert owner != null : "Owner cannot be null";
        assert username != null : "Username cannot be null";
        assert username.length() > 2 : "Username must be at least 3 characters long";
        assert username.length() < 16 : "Username can have a maximum of 15 characters";
        assert Runtime.checkWitness(owner) : "No authorization";
        assert usernames.get(username) == null : "Username already taken";

        StorageMap ownerMap = new StorageMap(ctx, owner.toByteArray());
        assert ownerMap.get(USERNAME_KEY) == null : "Address already has a username";
        new StorageMap(ctx, owner.toByteArray()).put(USERNAME_KEY, username);
        usernames.put(username, owner);
    }

    @Safe
    public static String getAllUsernames() {
        List<String> usernames = new List<>();
        Iterator<Struct<ByteString, ByteString>> iterator = Storage.find(ctx, USERNAMES_PREFIX,
                FindOptions.RemovePrefix);
        while (iterator.next()) {
            String username = iterator.get().key.toString();
            usernames.add(username);
        }
        return StdLib.jsonSerialize(usernames);
    }

    @Safe
    public static String getUsername(Hash160 owner) {
        ByteString result = new StorageMap(ctx, owner.toByteArray()).get(USERNAME_KEY);
        return result != null ? result.toString() : "";
    }

    @Safe
    public static Hash160 getOwner() {
        return new Hash160(Storage.get(ctx, OWNER_KEY));
    }

    @OnDeployment
    public static void deploy(Object data, boolean update) {
        if (!update) {
            Storage.put(ctx, OWNER_KEY, (Hash160) data);
        }
    }

    public static void update(ByteString script, String manifest) {
        assert Runtime.checkWitness(getOwner()) : "The calling entity is not the owner of this contract.";
        assert script.length() != 0 && manifest.length() != 0
                : "The new contract script and manifest must not be empty.";
        ContractManagement.update(script, manifest);
    }

}
