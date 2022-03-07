package io.neocandy.games.candyclash;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
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
public class CandyClashStakingTest extends AbstractCandyClashTest {

        private static final int BLOCKS_PER_DAY = 5760;
        private static final int CANDY_PER_DAY = 1000;
        private static final String EVENT_CLAIM_GOOD_CANDY = "goodCandyClaim";

        @Test
        void simpleStakeTest() throws IOException {
                assertDoesNotThrow(() -> mintNFT(gas, alice, BigInteger.valueOf(GAS_PRICE_PER_NFT),
                                candyClashNft,
                                neow3j));
                // stake token
                byte[] tokenId = new byte[] { (byte) 1 };

                NeoInvokeFunction result = candyClashNft.callInvokeFunction(TestHelper.OWNER_OF,
                                Arrays.asList(ContractParameter.byteArray(tokenId)));

                log.info("ownerOf: {}", result.getInvocationResult().getStack().get(0).getAddress());

                assertDoesNotThrow(() -> transfer11(candyClashNft, tokenId, alice,
                                candyClashStaking.getScriptHash(),
                                neow3j));
        }

}
