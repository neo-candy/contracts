package io.neocandy.games.candyclash;

import io.neow3j.devpack.Hash160;
import io.neow3j.devpack.Helper;
import io.neow3j.devpack.Runtime;

public class Utils {

    public static byte[] createStorageMapPrefix(Hash160 owner, byte[] prefix) {
        return Helper.concat(prefix, owner.toByteArray());
    }

    public static String generateName(boolean isVillain) {
        String[] NAMES = new String[] { "Cheddies", "Boogers", "Goofies" };
        return NAMES[randomNumber(NAMES.length)];
    }

    public static int randomNumber(int max) {
        Helper.assertTrue(max > 1);
        return (Runtime.getRandom() & 0xFFFFFFFF) % max;
    }
}
