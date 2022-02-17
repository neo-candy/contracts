package io.neocandy.games.candyclash;

import static org.junit.Assert.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import io.neocandy.tokens.nep17.NeoCandy;
import io.neow3j.contract.FungibleToken;
import io.neow3j.contract.NeoToken;
import io.neow3j.protocol.core.response.NeoInvokeFunction;
import io.neow3j.test.ContractTest;
import io.neow3j.transaction.AccountSigner;
import io.neow3j.transaction.Signer;
import io.neow3j.types.ContractParameter;

@ContractTest(blockTime = 1, contracts = { NeoCandy.class,
                CandyClashNFT.class,
                CandyClashStaking.class }, batchFile = "init.batch", configFile = "dev.neo-express")
public class CandyClashMintTest extends AbstractCandyClashTest {

        @Test
        void mintTest() throws Throwable {
                log.info("====================== mintTest() ======================");
                TestHelper.mintNFT(gas, alice, BigInteger.valueOf(100_00000000L), candyClashNft, neow3j);
                byte[] tokenId = new byte[] { (byte) 1 };
                NeoInvokeFunction result = candyClashNft.callInvokeFunction(TestHelper.JSON_PROPERTIES,
                                Arrays.asList(ContractParameter.byteArray(tokenId)));
                assertNull(result.getInvocationResult().getException());

                result = candyClashNft.callInvokeFunction(TestHelper.TOKENS_OF,
                                Arrays.asList(ContractParameter.hash160(alice)));
                assertEquals(10, result.getInvocationResult().getStack().get(0).getIterator().size());
        }

        @Test
        @RepeatedTest(value = 10)
        void paginationTest() throws Throwable {
                log.info("====================== paginationTest() ======================");
                TestHelper.mintNFT(gas, alice, BigInteger.valueOf(100_00000000L), candyClashNft, neow3j);

                NeoInvokeFunction result = candyClashNft.callInvokeFunction(TestHelper.GET_GOOD_CANDIES,
                                Arrays.asList(ContractParameter.integer(5), ContractParameter.integer(30)),
                                new Signer[] { AccountSigner.calledByEntry(alice) });
                log.info("goodCandies: {}", result.getInvocationResult().getStack().get(0).getString());

                result = candyClashNft.callInvokeFunction(TestHelper.GET_EVIL_CANDIES,
                                Arrays.asList(ContractParameter.integer(0), ContractParameter.integer(100)),

                                new Signer[] { AccountSigner.calledByEntry(alice) });
                log.info("evilCandies: {}", result.getInvocationResult().getStack().get(0).getString());
        }

        @Test
        void mintAmountReachedTest() {
                log.info("====================== mintAmountReachedTest() ======================");
                Exception ex = assertThrows(Exception.class, () -> TestHelper.mintNFT(gas, alice,
                                BigInteger.valueOf(110_00000000L), candyClashNft, neow3j));

                assertTrue(ex.getMessage().contains("mint amount reached"));
        }

        @Test
        void invalidAmountTest() {
                log.info("====================== invalidAmountTest() ======================");
                Exception ex = assertThrows(Exception.class, () -> TestHelper.mintNFT(gas, alice,
                                BigInteger.valueOf(99_00000000L), candyClashNft, neow3j));

                assertTrue(ex.getMessage().contains("invalid amount"));
        }

        @Test
        void invalidTokenTest() {
                log.info("====================== invalidTokenTest() ======================");
                FungibleToken neo = new NeoToken(neow3j);

                Exception ex = assertThrows(Exception.class,
                                () -> TestHelper.mintNFT(neo, alice,
                                                BigInteger.valueOf(1), candyClashNft, neow3j));

                assertTrue(ex.getMessage().contains("invalid token"));
        }

        @Test
        void candyPaymentNotAvailableTest() {
                log.info("====================== candyPaymentNotAvailableTest() ======================");
                Exception ex = assertThrows(Exception.class,
                                () -> TestHelper.mintNFT(candyToken, alice,
                                                BigInteger.valueOf(1), candyClashNft, neow3j));

                assertTrue(ex.getMessage().contains("not available"));
        }

        @Test
        void pauseTest() throws Throwable {
                log.info("====================== pauseTest() ======================");
                assertDoesNotThrow(() -> TestHelper.invokeWrite(candyClashNft, TestHelper.UPDATE_PAUSE,
                                Arrays.asList(ContractParameter.bool(true)),
                                alice, neow3j));
                Exception ex = assertThrows(Exception.class,
                                () -> TestHelper.mintNFT(gas, alice, BigInteger.valueOf(10), candyClashNft, neow3j));

                assertTrue(ex.getMessage().contains("isPaused"));
        }

        @Test
        void royaltiesTest() throws Throwable {
                log.info("====================== royaltiesTest() ======================");
                NeoInvokeFunction result = candyClashNft.callInvokeFunction(TestHelper.ROYALTIES);
                assertNull(result.getInvocationResult().getException());

                log.info("royalties: {}", result.getInvocationResult().getStack().get(0).getString());
        }

}
