package io.neocandy.candyland;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.neow3j.contract.SmartContract;
import io.neow3j.protocol.Neow3j;
import io.neow3j.protocol.core.response.NeoInvokeFunction;
import io.neow3j.test.ContractTest;
import io.neow3j.test.ContractTestExtension;
import io.neow3j.test.DeployConfig;
import io.neow3j.test.DeployConfiguration;
import io.neow3j.types.ContractParameter;
import io.neow3j.types.Hash160;

import static io.neow3j.types.ContractParameter.hash160;
import static io.neow3j.types.ContractParameter.string;

@ContractTest(blockTime = 1, contracts = CandylandGame.class)
public class CandylandGameTest {
    private static final Logger log = LoggerFactory.getLogger(CandylandGameTest.class);
    private static final String GET_OWNER = "getOwner";
    private static final String REGISTER = "register";
    private static final String QUERY_USERNAMES = "queryUsernames";
    private static final String OWNER_ADDRESS = "NXXazKH39yNFWWZF5MJ8tEN98VYHwzn7g3";

    @RegisterExtension
    private static ContractTestExtension ext = new ContractTestExtension();

    private static Neow3j neow3j;
    private static SmartContract contract;

    @BeforeAll
    public static void setUp() {
        neow3j = ext.getNeow3j();
        contract = ext.getDeployedContract(CandylandGame.class);
    }

    @DeployConfig(CandylandGame.class)
    public static DeployConfiguration configure() {
        DeployConfiguration config = new DeployConfiguration();
        ContractParameter owner = hash160(Hash160.fromAddress(OWNER_ADDRESS));
        config.setDeployParam(owner);
        return config;
    }

    @Test
    public void getOwnerTest() throws IOException {
        NeoInvokeFunction result = contract.callInvokeFunction(GET_OWNER);
        assertEquals(OWNER_ADDRESS, result.getInvocationResult().getStack().get(0).getAddress());
    }

    @Test
    public void registerUsernameTest() throws IOException {
        List<ContractParameter> params = new ArrayList<>();
        params.add(string("user023"));
        params.add(hash160(Hash160.fromAddress(OWNER_ADDRESS)));
        NeoInvokeFunction result = contract.callInvokeFunction(REGISTER, params);
        log.info("exception: {}", result.getInvocationResult().getException());
        assertTrue(result.getInvocationResult().getException().isEmpty());
    }

    /*
     * @Test
     * public void registerTooLongUsernameTest() throws IOException {
     * List<ContractParameter> params = new ArrayList<>();
     * params.add(string("ajdhfzejdhfjdeer"));
     * params.add(hash160(Hash160.fromAddress(OWNER_ADDRESS)));
     * NeoInvokeFunction result = contract.callInvokeFunction(REGISTER, params);
     * log.info(result.getInvocationResult().getException());
     * assertEquals("Username can have a maximum of 15 characters",
     * result.getInvocationResult().getException());
     * }
     * 
     * @Test
     * public void registerTooShortUsernameTest() throws IOException {
     * List<ContractParameter> params = new ArrayList<>();
     * params.add(string("aa"));
     * params.add(hash160(Hash160.fromAddress(OWNER_ADDRESS)));
     * NeoInvokeFunction result = contract.callInvokeFunction(REGISTER, params);
     * assert
     * log.info(result.getInvocationResult().getException());
     * assertEquals("Username must be at least 3 characters long",
     * result.getInvocationResult().getException());
     * }
     */

    @Test
    public void queryUsernamesTest() throws IOException {
        NeoInvokeFunction result = contract.callInvokeFunction(QUERY_USERNAMES);
        log.info("result3: {}", result.getInvocationResult().getStack().get(0).getString());
        // assertTrue(result.getInvocationResult().getStack().get(0).getBoolean());
    }
}
