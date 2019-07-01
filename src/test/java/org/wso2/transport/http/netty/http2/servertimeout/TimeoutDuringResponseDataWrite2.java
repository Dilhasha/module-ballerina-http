package org.wso2.transport.http.netty.http2.servertimeout;

import io.netty.handler.codec.http.HttpMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.transport.http.netty.contract.Constants;
import org.wso2.transport.http.netty.contract.HttpClientConnector;
import org.wso2.transport.http.netty.contract.HttpResponseFuture;
import org.wso2.transport.http.netty.contract.HttpWsConnectorFactory;
import org.wso2.transport.http.netty.contract.OperationStatus;
import org.wso2.transport.http.netty.contract.ServerConnector;
import org.wso2.transport.http.netty.contract.ServerConnectorFuture;
import org.wso2.transport.http.netty.contract.config.ListenerConfiguration;
import org.wso2.transport.http.netty.contract.config.SenderConfiguration;
import org.wso2.transport.http.netty.contract.config.TransportsConfiguration;
import org.wso2.transport.http.netty.contractimpl.DefaultHttpWsConnectorFactory;
import org.wso2.transport.http.netty.http2.listeners.Http2ServerWaitDuringDataWrite;
import org.wso2.transport.http.netty.message.HttpCarbonMessage;
import org.wso2.transport.http.netty.message.HttpConnectorUtil;
import org.wso2.transport.http.netty.util.DefaultHttpConnectorListener;
import org.wso2.transport.http.netty.util.TestUtil;
import org.wso2.transport.http.netty.util.client.http2.MessageGenerator;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

/**
 * Tests server timeout during response data write with a prior knowledge off HTTP/2 client.
 */
public class TimeoutDuringResponseDataWrite2 {
    private static final Logger LOG = LoggerFactory.getLogger(TimeoutDuringResponseDataWrite.class);

    private HttpClientConnector h2ClientWithoutPriorKnowledge;
    private ServerConnector serverConnector;
    private HttpWsConnectorFactory connectorFactory;
    private static final String PRIOR_OFF_EXPECTED_ERROR = "HTTP/2 stream 1 reset by the remote peer";


    @BeforeClass
    public void setup() throws InterruptedException {
        connectorFactory = new DefaultHttpWsConnectorFactory();
        ListenerConfiguration listenerConfiguration = new ListenerConfiguration();
        listenerConfiguration.setPort(TestUtil.HTTP_SERVER_PORT);
        listenerConfiguration.setScheme(Constants.HTTP_SCHEME);
        listenerConfiguration.setVersion(Constants.HTTP_2_0);
        //Give enough time for the server to respond before the timeout event occurs
        listenerConfiguration.setSocketIdleTimeout(5000);
        serverConnector = connectorFactory.createServerConnector(TestUtil.getDefaultServerBootstrapConfig(),
                                                                 listenerConfiguration);
        ServerConnectorFuture future = serverConnector.start();
        future.setHttpConnectorListener(new Http2ServerWaitDuringDataWrite(10000));
        future.sync();

        TransportsConfiguration transportsConfiguration = new TransportsConfiguration();
        SenderConfiguration senderConfiguration1 = getSenderConfiguration();
        senderConfiguration1.setForceHttp2(true);

        SenderConfiguration senderConfiguration2 = getSenderConfiguration();
        senderConfiguration2.setForceHttp2(false);
        h2ClientWithoutPriorKnowledge = connectorFactory.createHttpClientConnector(
                HttpConnectorUtil.getTransportProperties(transportsConfiguration), senderConfiguration2);
    }

    private SenderConfiguration getSenderConfiguration() {
        SenderConfiguration senderConfiguration = new SenderConfiguration();
        senderConfiguration.setScheme(Constants.HTTP_SCHEME);
        senderConfiguration.setHttpVersion(Constants.HTTP_2_0);
        //Set this to a value larger than server socket timeout value, to make sure that the server times out first
        senderConfiguration.setSocketIdleTimeout(500000);
        return senderConfiguration;
    }

    @Test
    public void testServerTimeout() {
        HttpCarbonMessage httpMsg1 = MessageGenerator.generateRequest(HttpMethod.POST, "Test Http2 Message");
        verifyResult(httpMsg1, h2ClientWithoutPriorKnowledge);
    }

    private void verifyResult(HttpCarbonMessage httpCarbonMessage, HttpClientConnector http2ClientConnector) {
        try {
            CountDownLatch latch = new CountDownLatch(1);
            DefaultHttpConnectorListener msgListener = new DefaultHttpConnectorListener(latch);
            HttpResponseFuture responseFuture = http2ClientConnector.send(httpCarbonMessage);
            responseFuture.setHttpConnectorListener(msgListener);
            latch.await(TestUtil.HTTP2_RESPONSE_TIME_OUT, TimeUnit.SECONDS);
            responseFuture.sync();
            Thread.sleep(10000); //Wait for the stream reset error.
            OperationStatus status = responseFuture.getStatus();
            if (status.getCause() != null) {
                String errorMsg = status.getCause().getMessage();
                assertEquals(errorMsg, PRIOR_OFF_EXPECTED_ERROR);
            } else {
                fail("TimeoutDuringResponseDataWrite2 is heavily dependent on timing of events and needs an alternate" +
                             " solution");
            }
        } catch (InterruptedException e) {
            LOG.error("Interrupted exception occurred");
        }
    }

    @AfterClass
    public void cleanUp() {
        h2ClientWithoutPriorKnowledge.close();
        serverConnector.stop();
        try {
            connectorFactory.shutdown();
        } catch (InterruptedException e) {
            LOG.warn("Interrupted while waiting for HttpWsFactory to close");
        }
    }
}
