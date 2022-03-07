package io.neocandy.games.candyclash;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.neocandy.tokens.nep17.NeoCandy;
import io.neow3j.protocol.core.response.NeoInvokeFunction;
import io.neow3j.protocol.core.response.NeoSendRawTransaction;
import io.neow3j.protocol.core.response.NeoApplicationLog.Execution.Notification;
import io.neow3j.protocol.core.stackitem.StackItem;
import io.neow3j.test.ContractTest;
import io.neow3j.transaction.AccountSigner;
import io.neow3j.transaction.Signer;
import io.neow3j.transaction.Transaction;
import io.neow3j.types.ContractParameter;
import io.neow3j.utils.Await;

import static io.neocandy.games.candyclash.TestHelper.transfer11;
import static io.neocandy.games.candyclash.TestHelper.mintNFT;
import static io.neocandy.games.candyclash.TestHelper.getProperties;

@ContractTest(blockTime = 1, contracts = { NeoCandy.class,
                CandyClashNFT.class,
                CandyClashStaking.class }, batchFile = "init.batch", configFile = "dev.neo-express")
public class CandyClashStakingStakeTest extends AbstractCandyClashTest {

        private static final int BLOCKS_PER_DAY = 5760;
        private static final int CANDY_PER_DAY = 1000;
        private static final String EVENT_CLAIM_GOOD_CANDY = "goodCandyClaim";

        @Test
        @Disabled("Takes too long, test it alone")
        void claimableAmountMultipleTokensTest() throws Throwable {
                // mint tokens
                byte[] tokenId1 = new byte[] { (byte) 1 };
                byte[] tokenId2 = new byte[] { (byte) 2 };

                assertDoesNotThrow(() -> mintNFT(gas, alice, BigInteger.valueOf(2 * GAS_PRICE_PER_NFT),
                                candyClashNft,
                                neow3j));

                // stake tokens
                assertDoesNotThrow(() -> transfer11(candyClashNft, tokenId1, alice,
                                candyClashStaking.getScriptHash(),
                                neow3j));
                assertDoesNotThrow(() -> transfer11(candyClashNft, tokenId2, alice,
                                candyClashStaking.getScriptHash(),
                                neow3j));

                // fast forward
                ext.fastForward(BLOCKS_PER_DAY);

                NeoInvokeFunction result = candyClashStaking.callInvokeFunction(TestHelper.AVAILABLE_CLAIM_AMOUNT,
                                Arrays.asList(ContractParameter.array(ContractParameter.byteArray(tokenId1),
                                                ContractParameter.byteArray(tokenId2))));
                assertNull(result.getResult().getException());
                String claimAmount = String.valueOf(result.getInvocationResult().getStack().get(0).getInteger());
                assertTrue(claimAmount.substring(0, 4).equals(String.valueOf(2 *
                                CANDY_PER_DAY)));
        }

        @Test
        @Disabled
        void claimableAmountSingleTokenTest() throws Throwable {
                // mint token
                byte[] tokenId1 = new byte[] { (byte) 1 };
                assertDoesNotThrow(() -> mintNFT(gas, alice, BigInteger.valueOf(1 * GAS_PRICE_PER_NFT),
                                candyClashNft,
                                neow3j));
                // stake token
                assertDoesNotThrow(() -> transfer11(candyClashNft, tokenId1, alice,
                                candyClashStaking.getScriptHash(),
                                neow3j));

                // fast forward
                /*
                 * ext.fastForward(MIN_STAKE_BLOCK_COUNT);
                 * 
                 * NeoInvokeFunction result =
                 * candyClashStaking.callInvokeFunction(TestHelper.AVAILABLE_CLAIM_AMOUNT,
                 * Arrays.asList(ContractParameter.array(ContractParameter.byteArray(tokenId1)))
                 * );
                 * assertNull(result.getResult().getException());
                 */
        }

        @Test
        void simpleStakeTest() {
                assertDoesNotThrow(() -> mintNFT(gas, alice, BigInteger.valueOf(1 * GAS_PRICE_PER_NFT),
                                candyClashNft,
                                neow3j));
                // stake token
                byte[] tokenId1 = new byte[] { (byte) 1 };
                assertDoesNotThrow(() -> transfer11(candyClashNft, tokenId1, alice,
                                candyClashStaking.getScriptHash(),
                                neow3j));

        }

        @Test
        @Disabled
        void minimumStakeDurationNotReachedTest() throws Throwable {
                // mint token
                byte[] tokenId = new byte[] { (byte) 3 };
                // mintNFT(tokenId, getProperties("good", "1"), alice, candyClashNft,neow3j);

                // stake token
                assertDoesNotThrow(() -> transfer11(candyClashNft, tokenId, alice,
                                candyClashStaking.getScriptHash(),
                                neow3j));

                NeoInvokeFunction result = candyClashStaking.callInvokeFunction(TestHelper.CLAIM,
                                Arrays.asList(ContractParameter.array(ContractParameter.byteArray(tokenId)),
                                                ContractParameter.bool(false), ContractParameter.hash160(alice)),
                                new Signer[] { AccountSigner.calledByEntry(alice) });
                assertTrue(result.getResult().getException().contains("minimum stake duration not reached"));
        }

        @Test
        @Disabled
        void notOwnerToClaimTest() throws Throwable {
                // mint token
                byte[] tokenId = new byte[] { (byte) 4 };
                // mintNFT(tokenId, getProperties("good", "1"), alice, candyClashNft,neow3j);

                // stake token
                assertDoesNotThrow(() -> transfer11(candyClashNft, tokenId, alice,
                                candyClashStaking.getScriptHash(),
                                neow3j));

                NeoInvokeFunction result = candyClashStaking.callInvokeFunction(TestHelper.CLAIM,
                                Arrays.asList(ContractParameter.array(ContractParameter.byteArray(tokenId)),
                                                ContractParameter.bool(false), ContractParameter.hash160(alice)),
                                new Signer[] { AccountSigner.calledByEntry(bob) });
                assertTrue(result.getResult().getException().contains("not owner"));
        }

        @Test
        @Disabled
        void claimAndPayTaxesTest() throws Throwable {
                // mint token
                byte[] tokenId = new byte[] { (byte) 5 };
                // mintNFT(tokenId, getProperties("good", "1"), alice, candyClashNft,neow3j);

                // stake token
                assertDoesNotThrow(() -> transfer11(candyClashNft, tokenId, alice,
                                candyClashStaking.getScriptHash(),
                                neow3j));
                ext.fastForward(MIN_STAKE_BLOCK_COUNT);

                NeoInvokeFunction result = candyClashStaking.callInvokeFunction(TestHelper.CLAIM,
                                Arrays.asList(ContractParameter.array(ContractParameter.byteArray(tokenId)),
                                                ContractParameter.bool(false), ContractParameter.hash160(alice)),
                                new Signer[] { AccountSigner.calledByEntry(alice) });
                assertNull(result.getResult().getException());
        }

        @Test
        @Disabled
        void claimGoodCandyEventTest() throws Throwable {
                // mint token
                byte[] tokenId = new byte[] { (byte) 8 };
                // mintNFT(tokenId, getProperties("good", "1"), alice, candyClashNft,neow3j);

                // stake token
                assertDoesNotThrow(() -> transfer11(candyClashNft, tokenId, alice,
                                candyClashStaking.getScriptHash(),
                                neow3j));
                ext.fastForward(MIN_STAKE_BLOCK_COUNT);

                Transaction tx = candyClashStaking.invokeFunction(TestHelper.CLAIM,
                                ContractParameter.array(ContractParameter.byteArray(tokenId)),
                                ContractParameter.bool(false), ContractParameter.hash160(alice))
                                .signers(new Signer[] { AccountSigner.calledByEntry(alice) }).sign();

                tx.send();
                Await.waitUntilTransactionIsExecuted(tx.getTxId(), neow3j);

                List<Notification> notification = tx.getApplicationLog().getExecutions().get(0).getNotifications()
                                .stream()
                                .filter(v -> v.getEventName().equals(EVENT_CLAIM_GOOD_CANDY))
                                .collect(Collectors.toList());

                assertEquals(1, notification.size());
                List<StackItem> eventParams = notification.get(0).getState().getList();

                assertArrayEquals(tokenId, eventParams.get(0).getByteArray());
                assertEquals(false, eventParams.get(2).getBoolean());

        }

}
