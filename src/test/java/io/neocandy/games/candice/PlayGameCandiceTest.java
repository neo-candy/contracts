package io.neocandy.games.candice;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Arrays;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import io.neocandy.games.Candice;
import io.neocandy.tokens.nep17.NeoCandy;
import io.neow3j.protocol.core.response.NeoInvokeFunction;
import io.neow3j.test.ContractTest;
import io.neow3j.transaction.AccountSigner;
import io.neow3j.transaction.Signer;
import io.neow3j.types.ContractParameter;
import io.neow3j.types.Hash160;

@ContractTest(blockTime = 1, contracts = { NeoCandy.class,
                Candice.class }, batchFile = "init.batch", configFile = "dev.neo-express")
public class PlayGameCandiceTest extends CandiceTest {

        @Test
        void noAuthTest() throws IOException {
                assertDoesNotThrow(() -> createRoom(alice, 100));
                assertDoesNotThrow(() -> joinRoom(alice, bob, 100));

                NeoInvokeFunction result = contract.callInvokeFunction(PLAY,
                                Arrays.asList(ContractParameter.hash160(alice),
                                                ContractParameter.hash160(bob)),
                                new Signer[] { AccountSigner.calledByEntry(charlie) });

                assertTrue(result.getInvocationResult().getException().contains("no authorization"));
        }

        @Test
        void gameDoesNotExistTest() throws IOException {
                NeoInvokeFunction result = contract.callInvokeFunction(PLAY,
                                Arrays.asList(ContractParameter.hash160(alice),
                                                ContractParameter.hash160(bob)),
                                new Signer[] { AccountSigner.calledByEntry(alice) });

                assertTrue(result.getInvocationResult().getException().contains("game does not exist"));
        }

        @RepeatedTest(value = 10, name = RepeatedTest.SHORT_DISPLAY_NAME)
        void playTest() throws IOException {
                assertDoesNotThrow(() -> createRoom(alice, 100));
                assertDoesNotThrow(() -> joinRoom(alice, bob, 100));

                NeoInvokeFunction result = contract.callInvokeFunction(PLAY,
                                Arrays.asList(ContractParameter.hash160(alice), ContractParameter.hash160(bob)),
                                new Signer[] { AccountSigner.calledByEntry(alice) });

                String winner = result.getInvocationResult().getStack().get(0).getAddress();
                assertTrue(winner.equals(alice.getAddress()) || winner.equals(bob.getAddress())
                                || winner.equals(Hash160.ZERO.toAddress()));

        }

}
