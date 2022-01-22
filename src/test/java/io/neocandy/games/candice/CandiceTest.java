package io.neocandy.games.candice;

import org.junit.jupiter.api.BeforeAll;

import org.junit.jupiter.api.extension.RegisterExtension;

import io.neocandy.games.Candice;
import io.neocandy.token.NeoCandy;
import io.neow3j.contract.FungibleToken;
import io.neow3j.contract.SmartContract;
import io.neow3j.protocol.Neow3j;
import io.neow3j.protocol.core.response.NeoSendRawTransaction;
import io.neow3j.test.ContractTestExtension;
import io.neow3j.test.DeployConfig;
import io.neow3j.test.DeployConfiguration;
import io.neow3j.test.DeployContext;
import io.neow3j.transaction.Transaction;
import io.neow3j.types.ContractParameter;
import io.neow3j.types.Hash160;
import io.neow3j.utils.Await;
import io.neow3j.wallet.Account;

import static io.neow3j.types.ContractParameter.hash160;

import java.math.BigInteger;

import static io.neow3j.types.ContractParameter.array;

public abstract class CandiceTest {
    protected static final String QUERY_ROOM = "queryRoom";
    protected static final String QUERY_GAME = "queryGame";
    protected static final String PLAY = "play";

    protected static Account alice;
    protected static Account bob;

    @RegisterExtension
    protected static ContractTestExtension ext = new ContractTestExtension();

    protected static Neow3j neow3j;
    protected static SmartContract contract;
    protected static FungibleToken token;

    @BeforeAll
    public static void setup() throws Exception {
        neow3j = ext.getNeow3j();
        alice = ext.getAccount("NQcSTBwSJs7hcFUZzku2QdPNLe2dkTGok2");
        bob = ext.getAccount("NhsVB4etFffHjpLoj2ngVkkfNbtxiSSmbk");
        contract = ext.getDeployedContract(Candice.class);
        token = new FungibleToken(ext.getDeployedContract(NeoCandy.class).getScriptHash(), neow3j);

        try {
            transfer(token, alice, bob.getScriptHash(), 100, null);
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

    @DeployConfig(Candice.class)
    public static DeployConfiguration configureCandice(DeployContext ctx) throws Exception {
        DeployConfiguration config = new DeployConfiguration();
        ContractParameter owner = array(hash160(ext.getAccount("NQcSTBwSJs7hcFUZzku2QdPNLe2dkTGok2").getScriptHash()),
                hash160(ctx.getDeployedContract(NeoCandy.class).getScriptHash()));
        config.setDeployParam(owner);
        config.setSubstitution("<NEO_CANDY_CONTRACT_HASH_PLACEHOLDER>",
                ctx.getDeployedContract(NeoCandy.class).getScriptHash().toString());
        return config;
    }

    protected static void transfer(
            FungibleToken token, Account from, Hash160 to, int amount, Hash160 data) throws Throwable {
        ContractParameter p;
        p = data == null ? null : ContractParameter.hash160(data);
        Transaction tx = token.transfer(
                from, to, BigInteger.valueOf(amount), p).sign();
        NeoSendRawTransaction res = tx.send();
        if (res.hasError()) {
            throw new Exception(res.getError().getMessage());
        }
        Await.waitUntilTransactionIsExecuted(res.getSendRawTransaction().getHash(), neow3j);
    }

    protected static void createRoom(Account creator, int amount) throws Throwable {
        transfer(token, creator, contract.getScriptHash(), amount,
                null);
    }

    protected static void joinRoom(Account host, Account player, int amount) throws Throwable {
        transfer(token, player, contract.getScriptHash(), amount,
                host.getScriptHash());
    }
}
