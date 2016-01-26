package com.shuffle.p2p;

/**
 * Created by cosmos on 1/25/16.
 */
/**
 * Identity -- the object used to identify a particular tcppeer.
 *
 * Message  -- the object which will be sent back and forth over this channel.
 *
 * Token    -- the information that defines a particular session. For example, for an ecrypted chat
 *             there is a key.
 *
 * Created by Daniel Krawisz on 12/16/15.
 */
public interface Channel<Identity, Message, Token> {

    // Turn on listening for other peers. Returns false if it is already listening.
    boolean listen(final Listener<Identity, Message, Token> listener);

    Peer<Identity, Message, Token> getPeer(Identity you);
}
