package io.neocandy.games.candyclash;

import io.neow3j.devpack.Hash160;
import io.neow3j.devpack.Helper;

public class Utils {

    public static byte[] createStorageMapPrefix(Hash160 owner, byte[] prefix) {
        return Helper.concat(prefix, owner.toByteArray());
    }
}
