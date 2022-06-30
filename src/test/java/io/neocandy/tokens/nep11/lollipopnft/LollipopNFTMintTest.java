package io.neocandy.tokens.nep11.lollipopnft;

import static org.junit.Assert.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import io.neocandy.tokens.nep11.lollipop.LollipopNFT;
import io.neocandy.tokens.nep17.NeoCandy;
import io.neow3j.protocol.core.response.NeoInvokeFunction;
import io.neow3j.test.ContractTest;
import io.neow3j.types.ContractParameter;

@ContractTest(blockTime = 1, contracts = { NeoCandy.class,
                LollipopNFT.class }, batchFile = "init.batch", configFile = "dev.neo-express")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class LollipopNFTMintTest extends AbstractLollipopNFTTest {

        private static final long INITIAL_MINT_PRICE = 500000000000000L;
        private static final long SECOND_MINT_PRICE = 1000000000000000L;
        private static final long THIRD_MINT_PRICE = 1250000000000000L;
        private static final long FOURTH_MINT_PRICE = 1500000000000000L;
        private static final long FIFTH_MINT_PRICE = 2000000000000000L;
        private static final long SIXTH_MINT_PRICE = 3000000000000000L;
        private static final long SEVENTH_MINT_PRICE = 4000000000000000L;

        @BeforeAll
        static void unPause() throws Throwable {
                TestHelper.invokeWrite(lollipopNFT, TestHelper.UPDATE_PAUSE,
                                Arrays.asList(ContractParameter.bool(false)),
                                alice, neow3j);
        }

        @Test
        @Order(1)
        void initialSupplyTest() throws IOException {
                log.info("====================== initialSupplyTest() ======================");
                NeoInvokeFunction result = lollipopNFT.callInvokeFunction(TestHelper.CURRENT_SUPPLY);
                assertNull(result.getInvocationResult().getException());
                assertEquals(22, result.getInvocationResult().getStack().get(0).getInteger().intValue());
        }

        @Test
        @Order(2)
        void currentPriceTest() throws IOException {
                log.info("====================== currentPriceTest() ======================");
                NeoInvokeFunction result = lollipopNFT.callInvokeFunction(TestHelper.CURRENT_PRICE);
                assertNull(result.getInvocationResult().getException());
                assertEquals(INITIAL_MINT_PRICE,
                                result.getInvocationResult().getStack().get(0).getInteger().longValue());
        }

        @Test
        @RepeatedTest(27)
        @Order(3)
        void mintTo50PriceUnchangedTest() throws IOException {
                log.info("====================== mintTo50PriceUnchangedTest() ======================");
                assertDoesNotThrow(
                                () -> TestHelper.mintNFT(candyToken, alice, BigInteger.valueOf(INITIAL_MINT_PRICE),
                                                lollipopNFT,
                                                neow3j, false));
        }

        @Test
        @Order(4)
        void mintPriceChangesAt50() throws IOException {
                log.info("====================== mintPriceChangesAt50() ======================");
                assertThrows(
                                Exception.class,
                                () -> TestHelper.mintNFT(candyToken, alice, BigInteger.valueOf(INITIAL_MINT_PRICE),
                                                lollipopNFT,
                                                neow3j, false));
                assertDoesNotThrow(
                                () -> TestHelper.mintNFT(candyToken, alice, BigInteger.valueOf(SECOND_MINT_PRICE),
                                                lollipopNFT,
                                                neow3j, false));
        }

        @Test
        @Order(5)
        @RepeatedTest(48)
        void mintTo100PriceUnchangedTest() throws IOException {
                log.info("====================== mintTo100PriceUnchangedTest() ======================");
                assertDoesNotThrow(
                                () -> TestHelper.mintNFT(candyToken, alice, BigInteger.valueOf(SECOND_MINT_PRICE),
                                                lollipopNFT,
                                                neow3j, false));
        }

        @Test
        @Order(6)
        void mintPriceChangesAt100() throws IOException {
                log.info("====================== mintPriceChangesAt100() ======================");
                assertThrows(
                                Exception.class,
                                () -> TestHelper.mintNFT(candyToken, alice, BigInteger.valueOf(SECOND_MINT_PRICE),
                                                lollipopNFT,
                                                neow3j, false));
                assertDoesNotThrow(
                                () -> TestHelper.mintNFT(candyToken, alice, BigInteger.valueOf(THIRD_MINT_PRICE),
                                                lollipopNFT,
                                                neow3j, false));
        }

        @Test
        @Order(7)
        @RepeatedTest(48)
        void mintTo150PriceUnchangedTest() throws IOException {
                log.info("====================== mintTo150PriceUnchangedTest() ======================");
                assertDoesNotThrow(
                                () -> TestHelper.mintNFT(candyToken, alice, BigInteger.valueOf(THIRD_MINT_PRICE),
                                                lollipopNFT,
                                                neow3j, false));
        }

        @Test
        @Order(8)
        void mintPriceChangesAt150() throws IOException {
                log.info("====================== mintPriceChangesAt150() ======================");
                assertThrows(
                                Exception.class,
                                () -> TestHelper.mintNFT(candyToken, alice, BigInteger.valueOf(THIRD_MINT_PRICE),
                                                lollipopNFT,
                                                neow3j, false));
                assertDoesNotThrow(
                                () -> TestHelper.mintNFT(candyToken, alice, BigInteger.valueOf(FOURTH_MINT_PRICE),
                                                lollipopNFT,
                                                neow3j, false));
        }

        @Test
        @Order(9)
        @RepeatedTest(48)
        void mintTo200PriceUnchangedTest() throws IOException {
                log.info("====================== mintTo200PriceUnchangedTest() ======================");
                assertDoesNotThrow(
                                () -> TestHelper.mintNFT(candyToken, alice, BigInteger.valueOf(FOURTH_MINT_PRICE),
                                                lollipopNFT,
                                                neow3j, false));
        }

        @Test
        @Order(10)
        void mintPriceChangesAt200() throws IOException {
                log.info("====================== mintPriceChangesAt200() ======================");
                assertThrows(
                                Exception.class,
                                () -> TestHelper.mintNFT(candyToken, alice, BigInteger.valueOf(FOURTH_MINT_PRICE),
                                                lollipopNFT,
                                                neow3j, false));
                assertDoesNotThrow(
                                () -> TestHelper.mintNFT(candyToken, alice, BigInteger.valueOf(FIFTH_MINT_PRICE),
                                                lollipopNFT,
                                                neow3j, false));
        }

        @Test
        @Order(11)
        @RepeatedTest(16)
        void mintTo218PriceUnchangedTest() throws IOException {
                log.info("====================== mintTo218PriceUnchangedTest() ======================");
                assertDoesNotThrow(
                                () -> TestHelper.mintNFT(candyToken, alice, BigInteger.valueOf(FIFTH_MINT_PRICE),
                                                lollipopNFT,
                                                neow3j, false));
        }

        @Test
        @Order(12)
        void mintPriceChangesAt218() throws IOException {
                log.info("====================== mintPriceChangesAt218() ======================");
                assertThrows(
                                Exception.class,
                                () -> TestHelper.mintNFT(candyToken, alice, BigInteger.valueOf(FIFTH_MINT_PRICE),
                                                lollipopNFT,
                                                neow3j, false));
                assertDoesNotThrow(
                                () -> TestHelper.mintNFT(candyToken, alice, BigInteger.valueOf(SIXTH_MINT_PRICE),
                                                lollipopNFT,
                                                neow3j, false));
        }

        @Test
        @Order(13)
        void mintTo220PriceUnchangedTest() throws IOException {
                log.info("====================== mintTo220PriceUnchangedTest() ======================");
                assertDoesNotThrow(
                                () -> TestHelper.mintNFT(candyToken, alice, BigInteger.valueOf(SIXTH_MINT_PRICE),
                                                lollipopNFT,
                                                neow3j, false));
        }

        @Test
        @Order(14)
        void mintPriceChangesAt220() throws IOException {
                log.info("====================== mintPriceChangesAt220() ======================");
                assertThrows(
                                Exception.class,
                                () -> TestHelper.mintNFT(candyToken, alice, BigInteger.valueOf(SIXTH_MINT_PRICE),
                                                lollipopNFT,
                                                neow3j, false));
                assertDoesNotThrow(
                                () -> TestHelper.mintNFT(candyToken, alice, BigInteger.valueOf(SEVENTH_MINT_PRICE),
                                                lollipopNFT,
                                                neow3j, false));
        }

        @Test
        @Order(15)
        void totalSupplyReachedTest() throws IOException {
                log.info("====================== totalSupplyReachedTest() ======================");
                assertDoesNotThrow(
                                () -> TestHelper.mintNFT(candyToken, alice, BigInteger.valueOf(SEVENTH_MINT_PRICE),
                                                lollipopNFT,
                                                neow3j, false));
                assertThrows(
                                Exception.class,
                                () -> TestHelper.mintNFT(candyToken, alice, BigInteger.valueOf(SEVENTH_MINT_PRICE),
                                                lollipopNFT,
                                                neow3j, false));

                NeoInvokeFunction result = lollipopNFT.callInvokeFunction(TestHelper.CURRENT_SUPPLY);
                assertEquals(222, result.getInvocationResult().getStack().get(0).getInteger().intValue());
        }

}
