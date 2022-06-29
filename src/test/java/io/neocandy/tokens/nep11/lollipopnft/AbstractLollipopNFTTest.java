package io.neocandy.tokens.nep11.lollipopnft;

import org.junit.jupiter.api.BeforeAll;

import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.neocandy.tokens.nep11.LollipopNFT;
import io.neocandy.tokens.nep17.NeoCandy;
import io.neow3j.contract.FungibleToken;
import io.neow3j.contract.GasToken;
import io.neow3j.contract.NonFungibleToken;
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

import static io.neow3j.types.ContractParameter.array;

public abstract class AbstractLollipopNFTTest {

    protected static final Logger log = LoggerFactory.getLogger(AbstractLollipopNFTTest.class);

    protected static Account alice;
    protected static Account bob;
    protected static Account charlie;

    @RegisterExtension
    protected static ContractTestExtension ext = new ContractTestExtension();

    protected static Neow3j neow3j;
    protected static NonFungibleToken lollipopNFT;
    protected static FungibleToken candyToken;
    protected static GasToken gas;

    /**
     * DEPLOY_CONFIG
     */
    private static final int MAX_SUPPLY = 222;
    private static final int ROYALTIES_AMOUNT = 1000;
    private static final String IMAGE_BASE = "ipfs://Qmeqst3PBH9CQUxmZrcoT45HeGFVd89aUYrHN6vwFuhTDJ/";

    @BeforeAll
    public static void setup() throws Exception {
        neow3j = ext.getNeow3j();
        alice = ext.getAccount("NQcSTBwSJs7hcFUZzku2QdPNLe2dkTGok2");
        bob = ext.getAccount("NhsVB4etFffHjpLoj2ngVkkfNbtxiSSmbk");
        charlie = ext.getAccount("NdbtgSku2qLuwsBBzLx3FLtmmMdm32Ktor");
        gas = new GasToken(neow3j);
        candyToken = new FungibleToken(ext.getDeployedContract(NeoCandy.class).getScriptHash(), neow3j);
        lollipopNFT = new NonFungibleToken(ext.getDeployedContract(LollipopNFT.class).getScriptHash(), neow3j);
    }

    @DeployConfig(NeoCandy.class)
    public static DeployConfiguration configureNeoCandy() throws Exception {
        DeployConfiguration config = new DeployConfiguration();
        ContractParameter owner = hash160(ext.getAccount("NQcSTBwSJs7hcFUZzku2QdPNLe2dkTGok2").getScriptHash());
        config.setDeployParam(owner);
        return config;
    }

    @DeployConfig(LollipopNFT.class)
    public static DeployConfiguration configureNeoCandyNFT(DeployContext ctx) throws Exception {
        DeployConfiguration config = new DeployConfiguration();
        ContractParameter owner = hash160(ext.getAccount("NQcSTBwSJs7hcFUZzku2QdPNLe2dkTGok2").getScriptHash());
        ContractParameter candy = hash160(ctx.getDeployedContract(NeoCandy.class).getScriptHash());
        ContractParameter imageBase = string(IMAGE_BASE);
        ContractParameter maxSupply = integer(MAX_SUPPLY);
        ContractParameter royaltiesReceiverAddress = string(
                ext.getAccount("NQcSTBwSJs7hcFUZzku2QdPNLe2dkTGok2").getAddress());
        ContractParameter royaltiesAmount = integer(ROYALTIES_AMOUNT);
        ContractParameter params = array(owner, candy, imageBase, maxSupply, royaltiesReceiverAddress,
                royaltiesAmount);
        config.setDeployParam(params);
        return config;
    }
}
