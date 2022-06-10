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
@Permission(contract = "*", methods = { "transfer", "balanceOf", "tokenType", "tokenLevel", "tokenActions",
        "propertiesJson", "removeActionPoint" })
@Permission(nativeContract = NativeContract.ContractManagement)
public class CandyClashStaking {

    @DisplayName("Debug")
    private static Event1Arg<Object> onDebug;

    @DisplayName("TokenStaked")
    private static Event3Args<Hash160, ByteString, Integer> onTokenStaked;

    @DisplayName("OnNep17Payment")
    static Event3Args<Hash160, Integer, Object> onPayment;

    @DisplayName("VillagerClaimed")
    static Event3Args<ByteString, Integer, Boolean> onVillagerClaim;

    @DisplayName("VillainClaimed")
    static Event3Args<ByteString, Integer, Boolean> onVillainClaim;

    private static final int BLOCKS_PER_DAY = 4 * 60 * 24;

    private static final byte[] ownerKey = Helper.toByteArray((byte) 1);
    private static final byte[] nftContractkey = Helper.toByteArray((byte) 2);
    private static final byte[] totalSugarStakedKey = Helper.toByteArray((byte) 3);
    private static final byte[] candyPerSugarKey = Helper.toByteArray((byte) 4);
    private static final byte[] totalVillagersStakedKey = Helper.toByteArray((byte) 5);
    private static final byte[] candiesContractKey = Helper.toByteArray((byte) 6);
    private static final byte[] isPausedKey = Helper.toByteArray((byte) 7);
    private static final byte[] minStakeBlockCountKey = Helper.toByteArray((byte) 8);
    private static final byte[] totalCandyEarnedKey = Helper.toByteArray((byte) 9);
    private static final byte[] dailyCandyRateKey = Helper.toByteArray((byte) 10);
    private static final byte[] lastClaimBlockIndexKey = Helper.toByteArray((byte) 11);
    private static final byte[] unaccountedRewardsKey = Helper.toByteArray((byte) 12);
    private static final byte[] taxAmountKey = Helper.toByteArray((byte) 13);
    private static final byte[] totalVillainsStakedKey = Helper.toByteArray((byte) 17);
    private static final byte[] tokensOfKey = Helper.toByteArray((byte) 18);
    private static final byte[] totalVillainClaimsKey = Helper.toByteArray((byte) 19);
    private static final byte[] totalVillagerClaimsKey = Helper.toByteArray((byte) 20);

    /* METADATA VALUES */
    private static final String TYPE_VILLAIN = "Villain";

    private static final StorageContext ctx = Storage.getStorageContext();
    private static final StorageMap villainStakes = new StorageMap(ctx, Helper.toByteArray((byte) 101));
    private static final StorageMap villagerStakes = new StorageMap(ctx, Helper.toByteArray((byte) 102));
    private static final StorageMap ownerOfMap = new StorageMap(ctx, Helper.toByteArray((byte) 103));
    private static final StorageMap villagerClaims = new StorageMap(ctx, Helper.toByteArray((byte) 105));
    private static final StorageMap villainClaims = new StorageMap(ctx, Helper.toByteArray((byte) 106));

    @OnNEP17Payment
    public static void onPayment(Hash160 from, int amount, Object data) throws Exception {
        if (from != owner()) {
            throw new Exception("onPayment_onlyOwner");
        }
        if (Runtime.getCallingScriptHash() != candyContractHash()) {
            throw new Exception("onPayment_onlyCandy");

        }
        onPayment.fire(from, amount, data);
    }

    @OnNEP11Payment
    public static void onPayment(Hash160 sender, int amount, ByteString tokenId, Object data) throws Exception {
        if (isPaused()) {
            throw new Exception("onPayment_isPaused");
        }
        if (candyBalance() <= 0) {
            throw new Exception("onPayment_emptyTreasury");
        }
        if (Runtime.getCallingScriptHash() != nftContractHash()) {
            throw new Exception("onPayment_invalidContract");
        }
        if (getTokenActions(tokenId) <= 0) {
            throw new Exception("onPayment_noActionsLeft");
        }
        // removeActionPoint(tokenId);
        if (getTokenType(tokenId) == TYPE_VILLAIN) {
            int level = getTokenLevel(tokenId);
            int updatedLevelValue = level + 10;
            stakeVillain(sender, tokenId, updatedLevelValue);
        } else {
            stakeVillager(sender, tokenId);
        }

        new StorageMap(ctx, Utils.createStorageMapPrefix(sender, tokensOfKey)).put(tokenId, 1);
        ownerOfMap.put(tokenId, sender);
    }

    @Safe
    public static Hash160 getOwnerOfToken(ByteString tokenId) {
        return new Hash160(ownerOfMap.get(tokenId));
    }

    @Safe
    public static List<String> tokensOf(Hash160 owner) throws Exception {
        Iterator<Struct<ByteString, ByteString>> iterator = (Iterator<Struct<ByteString, ByteString>>) Storage.find(
                ctx.asReadOnly(),
                Utils.createStorageMapPrefix(owner, tokensOfKey),
                FindOptions.RemovePrefix);

        List<String> tokens = new List();
        while (iterator.next()) {
            ByteString result = (ByteString) iterator.get().key;
            tokens.add(getPropertiesJson(result));
        }
        return tokens;
    }

    @Safe
    public static int availableClaimAmount(ByteString[] tokenIds) {
        int claimAmount = 0;
        for (int i = 0; i < tokenIds.length; i++) {
            if (candyBalance() == 0) {
                return 0;
            }
            if (getTokenType(tokenIds[i]) == TYPE_VILLAIN) {
                int sugarValue = getTokenLevel(tokenIds[i]);
                int stake = villainStake(tokenIds[i]);
                claimAmount += sugarValue * (candyPerLevel() - stake);
            } else {
                int stake = villagerStake(tokenIds[i]);
                if (totalCandyEarned() < candyBalance()) {
                    // int claimBonus = stringToInt(properties.get(BONUS));
                    claimAmount += (currentBlockIndex() - stake) * dailyCandyRate() / BLOCKS_PER_DAY;
                } else if (stake < lastClaimBlockIndex()) {
                    claimAmount += (lastClaimBlockIndex() - stake) * dailyCandyRate() / BLOCKS_PER_DAY;
                }
            }
        }
        return claimAmount;

    }

    @Safe
    public static int totalLevelsStaked() {
        return Storage.getIntOrZero(ctx, totalSugarStakedKey);
    }

    @Safe
    public static int taxAmount() {
        return Storage.getIntOrZero(ctx, taxAmountKey);
    }

    @Safe
    public static int totalVillagersStaked() {
        return Storage.getIntOrZero(ctx, totalVillagersStakedKey);
    }

    @Safe
    public static int totalVillainsStaked() {
        return Storage.getIntOrZero(ctx, totalVillainsStakedKey);
    }

    @Safe
    public static int candyBalance() {
        Hash160 candyContract = candyContractHash();
        return (int) Contract.call(candyContract, "balanceOf", CallFlags.All,
                new Object[] { Runtime.getExecutingScriptHash() });
    }

    @Safe
    public static int minStakeBlockCount() {
        return Storage.getInt(ctx, minStakeBlockCountKey);
    }

    /*
     * Total candy amount that has been earned. The candy does not have to be
     * claimed yet.
     */
    @Safe
    public static int totalCandyEarned() {
        return Storage.getIntOrZero(ctx, totalCandyEarnedKey);
    }

    @Safe
    public static int villainClaimsOf(Hash160 owner) {
        return villainClaims.getIntOrZero(owner.toByteArray());
    }

    @Safe
    public static int villagerClaimsOf(Hash160 owner) {
        return villagerClaims.getIntOrZero(owner.toByteArray());
    }

    @Safe
    public static int totalVillainClaims() {
        return Storage.getIntOrZero(ctx, totalVillainClaimsKey);
    }

    @Safe
    public static int totalVillagerClaims() {
        return Storage.getIntOrZero(ctx, totalVillagerClaimsKey);
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
        return Storage.getInt(ctx, isPausedKey) == 1 ? true : false;
    }

    @Safe
    public static int candyPerLevel() {
        return Storage.getIntOrZero(ctx, candyPerSugarKey);
    }

    @Safe
    public static Hash160 candyContractHash() {
        return new Hash160(Storage.get(ctx, candiesContractKey));
    }

    @Safe
    public static Hash160 nftContractHash() {
        return new Hash160(Storage.get(ctx, nftContractkey));
    }

    @Safe
    public static int villagerStake(ByteString tokenId) {
        return villagerStakes.getInt(tokenId);
    }

    @Safe
    public static int villainStake(ByteString tokenId) {
        return villainStakes.getInt(tokenId);
    }

    @Safe
    public static int unaccountedRewards() {
        return Storage.getIntOrZero(ctx, unaccountedRewardsKey);
    }

    @Safe
    public static int lastClaimBlockIndex() {
        return Storage.getIntOrZero(ctx, lastClaimBlockIndexKey);
    }

    @Safe
    public static int currentBlockIndex() {
        return LedgerContract.currentIndex();
    }

    /* PUBLIC WRITE */

    public static int claim(ByteString[] tokenIds, boolean unstake) throws Exception {
        if (isPaused()) {
            throw new Exception("claim_isPaused");
        }
        if (tokenIds.length == 0) {
            throw new Exception("claim_noTokenIdsProvided");
        }
        updateEarnings();
        int totalClaims = 0;
        Hash160 owner = null;
        for (int i = 0; i < tokenIds.length; i++) {
            owner = getOwnerOfToken(tokenIds[i]);
            if (!Runtime.checkWitness(owner)) {
                throw new Exception("claim_noAuth");
            }
            if (candyBalance() <= 0) {
                throw new Exception("claim_emptyTreasury");
            }
            if (getTokenType(tokenIds[i]) == TYPE_VILLAIN) {
                int level = getTokenLevel(tokenIds[i]);
                int villainCaims = villainEarnings(tokenIds[i], unstake, level, owner);
                totalClaims += villainCaims;
                increaseVillainEarnings(owner, villainCaims);
                increaseTotalVillainClaims(villainCaims);

            } else {
                int villagerClaims = villagerEarnings(tokenIds[i], unstake, owner);
                totalClaims += villagerClaims;
                increaseVillagerEarnings(owner, villagerClaims);
                increaseTotalVillagerClaims(villagerClaims);
            }
        }
        assert owner != null;
        if (totalClaims == 0) {
            return 0;
        }
        transferCandy(owner, totalClaims);
        return totalClaims;
    }

    /* UTILS */

    private static void stakeVillain(Hash160 owner, ByteString tokenId, int level) {
        incrementTotalSugarStaked(level);
        int candyPerLevel = candyPerLevel();
        updateVillainStake(tokenId, candyPerLevel);
        incTotalVillainsStaked(1);
        onTokenStaked.fire(owner, tokenId, candyPerLevel);
    }

    private static void stakeVillager(Hash160 owner, ByteString tokenId) {
        updateEarnings();
        int blockIndex = currentBlockIndex();
        updateVillagerStake(tokenId, blockIndex);
        incTotalVillagersStaked(1);
        onTokenStaked.fire(owner, tokenId, blockIndex);
    }

    private static int villagerEarnings(ByteString tokenId, boolean unstake, Hash160 owner) throws Exception {
        int stake = villagerStake(tokenId);
        int earnings = 0;
        if (totalCandyEarned() < candyBalance()) {
            earnings = (currentBlockIndex() - stake) * dailyCandyRate() / BLOCKS_PER_DAY;
        } else {
            earnings = (lastClaimBlockIndex() - stake) * dailyCandyRate() / BLOCKS_PER_DAY;
        }
        if (unstake) {
            if (getTokenActions(tokenId) <= 0) {
                throw new Exception("claim_noActionsLeft");
            }
            // removeActionPoint(tokenId);
            if (currentBlockIndex() - stake < minStakeBlockCount()) {
                throw new Exception("villagerEarnings_minStakeDuration");
            }
            boolean loseCandiesToVillain = Runtime.getRandom() % 2 == 0;
            if (loseCandiesToVillain) {
                payTax(earnings);
                earnings = 0;
            }
            deleteVillagerStake(tokenId);
            decTotalVillagersStaked(1);
            deleteOwnerOfToken(tokenId);
            new StorageMap(ctx, Utils.createStorageMapPrefix(owner, tokensOfKey)).delete(tokenId);
            safeTransferNft(owner, tokenId);
        } else {
            int taxedAmount = earnings * taxAmount() / 100;
            payTax(taxedAmount);
            earnings = earnings - taxedAmount;
            updateVillagerStake(tokenId, currentBlockIndex());
        }
        onVillagerClaim.fire(tokenId, earnings, unstake);
        return earnings;
    }

    private static int villainEarnings(ByteString tokenId, boolean unstake, int level, Hash160 owner) throws Exception {
        int stake = villainStake(tokenId);
        int claimAmount = level * (candyPerLevel() - stake);
        if (unstake) {
            if (getTokenActions(tokenId) <= 0) {
                throw new Exception("claim_noActionsLeft");
            }
            // removeActionPoint(tokenId);
            decTotalLevelsStaked(level);
            deleteVillainStake(tokenId);
            decTotalVillainsStaked(1);
            deleteOwnerOfToken(tokenId);
            new StorageMap(ctx, Utils.createStorageMapPrefix(owner, tokensOfKey)).delete(tokenId);
            safeTransferNft(owner, tokenId);
        } else {
            updateVillainStake(tokenId, candyPerLevel());
        }
        onVillainClaim.fire(tokenId, claimAmount, unstake);
        return claimAmount;
    }

    private static void payTax(int amount) {
        if (totalLevelsStaked() == 0) {
            incrementUnaccountedRewards(amount);
            return;
        }
        int increment = (amount + unaccountedRewards()) / totalLevelsStaked();
        incCandyPerLevel(increment);
        resetUnaccountedRewards();

    }

    private static void incrementTotalSugarStaked(int amount) {
        Storage.put(ctx, totalSugarStakedKey, totalLevelsStaked() + amount);
    }

    private static void decTotalLevelsStaked(int amount) {
        Storage.put(ctx, totalSugarStakedKey, totalLevelsStaked() - amount);
    }

    private static void incTotalVillagersStaked(int amount) {
        Storage.put(ctx, totalVillagersStakedKey, totalVillagersStaked() + amount);
    }

    private static void decTotalVillagersStaked(int amount) {
        Storage.put(ctx, totalVillagersStakedKey, totalVillagersStaked() - amount);
    }

    private static void incTotalVillainsStaked(int amount) {
        Storage.put(ctx, totalVillainsStakedKey, totalVillainsStaked() + amount);
    }

    private static void decTotalVillainsStaked(int amount) {
        Storage.put(ctx, totalVillainsStakedKey, totalVillainsStaked() - amount);
    }

    private static void incrementTotalCandyEarned(int amount) {
        Storage.put(ctx, totalCandyEarnedKey, totalCandyEarned() + amount);
    }

    private static void updateVillainStake(ByteString tokenId, int value) {
        villainStakes.put(tokenId, value);
    }

    private static void updateVillagerStake(ByteString tokenId, int value) {
        villagerStakes.put(tokenId, value);
    }

    private static void deleteVillainStake(ByteString tokenId) {
        villainStakes.delete(tokenId);
    }

    private static void deleteOwnerOfToken(ByteString tokenId) {
        ownerOfMap.delete(tokenId);
    }

    private static void deleteVillagerStake(ByteString tokenId) {
        villagerStakes.delete(tokenId);
    }

    private static void increaseVillagerEarnings(Hash160 account, int amount) {
        int result = villagerClaims.getIntOrZero(account.toByteArray());
        villagerClaims.put(account.toByteArray(), result + amount);
    }

    private static void increaseVillainEarnings(Hash160 account, int amount) {
        int result = villainClaims.getIntOrZero(account.toByteArray());
        villainClaims.put(account.toByteArray(), result + amount);
    }

    private static void increaseTotalVillagerClaims(int amount) {
        int result = Storage.getIntOrZero(ctx, totalVillagerClaimsKey);
        Storage.put(ctx, totalVillagerClaimsKey, result + amount);
    }

    private static void increaseTotalVillainClaims(int amount) {
        int result = Storage.getIntOrZero(ctx, totalVillainClaimsKey);
        Storage.put(ctx, totalVillainClaimsKey, result + amount);
    }

    private static void updateLastClaimBlockIndex(int value) {
        Storage.put(ctx, lastClaimBlockIndexKey, value);
    }

    private static void incrementUnaccountedRewards(int amount) {
        Storage.put(ctx, unaccountedRewardsKey, unaccountedRewards() + amount);
    }

    private static void resetUnaccountedRewards() {
        Storage.put(ctx, unaccountedRewardsKey, 0);
    }

    private static void incCandyPerLevel(int amount) {
        Storage.put(ctx, candyPerSugarKey, candyPerLevel() + amount);

    }

    private static void transferCandy(Hash160 to, int amount) {
        Hash160 candyContract = candyContractHash();
        Contract.call(candyContract, "transfer", CallFlags.All,
                new Object[] { Runtime.getExecutingScriptHash(), to, amount, null });
    }

    private static void safeTransferNft(Hash160 to, ByteString tokenId) {
        Hash160 nftContract = nftContractHash();
        Contract.call(nftContract, "transfer", CallFlags.All,
                new Object[] { to, tokenId, null });
    }

    private static void updateEarnings() {
        if (totalCandyEarned() < candyBalance()) {
            int amount = (currentBlockIndex() - lastClaimBlockIndex()) * totalVillagersStaked()
                    * dailyCandyRate();
            if (amount != 0) {
                amount = amount / BLOCKS_PER_DAY;
                incrementTotalCandyEarned(amount);
            }

            updateLastClaimBlockIndex(currentBlockIndex());
        }
    }

    /* CONTRACT CALLS */

    private static String getPropertiesJson(ByteString tokenId) {
        Hash160 nftContract = nftContractHash();
        return (String) Contract.call(nftContract, "propertiesJson", CallFlags.All, new Object[] { tokenId });
    }

    private static int removeActionPoint(ByteString tokenId) {
        Hash160 nftContract = nftContractHash();
        return (int) Contract.call(nftContract, "removeActionPoint", CallFlags.All, new Object[] { tokenId });
    }

    private static int getTokenActions(ByteString tokenId) {
        Hash160 nftContract = nftContractHash();
        return (int) Contract.call(nftContract, "tokenActions", CallFlags.All, new Object[] { tokenId });
    }

    private static int getTokenLevel(ByteString tokenId) {
        Hash160 nftContract = nftContractHash();
        return (int) Contract.call(nftContract, "tokenLevel", CallFlags.All, new Object[] { tokenId });
    }

    private static String getTokenType(ByteString tokenId) {
        Hash160 nftContract = nftContractHash();
        return (String) Contract.call(nftContract, "tokenType", CallFlags.All,
                new Object[] { tokenId });
    }

    /* PERMISSION CHECKS */

    private static void onlyOwner() throws Exception {
        if (!Runtime.checkWitness(owner())) {
            throw new Exception("onlyOwner");
        }
    }

    /* ONLY OWNER METHODS */

    public static void updateNftContract(Hash160 nftContract) throws Exception {
        onlyOwner();
        Storage.put(ctx, nftContractkey, nftContract);
    }

    public static void updatePause(boolean paused) throws Exception {
        onlyOwner();
        Storage.put(ctx, isPausedKey, paused ? 1 : 0);
    }

    /* CONTRACT MANAGEMENT */

    public static void update(ByteString script, String manifest) throws Exception {
        onlyOwner();
        ContractManagement.update(script, manifest);
    }

    @OnDeployment
    public static void deploy(Object data, boolean update) {
        Object[] arr = (Object[]) data;
        if (!update) {
            Storage.put(ctx, ownerKey, (Hash160) arr[0]);
            Storage.put(ctx, nftContractkey, (Hash160) arr[1]);
            Storage.put(ctx, minStakeBlockCountKey, (int) arr[2]);
            Storage.put(ctx, candiesContractKey, (Hash160) arr[3]);
            Storage.put(ctx, dailyCandyRateKey, (int) arr[4]);
            Storage.put(ctx, taxAmountKey, (int) arr[5]);
            Storage.put(ctx, isPausedKey, (int) arr[6]);
        }
    }

}
