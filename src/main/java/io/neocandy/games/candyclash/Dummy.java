package io.neocandy.games.candyclash;

import io.neow3j.devpack.Storage;
import io.neow3j.devpack.StorageContext;
import io.neow3j.devpack.StorageMap;

public class Dummy {

    private static final StorageContext ctx = Storage.getStorageContext();

    private static final StorageMap sugarValues = new StorageMap(ctx, (byte) 104);

    public static String foo() {
        return sugarValues.getString("key") + "foo";
    }

}