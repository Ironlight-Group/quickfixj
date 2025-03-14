/*******************************************************************************
 * Copyright (c) quickfixengine.org  All rights reserved.
 *
 * This file is part of the QuickFIX FIX Engine
 *
 * This file may be distributed under the terms of the quickfixengine.org
 * license as defined by quickfixengine.org and appearing in the file
 * LICENSE included in the packaging of this file.
 *
 * This file is provided AS IS with NO WARRANTY OF ANY KIND, INCLUDING
 * THE WARRANTY OF DESIGN, MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE.
 *
 * See http://www.quickfixengine.org/LICENSE for licensing information.
 *
 * Contact ask@quickfixengine.org if any conditions of this licensing
 * are not clear to you.
 ******************************************************************************/

package quickfix.mina.ssl;

import org.apache.mina.core.filterchain.IoFilterAdapter;
import org.apache.mina.core.filterchain.IoFilterChain;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.ssl.SslFilter;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import quickfix.Acceptor;
import quickfix.ApplicationAdapter;
import quickfix.ConfigError;
import quickfix.DefaultMessageFactory;
import quickfix.FixVersions;
import quickfix.Initiator;
import quickfix.MemoryStoreFactory;
import quickfix.MessageFactory;
import quickfix.MessageStoreFactory;
import quickfix.RuntimeError;
import quickfix.Session;
import quickfix.SessionFactory;
import quickfix.SessionID;
import quickfix.SessionSettings;
import quickfix.ThreadedSocketAcceptor;
import quickfix.ThreadedSocketInitiator;
import quickfix.mina.IoSessionResponder;
import quickfix.mina.ProtocolFactory;
import quickfix.mina.SessionConnector;

import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import org.apache.mina.util.AvailablePortFinder;
import org.junit.After;
import quickfix.mina.SocksProxyServer;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class SSLCertificateTest {

    // Note: To diagnose cipher suite errors, run with -Djavax.net.debug=ssl:handshake
    private static final String CIPHER_SUITES_TLS = "TLS_RSA_WITH_AES_128_CBC_SHA";

    @After
    public void cleanup() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            java.util.logging.Logger.getLogger(SSLCertificateTest.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Test
    public void shouldAuthenticateServerCertificate() throws Exception {
        int freePort = AvailablePortFinder.getNextAvailable();
        TestAcceptor acceptor = new TestAcceptor(createAcceptorSettings("single-session/server.keystore", false,
                "single-session/empty.keystore", CIPHER_SUITES_TLS, "TLSv1.2", "JKS", "JKS", freePort));

        try {
            acceptor.start();

            TestInitiator initiator = new TestInitiator(
                    createInitiatorSettings("single-session/empty.keystore", "single-session/client.truststore",
                            CIPHER_SUITES_TLS, "TLSv1.2", "ZULU", "ALFA", Integer.toString(freePort), "JKS", "JKS"));

            try {
                initiator.start();

                initiator.assertNoSslExceptionThrown();
                initiator.assertLoggedOn(new SessionID(FixVersions.BEGINSTRING_FIX44, "ZULU", "ALFA"));
                initiator.assertAuthenticated(new SessionID(FixVersions.BEGINSTRING_FIX44, "ZULU", "ALFA"),
                        new BigInteger("1448538842"));

                acceptor.assertNoSslExceptionThrown();
                acceptor.assertLoggedOn(new SessionID(FixVersions.BEGINSTRING_FIX44, "ALFA", "ZULU"));
                acceptor.assertNotAuthenticated(new SessionID(FixVersions.BEGINSTRING_FIX44, "ALFA", "ZULU"));
            } finally {
                initiator.stop();
            }
        } finally {
            acceptor.stop();
        }
    }

    @Test
    public void shouldLoginViaSocks4Proxy() throws Exception {
        shouldAuthenticateServerCertificateViaSocksProxy("4");
    }

    @Test
    public void shouldLoginViaSocks4aProxy() throws Exception {
        shouldAuthenticateServerCertificateViaSocksProxy("4a");
    }

    @Test
    public void shouldLoginViaSocks5Proxy() throws Exception {
        shouldAuthenticateServerCertificateViaSocksProxy("5");
    }

    public void shouldAuthenticateServerCertificateViaSocksProxy(String proxyVersion) throws Exception {
        int proxyPort = AvailablePortFinder.getNextAvailable();

        SocksProxyServer proxyServer = new SocksProxyServer(proxyPort);
        proxyServer.start();

        try {
            int port = AvailablePortFinder.getNextAvailable();
            TestAcceptor acceptor = new TestAcceptor(createAcceptorSettings("single-session/server.keystore", false,
                    "single-session/empty.keystore", CIPHER_SUITES_TLS, "TLSv1.2", "JKS", "JKS", port));

            try {
                acceptor.start();

                SessionSettings initiatorSettings = createInitiatorSettings("single-session/empty.keystore", "single-session/client.truststore",
                        CIPHER_SUITES_TLS, "TLSv1.2", "ZULU", "ALFA", Integer.toString(port), "JKS", "JKS");

                Properties defaults = initiatorSettings.getDefaultProperties();

                defaults.put(Initiator.SETTING_PROXY_HOST, "localhost");
                defaults.put(Initiator.SETTING_PROXY_PORT, Integer.toString(proxyPort));
                defaults.put(Initiator.SETTING_PROXY_TYPE, "socks");
                defaults.put(Initiator.SETTING_PROXY_VERSION, proxyVersion);
                defaults.put(Initiator.SETTING_PROXY_USER, "proxy-user");
                defaults.put(Initiator.SETTING_PROXY_PASSWORD, "proxy-password");

                TestInitiator initiator = new TestInitiator(initiatorSettings);

                try {
                    initiator.start();

                    initiator.assertNoSslExceptionThrown();
                    initiator.assertLoggedOn(new SessionID(FixVersions.BEGINSTRING_FIX44, "ZULU", "ALFA"));
                    initiator.assertAuthenticated(new SessionID(FixVersions.BEGINSTRING_FIX44, "ZULU", "ALFA"), new BigInteger("1448538842"));

                    acceptor.assertNoSslExceptionThrown();
                    acceptor.assertLoggedOn(new SessionID(FixVersions.BEGINSTRING_FIX44, "ALFA", "ZULU"));
                    acceptor.assertNotAuthenticated(new SessionID(FixVersions.BEGINSTRING_FIX44, "ALFA", "ZULU"));
                } finally {
                    initiator.stop();
                }
            } finally {
                acceptor.stop();
            }
        } finally {
            proxyServer.stop();
        }
    }

    /**
     * Server certificate has Common Name = localhost and no Server Alternative Name extension.
     */
    @Test
    public void shouldAuthenticateServerNameUsingServerCommonName() throws Exception {
        int freePort = AvailablePortFinder.getNextAvailable();
        TestAcceptor acceptor = new TestAcceptor(createAcceptorSettings("single-session/server-cn.keystore", false,
                "single-session/empty.keystore", CIPHER_SUITES_TLS, "TLSv1.2", "JKS", "JKS", freePort));

        try {
            acceptor.start();

            TestInitiator initiator = new TestInitiator(
                    createInitiatorSettings("single-session/empty.keystore", "single-session/client-cn.truststore",
                            CIPHER_SUITES_TLS, "TLSv1.2", "ZULU", "ALFA", Integer.toString(freePort), "JKS", "JKS", "HTTPS"));

            try {
                initiator.start();

                initiator.assertNoSslExceptionThrown();
                initiator.assertLoggedOn(new SessionID(FixVersions.BEGINSTRING_FIX44, "ZULU", "ALFA"));
                initiator.assertAuthenticated(new SessionID(FixVersions.BEGINSTRING_FIX44, "ZULU", "ALFA"),
                        new BigInteger("1683903911"));

                acceptor.assertNoSslExceptionThrown();
                acceptor.assertLoggedOn(new SessionID(FixVersions.BEGINSTRING_FIX44, "ALFA", "ZULU"));
                acceptor.assertNotAuthenticated(new SessionID(FixVersions.BEGINSTRING_FIX44, "ALFA", "ZULU"));
            } finally {
                initiator.stop();
            }
        } finally {
            acceptor.stop();
        }
    }

    /**
     * Server certificate has Common Name = server, but it has Server Alternative Name extension (DNS name).
     */
    @Test
    public void shouldAuthenticateServerNameUsingSNIExtension() throws Exception {
        int freePort = AvailablePortFinder.getNextAvailable();
        TestAcceptor acceptor = new TestAcceptor(createAcceptorSettings("single-session/server-sni.keystore", false,
                "single-session/empty.keystore", CIPHER_SUITES_TLS, "TLSv1.2", "JKS", "JKS", freePort));

        try {
            acceptor.start();

            TestInitiator initiator = new TestInitiator(
                    createInitiatorSettings("single-session/empty.keystore", "single-session/client-sni.truststore",
                            CIPHER_SUITES_TLS, "TLSv1.2", "ZULU", "ALFA", Integer.toString(freePort), "JKS", "JKS", "HTTPS"));

            try {
                initiator.start();

                initiator.assertNoSslExceptionThrown();
                initiator.assertLoggedOn(new SessionID(FixVersions.BEGINSTRING_FIX44, "ZULU", "ALFA"));
                initiator.assertAuthenticated(new SessionID(FixVersions.BEGINSTRING_FIX44, "ZULU", "ALFA"),
                        new BigInteger("1683904647"));

                acceptor.assertNoSslExceptionThrown();
                acceptor.assertLoggedOn(new SessionID(FixVersions.BEGINSTRING_FIX44, "ALFA", "ZULU"));
                acceptor.assertNotAuthenticated(new SessionID(FixVersions.BEGINSTRING_FIX44, "ALFA", "ZULU"));
            } finally {
                initiator.stop();
            }
        } finally {
            acceptor.stop();
        }
    }

    /**
     * Server certificate has Common Name = server and no Server Alternative Name extension.
     */
    @Test
    public void shouldFailWhenHostnameDoesNotMatchServerName() throws Exception {
        int freePort = AvailablePortFinder.getNextAvailable();

        TestAcceptor acceptor = new TestAcceptor(createAcceptorSettings("single-session/server-bad-cn.keystore", false,
                "single-session/empty.keystore", CIPHER_SUITES_TLS, "TLSv1.2", "JKS", "JKS", freePort));

        try {
            acceptor.start();

            TestInitiator initiator = new TestInitiator(
                    createInitiatorSettings("single-session/empty.keystore", "single-session/client-bad-cn.truststore",
                            CIPHER_SUITES_TLS, "TLSv1.2", "ZULU", "ALFA", Integer.toString(freePort), "JKS", "JKS", "HTTPS"));

            try {
                initiator.start();

                initiator.assertSslExceptionThrown("No name matching localhost found", SSLHandshakeException.class);
                initiator.assertNotLoggedOn(new SessionID(FixVersions.BEGINSTRING_FIX44, "ZULU", "ALFA"));
                initiator.assertNotAuthenticated(new SessionID(FixVersions.BEGINSTRING_FIX44, "ZULU", "ALFA"));

                acceptor.assertSslExceptionThrown("Received fatal alert: certificate_unknown", SSLHandshakeException.class);
                acceptor.assertNotLoggedOn(new SessionID(FixVersions.BEGINSTRING_FIX44, "ALFA", "ZULU"));
                acceptor.assertNotAuthenticated(new SessionID(FixVersions.BEGINSTRING_FIX44, "ALFA", "ZULU"));
            } finally {
                initiator.stop();
            }
        } finally {
            acceptor.stop();
        }
    }

    @Test
    public void shouldAuthenticateServerAndClientCertificates() throws Exception {
        int freePort = AvailablePortFinder.getNextAvailable();
        TestAcceptor acceptor = new TestAcceptor(createAcceptorSettings("single-session/server.keystore", true,
                "single-session/server.truststore", CIPHER_SUITES_TLS, "TLSv1.2", "JKS", "JKS", freePort));

        try {
            acceptor.start();

            TestInitiator initiator = new TestInitiator(
                    createInitiatorSettings("single-session/client.keystore", "single-session/client.truststore",
                            CIPHER_SUITES_TLS, "TLSv1.2", "ZULU", "ALFA", Integer.toString(freePort), "JKS", "JKS"));

            try {
                initiator.start();

                initiator.assertNoSslExceptionThrown();
                initiator.assertLoggedOn(new SessionID(FixVersions.BEGINSTRING_FIX44, "ZULU", "ALFA"));
                initiator.assertAuthenticated(new SessionID(FixVersions.BEGINSTRING_FIX44, "ZULU", "ALFA"),
                        new BigInteger("1448538842"));

                acceptor.assertNoSslExceptionThrown();
                acceptor.assertLoggedOn(new SessionID(FixVersions.BEGINSTRING_FIX44, "ALFA", "ZULU"));
                acceptor.assertAuthenticated(new SessionID(FixVersions.BEGINSTRING_FIX44, "ALFA", "ZULU"),
                        new BigInteger("1448538787"));
            } finally {
                initiator.stop();
            }
        } finally {
            acceptor.stop();
        }
    }

    @Test
    public void shouldAuthenticateServerAndClientCertificatesWhenUsingDifferentKeystoreFormats() throws Exception {
        int freePort = AvailablePortFinder.getNextAvailable();
        TestAcceptor acceptor = new TestAcceptor(createAcceptorSettings("single-session/server-pkcs12.keystore", true,
                "single-session/server-jceks.truststore", CIPHER_SUITES_TLS, "TLSv1.2", "PKCS12",
                "JCEKS", freePort));

        try {
            acceptor.start();

            TestInitiator initiator = new TestInitiator(createInitiatorSettings("single-session/client-jceks.keystore",
                    "single-session/client-jceks.keystore", CIPHER_SUITES_TLS, "TLSv1.2", "ZULU", "ALFA",
                    Integer.toString(freePort), "JCEKS", "JCEKS"));

            try {
                initiator.start();

                initiator.assertNoSslExceptionThrown();
                initiator.assertLoggedOn(new SessionID(FixVersions.BEGINSTRING_FIX44, "ZULU", "ALFA"));
                initiator.assertAuthenticated(new SessionID(FixVersions.BEGINSTRING_FIX44, "ZULU", "ALFA"),
                        new BigInteger("1449683167"));

                acceptor.assertNoSslExceptionThrown();
                acceptor.assertLoggedOn(new SessionID(FixVersions.BEGINSTRING_FIX44, "ALFA", "ZULU"));
                acceptor.assertAuthenticated(new SessionID(FixVersions.BEGINSTRING_FIX44, "ALFA", "ZULU"),
                        new BigInteger("1449683336"));
            } finally {
                initiator.stop();
            }
        } finally {
            acceptor.stop();
        }
    }

    @Test
    public void shouldAuthenticateServerAndClientCertificatesForIndividualSessions() throws Exception {
        TestAcceptor acceptor = new TestAcceptor(createMultiSessionAcceptorSettings(
                "multi-session/server.keystore", true, new String[] { "multi-session/server1.truststore",
                        "multi-session/server2.truststore", "multi-session/server3.truststore" },
                CIPHER_SUITES_TLS, "TLSv1.2"));

        try {
            acceptor.start();

            TestInitiator initiator1 = new TestInitiator(
                    createInitiatorSettings("multi-session/client1.keystore", "multi-session/client1.keystore",
                            CIPHER_SUITES_TLS, "TLSv1.2", "ZULU0", "ALFA0", "12340", "JKS", "JKS"));
            TestInitiator initiator2 = new TestInitiator(
                    createInitiatorSettings("multi-session/client2.keystore", "multi-session/client2.keystore",
                            CIPHER_SUITES_TLS, "TLSv1.2", "ZULU1", "ALFA1", "12341", "JKS", "JKS"));
            TestInitiator initiator3 = new TestInitiator(
                    createInitiatorSettings("multi-session/client3.keystore", "multi-session/client3.keystore",
                            CIPHER_SUITES_TLS, "TLSv1.2", "ZULU2", "ALFA2", "12342", "JKS", "JKS"));

            try {
                initiator1.start();
                initiator2.start();
                initiator3.start();

                initiator1.assertNoSslExceptionThrown();
                initiator1.assertLoggedOn(new SessionID(FixVersions.BEGINSTRING_FIX44, "ZULU0", "ALFA0"));
                initiator1.assertAuthenticated(new SessionID(FixVersions.BEGINSTRING_FIX44, "ZULU0", "ALFA0"),
                        new BigInteger("1449581686"));

                initiator2.assertNoSslExceptionThrown();
                initiator2.assertLoggedOn(new SessionID(FixVersions.BEGINSTRING_FIX44, "ZULU1", "ALFA1"));
                initiator2.assertAuthenticated(new SessionID(FixVersions.BEGINSTRING_FIX44, "ZULU1", "ALFA1"),
                        new BigInteger("1449581686"));

                initiator3.assertNoSslExceptionThrown();
                initiator3.assertLoggedOn(new SessionID(FixVersions.BEGINSTRING_FIX44, "ZULU2", "ALFA2"));
                initiator3.assertAuthenticated(new SessionID(FixVersions.BEGINSTRING_FIX44, "ZULU2", "ALFA2"),
                        new BigInteger("1449581686"));

                acceptor.assertNoSslExceptionThrown();
                acceptor.assertLoggedOn(new SessionID(FixVersions.BEGINSTRING_FIX44, "ALFA0", "ZULU0"));
                acceptor.assertLoggedOn(new SessionID(FixVersions.BEGINSTRING_FIX44, "ALFA1", "ZULU1"));
                acceptor.assertLoggedOn(new SessionID(FixVersions.BEGINSTRING_FIX44, "ALFA2", "ZULU2"));
                acceptor.assertAuthenticated(new SessionID(FixVersions.BEGINSTRING_FIX44, "ALFA0", "ZULU0"),
                        new BigInteger("1449581008"));
                acceptor.assertAuthenticated(new SessionID(FixVersions.BEGINSTRING_FIX44, "ALFA1", "ZULU1"),
                        new BigInteger("1449581372"));
                acceptor.assertAuthenticated(new SessionID(FixVersions.BEGINSTRING_FIX44, "ALFA2", "ZULU2"),
                        new BigInteger("1449581412"));

            } finally {
                initiator1.stop();
                initiator2.stop();
                initiator3.stop();
            }
        } finally {
            acceptor.stop();
        }
    }

    @Test
    public void shouldFailIndividualSessionsWhenInvalidCertificatesUsed() throws Exception {
        TestAcceptor acceptor = new TestAcceptor(createMultiSessionAcceptorSettings(
                "multi-session/server.keystore", true, new String[] { "multi-session/server1.truststore",
                        "multi-session/server2.truststore", "multi-session/server3.truststore" },
                CIPHER_SUITES_TLS, "TLSv1.2"));

        try {
            acceptor.start();

            TestInitiator initiator1 = new TestInitiator(
                    createInitiatorSettings("multi-session/client2.keystore", "multi-session/client2.keystore",
                            CIPHER_SUITES_TLS, "TLSv1.2", "ZULU0", "ALFA0", "12340", "JKS", "JKS"));
            TestInitiator initiator2 = new TestInitiator(
                    createInitiatorSettings("multi-session/client1.keystore", "multi-session/client1.keystore",
                            CIPHER_SUITES_TLS, "TLSv1.2", "ZULU1", "ALFA1", "12341", "JKS", "JKS"));
            TestInitiator initiator3 = new TestInitiator(
                    createInitiatorSettings("multi-session/client3.keystore", "multi-session/client3.keystore",
                            CIPHER_SUITES_TLS, "TLSv1.2", "ZULU2", "ALFA2", "12342", "JKS", "JKS"));

            try {
                initiator1.start();
                initiator2.start();
                initiator3.start();

                initiator1.assertSslExceptionThrown();
                initiator1.assertNotLoggedOn(new SessionID(FixVersions.BEGINSTRING_FIX44, "ZULU0", "ALFA0"));
                initiator1.assertNotAuthenticated(new SessionID(FixVersions.BEGINSTRING_FIX44, "ZULU0", "ALFA0"));

                initiator2.assertSslExceptionThrown();
                initiator2.assertNotLoggedOn(new SessionID(FixVersions.BEGINSTRING_FIX44, "ZULU1", "ALFA1"));
                initiator2.assertNotAuthenticated(new SessionID(FixVersions.BEGINSTRING_FIX44, "ZULU1", "ALFA1"));

                initiator3.assertNoSslExceptionThrown();
                initiator3.assertLoggedOn(new SessionID(FixVersions.BEGINSTRING_FIX44, "ZULU2", "ALFA2"));
                initiator3.assertAuthenticated(new SessionID(FixVersions.BEGINSTRING_FIX44, "ZULU2", "ALFA2"),
                        new BigInteger("1449581686"));

                acceptor.assertSslExceptionThrown();
                acceptor.assertNotLoggedOn(new SessionID(FixVersions.BEGINSTRING_FIX44, "ALFA0", "ZULU0"));
                acceptor.assertNotLoggedOn(new SessionID(FixVersions.BEGINSTRING_FIX44, "ALFA1", "ZULU1"));
                acceptor.assertLoggedOn(new SessionID(FixVersions.BEGINSTRING_FIX44, "ALFA2", "ZULU2"));
                acceptor.assertNotAuthenticated(new SessionID(FixVersions.BEGINSTRING_FIX44, "ALFA0", "ZULU0"));
                acceptor.assertNotAuthenticated(new SessionID(FixVersions.BEGINSTRING_FIX44, "ALFA1", "ZULU1"));
                acceptor.assertAuthenticated(new SessionID(FixVersions.BEGINSTRING_FIX44, "ALFA2", "ZULU2"),
                        new BigInteger("1449581412"));
            } finally {
                initiator1.stop();
                initiator2.stop();
                initiator3.stop();
            }
        } finally {
            acceptor.stop();
        }
    }

    @Test
    public void shouldFailWhenUsingEmptyServerKeyStore() throws Exception {
        int freePort = AvailablePortFinder.getNextAvailable();
        TestAcceptor acceptor = new TestAcceptor(createAcceptorSettings("single-session/empty.keystore", false,
                "single-session/empty.keystore", null, null, "JKS", "JKS", freePort));

        try {
            acceptor.start();

            TestInitiator initiator = new TestInitiator(createInitiatorSettings("single-session/empty.keystore",
                    "single-session/empty.keystore", null, null, "ZULU", "ALFA", Integer.toString(freePort), "JKS", "JKS"));

            try {
                initiator.start();

                initiator.assertSslExceptionThrown();
                initiator.assertNotLoggedOn(new SessionID(FixVersions.BEGINSTRING_FIX44, "ZULU", "ALFA"));
                initiator.assertNotAuthenticated(new SessionID(FixVersions.BEGINSTRING_FIX44, "ZULU", "ALFA"));

                acceptor.assertSslExceptionThrown();
                acceptor.assertNotLoggedOn(new SessionID(FixVersions.BEGINSTRING_FIX44, "ALFA", "ZULU"));
                acceptor.assertNotAuthenticated(new SessionID(FixVersions.BEGINSTRING_FIX44, "ALFA", "ZULU"));
            } finally {
                initiator.stop();
            }
        } finally {
            acceptor.stop();
        }
    }

    @Test
    public void shouldFailWhenUsingEmptyClientTruststore() throws Exception {
        int freePort = AvailablePortFinder.getNextAvailable();
        TestAcceptor acceptor = new TestAcceptor(createAcceptorSettings("single-session/server.keystore", false,
                "single-session/empty.keystore", CIPHER_SUITES_TLS, "TLSv1.2", "JKS", "JKS", freePort));

        try {
            acceptor.start();

            TestInitiator initiator = new TestInitiator(
                    createInitiatorSettings("single-session/empty.keystore", "single-session/empty.keystore",
                            CIPHER_SUITES_TLS, "TLSv1.2", "ZULU", "ALFA", Integer.toString(freePort), "JKS", "JKS"));

            try {
                initiator.start();

                initiator.assertSslExceptionThrown();
                initiator.assertNotLoggedOn(new SessionID(FixVersions.BEGINSTRING_FIX44, "ZULU", "ALFA"));
                initiator.assertNotAuthenticated(new SessionID(FixVersions.BEGINSTRING_FIX44, "ZULU", "ALFA"));

                // client disconnects before acceptor throws an exception
                acceptor.assertNotLoggedOn(new SessionID(FixVersions.BEGINSTRING_FIX44, "ALFA", "ZULU"));
                acceptor.assertNotAuthenticated(new SessionID(FixVersions.BEGINSTRING_FIX44, "ALFA", "ZULU"));
            } finally {
                initiator.stop();
            }
        } finally {
            acceptor.stop();
        }
    }

    @Test
    public void shouldFailWhenUsingEmptyServerTrustore() throws Exception {
        int freePort = AvailablePortFinder.getNextAvailable();
        TestAcceptor acceptor = new TestAcceptor(createAcceptorSettings("single-session/server.keystore", true,
                "single-session/empty.keystore", CIPHER_SUITES_TLS, "TLSv1.2", "JKS", "JKS", freePort));

        try {
            acceptor.start();

            TestInitiator initiator = new TestInitiator(
                    createInitiatorSettings("single-session/client.keystore", "single-session/client.truststore",
                            CIPHER_SUITES_TLS, "TLSv1.2", "ZULU", "ALFA", Integer.toString(freePort), "JKS", "JKS"));

            try {
                initiator.start();

                initiator.assertSslExceptionThrown();
                initiator.assertNotLoggedOn(new SessionID(FixVersions.BEGINSTRING_FIX44, "ZULU", "ALFA"));
                initiator.assertNotAuthenticated(new SessionID(FixVersions.BEGINSTRING_FIX44, "ZULU", "ALFA"));

                acceptor.assertSslExceptionThrown();
                acceptor.assertNotLoggedOn(new SessionID(FixVersions.BEGINSTRING_FIX44, "ALFA", "ZULU"));
                acceptor.assertNotAuthenticated(new SessionID(FixVersions.BEGINSTRING_FIX44, "ALFA", "ZULU"));
            } finally {
                initiator.stop();
            }
        } finally {
            acceptor.stop();
        }
    }

    @Test
    public void shouldFailWhenUsingBadClientCertificate() throws Exception {
        int freePort = AvailablePortFinder.getNextAvailable();
        TestAcceptor acceptor = new TestAcceptor(createAcceptorSettings("single-session/server.keystore", true,
                "single-session/server.truststore", CIPHER_SUITES_TLS, "TLSv1.2", "JKS", "JKS", freePort));

        try {
            acceptor.start();

            TestInitiator initiator = new TestInitiator(
                    createInitiatorSettings("single-session/server.keystore", "single-session/client.truststore",
                            CIPHER_SUITES_TLS, "TLSv1.2", "ZULU", "ALFA", Integer.toString(freePort), "JKS", "JKS"));

            try {
                initiator.start();

                initiator.assertSslExceptionThrown();
                initiator.assertNotLoggedOn(new SessionID(FixVersions.BEGINSTRING_FIX44, "ZULU", "ALFA"));
                initiator.assertNotAuthenticated(new SessionID(FixVersions.BEGINSTRING_FIX44, "ZULU", "ALFA"));

                acceptor.assertSslExceptionThrown();
                acceptor.assertNotLoggedOn(new SessionID(FixVersions.BEGINSTRING_FIX44, "ALFA", "ZULU"));
                acceptor.assertNotAuthenticated(new SessionID(FixVersions.BEGINSTRING_FIX44, "ALFA", "ZULU"));
            } finally {
                initiator.stop();
            }
        } finally {
            acceptor.stop();
        }
    }

    @Test
    public void shouldFailWhenUsingBadServerCertificate() throws Exception {
        int freePort = AvailablePortFinder.getNextAvailable();
        TestAcceptor acceptor = new TestAcceptor(createAcceptorSettings("single-session/client.keystore", false,
                "single-session/empty.keystore", CIPHER_SUITES_TLS, "TLSv1.2", "JKS", "JKS", freePort));

        try {
            acceptor.start();

            TestInitiator initiator = new TestInitiator(
                    createInitiatorSettings("single-session/empty.keystore", "single-session/client.truststore",
                            CIPHER_SUITES_TLS, "TLSv1.2", "ZULU", "ALFA", Integer.toString(freePort), "JKS", "JKS"));

            try {
                initiator.start();

                initiator.assertSslExceptionThrown();
                initiator.assertNotLoggedOn(new SessionID(FixVersions.BEGINSTRING_FIX44, "ZULU", "ALFA"));
                initiator.assertNotAuthenticated(new SessionID(FixVersions.BEGINSTRING_FIX44, "ZULU", "ALFA"));

                acceptor.assertSslExceptionThrown();
                acceptor.assertNotLoggedOn(new SessionID(FixVersions.BEGINSTRING_FIX44, "ALFA", "ZULU"));
                acceptor.assertNotAuthenticated(new SessionID(FixVersions.BEGINSTRING_FIX44, "ALFA", "ZULU"));
            } finally {
                initiator.stop();
            }
        } finally {
            acceptor.stop();
        }
    }

    @Test
    public void shouldConnectDifferentTypesOfSessions() throws Exception {
        int sslPort = AvailablePortFinder.getNextAvailable();
        int nonSslPort = AvailablePortFinder.getNextAvailable();
        TestAcceptor acceptor = new TestAcceptor(createMixedSessionAcceptorSettings(sslPort, nonSslPort, "single-session/server.keystore"));

        try {
            acceptor.start();

            TestInitiator sslInitiator = new TestInitiator(
                    createInitiatorSettings("single-session/client.keystore", "single-session/client.truststore",
                            CIPHER_SUITES_TLS, "TLSv1.2", "ZULU_SSL", "ALFA_SSL", Integer.toString(sslPort), "JKS", "JKS"));

            TestInitiator nonSslInitiator = new TestInitiator(createInitiatorSettings("ZULU_NON_SSL", "ALFA_NON_SSL", nonSslPort));

            try {
                sslInitiator.start();
                nonSslInitiator.start();

                sslInitiator.assertNoSslExceptionThrown();
                sslInitiator.assertLoggedOn(new SessionID(FixVersions.BEGINSTRING_FIX44, "ZULU_SSL", "ALFA_SSL"));
                sslInitiator.assertAuthenticated(new SessionID(FixVersions.BEGINSTRING_FIX44, "ZULU_SSL", "ALFA_SSL"),
                        new BigInteger("1448538842"));

                acceptor.assertNoSslExceptionThrown();
                acceptor.assertLoggedOn(new SessionID(FixVersions.BEGINSTRING_FIX44, "ALFA_SSL", "ZULU_SSL"));
                acceptor.assertNotAuthenticated(new SessionID(FixVersions.BEGINSTRING_FIX44, "ALFA_SSL", "ZULU_SSL"));

                nonSslInitiator.assertNoSslExceptionThrown();
                nonSslInitiator.assertLoggedOn(new SessionID(FixVersions.BEGINSTRING_FIX44, "ZULU_NON_SSL", "ALFA_NON_SSL"));
                nonSslInitiator.assertNotAuthenticated(new SessionID(FixVersions.BEGINSTRING_FIX44, "ZULU_NON_SSL", "ALFA_NON_SSL"));

                acceptor.assertNoSslExceptionThrown();
                acceptor.assertLoggedOn(new SessionID(FixVersions.BEGINSTRING_FIX44, "ALFA_NON_SSL", "ZULU_NON_SSL"));
                acceptor.assertNotAuthenticated(new SessionID(FixVersions.BEGINSTRING_FIX44, "ALFA_NON_SSL", "ZULU_NON_SSL"));

            } finally {
                sslInitiator.stop();
                nonSslInitiator.stop();
            }
        } finally {
            acceptor.stop();
        }
    }

    static abstract class TestConnector {
        private static final Logger LOGGER = LoggerFactory.getLogger(TestConnector.class);
        private static final int TIMEOUT_SECONDS = 5;

        private final SessionConnector connector;
        private final CountDownLatch exceptionThrownLatch;
        private final AtomicReference<Throwable> exception;

        public TestConnector(SessionSettings sessionSettings) throws ConfigError {
            this.connector = prepareConnector(sessionSettings);
            this.exceptionThrownLatch = new CountDownLatch(1);
            this.exception = new AtomicReference<>();
        }

        private SessionConnector prepareConnector(SessionSettings sessionSettings) throws ConfigError {
            SessionConnector sessionConnector = createConnector(sessionSettings);
            sessionConnector.setIoFilterChainBuilder(chain -> chain.addFirst("Exception handler", new IoFilterAdapter() {
                @Override
                public void exceptionCaught(NextFilter nextFilter, IoSession session, Throwable cause) {
                    LOGGER.info("exceptionCaught", cause);
                    exception.set(cause);
                    exceptionThrownLatch.countDown();
                    nextFilter.exceptionCaught(session, cause);
                }
            }));

            return sessionConnector;
        }

        public abstract SessionConnector createConnector(SessionSettings sessionSettings) throws ConfigError;

        private SSLSession findSSLSession(Session session) throws Exception {
            IoSession ioSession = findIoSession(session);

            if (ioSession == null)
                return null;

            IoFilterChain filterChain = ioSession.getFilterChain();
            SslFilter sslFilter = (SslFilter) filterChain.get(SSLSupport.FILTER_NAME);

            if (sslFilter == null)
                return null;

            return (SSLSession) ioSession.getAttribute(SslFilter.SSL_SECURED);
        }

        private Session findSession(SessionID sessionID) {
            for (Session session : connector.getManagedSessions()) {
                if (session.getSessionID().equals(sessionID))
                    return session;
            }

            return null;
        }

        private IoSession findIoSession(Session session) throws Exception {
            IoSessionResponder ioSessionResponder = (IoSessionResponder) session.getResponder();

            if (ioSessionResponder == null)
                return null;

            Field field = IoSessionResponder.class.getDeclaredField("ioSession");
            field.setAccessible(true);

            return (IoSession) field.get(ioSessionResponder);
        }

        public void assertAuthenticated(SessionID sessionID, BigInteger serialNumber) throws Exception {
            Session session = findSession(sessionID);
            SSLSession sslSession = findSSLSession(session);

            Certificate[] peerCertificates = sslSession.getPeerCertificates();

            for (Certificate peerCertificate : peerCertificates) {
                if (!(peerCertificate instanceof X509Certificate)) {
                    continue;
                }

                if (((X509Certificate)peerCertificate).getSerialNumber().compareTo(serialNumber) == 0) {
                    return;
                }
            }

            throw new AssertionError("Certificate with serial number " + serialNumber + " was not authenticated");
        }

        public void assertNotAuthenticated(SessionID sessionID) throws Exception {
            Session session = findSession(sessionID);
            SSLSession sslSession = findSSLSession(session);

            if (sslSession == null)
                return;

            try {
                Certificate[] peerCertificates = sslSession.getPeerCertificates();

                if (peerCertificates != null && peerCertificates.length > 0) {
                    throw new AssertionError("Certificate was authenticated");
                }
            } catch (SSLPeerUnverifiedException e) {
            }
        }

        public void assertLoggedOn(SessionID sessionID) {
            Session session = findSession(sessionID);

            if (!session.isLoggedOn())
                throw new AssertionError("Session is not logged on");
        }

        public void assertNotLoggedOn(SessionID sessionID) {
            Session session = findSession(sessionID);

            if (session.isLoggedOn())
                throw new AssertionError("Session is logged on");
        }

        public void assertSslExceptionThrown() throws Exception {
            assertSslExceptionThrown(null, null);
        }

        public void assertSslExceptionThrown(String expectedErrorMessage, Class<?> expectedErrorType) throws Exception {
            boolean reachedZero = exceptionThrownLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!reachedZero) {
                throw new AssertionError("No SSL exception thrown");
            }

            Throwable throwable = exception.get();

            if (expectedErrorMessage != null) {
                String thrownErrorMessage = throwable.getMessage();
                assertTrue("Thrown error message: " + thrownErrorMessage + " does not contain: " + expectedErrorMessage,
                    thrownErrorMessage != null && thrownErrorMessage.contains(expectedErrorMessage));
            }

            if (expectedErrorType != null) {
                assertSame(expectedErrorType, throwable.getClass());
            }
        }

        public void assertNoSslExceptionThrown() throws Exception {
            boolean reachedZero = exceptionThrownLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (reachedZero) {
                throw new AssertionError("SSL exception thrown");
            }
        }

        public void start() throws RuntimeError, ConfigError {
            connector.start();
        }

        public void stop() {
            connector.stop();
        }
    }

    static class TestAcceptor extends TestConnector {
        private static final Logger LOGGER = LoggerFactory.getLogger(TestAcceptor.class);

        public TestAcceptor(SessionSettings sessionSettings) throws ConfigError {
            super(sessionSettings);
        }

        @Override
        public SessionConnector createConnector(SessionSettings sessionSettings) throws ConfigError {
            LOGGER.info("Creating acceptor: {}", sessionSettings);

            MessageStoreFactory messageStoreFactory = new MemoryStoreFactory();
            MessageFactory messageFactory = new DefaultMessageFactory();

            return new ThreadedSocketAcceptor(new ApplicationAdapter(),
                    messageStoreFactory, sessionSettings, messageFactory);
        }
    }

    static class TestInitiator extends TestConnector {
        private static final Logger LOGGER = LoggerFactory.getLogger(TestInitiator.class);

        public TestInitiator(SessionSettings sessionSettings) throws ConfigError {
            super(sessionSettings);
        }

        @Override
        public SessionConnector createConnector(SessionSettings sessionSettings) throws ConfigError {
            LOGGER.info("Creating initiator: {}", sessionSettings);

            MessageStoreFactory messageStoreFactory = new MemoryStoreFactory();
            MessageFactory messageFactory = new DefaultMessageFactory();

            return new ThreadedSocketInitiator(new ApplicationAdapter(),
                    messageStoreFactory, sessionSettings, messageFactory);
        }
    }

    /**
     * Creates acceptor settings that contains two sessions. One with SSL support, one without.
     */
    private SessionSettings createMixedSessionAcceptorSettings(int sslPort, int nonSslPort, String keyStoreName) {
        HashMap<Object, Object> defaults = new HashMap<>();
        defaults.put(SessionFactory.SETTING_CONNECTION_TYPE, "acceptor");
        defaults.put(Session.SETTING_START_TIME, "00:00:00");
        defaults.put(Session.SETTING_END_TIME, "00:00:00");
        defaults.put(Session.SETTING_HEARTBTINT, "30");

        SessionSettings sessionSettings = new SessionSettings();
        sessionSettings.set(defaults);

        SessionID sslSession = new SessionID(FixVersions.BEGINSTRING_FIX44, "ALFA_SSL", "ZULU_SSL");
        sessionSettings.setString(sslSession, "BeginString", FixVersions.BEGINSTRING_FIX44);
        sessionSettings.setString(sslSession, "DataDictionary", "FIX44.xml");
        sessionSettings.setString(sslSession, "TargetCompID", "ZULU_SSL");
        sessionSettings.setString(sslSession, "SenderCompID", "ALFA_SSL");
        sessionSettings.setString(sslSession, SSLSupport.SETTING_USE_SSL, "Y");
        sessionSettings.setString(sslSession, SSLSupport.SETTING_KEY_STORE_NAME, keyStoreName);
        sessionSettings.setString(sslSession, SSLSupport.SETTING_KEY_STORE_PWD, "password");
        sessionSettings.setString(sslSession, SSLSupport.SETTING_NEED_CLIENT_AUTH, "N");
        sessionSettings.setString(sslSession, "SocketAcceptPort", Integer.toString(sslPort));

        SessionID nonSslSession = new SessionID(FixVersions.BEGINSTRING_FIX44, "ALFA_NON_SSL", "ZULU_NON_SSL");
        sessionSettings.setString(nonSslSession, "BeginString", FixVersions.BEGINSTRING_FIX44);
        sessionSettings.setString(nonSslSession, "DataDictionary", "FIX44.xml");
        sessionSettings.setString(nonSslSession, "TargetCompID", "ZULU_NON_SSL");
        sessionSettings.setString(nonSslSession, "SenderCompID", "ALFA_NON_SSL");
        sessionSettings.setString(nonSslSession, "SocketAcceptPort", Integer.toString(nonSslPort));

        return sessionSettings;
    }

    private SessionSettings createMultiSessionAcceptorSettings(String keyStoreName, boolean needClientAuth,
            String[] trustStoreNames, String cipherSuites, String protocols) {
        HashMap<Object, Object> defaults = new HashMap<>();
        defaults.put(SessionFactory.SETTING_CONNECTION_TYPE, "acceptor");
        defaults.put(SSLSupport.SETTING_USE_SSL, "Y");
        defaults.put(SSLSupport.SETTING_KEY_STORE_NAME, keyStoreName);
        defaults.put(SSLSupport.SETTING_KEY_STORE_PWD, "password");
        defaults.put(SSLSupport.SETTING_NEED_CLIENT_AUTH, needClientAuth ? "Y" : "N");
        defaults.put(Session.SETTING_START_TIME, "00:00:00");
        defaults.put(Session.SETTING_END_TIME, "00:00:00");
        defaults.put(Session.SETTING_HEARTBTINT, "30");

        if (cipherSuites != null) {
            defaults.put(SSLSupport.SETTING_CIPHER_SUITES, cipherSuites);
        }

        if (protocols != null) {
            defaults.put(SSLSupport.SETTING_ENABLED_PROTOCOLS, protocols);
        }

        SessionSettings sessionSettings = new SessionSettings();
        sessionSettings.set(defaults);

        for (int i = 0; i < trustStoreNames.length; i++) {
            SessionID sessionID = new SessionID(FixVersions.BEGINSTRING_FIX44, "ALFA" + i, "ZULU" + i);
            sessionSettings.setString(sessionID, "BeginString", FixVersions.BEGINSTRING_FIX44);
            sessionSettings.setString(sessionID, "DataDictionary", "FIX44.xml");
            sessionSettings.setString(sessionID, "TargetCompID", "ZULU" + i);
            sessionSettings.setString(sessionID, "SenderCompID", "ALFA" + i);
            sessionSettings.setString(sessionID, SSLSupport.SETTING_TRUST_STORE_NAME, trustStoreNames[i]);
            sessionSettings.setString(sessionID, SSLSupport.SETTING_TRUST_STORE_PWD, "password");
            sessionSettings.setString(sessionID, "SocketAcceptPort", "1234" + i);
        }

        return sessionSettings;
    }

    private SessionSettings createAcceptorSettings(String keyStoreName, boolean needClientAuth, String trustStoreName,
            String cipherSuites, String protocols, String keyStoreType, String trustStoreType, int port) {
        HashMap<Object, Object> defaults = new HashMap<>();
        defaults.put(SessionFactory.SETTING_CONNECTION_TYPE, "acceptor");
        defaults.put(SSLSupport.SETTING_USE_SSL, "Y");
        defaults.put(SSLSupport.SETTING_KEY_STORE_NAME, keyStoreName);
        defaults.put(SSLSupport.SETTING_KEY_STORE_PWD, "password");

        if (keyStoreType != null) {
            defaults.put(SSLSupport.SETTING_KEY_STORE_TYPE, keyStoreType);
        }

        if (trustStoreName != null) {
            defaults.put(SSLSupport.SETTING_TRUST_STORE_NAME, trustStoreName);
            defaults.put(SSLSupport.SETTING_TRUST_STORE_PWD, "password");

            if (trustStoreType != null) {
                defaults.put(SSLSupport.SETTING_TRUST_STORE_TYPE, trustStoreType);
            }
        }

        defaults.put(SSLSupport.SETTING_NEED_CLIENT_AUTH, needClientAuth ? "Y" : "N");
        defaults.put(Acceptor.SETTING_SOCKET_ACCEPT_PORT, Integer.toString(port));
        defaults.put(Session.SETTING_START_TIME, "00:00:00");
        defaults.put(Session.SETTING_END_TIME, "00:00:00");
        defaults.put(Session.SETTING_HEARTBTINT, "30");

        if (cipherSuites != null) {
            defaults.put(SSLSupport.SETTING_CIPHER_SUITES, cipherSuites);
        }

        if (protocols != null) {
            defaults.put(SSLSupport.SETTING_ENABLED_PROTOCOLS, protocols);
        }

        SessionID sessionID = new SessionID(FixVersions.BEGINSTRING_FIX44, "ALFA", "ZULU");

        SessionSettings sessionSettings = new SessionSettings();
        sessionSettings.set(defaults);
        sessionSettings.setString(sessionID, "BeginString", FixVersions.BEGINSTRING_FIX44);
        sessionSettings.setString(sessionID, "DataDictionary", "FIX44.xml");
        sessionSettings.setString(sessionID, "SenderCompID", "ALFA");
        sessionSettings.setString(sessionID, "TargetCompID", "ZULU");

        return sessionSettings;
    }

    private SessionSettings createInitiatorSettings(String keyStoreName, String trustStoreName, String cipherSuites,
                                                    String protocols, String senderId, String targetId, String port, String keyStoreType,
                                                    String trustStoreType) {
        return createInitiatorSettings(keyStoreName, trustStoreName, cipherSuites, protocols, senderId, targetId, port,keyStoreType, trustStoreType, null);
    }

    private SessionSettings createInitiatorSettings(String keyStoreName, String trustStoreName, String cipherSuites,
                                                    String protocols, String senderId, String targetId, String port, String keyStoreType,
                                                    String trustStoreType, String endpointIdentificationAlgorithm) {
        HashMap<Object, Object> defaults = new HashMap<>();
        defaults.put(SessionFactory.SETTING_CONNECTION_TYPE, "initiator");
        defaults.put(Initiator.SETTING_SOCKET_CONNECT_PROTOCOL, ProtocolFactory.getTypeString(ProtocolFactory.SOCKET));
        defaults.put(SSLSupport.SETTING_USE_SSL, "Y");
        defaults.put(SSLSupport.SETTING_KEY_STORE_NAME, keyStoreName);
        defaults.put(SSLSupport.SETTING_KEY_STORE_PWD, "password");

        if (keyStoreType != null) {
            defaults.put(SSLSupport.SETTING_KEY_STORE_TYPE, keyStoreType);
        }

        if (trustStoreName != null) {
            defaults.put(SSLSupport.SETTING_TRUST_STORE_NAME, trustStoreName);
            defaults.put(SSLSupport.SETTING_TRUST_STORE_PWD, "password");

            if (trustStoreType != null) {
                defaults.put(SSLSupport.SETTING_TRUST_STORE_TYPE, trustStoreType);
            }
        }

        defaults.put(Initiator.SETTING_SOCKET_CONNECT_HOST, "localhost");
        defaults.put(Initiator.SETTING_SOCKET_CONNECT_PORT, port);
        defaults.put(Initiator.SETTING_RECONNECT_INTERVAL, "2");
        defaults.put(Session.SETTING_START_TIME, "00:00:00");
        defaults.put(Session.SETTING_END_TIME, "00:00:00");
        defaults.put(Session.SETTING_HEARTBTINT, "30");

        if (cipherSuites != null) {
            defaults.put(SSLSupport.SETTING_CIPHER_SUITES, cipherSuites);
        }

        if (protocols != null) {
            defaults.put(SSLSupport.SETTING_ENABLED_PROTOCOLS, protocols);
        }

        if (endpointIdentificationAlgorithm != null) {
            defaults.put(SSLSupport.SETTING_ENDPOINT_IDENTIFICATION_ALGORITHM, endpointIdentificationAlgorithm);
        }

        SessionID sessionID = new SessionID(FixVersions.BEGINSTRING_FIX44, senderId, targetId);

        SessionSettings sessionSettings = new SessionSettings();
        sessionSettings.set(defaults);
        sessionSettings.setString(sessionID, "BeginString", FixVersions.BEGINSTRING_FIX44);
        sessionSettings.setString(sessionID, "DataDictionary", "FIX44.xml");
        sessionSettings.setString(sessionID, "SenderCompID", senderId);
        sessionSettings.setString(sessionID, "TargetCompID", targetId);

        return sessionSettings;
    }

    private SessionSettings createInitiatorSettings(String senderId, String targetId, int port) {
        HashMap<Object, Object> defaults = new HashMap<>();
        defaults.put(SessionFactory.SETTING_CONNECTION_TYPE, "initiator");
        defaults.put(Initiator.SETTING_SOCKET_CONNECT_PROTOCOL, ProtocolFactory.getTypeString(ProtocolFactory.SOCKET));
        defaults.put(Initiator.SETTING_SOCKET_CONNECT_HOST, "localhost");
        defaults.put(Initiator.SETTING_SOCKET_CONNECT_PORT, Integer.toString(port));
        defaults.put(Initiator.SETTING_RECONNECT_INTERVAL, "2");
        defaults.put(Session.SETTING_START_TIME, "00:00:00");
        defaults.put(Session.SETTING_END_TIME, "00:00:00");
        defaults.put(Session.SETTING_HEARTBTINT, "30");

        SessionID sessionID = new SessionID(FixVersions.BEGINSTRING_FIX44, senderId, targetId);

        SessionSettings sessionSettings = new SessionSettings();
        sessionSettings.set(defaults);
        sessionSettings.setString(sessionID, "BeginString", FixVersions.BEGINSTRING_FIX44);
        sessionSettings.setString(sessionID, "DataDictionary", "FIX44.xml");
        sessionSettings.setString(sessionID, "SenderCompID", senderId);
        sessionSettings.setString(sessionID, "TargetCompID", targetId);

        return sessionSettings;
    }
}
