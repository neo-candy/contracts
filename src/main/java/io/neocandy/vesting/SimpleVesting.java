package io.neocandy.vesting;

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
import io.neow3j.devpack.annotations.Permission;
import io.neow3j.devpack.annotations.Safe;
import io.neow3j.devpack.constants.CallFlags;
import io.neow3j.devpack.contracts.StdLib;
import io.neow3j.devpack.events.Event1Arg;
import io.neow3j.devpack.events.Event2Args;
import io.neow3j.devpack.events.Event5Args;

@ManifestExtra(key = "author", value = "NeoCandy")
@ManifestExtra(key = "description", value = "NeoCandy Vesting Contract")
@Permission(contract = "*", methods = "transfer")
@Permission(contract = "0xfffdc93764dbaddd97c48f252a53ea4643faa3fd", methods = "*")
public class SimpleVesting {

    @DisplayName("vestingScheduleCreated")
    private static Event5Args<Hash160, Integer, Integer, Integer, Boolean> onVestingScheduleCreated;

    @DisplayName("vestingTokensCreated")
    private static Event5Args<Hash160, Integer, Integer, Hash160, Hash160> onVestingTokensCreated;

    @DisplayName("grantRevoked")
    private static Event2Args<Hash160, Integer> onGrantRevoked;

    @DisplayName("accountRegistered")
    private static Event1Arg<Hash160> onAccountRegistered;

    /*
     * See
     * https://www.timeanddate.com/date/durationresult.html?m1=1&
     * d1=1&y1=2000&m2=1&d2=1&y2=3000
     */
    private static final int THOUSAND_YEARS_DAYS = 365243;
    /*
     * Includes leap years (though it doesn't
     * really matter)
     */
    private static final int TEN_YEARS_DAYS = THOUSAND_YEARS_DAYS / 100;
    /* 86400 seconds in a day */
    private static final int SECONDS_PER_DAY = 24 * 60 * 60;
    /*
     * Saturday, January 1, 2000 0:00:00 (GMT) (see
     * https://www.epochconverter.com/)
     */
    private static final int JAN_1_2000_SECONDS = 946684800;
    private static final int JAN_1_2000_DAYS = JAN_1_2000_SECONDS / SECONDS_PER_DAY;
    private static final int JAN_1_3000_DAYS = JAN_1_2000_DAYS + THOUSAND_YEARS_DAYS;

    private static final StorageContext ctx = Storage.getStorageContext();
    private static final byte[] ownerKey = Helper.toByteArray((byte) 1);
    private static final byte[] tokenKey = Helper.toByteArray((byte) 2);
    private static final StorageMap vestingSchedules = new StorageMap(ctx, Helper.toByteArray((byte) 3));
    private static final StorageMap tokenGrants = new StorageMap(ctx, Helper.toByteArray((byte) 4));
    private static final StorageMap grantors = new StorageMap(ctx, Helper.toByteArray((byte) 5));
    private static final StorageMap isRegistered = new StorageMap(ctx, Helper.toByteArray((byte) 6));

    /**
     * Immediately grants tokens to an address, including a portion that will vest
     * over time
     * according to a set vesting schedule. The overall duration and cliff duration
     * of the grant must
     * be an even multiple of the vesting interval.
     *
     * @param beneficiary   = Address to which tokens will be granted.
     * @param totalAmount   = Total number of tokens to deposit into the account.
     * @param vestingAmount = Out of totalAmount, the number of tokens subject to
     *                      vesting.
     * @param startDay      = Start day of the grant's vesting schedule, in days
     *                      since the UNIX epoch
     *                      (start of day). The startDay may be given as a date in
     *                      the future or in the past, going as far
     *                      back as year 2000.
     * @param duration      = Duration of the vesting schedule, with respect to the
     *                      grant start day, in days.
     * @param cliffDuration = Duration of the cliff, with respect to the grant start
     *                      day, in days.
     * @param interval      = Number of days between vesting increases.
     * @param isRevocable   = True if the grant can be revoked (i.e. was a gift) or
     *                      false if it cannot
     *                      be revoked (i.e. tokens were purchased).
     */
    public static void grantVestingTokens(
            Hash160 beneficiary,
            int totalAmount,
            int vestingAmount,
            int start,
            int duration,
            int cliffDuration,
            int interval,
            boolean isRevocable) {
        onlyGrantor();

        // The vesting schedule is unique to this wallet and so will be stored here,
        setVestingSchedule(beneficiary, cliffDuration, duration, interval, isRevocable);

        // Issue grantor tokens to the beneficiary, using beneficiary's own vesting
        // schedule.
        grantVestingTokens(beneficiary, totalAmount, vestingAmount, start, beneficiary, Runtime.getCallingScriptHash());
    }

    /**
     * This variant only grants tokens if the beneficiary account has previously
     * self-registered.
     */
    public static void safeGrantVestingTokens(Hash160 grantor,
            Hash160 beneficiary,
            int totalAmount,
            int vestingAmount,
            int startDay,
            int duration,
            int cliffDuration,
            int interval,
            boolean isRevocable) {
        onlyExistingAccount(beneficiary);
        grantVestingTokens(
                beneficiary, totalAmount, vestingAmount,
                startDay, duration, cliffDuration, interval,
                isRevocable);
    }

    /**
     * returns all information about the vesting schedule directly associated with
     * the given
     * account. This can be used to double check that a uniform grantor has been set
     * up with a
     * correct vesting schedule. Also, recipients of standard (non-uniform) grants
     * can use this.
     * This method is only callable by the account holder or a grantor, so this is
     * mainly intended
     * for administrative use.
     *
     * Holders of uniform grants must use vestingAsOf() to view their vesting
     * schedule, as it is
     * stored in the grantor account.
     *
     * @param grantHolder = The address to do this for.
     *                    the special value 0 to indicate today.
     * @return = An array with the following values:
     *         vestDuration = grant duration in days.
     *         cliffDuration = duration of the cliff.
     *         vestIntervalDays = number of days between vesting periods.
     */
    @Safe
    public static Object getIntrinsicVestingSchedule(Hash160 grantHolder) {
        onlyGrantorOrSelf(grantHolder);
        VestingSchedule vestingSchedule = vestingSchedule(grantHolder);
        assert (vestingSchedule != null) : "no vesting";
        int[] result = new int[] { vestingSchedule.duration, vestingSchedule.cliffDuration, vestingSchedule.interval };
        return result;
    }

    /**
     * @dev returns all information about the grant's vesting as of the given day
     *      for the given account. Only callable by the account holder or a grantor,
     *      so
     *      this is mainly intended for administrative use.
     *
     * @param grantHolder  = The address to do this for.
     * @param onDayOrToday = The day to check for, in days since the UNIX epoch. Can
     *                     pass
     *                     the special value 0 to indicate today.
     * @return = An array with the following values:
     *         amountVested = the amount out of vestingAmount that is vested
     *         amountNotVested = the amount that is vested (equal to vestingAmount -
     *         vestedAmount)
     *         amountOfGrant = the amount of tokens subject to vesting.
     *         vestStartDay = starting day of the grant (in days since the UNIX
     *         epoch).
     *         vestDuration = grant duration in days.
     *         cliffDuration = duration of the cliff.
     *         vestIntervalDays = number of days between vesting periods.
     *         isActive = true if the vesting schedule is currently active.
     *         wasRevoked = true if the vesting schedule was revoked.
     */
    @Safe
    public static Object[] vestingForAccountAsOf(
            Hash160 grantHolder,
            int onDayOrToday) {
        onlyGrantorOrSelf(grantHolder);
        TokenGrant tokenGrant = tokenGrant(grantHolder);
        assert (tokenGrant != null) : "no grant";
        VestingSchedule vestingSchedule = vestingSchedule(tokenGrant.vestingLocation);
        assert (tokenGrant != null) : "no vesting";
        int notVestedAmount = getNotVestedAmount(grantHolder, onDayOrToday);
        int grantAmount = tokenGrant.amount;
        Object[] result = new Object[] { grantAmount - notVestedAmount, notVestedAmount, tokenGrant.startDay,
                vestingSchedule.duration, vestingSchedule.cliffDuration, vestingSchedule.interval, tokenGrant.isActive,
                tokenGrant.wasRevoked };
        return result;
    }

    /**
     * returns all information about the grant's vesting as of the given day
     * for the current account, to be called by the account holder.
     *
     * @param onDayOrToday = The day to check for, in days since the UNIX epoch. Can
     *                     pass
     *                     the special value 0 to indicate today.
     * @return = An array with the following values:
     *         amountVested = the amount out of vestingAmount that is vested
     *         amountNotVested = the amount that is vested (equal to vestingAmount -
     *         vestedAmount)
     *         amountOfGrant = the amount of tokens subject to vesting.
     *         vestStartDay = starting day of the grant (in days since the UNIX
     *         epoch).
     *         cliffDuration = duration of the cliff.
     *         vestDuration = grant duration in days.
     *         vestIntervalDays = number of days between vesting periods.
     *         isActive = true if the vesting schedule is currently active.
     *         wasRevoked = true if the vesting schedule was revoked.
     */
    @Safe
    public static Object[] vestingAsOf(
            int onDayOrToday) {

        return vestingForAccountAsOf(Runtime.getCallingScriptHash(), onDayOrToday);

    }

    /**
     * If the account has a revocable grant, this forces the grant to end based on
     * computing
     * the amount vested up to the given date. All tokens that would no longer vest
     * are returned
     * to the account of the original grantor.
     *
     * @param grantHolder = Address to which tokens will be granted.
     * @param onDay       = The date upon which the vesting schedule will be
     *                    effectively terminated,
     *                    in days since the UNIX epoch (start of day).
     */
    public static void revokeGrant(Hash160 grantHolder, int onDay) {
        onlyGrantor();
        TokenGrant tokenGrant = tokenGrant(grantHolder);
        assert (tokenGrant != null) : "no grant";
        VestingSchedule vestingSchedule = vestingSchedule(tokenGrant.vestingLocation);
        assert (tokenGrant != null) : "no vesting";
        int notVestedAmount;

        // Make sure grantor can only revoke from own pool.
        assert (Runtime.checkWitness(owner()) || Runtime.checkWitness(tokenGrant.grantor)) : "not allowed";
        // Make sure a vesting schedule has previously been set.
        assert (tokenGrant.isActive) : "no active grant";
        // Make sure it's revocable.
        assert (vestingSchedule.isRevocable) : "irrevocable";
        // Fail on likely erroneous input.
        assert (onDay <= tokenGrant.startDay + vestingSchedule.duration) : "no effect";
        // Don"t let grantor revoke anf portion of vested amount.
        assert (onDay >= today()) : "cannot revoke vested holdings";

        notVestedAmount = getNotVestedAmount(grantHolder, onDay);

        // Kill the grant by updating wasRevoked and isActive.
        tokenGrant.isActive = false;
        tokenGrant.wasRevoked = true;
        tokenGrants.put(grantHolder.toByteArray(), StdLib.serialize(tokenGrant));

        transferFrom(grantHolder, tokenGrant.grantor, notVestedAmount);

        onGrantRevoked.fire(grantHolder, onDay);
    }

    /**
     * Immediately grants tokens to an account, referencing a vesting schedule
     * which may be
     * stored in the same account (individual/one-off) or in a different
     * account (shared/uniform).
     *
     * @param beneficiary     = Address to which tokens will be granted.
     * @param totalAmount     = Total number of tokens to deposit into the account.
     * @param vestingAmount   = Out of totalAmount, the number of tokens subject to
     *                        vesting.
     * @param startDay        = Start day of the grant's vesting schedule, in days
     *                        since the UNIX epoch
     *                        (start of day). The startDay may be given as a date in
     *                        the future or in the past, going as far
     *                        back as year 2000.
     * @param vestingLocation = Account where the vesting schedule is held (must
     *                        already exist).
     * @param grantor         = Account which performed the grant. Also the account
     *                        from where the granted
     *                        funds will be withdrawn.
     */
    private static void grantVestingTokens(
            Hash160 beneficiary,
            int totalAmount,
            int vestingAmount,
            int startDay,
            Hash160 vestingLocation,
            Hash160 grantor) {
        TokenGrant grant = tokenGrant(beneficiary);
        assert (grant == null || !grant.isActive) : "grant already exists";

        // Check for valid vestingAmount
        assert (vestingAmount <= totalAmount && vestingAmount > 0
                && startDay >= JAN_1_2000_DAYS && startDay < JAN_1_3000_DAYS) : "invalid vesting params";

        VestingSchedule vestingSchedule = vestingSchedule(vestingLocation);

        // Make sure the vesting schedule we are about to use is valid.
        assert (vestingSchedule != null && vestingSchedule.isValid) : "no such vesting schedule";

        // Create and populate a token grant, referencing vesting schedule.
        TokenGrant tokenGrant = new TokenGrant(true/* isActive */,
                false/* wasRevoked */,
                startDay,
                vestingAmount,
                vestingLocation, /* The wallet address where the vesting schedule is kept. */
                grantor /* The account that performed the grant (where revoked funds would be sent) */);
        tokenGrants.put(beneficiary.toByteArray(), StdLib.serialize(tokenGrant));

        // Transfer the total number of tokens from grantor into the account's holdings.
        transferFrom(grantor, beneficiary, totalAmount);

        onVestingTokensCreated.fire(beneficiary, vestingAmount, startDay, vestingLocation, grantor);
    }

    private static void setVestingSchedule(Hash160 vestingLocation,
            int cliffDuration, int duration, int interval,
            boolean isRevocable) {

        // Check for a valid vesting schedule given (disallow absurd values to reject
        // likely bad input).
        assert (duration > 0 && duration <= TEN_YEARS_DAYS
                && cliffDuration < duration
                && interval >= 1) : "invalid vesting schedule";

        // Make sure the duration values are in harmony with interval (both should be an
        // exact multiple of interval).
        assert (duration % interval == 0 && cliffDuration % interval == 0) : "invalid cliff/duration for interval";

        // Create and populate a vesting schedule.
        VestingSchedule vs = new VestingSchedule(true/* isValid */, isRevocable, cliffDuration, duration,
                interval);

        vestingSchedules.put(vestingLocation.toByteArray(), StdLib.serialize(vs));

        // Emit the event and return success.
        onVestingScheduleCreated.fire(vestingLocation, cliffDuration, duration, interval, isRevocable);
    }

    /**
     * Determines the amount of tokens that have not vested in the given
     * account.
     *
     * The math is: not vested amount = vesting amount * (end date - on
     * date)/(end date - start date)
     *
     * @param grantHolder  = The account to check.
     * @param onDayOrToday = The day to check for, in days since the UNIX epoch. Can
     *                     pass
     *                     the special value 0 to indicate today.
     */

    private static int getNotVestedAmount(Hash160 grantHolder, int onDayOrToday) {
        TokenGrant tokenGrant = tokenGrant(grantHolder);
        assert (tokenGrant != null) : "grant does not exist";
        VestingSchedule vestingSchedule = vestingSchedule(tokenGrant.vestingLocation);
        int onDay = onDayOrToday == 0 ? today() : onDayOrToday;
        if (!tokenGrant.isActive || onDay < (tokenGrant.startDay + vestingSchedule.cliffDuration)) {
            // None are vested (all are not vested)
            return tokenGrant.amount;
        }
        // If after end of vesting, then the not vested amount is zero (all are vested).
        else if (onDay >= (tokenGrant.startDay + vestingSchedule.duration)) {
            // All are vested (none are not vested)
            return 0;
        }
        // Otherwise a fractional amount is vested.
        else {
            int daysVested = onDay - tokenGrant.startDay;
            // Adjust result rounding down to take into consideration the interval.
            int effectiveDaysVested = (daysVested / vestingSchedule.interval) * vestingSchedule.interval;
            int vested = tokenGrant.amount * effectiveDaysVested / vestingSchedule.duration;
            return tokenGrant.amount - vested;
        }
    }

    /**
     * Computes the amount of funds in the given account which are available for use
     * as of
     * the given day. If there's no vesting schedule then 0 tokens are considered to
     * be vested and
     * this just returns the full account balance.
     *
     * The math is: available amount = total funds - notVestedAmount.
     *
     * @param grantHolder = The account to check.
     * @param onDay       = The day to check for, in days since the UNIX epoch.
     */
    private static int _getAvailableAmount(Hash160 grantHolder, int onDay) {
        int totalTokens = balanceOf(grantHolder);
        int vested = totalTokens - getNotVestedAmount(grantHolder, onDay);
        return vested;
    }

    /**
     * returns true if the account has sufficient funds available to cover the given
     * amount,
     * including consideration for vesting tokens.
     *
     * @param account = The account to check.
     * @param amount  = The required amount of vested funds.
     * @param onDay   = The day to check for, in days since the UNIX epoch.
     */
    private static boolean fundsAreAvailableOn(Hash160 account, int amount, int onDay) {
        return (amount <= _getAvailableAmount(account, onDay));
    }

    /* HELPER FUNCTIONS */

    public static void transfer(Hash160 to, int amount) {
        onlyIfFundsAvailableNow(Runtime.getCallingScriptHash(), amount);
        Contract.call(token(), "transfer", CallFlags.All,
                new Object[] { Runtime.getExecutingScriptHash(), to, amount, null });
    }

    private static void safeTransfer(Hash160 to, int amount) {
        onlyExistingAccount(to);
        Contract.call(token(), "transfer", CallFlags.All,
                new Object[] { Runtime.getExecutingScriptHash(), to, amount, null });
    }

    private static void transferFrom(Hash160 from, Hash160 to, int amount) {
        Contract.call(token(), "transfer", CallFlags.All,
                new Object[] { from, to, amount, null });
    }

    private static int balanceOf(Hash160 from) {
        return (int) Contract.call(token(), "balanceOf", CallFlags.All,
                new Object[] { from });
    }

    private static void onlyIfFundsAvailableNow(Hash160 account, int amount) {
        // Distinguish insufficient overall balance from insufficient vested funds
        // balance in failure msg.
        assert (fundsAreAvailableOn(account, amount, today()))
                : balanceOf(account) < amount ? "insufficient funds" : "insufficient vested funds";
    }

    private static TokenGrant tokenGrant(Hash160 beneficiary) {
        ByteString result = tokenGrants.get(beneficiary.toByteArray());
        return result != null ? (TokenGrant) StdLib.deserialize(result) : null;
    }

    private static VestingSchedule vestingSchedule(Hash160 account) {
        ByteString result = vestingSchedules.get(account.toByteArray());
        return result != null ? (VestingSchedule) StdLib.deserialize(result) : null;
    }

    private static int today() {
        return Runtime.getTime() / SECONDS_PER_DAY;
    }

    /* REGISTRATION */

    /**
     * This registers the signing address as a known address. Operations that
     * transfer responsibility
     * may require the target account to be a registered account, to protect the
     * system from getting into a
     * state where administration or a large amount of funds can become forever
     * inaccessible.
     */
    public static void registerAccount(Hash160 account) {
        assert (Runtime.checkWitness(account)) : "no authorization";
        isRegistered.put(account.toByteArray(), 1);
        onAccountRegistered.fire(account);
    }

    @Safe
    public static boolean isRegistered(Hash160 account) {
        return isRegistered.get(account.toByteArray()) != null;
    }

    /* ROLES AND PERMISSIONS */

    @Safe
    public static boolean isGrantor(Hash160 account) {
        return grantors.get(account.toByteArray()) != null;
    }

    public static void addGrantor(Hash160 account, boolean isUniformGrantor) {
        assert (Runtime.checkWitness(owner())) : "no authorization";
        assert (account != Hash160.zero() && Hash160.isValid(account)) : "not valid account";
        grantors.put(account.toByteArray(), isUniformGrantor ? 1 : 0);
    }

    public static void removeGrantor(Hash160 account, boolean isUniformGrantor) {
        assert (Runtime.checkWitness(owner())) : "no authorization";
        grantors.delete(account.toByteArray());
    }

    @Safe
    public static boolean isUniformGrantor(Hash160 account) {
        return isGrantor(account) && grantors.getBoolean(account.toByteArray());
    }

    private static void onlyExistingAccount(Hash160 account) {
        assert (isRegistered(account)) : "account not registered";
    }

    private static boolean callerIsSigner() {
        return Runtime.checkWitness(Runtime.getCallingScriptHash());
    }

    private static void onlyGrantorOrSelf(Hash160 account) {
        assert (isGrantor(account) || Runtime.checkWitness(account)) : "onlyGrantorOrSelf";
    }

    private static void onlyGrantor() {
        assert (isGrantor(Runtime.getCallingScriptHash()) && callerIsSigner())
                : "onlyGrantor";
    }

    private static void onlyUniformGrantor() {
        assert (isUniformGrantor(Runtime.getCallingScriptHash())
                && callerIsSigner()) : "onlyUniformGrantor";
    }

    /* CONTRACT */

    @OnDeployment
    public static void deploy(Object data, boolean update) {
        if (!update) {
            Object[] arr = (Object[]) data;
            Storage.put(ctx, ownerKey, (Hash160) arr[0]);
            Storage.put(ctx, tokenKey, (Hash160) arr[1]);
        }
    }

    @Safe
    public static Hash160 owner() {
        return new Hash160(Storage.get(ctx, ownerKey));
    }

    @Safe
    public static Hash160 token() {
        return new Hash160(Storage.get(ctx, tokenKey));
    }

}
