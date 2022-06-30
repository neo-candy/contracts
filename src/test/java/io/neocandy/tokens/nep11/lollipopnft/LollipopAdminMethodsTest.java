package io.neocandy.tokens.nep11.lollipopnft;

import static org.junit.Assert.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import io.neocandy.tokens.nep11.lollipop.LollipopNFT;
import io.neocandy.tokens.nep17.NeoCandy;
import io.neow3j.protocol.core.response.NeoInvokeFunction;
import io.neow3j.test.ContractTest;
import io.neow3j.types.ContractParameter;

@ContractTest(blockTime = 1, contracts = { NeoCandy.class,
        LollipopNFT.class }, batchFile = "init.batch", configFile = "dev.neo-express")
public class LollipopAdminMethodsTest extends AbstractLollipopNFTTest {
    private static final long INITIAL_MINT_PRICE = 500000000000000L;

    @BeforeAll
    static void unPause() throws Throwable {
        TestHelper.invokeWrite(lollipopNFT, TestHelper.UPDATE_PAUSE, Arrays.asList(ContractParameter.bool(false)),
                alice, neow3j);
    }

    @Test
    void adminMintTest() throws IOException {
        log.info("====================== adminMintTest() ======================");
        assertDoesNotThrow(
                () -> TestHelper.invokeWrite(lollipopNFT, TestHelper.ADMIN_MINT, Arrays.asList(), alice, neow3j));
        NeoInvokeFunction result = lollipopNFT.callInvokeFunction(TestHelper.CURRENT_SUPPLY);
        assertNull(result.getInvocationResult().getException());
    }

    @Test
    void getCandyTest() throws IOException {
        log.info("====================== getCandyTest() ======================");
        assertDoesNotThrow(
                () -> TestHelper.mintNFT(candyToken, alice, BigInteger.valueOf(INITIAL_MINT_PRICE),
                        lollipopNFT,
                        neow3j, false));

        log.info("balanceBefore: {}", candyToken.getBalanceOf(lollipopNFT.getScriptHash()));

        assertDoesNotThrow(
                () -> TestHelper.invokeWrite(lollipopNFT, TestHelper.GET_CANDY,
                        Arrays.asList(
                                ContractParameter.integer((candyToken.getBalanceOf(lollipopNFT.getScriptHash())))),
                        alice, neow3j));
        log.info("balanceAfter: {}", candyToken.getBalanceOf(lollipopNFT.getScriptHash()));
    }

}
