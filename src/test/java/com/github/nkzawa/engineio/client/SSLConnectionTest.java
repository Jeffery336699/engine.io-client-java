package com.github.nkzawa.engineio.client;

import com.github.nkzawa.emitter.Emitter;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.concurrent.CountDownLatch;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

@RunWith(JUnit4.class)
public class SSLConnectionTest extends Connection {

    static {
        // for test on localhost
        javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier(
                new javax.net.ssl.HostnameVerifier(){
                    public boolean verify(String hostname, javax.net.ssl.SSLSession sslSession) {
                        return hostname.equals("localhost");
                    }
                });
    }

    private Socket socket;

    @Override
    Socket.Options createOptions() {
        Socket.Options opts = super.createOptions();
        opts.secure = true;
        return opts;
    }

    @Override
    String[] createEnv() {
        return new String[] {"DEBUG=engine*", "PORT=" + PORT, "SSL=1"};
    }

    SSLContext createSSLContext() throws GeneralSecurityException, IOException {
        KeyStore ks = KeyStore.getInstance("JKS");
        File file = new File("src/test/resources/keystore.jks");
        ks.load(new FileInputStream(file), "password".toCharArray());

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ks, "password".toCharArray());

        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(ks);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        return sslContext;
    }

    @Test(timeout = TIMEOUT)
    public void connect() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);

        Socket.Options opts = createOptions();
        opts.sslContext = createSSLContext();
        socket = new Socket(opts);
        socket.on(Socket.EVENT_OPEN, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                socket.on(Socket.EVENT_MESSAGE, new Emitter.Listener() {
                    @Override
                    public void call(Object... args) {
                        assertThat((String)args[0], is("hi"));
                        socket.close();
                        latch.countDown();
                    }
                });
            }
        }).on("error", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                ((Exception)args[0]).printStackTrace();
            }
        });
        socket.open();
        latch.await();
    }

    @Test(timeout = TIMEOUT)
    public void upgrade() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);

        Socket.Options opts = createOptions();
        opts.sslContext = createSSLContext();
        socket = new Socket(opts);
        socket.on(Socket.EVENT_OPEN, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                socket.on(Socket.EVENT_UPGRADE, new Emitter.Listener() {
                    @Override
                    public void call(Object... args) {
                        socket.send("hi");
                        socket.on(Socket.EVENT_MESSAGE, new Emitter.Listener() {
                            @Override
                            public void call(Object... args) {
                                assertThat((String) args[0], is("hi"));
                                socket.close();
                                latch.countDown();
                            }
                        });
                    }
                });
            }
        });
        socket.open();
        latch.await();
    }
}
