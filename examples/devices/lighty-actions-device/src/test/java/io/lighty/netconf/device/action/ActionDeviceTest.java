/*
 * Copyright (c) 2020 PANTHEON.tech s.r.o. All Rights Reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v10.html
 */
package io.lighty.netconf.device.action;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import io.lighty.netconf.device.utils.TimeoutUtil;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opendaylight.netconf.api.messages.NetconfMessage;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.client.NetconfClientFactoryImpl;
import org.opendaylight.netconf.client.NetconfClientSession;
import org.opendaylight.netconf.client.NetconfClientSessionListener;
import org.opendaylight.netconf.client.SimpleNetconfClientSessionListener;
import org.opendaylight.netconf.client.conf.NetconfClientConfiguration;
import org.opendaylight.netconf.client.conf.NetconfClientConfigurationBuilder;
import org.opendaylight.netconf.common.impl.DefaultNetconfTimer;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010.password.grouping.password.type.CleartextPasswordBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.client.rev240814.netconf.client.initiate.stack.grouping.transport.ssh.ssh.SshClientParametersBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.client.rev240814.netconf.client.initiate.stack.grouping.transport.ssh.ssh.TcpClientParametersBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev241010.ssh.client.grouping.ClientIdentityBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev241010.ssh.client.grouping.client.identity.PasswordBuilder;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class ActionDeviceTest {

    private static final long REQUEST_TIMEOUT_MILLIS = 5_000;
    private static final String USER = "admin";
    private static final String PASS = "admin";
    private static final int DEVICE_SIMULATOR_PORT = 9090;
    private static final String RESET_ACTION_EXPECTED_VALUE = "2020-09-03T16:20:00Z";
    private static final String START_ACTION_EXPECTED_VALUE = "2020-09-03T16:30:00Z";
    public static final String START_ACTION_REQUEST_XML = "start_action_request.xml";
    public static final String RESET_ACTION_REQUEST_XML = "reset_action_request.xml";
    public static final String START_TAG = "start-finished-at";
    public static final String RESET_TAG = "reset-finished-at";

    private static Main deviceSimulator;
    private static NetconfClientFactoryImpl dispatcher;

    @BeforeAll
    public static void setUpClass() {
        deviceSimulator = new Main();
        deviceSimulator.start(new String[]{DEVICE_SIMULATOR_PORT + ""}, false);
        dispatcher = new NetconfClientFactoryImpl(new DefaultNetconfTimer());
    }

    @AfterAll
    public static void cleanUpClass() throws InterruptedException {
        deviceSimulator.shutdown();
    }

    private static NetconfClientConfiguration createSHHConfig(final NetconfClientSessionListener sessionListener) {
        return NetconfClientConfigurationBuilder.create()
            .withTcpParameters(new TcpClientParametersBuilder()
                .setRemoteAddress(new Host(new IpAddress(Ipv4Address.getDefaultInstance("127.0.0.1"))))
                .setRemotePort(new PortNumber(Uint16.valueOf(DEVICE_SIMULATOR_PORT))).build())
                .withSessionListener(sessionListener)
                .withConnectionTimeoutMillis(NetconfClientConfigurationBuilder.DEFAULT_CONNECTION_TIMEOUT_MILLIS)
                .withProtocol(NetconfClientConfiguration.NetconfClientProtocol.SSH)
            .withSshParameters(new SshClientParametersBuilder().setClientIdentity(new ClientIdentityBuilder()
                    .setUsername(USER)
                    .setPassword(new PasswordBuilder()
                        .setPasswordType(new CleartextPasswordBuilder()
                            .setCleartextPassword(PASS)
                            .build())
                        .build())
                    .build())
                .build())
                .build();
    }

    @Test
    public void getSchemaTest() throws IOException, URISyntaxException, SAXException, InterruptedException,
            ExecutionException, TimeoutException, UnsupportedConfigurationException {
        final SimpleNetconfClientSessionListener sessionListener = new SimpleNetconfClientSessionListener();

        try (NetconfClientSession session =
                dispatcher.createClient(createSHHConfig(sessionListener))
                        .get(TimeoutUtil.TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
            final NetconfMessage schemaResponse = sentRequesttoDevice(
                    sessionListener, "get_schemas_request.xml");

            final NodeList schema = schemaResponse.getDocument().getDocumentElement().getElementsByTagName("schema");
            assertTrue(schema.getLength() > 0);

            boolean exampleDataCenterSchemaContained = false;
            for (int i = 0; i < schema.getLength(); i++) {
                if (schema.item(i).getNodeType() == Node.ELEMENT_NODE) {
                    final Element item = (Element) schema.item(i);
                    final String schemaName = item.getElementsByTagName("identifier").item(0).getTextContent();
                    final String schemaNameSpace = item.getElementsByTagName("namespace").item(0).getTextContent();
                    if ("example-data-center".equals(schemaName)
                            && "urn:example:data-center".equals(schemaNameSpace)) {
                        exampleDataCenterSchemaContained = true;
                        break;
                    }
                }
            }
            assertTrue(exampleDataCenterSchemaContained);
        }
    }

    @Test
    public void actionsTest() throws IOException, URISyntaxException, SAXException, InterruptedException,
            ExecutionException, TimeoutException, UnsupportedConfigurationException {
        final SimpleNetconfClientSessionListener sessionListener = new SimpleNetconfClientSessionListener();
        try (NetconfClientSession session =
                dispatcher.createClient(createSHHConfig(sessionListener))
                        .get(TimeoutUtil.TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
            final NetconfMessage startActionResponse = sentRequesttoDevice(sessionListener, START_ACTION_REQUEST_XML);
            final String startResultTag = startActionResponse.getDocument().getDocumentElement().getElementsByTagName(
                    START_TAG).item(0).getTextContent();
            assertEquals(startResultTag, START_ACTION_EXPECTED_VALUE);

            final NetconfMessage resetActionResponse = sentRequesttoDevice(sessionListener, RESET_ACTION_REQUEST_XML);
            final String resetResultTag = resetActionResponse.getDocument().getDocumentElement().getElementsByTagName(
                    RESET_TAG).item(0).getTextContent();
            assertEquals(resetResultTag, RESET_ACTION_EXPECTED_VALUE);
        }
    }

    private NetconfMessage sentRequesttoDevice(SimpleNetconfClientSessionListener sessionListener,
                                               String requestFileName)
            throws SAXException, IOException, URISyntaxException,
            InterruptedException, ExecutionException, TimeoutException {
        final NetconfMessage actionSchemaMessage =
                new NetconfMessage(XmlUtil.readXmlToDocument(xmlFileToInputStream(requestFileName)));

        return sessionListener
                .sendRequest(actionSchemaMessage)
                .get(REQUEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    }

    public static InputStream xmlFileToInputStream(final String fileName) throws URISyntaxException, IOException {
        final URL getRequest = ActionDeviceTest.class.getClassLoader().getResource(fileName);
        return new FileInputStream(new File(Objects.requireNonNull(getRequest).toURI()));
    }

}
