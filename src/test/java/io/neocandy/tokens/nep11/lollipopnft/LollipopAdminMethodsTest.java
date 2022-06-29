package io.neocandy.tokens.nep11.lollipopnft;

import static org.junit.Assert.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.util.Arrays;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import io.neocandy.tokens.nep11.LollipopNFT;
import io.neocandy.tokens.nep17.NeoCandy;
import io.neow3j.protocol.core.response.NeoInvokeFunction;
import io.neow3j.test.ContractTest;
import io.neow3j.types.ContractParameter;

@ContractTest(blockTime = 1, contracts = { NeoCandy.class,
        LollipopNFT.class }, batchFile = "init.batch", configFile = "dev.neo-express")
public class LollipopAdminMethodsTest extends AbstractLollipopNFTTest {

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
        assertEquals(23, result.getInvocationResult().getStack().get(0).getInteger().intValue());
    }

}
