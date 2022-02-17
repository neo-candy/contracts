package io.neocandy.games.candyclash;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigInteger;

import org.junit.jupiter.api.Test;

import io.neocandy.tokens.nep17.NeoCandy;
import io.neow3j.contract.GasToken;
import io.neow3j.protocol.core.response.NeoInvokeFunction;
import io.neow3j.test.ContractTest;
import io.neow3j.transaction.AccountSigner;
import io.neow3j.transaction.Signer;

import static io.neocandy.games.candyclash.TestHelper.transfer17;

@ContractTest(blockTime = 1, contracts = { NeoCandy.class,
                CandyClashNFT.class,
                CandyClashStaking.class }, batchFile = "init.batch", configFile = "dev.neo-express")
public class CandyClashStakingPaymentsTest extends AbstractCandyClashTest {

        @Test
        void onlyOwnerCanMakeNep17PaymentsTest() {
                Exception ex = assertThrows(Exception.class,
                                () -> transfer17(candyToken, bob, candyClashStaking.getScriptHash(),
                                                BigInteger.valueOf(100),
                                                null, neow3j));

                assertTrue(ex.getMessage().contains("onlyOwner"));
        }

        @Test
        void onlyCandyTokenForNep17PaymentsTest() throws IOException {
                Exception ex = assertThrows(Exception.class,
                                () -> transfer17(new GasToken(neow3j), alice, candyClashStaking.getScriptHash(),
                                                BigInteger.valueOf(100),
                                                null, neow3j));

                assertTrue(ex.getMessage().contains("onlyCandy"));
        }

        @Test
        void nep17PaymentSuccessTest() throws IOException {
                assertDoesNotThrow(
                                () -> transfer17(candyToken, alice, candyClashStaking.getScriptHash(),
                                                BigInteger.valueOf(100),
                                                null, neow3j));

                NeoInvokeFunction result = candyClashStaking.callInvokeFunction(TestHelper.MAX_CANDIES_TO_EARN,
                                new Signer[] { AccountSigner.calledByEntry(alice) });

                assertEquals(100, result.getInvocationResult().getStack().get(0).getInteger().intValue());

        }

}
