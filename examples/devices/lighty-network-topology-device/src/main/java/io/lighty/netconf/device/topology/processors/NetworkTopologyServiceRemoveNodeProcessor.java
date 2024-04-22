/*
 * Copyright (c) 2020 PANTHEON.tech s.r.o. All Rights Reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v10.html
 */
package io.lighty.netconf.device.topology.processors;

import com.google.common.util.concurrent.ListenableFuture;
import io.lighty.netconf.device.topology.rpcs.NetworkTopologyServiceImpl;
import org.opendaylight.yang.gen.v1.urn.tech.pantheon.netconfdevice.network.topology.rpcs.rev230927.RemoveNodeFromTopology;
import org.opendaylight.yang.gen.v1.urn.tech.pantheon.netconfdevice.network.topology.rpcs.rev230927.RemoveNodeFromTopologyInput;
import org.opendaylight.yang.gen.v1.urn.tech.pantheon.netconfdevice.network.topology.rpcs.rev230927.RemoveNodeFromTopologyOutput;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("checkstyle:MemberName")
public class NetworkTopologyServiceRemoveNodeProcessor extends
        NetworkTopologyServiceAbstractProcessor<RemoveNodeFromTopologyInput, RemoveNodeFromTopologyOutput>
        implements RemoveNodeFromTopology {
    private static final Logger LOG = LoggerFactory.getLogger(NetworkTopologyServiceRemoveNodeProcessor.class);

    private final NetworkTopologyServiceImpl networkTopologyRpcsService;
    private final QName qName = QName.create("urn:tech.pantheon.netconfdevice.network.topology.rpcs",
        "remove-node-from-topology");

    public NetworkTopologyServiceRemoveNodeProcessor(final NetworkTopologyServiceImpl networkTopologyRpcsService) {
        this.networkTopologyRpcsService = networkTopologyRpcsService;
    }

    @Override
    public QName getIdentifier() {
        return this.qName;
    }

    @Override
    public ListenableFuture<RpcResult<RemoveNodeFromTopologyOutput>> invoke(final RemoveNodeFromTopologyInput input) {
        return networkTopologyRpcsService.removeNodeFromTopology(input);
    }
}
