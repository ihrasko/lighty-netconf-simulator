/*
 * Copyright (c) 2020 PANTHEON.tech s.r.o. All Rights Reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v10.html
 */
package io.lighty.netconf.device.action.processors;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import io.lighty.codecs.util.XmlNodeConverter;
import io.lighty.codecs.util.exception.DeserializationException;
import io.lighty.netconf.device.response.Response;
import io.lighty.netconf.device.response.ResponseData;
import io.lighty.netconf.device.utils.RPCUtil;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import javax.xml.transform.TransformerException;
import org.opendaylight.mdsal.binding.dom.adapter.ConstantAdapterContext;
import org.opendaylight.mdsal.binding.dom.adapter.CurrentAdapterSerializer;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.xml.XmlElement;
import org.opendaylight.yang.gen.v1.urn.example.data.center.rev180807.Server;
import org.opendaylight.yang.gen.v1.urn.example.data.center.rev180807.ServerKey;
import org.opendaylight.yang.gen.v1.urn.example.data.center.rev180807.server.Reset;
import org.opendaylight.yang.gen.v1.urn.example.data.center.rev180807.server.ResetInput;
import org.opendaylight.yang.gen.v1.urn.example.data.center.rev180807.server.ResetOutput;
import org.opendaylight.yangtools.binding.data.codec.spi.BindingDOMCodecServices;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.KeyedInstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.ActionDefinition;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public final class ResetActionProcessor extends ActionServiceDeviceProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(ResetActionProcessor.class);

    private final Reset resetAction;
    private final Absolute path;
    private final ActionDefinition definition;
    private final CurrentAdapterSerializer adapterSerializer;

    public ResetActionProcessor(final Reset resetAction, final Absolute path, final ActionDefinition definition,
            final BindingDOMCodecServices codecServices) {
        this.resetAction = resetAction;
        this.path = path;
        this.definition = definition;
        this.adapterSerializer = new ConstantAdapterContext(codecServices).currentSerializer();
    }

    @SuppressWarnings({"rawtypes", "unchecked", "checkstyle:IllegalCatch"})
    @Override
    protected CompletableFuture<Response> execute(final Element requestXmlElement) {
        final XmlNodeConverter xmlNodeConverter = getNetconfDeviceServices().getXmlNodeConverter();
        try {
            final XmlElement xmlElement = XmlElement.fromDomElement(requestXmlElement);
            final Element actionElement = findInputElement(xmlElement, this.definition.getQName());
            final Reader readerFromElement = RPCUtil.createReaderFromElement(actionElement);
            final Absolute actionInput = getActionInput(this.path, this.definition);
            final ContainerNode deserializedNode = (ContainerNode) xmlNodeConverter
                    .deserialize(actionInput, readerFromElement);
            final ResetInput input = this.adapterSerializer.fromNormalizedNodeActionInput(Reset.class,
                    deserializedNode);
            final String key = findNameElement(xmlElement);
            Preconditions.checkNotNull(key);
            final Class listItem = Server.class;
            final ServerKey listKey = new ServerKey(key);
            final InstanceIdentifier instanceIdentifier = InstanceIdentifier.builder(listItem, listKey).build();
            final KeyedInstanceIdentifier<Server, ServerKey> keydIID
                    = (KeyedInstanceIdentifier<Server, ServerKey>) instanceIdentifier;

            final ListenableFuture<RpcResult<ResetOutput>> outputFuture =
                this.resetAction.invoke(keydIID.toIdentifier(), input);
            final CompletableFuture<Response> completableFuture = new CompletableFuture<>();
            Futures.addCallback(outputFuture, new FutureCallback<RpcResult<ResetOutput>>() {

                @Override
                public void onSuccess(final RpcResult<ResetOutput> result) {
                    final NormalizedNode domOutput = ResetActionProcessor.this.adapterSerializer
                            .toNormalizedNodeActionOutput(Reset.class, result.getResult());
                    final List<NormalizedNode> list = new ArrayList<>();
                    list.add(domOutput);
                    completableFuture.complete(new ResponseData(list));
                }

                @Override
                public void onFailure(final Throwable throwable) {
                }
            }, Executors.newSingleThreadExecutor());
            return completableFuture;
        } catch (final TransformerException | DocumentedException | DeserializationException e) {
            throw new RuntimeException(e);
        }
    }

    private String findNameElement(final XmlElement xmlElement) throws DocumentedException {
        final NodeList elementsByTagName = xmlElement.getDomElement().getOwnerDocument().getElementsByTagName("name");
        final Node item = elementsByTagName.item(0);
        return item.getFirstChild().getNodeValue();
    }

    @Override
    protected ActionDefinition getActionDefinition() {
        return this.definition;
    }

    @Override
    protected Absolute getActionPath() {
        return this.path;
    }
}
