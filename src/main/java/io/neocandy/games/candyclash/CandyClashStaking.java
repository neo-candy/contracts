package io.neocandy.games.candyclash;

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

@ManifestExtra(key = "name", value = "CandyClash Staking")
@ManifestExtra(key = "author", value = "NeoCandy")
@ManifestExtra(key = "description", value = "CandyClash Staking Contract")
@ManifestExtra(key = "email", value = "hello@neocandy.io")
@Permission(contract = "*", methods = { "transfer", "balanceOf", "getTypeOfToken", "getSugarOfToken",
        "propertiesJson" })
@Permission(nativeContract = NativeContract.ContractManagement)
public class CandyClashStaking {

    @DisplayName("debug")
    private static Event1Arg<Object> onDebug;

    @DisplayName("tokenStaked")
    private static Event3Args<Hash160, ByteString, Integer> onTokenStaked;

    @DisplayName("onPayment")
    static Event3Args<Hash160, Integer, Object> onPayment;

    @DisplayName("villagerCandyClaim")
    static Event3Args<ByteString, Integer, Boolean> onVillagerCandyClaim;

    @DisplayName("villainCandyClaim")
    static Event3Args<ByteString, Integer, Boolean> onVillainCandyClaim;

    private static final int BLOCKS_PER_DAY = 4 * 60 * 24;

    private static final byte[] ownerKey = Helper.toByteArray((byte) 1);
    private static final byte[] nftContractkey = Helper.toByteArray((byte) 2);
    private static final byte[] totalSugarStakedKey = Helper.toByteArray((byte) 3);
    private static final byte[] candyPerSugarKey = Helper.toByteArray((byte) 4);
    private static final byte[] totalVillagerCandiesStakedKey = Helper.toByteArray((byte) 5);
    private static final byte[] candiesContractKey = Helper.toByteArray((byte) 6);
    private static final byte[] pausedKey = Helper.toByteArray((byte) 7);
    private static final byte[] minStakeBlockCountKey = Helper.toByteArray((byte) 8);
    private static final byte[] totalCandiesEarnedKey = Helper.toByteArray((byte) 9);
    private static final byte[] dailyCandyRateKey = Helper.toByteArray((byte) 10);
    private static final byte[] lastClaimBlockIndexKey = Helper.toByteArray((byte) 11);
    private static final byte[] unaccountedRewardsKey = Helper.toByteArray((byte) 12);
    private static final byte[] taxAmountKey = Helper.toByteArray((byte) 13);
    private static final byte[] totalVillainCandiesStakedKey = Helper.toByteArray((byte) 17);
    private static final byte[] tokensOfKey = Helper.toByteArray((byte) 18);

    // CUSTOM METADATA
    private static final String SUGAR = "Sugar";
    private static final String GENERATION = "Generation";
    private static final String TYPE = "Type";
    private static final String TYPE_VILLAIN = "Villain";
    private static final String ATTRIBUTES = "attributes";
    private static final String TYPE_VILLAGER = "Villager";
    private static final String BONUS = "Claim Bonus";

    private static final StorageContext ctx = Storage.getStorageContext();
    private static final StorageMap villainCandiesStake = new StorageMap(ctx, Helper.toByteArray((byte) 14));
    private static final StorageMap villagerCandiesStake = new StorageMap(ctx, Helper.toByteArray((byte) 15));
    private static final StorageMap ownerOfMap = new StorageMap(ctx, Helper.toByteArray((byte) 16));

    /* RECEIVING PAYMENTS */

    @OnNEP17Payment
    public static void onPayment(Hash160 from, int amount, Object data) {
        assert (from == owner()) : "onlyOwner";
        assert (Runtime.getCallingScriptHash() == candyContractHash()) : "onlyCandy";
        onPayment.fire(from, amount, data);
    }

    @OnNEP11Payment
    public static void onPayment(Hash160 sender, int amount, ByteString tokenId, Object data) {
        assert (!isPaused()) : "isPaused";
        assert (maxCandiesToEarn() > 0) : "empty treasury";
        assert (Runtime.getCallingScriptHash() == nftContractHash()) : "invalid contract";

        if (getTypeOfToken(tokenId) == TYPE_VILLAIN) {
            int sugar = Integer.valueOf(getSugarOfToken(tokenId));
            addVillainCandy(sender, tokenId, sugar);
        } else {
            addVillagerCandy(sender, tokenId);
        }

        new StorageMap(ctx, createTokensOfPrefix(sender)).put(tokenId, 1);
        ownerOfMap.put(tokenId, sender);
    }

    public static int claim(ByteString[] tokenIds, boolean unstake, Hash160 receiver) {
        assert (!isPaused()) : "isPaused";
        updateEarnings();
        int claimAmount = 0;
        for (int i = 0; i < tokenIds.length; i++) {
            Hash160 owner = getOwnerOfToken(tokenIds[i]);
            assert (Runtime.checkWitness(owner) && owner == receiver) : "not owner";
            assert (maxCandiesToEarn() > 0) : "no more candies to earn";
            if (getTypeOfToken(tokenIds[i]) == TYPE_VILLAIN) {
                int sugar = Integer.valueOf(getSugarOfToken(tokenIds[i]));
                claimAmount += claimVillainCandy(tokenIds[i], unstake, sugar, owner);
            } else {
                claimAmount += claimVillagerCandy(tokenIds[i], unstake, owner);
            }
        }
        if (claimAmount == 0) {
            return 0;
        }
        transferCandy(receiver, claimAmount);
        return claimAmount;
    }

    private static void addVillainCandy(Hash160 owner, ByteString tokenId, int sugar) {
        incrementTotalSugarStaked(sugar);
        int candyPerSugar = candyPerSugar();
        updateVillainCandyStake(tokenId, candyPerSugar);
        incrementTotalVillainCandiesStaked(1);
        onTokenStaked.fire(owner, tokenId, candyPerSugar);
    }

    private static void addVillagerCandy(Hash160 owner, ByteString tokenId) {
        updateEarnings();
        int blockIndex = currentBlockIndex();
        updateVillagerCandyStake(tokenId, blockIndex);
        incrementTotalVillagerCandiesStaked(1);
        onTokenStaked.fire(owner, tokenId, blockIndex);
    }

    private static int claimVillagerCandy(ByteString tokenId, boolean unstake, Hash160 owner) {
        int stake = getVillagerCandyStake(tokenId);
        int claimAmount = 0;
        if (totalCandiesEarned() < maxCandiesToEarn()) {
            claimAmount = (currentBlockIndex() - stake) * dailyCandyRate() / BLOCKS_PER_DAY;
        } else {
            claimAmount = (lastClaimBlockIndex() - stake) * dailyCandyRate() / BLOCKS_PER_DAY;
        }
        onDebug.fire(unstake);
        if (unstake) {
            assert (currentBlockIndex() - stake >= minStakeBlockCount()) : "minimum stake duration not reached";
            boolean stealAllCandies = Runtime.getRandom() % 2 == 0;
            if (stealAllCandies) {
                payTax(claimAmount);
                claimAmount = 0;
            }
            deleteVillagerCandyStake(tokenId);
            decrementTotalVillagerCandiesStaked(1);
            deleteOwnerOfToken(tokenId);
            new StorageMap(ctx, createTokensOfPrefix(owner)).delete(tokenId);
            transferNFT(owner, tokenId);
        } else {
            int taxedAmount = claimAmount * taxAmount() / 100;
            payTax(taxedAmount);
            claimAmount = claimAmount - taxedAmount;
            updateVillagerCandyStake(tokenId, currentBlockIndex());
        }
        onVillagerCandyClaim.fire(tokenId, claimAmount, unstake);
        return claimAmount;
    }

    private static int claimVillainCandy(ByteString tokenId, boolean unstake, int sugar, Hash160 owner) {
        int stake = getVillainCandyStake(tokenId);
        int claimAmount = sugar * (candyPerSugar() - stake);
        if (unstake) {
            decrementTotalSugarStaked(sugar);
            deleteVillainCandyStake(tokenId);
            decrementTotalVillainCandiesStaked(1);
            deleteOwnerOfToken(tokenId);
            new StorageMap(ctx, createTokensOfPrefix(owner)).delete(tokenId);
            transferNFT(owner, tokenId);
        } else {
            updateVillainCandyStake(tokenId, candyPerSugar());
        }
        onVillainCandyClaim.fire(tokenId, claimAmount, unstake);
        return claimAmount;
    }

    private static void payTax(int amount) {
        if (totalSugarStaked() == 0) {
            incrementUnaccountedRewards(amount);
            return;
        }
        int increment = (amount + unaccountedRewards()) / totalSugarStaked();
        incrementCandyPerSugar(increment);
        resetUnaccountedRewards();

    }

    private static byte[] createTokensOfPrefix(Hash160 owner) {
        return Helper.concat(tokensOfKey, owner.toByteArray());
    }

    @Safe
    public static List<String> tokensOf(Hash160 owner) throws Exception {
        Iterator<Struct<ByteString, ByteString>> iterator = (Iterator<Struct<ByteString, ByteString>>) Storage.find(
                ctx.asReadOnly(),
                createTokensOfPrefix(owner),
                FindOptions.RemovePrefix);

        List<String> tokens = new List();
        while (iterator.next()) {
            ByteString result = (ByteString) iterator.get().key;
            tokens.add(getPropertiesJson(result));
        }
        return tokens;
    }

    private static String getPropertiesJson(ByteString tokenId) {
        Hash160 nftContract = nftContractHash();
        return (String) Contract.call(nftContract, "propertiesJson", CallFlags.All, new Object[] { tokenId });
    }

    /* UTIL */

    @Safe
    public static int availableClaimAmount(ByteString[] tokenIds) {
        int claimAmount = 0;
        for (int i = 0; i < tokenIds.length; i++) {
            if (maxCandiesToEarn() == 0) {
                return 0;
            }
            if (getTypeOfToken(tokenIds[i]) == TYPE_VILLAIN) {
                int sugarValue = Integer.valueOf(getSugarOfToken(tokenIds[i]));
                int stake = getVillainCandyStake(tokenIds[i]);
                claimAmount += sugarValue * (candyPerSugar() - stake);
            } else {
                int stake = getVillagerCandyStake(tokenIds[i]);
                if (totalCandiesEarned() < maxCandiesToEarn()) {
                    // int claimBonus = stringToInt(properties.get(BONUS));
                    claimAmount += (currentBlockIndex() - stake) * dailyCandyRate() / BLOCKS_PER_DAY;
                } else {
                    claimAmount += (lastClaimBlockIndex() - stake) * dailyCandyRate() / BLOCKS_PER_DAY;
                }
            }
        }
        return claimAmount;

    }

    private static String getSugarOfToken(ByteString tokenId) {
        Hash160 nftContract = nftContractHash();
        return (String) Contract.call(nftContract, "getSugarOfToken", CallFlags.All, new Object[] { tokenId });
    }

    private static String getTypeOfToken(ByteString tokenId) {
        Hash160 nftContract = nftContractHash();
        return (String) Contract.call(nftContract, "getTypeOfToken", CallFlags.All,
                new Object[] { tokenId });
    }

    private static Hash160 getOwnerOfToken(ByteString tokenId) {
        return new Hash160(ownerOfMap.get(tokenId));
    }

    public static void setPaused(int value) {
        Storage.put(ctx, pausedKey, value);
    }

    @Safe
    public static int totalSugarStaked() {
        return Storage.getIntOrZero(ctx, totalSugarStakedKey);
    }

    @Safe
    public static int taxAmount() {
        return Storage.getIntOrZero(ctx, taxAmountKey);
    }

    @Safe
    public static int totalVillagerCandiesStaked() {
        return Storage.getIntOrZero(ctx, totalVillagerCandiesStakedKey);
    }

    @Safe
    public static int totalVillainCandiesStaked() {
        return Storage.getIntOrZero(ctx, totalVillainCandiesStakedKey);
    }

    @Safe
    public static int maxCandiesToEarn() {
        Hash160 candyContract = candyContractHash();
        return (int) Contract.call(candyContract, "balanceOf", CallFlags.All,
                new Object[] { Runtime.getExecutingScriptHash() });
    }

    @Safe
    public static int minStakeBlockCount() {
        return Storage.getInt(ctx, minStakeBlockCountKey);
    }

    @Safe
    public static int totalCandiesEarned() {
        return Storage.getIntOrZero(ctx, totalCandiesEarnedKey);
    }

    @Safe
    public static Hash160 owner() {
        return new Hash160(Storage.get(ctx, ownerKey));
    }

    @Safe
    public static int dailyCandyRate() {
        return Storage.getInt(ctx, dailyCandyRateKey);
    }

    @Safe
    public static boolean isPaused() {
        return Storage.getInt(ctx, pausedKey) == 1 ? true : false;
    }

    private static void onlyOwner() {
        assert (Runtime.checkWitness(owner())) : "onlyOwner";
    }

    private static void incrementTotalSugarStaked(int amount) {
        Storage.put(ctx, totalSugarStakedKey, totalSugarStaked() + amount);
    }

    private static void decrementTotalSugarStaked(int amount) {
        Storage.put(ctx, totalSugarStakedKey, totalSugarStaked() - amount);
    }

    private static void incrementTotalVillagerCandiesStaked(int amount) {
        Storage.put(ctx, totalVillagerCandiesStakedKey, totalVillagerCandiesStaked() + amount);
    }

    private static void decrementTotalVillagerCandiesStaked(int amount) {
        Storage.put(ctx, totalVillagerCandiesStakedKey, totalVillagerCandiesStaked() - amount);
    }

    private static void incrementTotalVillainCandiesStaked(int amount) {
        Storage.put(ctx, totalVillainCandiesStakedKey, totalVillainCandiesStaked() + amount);
    }

    private static void decrementTotalVillainCandiesStaked(int amount) {
        Storage.put(ctx, totalVillainCandiesStakedKey, totalVillagerCandiesStaked() - amount);
    }

    private static void incrementTotalCandiesEarned(int amount) {
        Storage.put(ctx, totalCandiesEarnedKey, totalCandiesEarned() + amount);
    }

    private static void updateVillainCandyStake(ByteString tokenId, int value) {
        villainCandiesStake.put(tokenId, value);
    }

    private static void updateVillagerCandyStake(ByteString tokenId, int value) {
        villagerCandiesStake.put(tokenId, value);
    }

    private static int getVillagerCandyStake(ByteString tokenId) {
        return villagerCandiesStake.getInt(tokenId);
    }

    private static int getVillainCandyStake(ByteString tokenId) {
        return villainCandiesStake.getInt(tokenId);
    }

    private static void deleteVillainCandyStake(ByteString tokenId) {
        villainCandiesStake.delete(tokenId);
    }

    private static void deleteOwnerOfToken(ByteString tokenId) {
        ownerOfMap.delete(tokenId);
    }

    private static void deleteVillagerCandyStake(ByteString tokenId) {
        villagerCandiesStake.delete(tokenId);
    }

    private static int lastClaimBlockIndex() {
        return Storage.getIntOrZero(ctx, lastClaimBlockIndexKey);
    }

    private static void updateLastClaimBlockIndex(int value) {
        Storage.put(ctx, lastClaimBlockIndexKey, value);
    }

    private static int unaccountedRewards() {
        return Storage.getIntOrZero(ctx, unaccountedRewardsKey);
    }

    private static void incrementUnaccountedRewards(int amount) {
        Storage.put(ctx, unaccountedRewardsKey, unaccountedRewards() + amount);
    }

    private static void resetUnaccountedRewards() {
        Storage.put(ctx, unaccountedRewardsKey, 0);
    }

    private static int candyPerSugar() {
        return Storage.getIntOrZero(ctx, candyPerSugarKey);
    }

    private static Hash160 candyContractHash() {
        return new Hash160(Storage.get(ctx, candiesContractKey));
    }

    private static Hash160 nftContractHash() {
        return new Hash160(Storage.get(ctx, nftContractkey));
    }

    private static void incrementCandyPerSugar(int amount) {
        Storage.put(ctx, candyPerSugarKey, candyPerSugar() + amount);

    }

    private static void transferCandy(Hash160 to, int amount) {
        Hash160 candyContract = candyContractHash();
        Contract.call(candyContract, "transfer", CallFlags.All,
                new Object[] { Runtime.getExecutingScriptHash(), to, amount, null });
    }

    private static void transferNFT(Hash160 to, ByteString tokenId) {
        Hash160 nftContract = nftContractHash();
        Contract.call(nftContract, "transfer", CallFlags.All,
                new Object[] { to, tokenId, null });
    }

    private static int currentBlockIndex() {
        return LedgerContract.currentIndex();
    }

    private static void updateEarnings() {
        if (totalCandiesEarned() < maxCandiesToEarn()) {
            int amount = (currentBlockIndex() - lastClaimBlockIndex()) * totalVillagerCandiesStaked()
                    * dailyCandyRate();
            if (amount != 0) {
                amount = amount / BLOCKS_PER_DAY;
                incrementTotalCandiesEarned(amount);
            }

            updateLastClaimBlockIndex(currentBlockIndex());
        }
    }

    /* ONLY OWNER METHODS */

    public static void updateNftContract(Hash160 nftContract) {
        onlyOwner();
        Storage.put(ctx, nftContractkey, nftContract);
    }

    /* CONTRACT MANAGEMENT */

    public static void update(ByteString script, String manifest) {
        onlyOwner();
        ContractManagement.update(script, manifest);
    }

    @OnDeployment
    public static void deploy(Object data, boolean update) {
        Object[] arr = (Object[]) data;
        if (!update) {
            // TODO: add checks
            Storage.put(ctx, ownerKey, (Hash160) arr[0]);
            Storage.put(ctx, nftContractkey, (Hash160) arr[1]);
            Storage.put(ctx, minStakeBlockCountKey, (int) arr[2]);
            Storage.put(ctx, candiesContractKey, (Hash160) arr[3]);
            Storage.put(ctx, dailyCandyRateKey, (int) arr[4]);
            Storage.put(ctx, taxAmountKey, (int) arr[5]);
            Storage.put(ctx, pausedKey, (int) arr[6]);
        }
    }

}
