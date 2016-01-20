package com.shuffle.protocol;

import com.shuffle.bitcoin.CryptographyError;
import com.shuffle.bitcoin.VerificationKey;

import org.junit.Assert;
import org.junit.Test;

import java.net.ProtocolException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Tests for certain functions pertaining to network interactions.
 *
 * Created by Daniel Krawisz on 12/7/15.
 */
public class TestNetworkOperations  {

    public CoinShuffle.ShuffleMachine.Round net(
            int seed,
            MockSessionIdentifier session,
            MockSigningKey sk,
            Map<Integer, VerificationKey> players,
            Phase phase,
            MockMessageFactory messages,
            Network network) throws InvalidParticipantSetException {
        SortedSet<VerificationKey> playerSet = new TreeSet<>();
        playerSet.addAll(players.values());
        CoinShuffle.ShuffleMachine machine =
                new CoinShuffle(messages, new MockCrypto(seed), new MockCoin(), network).new
                    ShuffleMachine(session, 20l, sk, playerSet, null, 1, 2);
        machine.phase = phase;
        return machine.new Round(players, null);
    }

    static class playerSetTestCase {
        int i; // Minimum number blockchain take.
        int n; // Maximum number blockchain take.
        int N; // Number of players.
        int player; // Which player is us.
        int[] expected; // Which keys should have been returned.

        playerSetTestCase(int i, int n, int N, int player, int[] expected) {
            this.i = i;
            this.n = n;
            this.N = N;
            this.player = player;
            this.expected = expected;
        }

        @Override
        public String toString() {
            return "player set test case {" + i + ", " + n + ", " + N + ", " + player + ", " + Arrays.toString(expected) + "}";
        }
    }

    @Test
    public void testPlayerSet() throws InvalidParticipantSetException {
        playerSetTestCase tests[] =
                new playerSetTestCase[]{
                        new playerSetTestCase(
                                1,5,1,1,
                                new int[]{1}
                        ),
                        new playerSetTestCase(
                                1,5,5,1,
                                new int[]{1,2,3,4,5}
                        ),
                        new playerSetTestCase(
                                1,3,5,1,
                                new int[]{1,2,3}
                        ),
                        new playerSetTestCase(
                                2,4,5,3,
                                new int[]{2,3,4}
                        ),
                        new playerSetTestCase(
                                -1,7,5,3,
                                new int[]{1,2,3,4,5}
                        ),
                        new playerSetTestCase(
                                4,7,5,1,
                                new int[]{4,5}
                        )
                };

        int i = 0;
        for(playerSetTestCase test : tests) {
            // make the set of players.
            TreeMap<Integer, VerificationKey> players = new TreeMap<Integer, VerificationKey>();
            for (int j = 1; j <= test.N; j ++) {
                players.put(j, new MockVerificationKey(j));
            }

            Set<VerificationKey> result = null;
                // Set up the network operations object.
                CoinShuffle.ShuffleMachine.Round netop =
                    net(234, new MockSessionIdentifier("testPlayerSet" + i), new MockSigningKey(test.player), players,
                        Phase.Shuffling, new MockMessageFactory(), new MockNetwork());
                result = netop.playerSet(test.i, test.n);

            for (int expect : test.expected) {
                Assert.assertTrue(String.format("Unable to remove expected player %d",expect),result.remove(new MockVerificationKey(expect)));
            }

            Assert.assertTrue("Not every expected player was removed.",result.isEmpty());
            i++;
        }
    }

    static class broadcastTestCase {
        int recipients;
        int sender;

        public broadcastTestCase(int recipients, int sender) {
            this.recipients = recipients;
            this.sender = sender;
        }
    }

    @Test
    public void testBroadcast() throws InvalidParticipantSetException {
        broadcastTestCase tests[] =
                new broadcastTestCase[]{
                        new broadcastTestCase(1, 1),
                        new broadcastTestCase(2, 1),
                        new broadcastTestCase(2, 2),
                        new broadcastTestCase(3, 2),
                        new broadcastTestCase(4, 2),
                };

        for (broadcastTestCase test : tests) {

            // Create mock network object.
            MockNetwork network = new MockNetwork();

            // make the set of players.
            TreeMap<Integer, VerificationKey> players = new TreeMap<Integer, VerificationKey>();
            for (int i = 1; i <= test.recipients; i ++) {
                players.put(i, new MockVerificationKey(i));
            }

            MockMessageFactory messages = new MockMessageFactory();

            // Set up the network operations object.
                CoinShuffle.ShuffleMachine.Round netop =
                        net(577, new MockSessionIdentifier("broadcastTest" + test.recipients), new MockSigningKey(1), players,
                            Phase.Shuffling, messages, network);
                netop.broadcast(messages.make());
        }
    }

    static class sendToTestCase {
        int sender;
        int recipient;
        int players;
        boolean success;

        public sendToTestCase(int sender, int recipient, int players, boolean success) {
            this.sender = sender;
            this.recipient = recipient;
            this.players = players;
            this.success = success;
        }
    }

    @Test
    public void testSend() throws InvalidParticipantSetException {
        sendToTestCase tests[] = new sendToTestCase[]{
                // Case where recipient does not exist.
                new sendToTestCase(1, 3, 2, false),
                // Cannot send to myself.
                new sendToTestCase(1, 1, 2, false),
                // Success case.
                new sendToTestCase(1, 2, 2, true)
        };

        int i = 0;
        for (sendToTestCase test : tests) {
            // The player sending and inbox.
            MockSigningKey sk = new MockSigningKey(1);

            // Create mock network object.
            MockNetwork network = new MockNetwork();

            // make the set of players.
            TreeMap<Integer, VerificationKey> players = new TreeMap<Integer, VerificationKey>();
            for (int j = 1; j <= test.players; j ++) {
                players.put(j, new MockVerificationKey(j));
            }

            // Set up the shuffle machine (only used to query for the current phase).
            MockMessageFactory message = new MockMessageFactory();
            MockSessionIdentifier mockSessionIdentifier = new MockSessionIdentifier("testSend" + i);

            // Set up the network operations object.
            CoinShuffle.ShuffleMachine.Round netop =
                    net(8989, mockSessionIdentifier, sk, players,
                            Phase.Shuffling, message, network);

            netop.send(new Packet(
                    new MockMessage(), mockSessionIdentifier,
                    Phase.Shuffling,
                    sk.VerificationKey(),
                    new MockVerificationKey(test.recipient)));

            int expected = (test.success ? 1 : 0);

            Queue<Map.Entry<Packet, MockVerificationKey>> responses = network.getResponses();
            Assert.assertEquals(
                    String.format(
                            "Recieved %d responses when only expected %d in test case %d",
                            responses.size(), expected, i),
                    (test.success ? 1 : 0),
                    responses.size());

            for (Map.Entry msg : responses) {
                Assert.assertTrue("Received response does not equal expected",new MockVerificationKey(test.recipient).equals(msg.getValue()));
            }
            i++;
        }
    }

    static class receiveFromTestCase {
        int[] players;
        int requested; // The player that the message was expected from.
        Phase phase; // The expected phase.
        Packet packet;
        Error e; // If an exception is expected.

        public receiveFromTestCase(int[] players, int requested, Phase phase,Packet packet, Error e) {
            this.players = players;
            this.requested = requested;
            this.packet = packet;
            this.e = e;
            this.phase = phase;
        }
    }

    @Test(expected = TimeoutError.class)
    public void testReceiveFrom() throws InvalidParticipantSetException, InterruptedException, BlameException, ValueException, FormatException {
        receiveFromTestCase tests[] = new receiveFromTestCase[]{
                // time out exception test case.
                new receiveFromTestCase(new int []{1,2,3}, 2, Phase.Shuffling, null, new TimeoutError()),
                // Various malformed inputs.
                // TODO
        };

        int i = 0;
        for (receiveFromTestCase test : tests) {
            // The player sending and inbox.
            MockSigningKey sk = new MockSigningKey(1);

            // Create mock network object.
            MockNetwork network = new MockNetwork();

            // make the set of players.
            TreeMap<Integer, VerificationKey> players = new TreeMap<Integer, VerificationKey>();
            for (int j = 1; j <= test.players.length; j ++) {
                players.put(j, new MockVerificationKey(test.players[j - 1]));
            }

            MockSessionIdentifier mockSessionIdentifier = new MockSessionIdentifier("receiveFromTest" + i);

            // Set up the network operations object.
            CoinShuffle.ShuffleMachine.Round netop =
                    net(9341, mockSessionIdentifier, sk, players,
                            Phase.Shuffling, new MockMessageFactory(), network);

            netop.receiveFrom(new MockVerificationKey(test.requested), test.phase);
            i++;
        }
    }

    static class receiveFromMultipleTestCase {
        final int players;
        final int me;
        final int[] receiveFrom; // Who are we expecting?
        final int[] sendBefore; // Send before we call the function.
        final int[] sendAfter; // Send after the function is called.
        final boolean timeoutExpected;

        public receiveFromMultipleTestCase(int players, int me, int[] receiveFrom, int[] sendBefore, int[] sendAfter) {
            this.players = players;
            this.me = me;
            this.receiveFrom = receiveFrom;
            this.sendBefore = sendBefore;
            this.sendAfter = sendAfter;
            timeoutExpected = false;
        }

        public receiveFromMultipleTestCase(int players, int me, int[] receiveFrom, int[] sendBefore, int[] sendAfter, boolean timeoutExpected) {
            this.players = players;
            this.me = me;
            this.receiveFrom = receiveFrom;
            this.sendBefore = sendBefore;
            this.sendAfter = sendAfter;
            this.timeoutExpected = timeoutExpected;
        }
    }

    @Test
    public void testReceiveFromMultiple() throws InvalidParticipantSetException, InterruptedException, BlameException, ValueException, FormatException, ProtocolException {

        receiveFromMultipleTestCase tests[] = new receiveFromMultipleTestCase[]{
                // Very simple test case.
                new receiveFromMultipleTestCase(3, 2,
                        new int[]{3},
                        new int[]{},
                        new int[]{3}),
                // Timeout expected.
                new receiveFromMultipleTestCase(4, 2,
                        new int[]{3, 4},
                        new int[]{},
                        new int[]{3}, true),
                // before and after.
                new receiveFromMultipleTestCase(4, 2,
                        new int[]{3, 4},
                        new int[]{4},
                        new int[]{3})
        };

        int i = 0;
        for (receiveFromMultipleTestCase test : tests) {
            // The player sending and inbox.
            MockSigningKey sk = new MockSigningKey(test.me);

            // Create mock network object.
            MockNetwork network = new MockNetwork();

            // make the set of players.
            TreeMap<Integer, VerificationKey> players = new TreeMap<Integer, VerificationKey>();
            for (int j = 1; j <= test.players; j ++) {
                players.put(j, new MockVerificationKey(j));
            }

            MockSessionIdentifier mockSessionIdentifier = new MockSessionIdentifier("receiveFromMultiple" + i);

            // Set up the network operations object.
            CoinShuffle.ShuffleMachine.Round netop =
                    net(475, mockSessionIdentifier, sk, players,
                            Phase.Shuffling, new MockMessageFactory(), network);

            // Send the first set of messages.
            for (int from: test.sendBefore) {
                network.deliver(new Packet(
                        new MockMessage(),
                        mockSessionIdentifier,
                        Phase.BroadcastOutput,
                        new MockVerificationKey(from),
                        new MockVerificationKey(test.me)));
            }

            // Receive a message from an earlier phase to make sure we flip through the first set of messages.
            network.deliver(new Packet(
                    new MockMessage(),
                    mockSessionIdentifier,
                    Phase.Shuffling,
                    new MockVerificationKey(1),
                    new MockVerificationKey(test.me)));

            try {
                netop.receiveFrom(new MockVerificationKey(1), Phase.Shuffling);
            } catch (TimeoutError e) {
                Assert.fail();
            }

            // Then send the second set of messages.
            for (int from: test.sendAfter) {
                network.deliver(new Packet(
                        new MockMessage(),
                        mockSessionIdentifier,
                        Phase.BroadcastOutput,
                        new MockVerificationKey(from),
                        new MockVerificationKey(test.me)));
            }

            Set<VerificationKey> receiveFrom = new HashSet<>();
            for (int from : test.receiveFrom) {
                receiveFrom.add(new MockVerificationKey(from));
            }

            // Now receive them all.
            Map<VerificationKey, Message> messages = null;
            try {
                messages = netop.receiveFromMultiple(receiveFrom, Phase.BroadcastOutput, true);
                if (test.timeoutExpected) {
                    Assert.fail("Failed to throw TimeoutError in test case " + i);
                }
            } catch (TimeoutError e) {
                if (!test.timeoutExpected) {
                    Assert.fail();
                }
                continue;
            }

            // There should be one message for each participant.
            for (int from : test.receiveFrom) {
                if (!messages.containsKey(new MockVerificationKey(from))) {
                    Assert.fail();
                }
                messages.remove(new MockVerificationKey(from));
            }

            Assert.assertEquals(0, messages.size());

            i++;
        }
    }
}
