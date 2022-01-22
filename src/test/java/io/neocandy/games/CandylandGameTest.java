package io.neocandy.games;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.neocandy.games.CandylandGame;
import io.neow3j.contract.SmartContract;
import io.neow3j.protocol.Neow3j;
import io.neow3j.protocol.core.response.NeoInvokeFunction;
import io.neow3j.test.ContractTest;
import io.neow3j.test.ContractTestExtension;
import io.neow3j.test.DeployConfig;
import io.neow3j.test.DeployConfiguration;
import io.neow3j.transaction.AccountSigner;
import io.neow3j.transaction.Signer;
import io.neow3j.types.ContractParameter;
import io.neow3j.types.Hash256;
import io.neow3j.utils.Await;
import io.neow3j.wallet.Account;

import static io.neow3j.types.ContractParameter.hash160;
import static io.neow3j.types.ContractParameter.string;

@ContractTest(blockTime = 1, contracts = CandylandGame.class, batchFile = "init.batch", configFile = "dev.neo-express")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CandylandGameTest {
    private static final Logger log = LoggerFactory.getLogger(CandylandGameTest.class);
    private static final String GET_OWNER = "getOwner";
    private static final String REGISTER = "register";
    private static final String QUERY_USERNAMES = "queryUsernames";
    private static Account alice;
    private static Account bob;

    @RegisterExtension
    private static ContractTestExtension ext = new ContractTestExtension();

    private static Neow3j neow3j;
    private static SmartContract contract;

    @BeforeAll
    public static void setup() throws Exception {
        neow3j = ext.getNeow3j();
        alice = ext.getAccount("NQcSTBwSJs7hcFUZzku2QdPNLe2dkTGok2");
        bob = ext.getAccount("NhsVB4etFffHjpLoj2ngVkkfNbtxiSSmbk");
        contract = ext.getDeployedContract(CandylandGame.class);
    }

    @DeployConfig(CandylandGame.class)
    public static DeployConfiguration configure() throws Exception {
        DeployConfiguration config = new DeployConfiguration();
        ContractParameter owner = hash160(ext.getAccount("NQcSTBwSJs7hcFUZzku2QdPNLe2dkTGok2").getScriptHash());
        config.setDeployParam(owner);
        return config;
    }

    @Test
    public void getOwnerTest() throws IOException {
        NeoInvokeFunction result = contract.callInvokeFunction(GET_OWNER,
                new Signer[] { AccountSigner.calledByEntry(alice) });
        assertEquals(alice.getAddress(), result.getInvocationResult().getStack().get(0).getAddress());
    }

    @Test
    @Order(1)
    public void queryUsernamesTest() throws Throwable {
        Hash256 tx = contract
                .invokeFunction(REGISTER,
                        new ContractParameter[] { string("username01"), hash160(alice) })
                .signers(new Signer[] { AccountSigner.calledByEntry(alice) }).sign().send().getSendRawTransaction()
                .getHash();

        Await.waitUntilTransactionIsExecuted(tx, neow3j);

        NeoInvokeFunction result = contract.callInvokeFunction(QUERY_USERNAMES);
        assertTrue(result.getInvocationResult().getStack().get(0).getString().contains("username01"));
    }

    @Test
    @Order(2)
    public void usernameExistsTest() throws IOException {
        List<ContractParameter> params = new ArrayList<>();
        params.add(string("username01"));
        params.add(hash160(alice));
        String ex = contract.callInvokeFunction(REGISTER, params,
                new Signer[] { AccountSigner.calledByEntry(alice) }).getInvocationResult().getException();
        assertTrue(ex.contains("Username already taken"));
    }

    @Test
    @Order(3)
    public void accountAlreadyHasUsername() throws IOException {
        List<ContractParameter> params = new ArrayList<>();
        params.add(string("username13"));
        params.add(hash160(alice));
        String ex = contract.callInvokeFunction(REGISTER, params,
                new Signer[] { AccountSigner.calledByEntry(alice) }).getInvocationResult().getException();
        assertTrue(ex.contains("Address already has a username"));
    }

    @Test
    public void registerTooLongUsernameTest() throws IOException {
        List<ContractParameter> params = new ArrayList<>();
        params.add(string("ajdhfzejdhfjdeer"));
        params.add(hash160(alice));
        String ex = contract.callInvokeFunction(REGISTER, params,
                new Signer[] { AccountSigner.calledByEntry(bob) }).getInvocationResult().getException();
        assertTrue(ex.contains("Username can have a maximum of 15 characters"));
    }

    @Test
    public void registerTooShortUsernameTest() throws IOException {
        List<ContractParameter> params = new ArrayList<>();
        params.add(string("aa"));
        params.add(hash160(alice));
        String ex = contract.callInvokeFunction(REGISTER, params,
                new Signer[] { AccountSigner.calledByEntry(bob) }).getInvocationResult().getException();
        assertTrue(ex.contains("Username must be at least 3 characters long"));
    }

}
