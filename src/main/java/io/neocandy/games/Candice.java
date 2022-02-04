package io.neocandy.games;

import io.neow3j.devpack.ByteString;
import io.neow3j.devpack.Contract;
import io.neow3j.devpack.Hash160;
import io.neow3j.devpack.Helper;
import io.neow3j.devpack.Runtime;
import io.neow3j.devpack.Storage;
import io.neow3j.devpack.StorageContext;
import io.neow3j.devpack.StorageMap;
import io.neow3j.devpack.annotations.DisplayName;
import io.neow3j.devpack.annotations.ManifestExtra;
import io.neow3j.devpack.annotations.OnDeployment;
import io.neow3j.devpack.annotations.OnNEP17Payment;
import io.neow3j.devpack.annotations.Permission;
import io.neow3j.devpack.annotations.Safe;
import io.neow3j.devpack.constants.CallFlags;
import io.neow3j.devpack.contracts.ContractManagement;
import io.neow3j.devpack.events.Event2Args;
import io.neow3j.devpack.events.Event3Args;
import io.neow3j.devpack.events.Event4Args;

@ManifestExtra(key = "author", value = "NeoCandy")
@ManifestExtra(key = "description", value = "Candice Game Contract")
@Permission(contract = "*", methods = "OnNEP17Payment")
@Permission(contract = "0xfffdc93764dbaddd97c48f252a53ea4643faa3fd", methods = "*")
@Permission(contract = "<NEO_CANDY_CONTRACT_HASH_PLACEHOLDER>", methods = "transfer")
public class Candice {

    @DisplayName("onPayment")
    private static Event3Args<Hash160, Integer, Object> onPayment;

    @DisplayName("onRoomCreated")
    private static Event2Args<Hash160, Integer> onRoomCreated;

    @DisplayName("onPlay")
    private static Event4Args<Hash160, Hash160, Integer, Hash160> onPlayed;

    private static final byte[] OWNER_KEY = Helper.toByteArray((byte) 1);
    private static final byte[] TOKEN_KEY = Helper.toByteArray((byte) 2);
    private static final StorageContext ctx = Storage.getStorageContext();

    private static final byte[] ROOMS_PREFIX = Helper.toByteArray((byte) 3);
    private static final StorageMap rooms = new StorageMap(ctx, ROOMS_PREFIX);

    private static final byte[] GAMES_PREFIX = Helper.toByteArray((byte) 4);
    private static final StorageMap games = new StorageMap(ctx, GAMES_PREFIX);

    /**
     * 
     * @param from
     * @param amount
     * @param data
     * @throws Exception
     */
    @OnNEP17Payment
    public static void onPayment(Hash160 from, int amount, Object data) {
        Hash160 candy = new Hash160(Storage.get(ctx, TOKEN_KEY));
        assert Runtime.getCallingScriptHash().equals(candy) : "contract accepts only NeoCandy";
        assert amount > 0 : "stake must be greater than 0";

        Hash160 opponent = (Hash160) data;
        if (opponent != null) {
            assert opponent != from : "invalid opponent";
            join(opponent, from, amount);
        } else {
            assert rooms.get(from.toByteArray()).toIntOrZero() == 0 : "room already exists";
            rooms.put(from.toByteArray(), amount);
            onRoomCreated.fire(from, amount);
        }
        onPayment.fire(from, amount, data);
    }

    /**
     * 
     * @param p1
     * @param p2
     * @return
     */
    public static Hash160 play(Hash160 p1, Hash160 p2) {
        assert Runtime.checkWitness(p1) || Runtime.checkWitness(p2) : "no authorization";
        byte[] key = getGameKey(p1, p2);
        int stake = games.get(key).toIntOrZero();
        assert stake > 0 : "game does not exist";
        games.delete(key);
        int a = Runtime.getRandom() % 10;
        int b = Runtime.getRandom() % 10;
        Hash160 result = Hash160.zero();
        if (a > b) {
            doTransfer(stake * 2, p1);
            result = p1;
        } else if (b < a) {
            doTransfer(stake * 2, p2);
            result = p2;
        } else {
            doTransfer(stake, p1);
            doTransfer(stake, p2);
        }
        onPlayed.fire(p1, p2, stake, result);
        return result;
    }

    @Safe
    public static int queryGame(Hash160 p1, Hash160 p2) {
        return games.get(getGameKey(p1, p2)).toIntOrZero();
    }

    @Safe
    public static int queryRoom(Hash160 creator) {
        return rooms.get(creator.toByteArray()).toIntOrZero();
    }

    @Safe
    public static Hash160 getOwner() {
        return new Hash160(Storage.get(ctx, OWNER_KEY));
    }

    @Safe
    public static int getRooms() {
        return 2;
    }

    @OnDeployment
    public static void deploy(Object data, boolean update) {
        if (!update) {
            Object[] d = (Object[]) data;
            Storage.put(ctx, OWNER_KEY, (Hash160) d[0]);
            Storage.put(ctx, TOKEN_KEY, (Hash160) d[1]);
        }
    }

    public static void update(ByteString script, String manifest) {
        assert Runtime.checkWitness(getOwner()) : "The calling entity is not the owner of this contract.";
        assert script.length() != 0 && manifest.length() != 0
                : "The new contract script and manifest must not be empty.";
        ContractManagement.update(script, manifest);
    }

    /**
     * 
     * @param host
     * @param player
     * @return
     */
    private static void join(Hash160 host, Hash160 player, int amount) {
        int stake = rooms.get(host.toByteArray()).toIntOrZero();
        assert stake > 0 : "room does not exist";
        assert stake == amount : "invalid amount";
        byte[] key = getGameKey(host, player);
        int game = games.get(key).toIntOrZero();
        assert game == 0 : "active game";
        rooms.delete(host.toByteArray());
        games.put(key, amount);
    }

    private static void doTransfer(int amount, Hash160 to) {
        Contract.call(new Hash160(Storage.get(ctx, TOKEN_KEY)), "transfer", CallFlags.All,
                new Object[] { Runtime.getExecutingScriptHash(), to, amount, null });
    }

    private static byte[] getGameKey(Hash160 p1, Hash160 p2) {
        byte[] a = p1.toByteArray();
        byte[] b = p2.toByteArray();
        if (Helper.toInteger(a) < Helper.toInteger(b)) {
            a = p2.toByteArray();
            b = p1.toByteArray();
        }
        return Helper.concat(a, b);
    }

}
