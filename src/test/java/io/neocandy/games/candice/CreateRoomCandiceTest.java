package io.neocandy.games.candice;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import io.neocandy.games.Candice;
import io.neocandy.tokens.nep17.NeoCandy;
import io.neow3j.contract.GasToken;

import io.neow3j.protocol.core.response.NeoInvokeFunction;
import io.neow3j.test.ContractTest;

import io.neow3j.transaction.AccountSigner;
import io.neow3j.transaction.Signer;
import io.neow3j.types.ContractParameter;

@ContractTest(blockTime = 1, contracts = { NeoCandy.class,
        Candice.class }, batchFile = "init.batch", configFile = "dev.neo-express")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CreateRoomCandiceTest extends CandiceTest {

    @Test
    public void wrongTokenTest() {
        Exception ex = assertThrows(Exception.class,
                () -> transfer(new GasToken(ext.getNeow3j()), alice, contract.getScriptHash(), 100,
                        null));

        assertTrue(ex.getMessage().contains("contract accepts only NeoCandy"));
    }

    @Test
    public void wrongStakeTest() {
        Exception ex = assertThrows(Exception.class,
                () -> createRoom(alice, 0));

        assertTrue(ex.getMessage().contains("stake must be greater than 0"));
    }

    @Test
    @Order(1)
    public void createNewRoomTest() throws IOException {
        assertDoesNotThrow(() -> createRoom(alice, 100));

        NeoInvokeFunction result = contract.callInvokeFunction(QUERY_ROOM,
                Arrays.asList(ContractParameter.hash160(alice)),
                new Signer[] { AccountSigner.calledByEntry(alice) });

        assertEquals(BigInteger.valueOf(100),
                result.getInvocationResult().getStack().get(0).getInteger());
    }

    @Test
    @Order(2)
    public void roomAlreadyExistsTest() {
        Exception ex = assertThrows(Exception.class, () -> createRoom(alice, 100));
        assertTrue(ex.getMessage().contains("room already exists"));
    }

}
