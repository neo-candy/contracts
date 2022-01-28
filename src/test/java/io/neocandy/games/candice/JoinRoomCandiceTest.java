package io.neocandy.games.candice;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import io.neocandy.games.Candice;
import io.neocandy.tokens.nep17.NeoCandy;
import io.neow3j.test.ContractTest;

@ContractTest(blockTime = 1, contracts = { NeoCandy.class,
        Candice.class }, batchFile = "init.batch", configFile = "dev.neo-express")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class JoinRoomCandiceTest extends CandiceTest {

    @Test
    public void joinNonExistingRoomTest() {
        Exception ex = assertThrows(Exception.class, () -> joinRoom(bob, alice, 100));
        assertTrue(ex.getMessage().contains("room does not exist"));
    }

    @Test
    @Order(1)
    public void invalidOpponentTest() {
        assertDoesNotThrow(() -> createRoom(alice, 100));

        Exception ex = assertThrows(Exception.class,
                () -> joinRoom(alice, alice, 100));
        assertTrue(ex.getMessage().contains("invalid opponent"));
    }

    @Test
    @Order(2)
    public void joinRoomWithWrongStakeTest() {
        Exception ex = assertThrows(Exception.class,
                () -> joinRoom(alice, bob, 20));

        assertTrue(ex.getMessage().contains("invalid amount"));
    }

    @Test
    @Order(3)
    public void joinDeletedRoomTest() throws IOException {
        assertDoesNotThrow(() -> joinRoom(alice, bob, 100));

        Exception ex = assertThrows(Exception.class,
                () -> joinRoom(alice, bob, 100));

        assertTrue(ex.getMessage().contains("room does not exist"));
    }

    @Test
    @Order(4)
    public void joinRoomButGameExistsAlreadyTest() throws IOException {

        assertDoesNotThrow(() -> createRoom(alice, 100));
        Exception ex = assertThrows(Exception.class,
                () -> joinRoom(alice, bob, 100));

        assertTrue(ex.getMessage().contains("active game"));

    }

}
