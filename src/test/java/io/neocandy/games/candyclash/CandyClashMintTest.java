package io.neocandy.games.candyclash;

import static org.junit.Assert.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;

import org.junit.jupiter.api.Disabled;
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
        @Disabled
        void mintTest() throws IOException {
                long times = 1;
                log.info("====================== mintTest() ======================");
                log.info("alice gas balance: {}", gas.getBalanceOf(alice));
                assertDoesNotThrow(() -> TestHelper.mintNFT(gas, alice, BigInteger.valueOf(times * 1000000000L),
                                candyClashNft,
                                neow3j));

                byte[] tokenId = new byte[] { (byte) 1 };
                NeoInvokeFunction result = candyClashNft.callInvokeFunction(TestHelper.JSON_PROPERTIES,
                                Arrays.asList(ContractParameter.byteArray(tokenId)));
                assertNull(result.getInvocationResult().getException());
                log.info("jsonProperties: {}",
                                result.getInvocationResult().getStack().get(0).getString());
                result = candyClashNft.callInvokeFunction(TestHelper.TOKENS_OF,
                                Arrays.asList(ContractParameter.hash160(alice)));
                assertEquals(times,
                                result.getInvocationResult().getStack().get(0).getIterator().size());

        }

        @RepeatedTest(value = 10)
        @Disabled
        void paginationTest() throws Throwable {
                log.info("====================== paginationTest() ======================");
                TestHelper.mintNFT(gas, alice, BigInteger.valueOf(100_00000000L),
                                candyClashNft, neow3j);

                NeoInvokeFunction result = candyClashNft.callInvokeFunction(TestHelper.GET_VILLAGER_CANDIES,
                                Arrays.asList(ContractParameter.integer(0), ContractParameter.integer(30)),
                                new Signer[] { AccountSigner.calledByEntry(alice) });
                log.info("villagerCandies: {}",
                                result.getInvocationResult().getStack().get(0).getString());

                result = candyClashNft.callInvokeFunction(TestHelper.GET_VILLAIN_CANDIES,
                                Arrays.asList(ContractParameter.integer(0), ContractParameter.integer(100)),

                                new Signer[] { AccountSigner.calledByEntry(alice) });
                log.info("villainCandies: {}",
                                result.getInvocationResult().getStack().get(0).getString());
        }

        @Test
        @Disabled
        void mintAmountReachedTest() {
                log.info("====================== mintAmountReachedTest() ======================");
                Exception ex = assertThrows(Exception.class, () -> TestHelper.mintNFT(gas, alice,
                                BigInteger.valueOf(110_00000000L), candyClashNft, neow3j));

                assertTrue(ex.getMessage().contains("mint amount reached"));
        }

        @Test
        @Disabled
        void invalidAmountTest() {
                log.info("====================== invalidAmountTest() ======================");
                Exception ex = assertThrows(Exception.class, () -> TestHelper.mintNFT(gas, alice,
                                BigInteger.valueOf(99_00000000L), candyClashNft, neow3j));

                assertTrue(ex.getMessage().contains("invalid amount"));
        }

        @Test
        @Disabled
        void invalidTokenTest() {
                log.info("====================== invalidTokenTest() ======================");
                FungibleToken neo = new NeoToken(neow3j);

                Exception ex = assertThrows(Exception.class,
                                () -> TestHelper.mintNFT(neo, alice,
                                                BigInteger.valueOf(1), candyClashNft, neow3j));

                assertTrue(ex.getMessage().contains("invalid token"));
        }

        @Test
        @Disabled
        void candyPaymentNotAvailableTest() {
                log.info("====================== candyPaymentNotAvailableTest() ======================");
                Exception ex = assertThrows(Exception.class,
                                () -> TestHelper.mintNFT(candyToken, alice,
                                                BigInteger.valueOf(1), candyClashNft, neow3j));

                assertTrue(ex.getMessage().contains("not available"));
        }

        @Test
        @Disabled
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
        @Disabled
        void royaltiesTest() throws Throwable {
                log.info("====================== royaltiesTest() ======================");
                NeoInvokeFunction result = candyClashNft.callInvokeFunction(TestHelper.ROYALTIES);
                assertNull(result.getInvocationResult().getException());

                log.info("royalties: {}", result.getInvocationResult().getStack().get(0).getString());
        }

        @Test
        void ownerOfTest() throws Throwable {
                log.info("====================== ownerOfTest() ======================");
                TestHelper.mintNFT(gas, bob,
                                BigInteger.valueOf(GAS_PRICE_PER_NFT), candyClashNft, neow3j);

                NeoInvokeFunction result = candyClashNft.callInvokeFunction(TestHelper.OWNER_OF,
                                Arrays.asList(ContractParameter.byteArray(new byte[] { (byte) 1 })));

                log.info("ownerOf2: {}", result.getInvocationResult().getStack().get(0));

        }

}
