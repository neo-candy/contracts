package io.neocandy.games.candyclash;

import org.junit.jupiter.api.BeforeAll;

import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.neocandy.tokens.nep17.NeoCandy;
import io.neow3j.contract.FungibleToken;
import io.neow3j.contract.GasToken;
import io.neow3j.contract.SmartContract;
import io.neow3j.protocol.Neow3j;

import io.neow3j.test.ContractTestExtension;
import io.neow3j.test.DeployConfig;
import io.neow3j.test.DeployConfiguration;
import io.neow3j.test.DeployContext;
import io.neow3j.types.ContractParameter;
import io.neow3j.wallet.Account;

import static io.neow3j.types.ContractParameter.hash160;
import static io.neow3j.types.ContractParameter.integer;
import static io.neow3j.types.ContractParameter.string;
import static io.neow3j.types.ContractParameter.bool;

import java.math.BigInteger;
import java.util.Arrays;

import static io.neow3j.types.ContractParameter.array;
import static io.neocandy.games.candyclash.TestHelper.transfer17;

public abstract class AbstractCandyClashTest {

    protected static final Logger log = LoggerFactory.getLogger(AbstractCandyClashTest.class);

    protected static Account alice;
    protected static Account bob;
    protected static Account charlie;

    @RegisterExtension
    protected static ContractTestExtension ext = new ContractTestExtension();

    protected static Neow3j neow3j;
    protected static SmartContract candyClashNft;
    protected static SmartContract candyClashStaking;
    protected static FungibleToken candyToken;
    protected static GasToken gas;

    /**
     * DEPLOY_CONFIG
     */
    protected static final int GAS_PRICE_PER_NFT = 5_00000000;
    private static final long CANDY_PRICE_PER_NFT = 500_000000000L;
    private static final int GENESIS_AMOUNT = 2000;
    private static final int MAX_AMOUNT = 10000;
    private static final int ROYALTIES_AMOUNT = 1000;
    private static final int MAX_MINT_AMOUNT = 10;
    protected static final long INITIAL_CANDY_AMOUNT = 100000000000000L;
    protected static final int MIN_STAKE_BLOCK_COUNT = 5; // 11520; // ~2 days
    private static final long DAILY_CANDY_RATE = 1000_000000000L;
    private static final int TAX_IN_PERCENT = 20;
    private static final String IMAGE_BASE = "ipfs://Qmeqst3PBH9CQUxmZrcoT45HeGFVd89aUYrHN6vwFuhTDJ";

    @BeforeAll
    public static void setup() throws Exception {
        neow3j = ext.getNeow3j();
        alice = ext.getAccount("NQcSTBwSJs7hcFUZzku2QdPNLe2dkTGok2");
        bob = ext.getAccount("NhsVB4etFffHjpLoj2ngVkkfNbtxiSSmbk");
        charlie = ext.getAccount("NdbtgSku2qLuwsBBzLx3FLtmmMdm32Ktor");
        gas = new GasToken(neow3j);
        candyToken = new FungibleToken(ext.getDeployedContract(NeoCandy.class).getScriptHash(), neow3j);
        candyClashStaking = ext.getDeployedContract(CandyClashStaking.class);
        candyClashNft = new SmartContract(ext.getDeployedContract(CandyClashNFT.class).getScriptHash(),
                ext.getNeow3j());
        try {

            // send candy from alice to bob
            transfer17(candyToken, alice, bob.getScriptHash(), BigInteger.valueOf(100L), null, neow3j);

            // send candy from alice to staking contract
            transfer17(candyToken, alice, candyClashStaking.getScriptHash(),
                    BigInteger.valueOf(INITIAL_CANDY_AMOUNT),
                    null, neow3j);

            TestHelper.invokeWrite(candyClashNft, TestHelper.CONNECT_STAKING_CONTRACT,
                    Arrays.asList(ContractParameter.hash160(candyClashStaking.getScriptHash())), alice, neow3j);

        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    @DeployConfig(NeoCandy.class)
    public static DeployConfiguration configureNeoCandy() throws Exception {
        DeployConfiguration config = new DeployConfiguration();
        ContractParameter owner = hash160(ext.getAccount("NQcSTBwSJs7hcFUZzku2QdPNLe2dkTGok2").getScriptHash());
        config.setDeployParam(owner);
        return config;
    }

    @DeployConfig(CandyClashNFT.class)
    public static DeployConfiguration configureCandyClashNFT(DeployContext ctx) throws Exception {
        DeployConfiguration config = new DeployConfiguration();
        ContractParameter owner = hash160(ext.getAccount("NQcSTBwSJs7hcFUZzku2QdPNLe2dkTGok2").getScriptHash());
        ContractParameter gasPrice = integer(GAS_PRICE_PER_NFT);
        ContractParameter candyPrice = integer(BigInteger.valueOf(CANDY_PRICE_PER_NFT));
        ContractParameter candy = hash160(ctx.getDeployedContract(NeoCandy.class).getScriptHash());
        ContractParameter imageBase = string(IMAGE_BASE);
        ContractParameter maxTokens = integer(MAX_AMOUNT);
        ContractParameter maxGenesis = integer(GENESIS_AMOUNT);
        ContractParameter isPaused = bool(false);
        ContractParameter royaltiesReceiverAddress = string(
                ext.getAccount("NQcSTBwSJs7hcFUZzku2QdPNLe2dkTGok2").getAddress());
        ContractParameter royaltiesAmount = integer(ROYALTIES_AMOUNT);
        ContractParameter maxMintAmount = integer(MAX_MINT_AMOUNT);
        ContractParameter params = array(owner, gasPrice, candyPrice, candy, imageBase, maxTokens,
                maxGenesis, isPaused, royaltiesReceiverAddress, royaltiesAmount, maxMintAmount);
        config.setDeployParam(params);
        return config;
    }

    @DeployConfig(CandyClashStaking.class)
    public static DeployConfiguration configureCandyClashStaking(DeployContext ctx) throws Exception {
        DeployConfiguration config = new DeployConfiguration();
        ContractParameter owner = hash160(ext.getAccount("NQcSTBwSJs7hcFUZzku2QdPNLe2dkTGok2").getScriptHash());
        ContractParameter nftContract = hash160(ctx.getDeployedContract(CandyClashNFT.class).getScriptHash());
        ContractParameter minStakeBlockCount = integer(MIN_STAKE_BLOCK_COUNT);
        ContractParameter candy = hash160(ctx.getDeployedContract(NeoCandy.class).getScriptHash());
        ContractParameter dailyCandyRate = integer(BigInteger.valueOf(DAILY_CANDY_RATE));
        ContractParameter tax = integer(TAX_IN_PERCENT);
        ContractParameter isPaused = bool(false);
        config.setDeployParam(
                ContractParameter.array(owner, nftContract, minStakeBlockCount, candy, dailyCandyRate, tax, isPaused));
        return config;
    }
}
