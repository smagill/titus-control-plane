/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.titus.runtime.clustermembership.endpoint.grpc;

import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

import com.netflix.titus.common.framework.fit.adapter.GrpcFitInterceptor;
import com.netflix.titus.common.runtime.TitusRuntime;
import com.netflix.titus.grpc.protogen.ClusterMembershipServiceGrpc;
import com.netflix.titus.runtime.connector.common.reactor.GrpcToReactorServerFactory;
import com.netflix.titus.runtime.endpoint.common.grpc.CommonAbstractTitusGrpcServer;
import com.netflix.titus.runtime.endpoint.common.grpc.CommonGrpcEndpointConfiguration;
import io.grpc.ServerInterceptor;
import io.grpc.ServiceDescriptor;

@Singleton
public class ClusterMembershipGrpcServer extends CommonAbstractTitusGrpcServer {

    private final TitusRuntime titusRuntime;

    @Inject
    public ClusterMembershipGrpcServer(CommonGrpcEndpointConfiguration configuration,
                                       ReactorClusterMembershipGrpcService reactorClusterMembershipGrpcService,
                                       GrpcToReactorServerFactory reactorServerFactory,
                                       TitusRuntime titusRuntime) {
        super(configuration, reactorServerFactory.apply(ClusterMembershipServiceGrpc.getServiceDescriptor(), reactorClusterMembershipGrpcService));
        this.titusRuntime = titusRuntime;
    }

    @Override
    protected List<ServerInterceptor> createInterceptors(ServiceDescriptor serviceDescriptor) {
        return GrpcFitInterceptor.appendIfFitEnabled(super.createInterceptors(serviceDescriptor), titusRuntime);
    }
}