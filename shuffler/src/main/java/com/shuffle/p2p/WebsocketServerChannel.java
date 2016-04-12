/**
 *
 * Copyright © 2016 Mycelium.
 * Use of this source code is governed by an ISC
 * license that can be found in the LICENSE file.
 *
 */

package com.shuffle.p2p;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

import javax.websocket.CloseReason;
import javax.websocket.DeploymentException;
import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;
import org.glassfish.tyrus.container.grizzly.server.*;
import org.glassfish.tyrus.core.TyrusSession;
import org.glassfish.tyrus.server.Server;

/**
 * Created by Eugene Siegel on 4/1/16.
 */

public class WebsocketServerChannel implements Channel<InetAddress, Bytestring> {

    /**
     *  Necessary class to listen for remote websocket peers
     */

    Listener<InetAddress, Bytestring> globalListener = null;

    @ServerEndpoint("/")  //    ws://localhost:port/path
    private class WebsocketServerEndpoint {

        Session userSession;

        @OnOpen
        public void onOpen(Session userSession) {
            this.userSession = userSession;
            String clientIp = ((TyrusSession)this.userSession).getRemoteAddr();
            InetAddress identity;
            try {
                identity = InetAddress.getByName(clientIp);
            } catch (UnknownHostException e) {
                this.userSession = null;
                return;
            }
            WebsocketPeer.WebsocketSession session = openSessions.putOpenSession(identity,this.userSession);
            globalListener.newSession(session);
        }

        //@OnMessage? Will this receive any messages?

        @OnClose
        public void onClose(Session userSession, CloseReason reason) {
            this.userSession = null;
        }

    }

    private class Peers {

        private final Map<InetAddress, WebsocketPeer> peers = new HashMap<>();

        public synchronized WebsocketPeer get(InetAddress identity) {
            WebsocketPeer peer = peers.get(identity);
            if (peer == null) {
                peer = peers.put(identity, new WebsocketPeer(identity));
            }
            return peer;
        }
    }

    final Peers peers = new Peers();

    class OpenSessions {

        // The sessions which are currently open.
        private Map<InetAddress, WebsocketPeer.WebsocketSession> openSessions = new ConcurrentHashMap<>();

        // This is for creating a session that was initiated by a remote peer.
        public synchronized WebsocketPeer.WebsocketSession putOpenSession(
                InetAddress identity,
                Session session
        ) {
            WebsocketPeer.WebsocketSession openSession = openSessions.get(identity);
            if (openSession != null) {
                if (openSession.session.isOpen()) {
                    return null;
                }

                openSessions.remove(identity);
            }

            WebsocketPeer peer;
            try {
                peer = peers.get(identity).setSession(session);
            } catch (IOException e) {
                return null;
            }

            return openSessions.put(identity, peer.currentSession);

        }

        public WebsocketPeer.WebsocketSession get(InetAddress identity) {
            return openSessions.get(identity);
        }

        public WebsocketPeer.WebsocketSession remove(InetAddress identity) {
            return openSessions.remove(identity);
        }

        public void closeAll() {
            for (WebsocketPeer.WebsocketSession session : openSessions.values()) {
                session.close();
            }
        }

    }

    private OpenSessions openSessions = null;

    public class WebsocketPeer extends FundamentalPeer<InetAddress, Bytestring> {

        WebsocketSession currentSession;

        public WebsocketPeer(InetAddress identity) { super(identity); }

        private WebsocketPeer setSession(javax.websocket.Session session) throws IOException {
            currentSession = new WebsocketSession(session);
            return this;
        }

        @Override
        public synchronized com.shuffle.p2p.Session<InetAddress, Bytestring> openSession(
                final Receiver<Bytestring> receiver) {
            return null;
        }


        public class WebsocketSession implements com.shuffle.p2p.Session<InetAddress, Bytestring> {
            javax.websocket.Session session;

            public WebsocketSession(javax.websocket.Session session) throws IOException {
                this.session = session;
            }

            @Override
            public synchronized boolean send(Bytestring message) {
                // Don't allow sending messages while we're opening or closing the channel.
                synchronized (lock) { }

                if (!session.isOpen()) {
                    return false;
                }

                try {
                    // MUST sendBinary rather than sendText to receive byte[] messages
                    ByteBuffer buf = ByteBuffer.wrap(message.bytes);
                    session.getBasicRemote().sendBinary(buf);
                } catch (IOException e) {
                    return false;
                }
                return true;
            }

            @Override
            public synchronized void close() {
                try {
                    session.close();
                } catch (IOException e) {

                }
                session = null;
                WebsocketPeer.this.currentSession = null;
                openSessions.remove(WebsocketPeer.this.identity());
            }

            @Override
            public synchronized boolean closed() {
                return session == null || !session.isOpen();
            }

            @Override
            public Peer<InetAddress, Bytestring> peer() {
                return WebsocketPeer.this;
            }

        }

    }

    private final int port;
    private final String hostName;
    //private static final String path;
    private Server server;
    private boolean running = false;
    private final Object lock = new Object();

    public WebsocketServerChannel(
            int port,
            String hostName
            //String path
    ) {

        this.port = port;
        this.hostName = hostName;
        //this.path = path;
    }

    private class WebsocketConnection implements Connection<InetAddress, Bytestring> {

        @Override
        public void close() {
            synchronized (lock) {
                if (server != null) {
                    server.stop();
                    openSessions.closeAll();
                    openSessions = null;
                    running = false;
                    server = null;
                    globalListener = null;
                }
            }
        }
    }


    @Override
    public Connection<InetAddress, Bytestring> open(Listener<InetAddress, Bytestring> listener) {
        if (listener == null) {
            throw new NullPointerException();
        }

        globalListener = listener;

        synchronized (lock) {
            if (running) return null;

            if (server == null) {
                try {
                    //rootPath variable?
                    server = new Server(hostName, port, "", new HashMap<String, Object>(), WebsocketServerEndpoint.class);
                    server.start();
                } catch (DeploymentException e) {
                    return null;
                }
            }

            running = true;
            openSessions = new OpenSessions();
            return new WebsocketConnection();
        }
    }

    @Override
    public Peer<InetAddress, Bytestring> getPeer(InetAddress you) {
        return peers.get(you);
    }

}
