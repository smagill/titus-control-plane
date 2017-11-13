/*
 * Copyright 2017 Netflix, Inc.
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

package io.netflix.titus.ext.aws;

import java.util.Collection;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import javax.inject.Inject;

import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.handlers.AsyncHandler;
import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancingAsync;
import com.amazonaws.services.elasticloadbalancingv2.model.DeregisterTargetsRequest;
import com.amazonaws.services.elasticloadbalancingv2.model.DeregisterTargetsResult;
import com.amazonaws.services.elasticloadbalancingv2.model.RegisterTargetsRequest;
import com.amazonaws.services.elasticloadbalancingv2.model.RegisterTargetsResult;
import com.amazonaws.services.elasticloadbalancingv2.model.TargetDescription;
import io.netflix.titus.api.connector.cloud.LoadBalancerClient;
import io.netflix.titus.common.util.CollectionsExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Completable;
import rx.CompletableSubscriber;
import rx.Scheduler;
import rx.functions.Action1;
import rx.functions.Action2;
import rx.schedulers.Schedulers;
import rx.subscriptions.Subscriptions;

public class AwsLoadBalancerClient implements LoadBalancerClient {
    private static final Logger logger = LoggerFactory.getLogger(AwsLoadBalancerClient.class);

    private final AmazonElasticLoadBalancingAsync client;
    private final Scheduler scheduler;

    @Inject
    public AwsLoadBalancerClient(AmazonElasticLoadBalancingAsync client) {
        this(client, Schedulers.computation());
    }

    public AwsLoadBalancerClient(AmazonElasticLoadBalancingAsync client, Scheduler scheduler) {
        this.client = client;
        this.scheduler = scheduler;
    }

    @Override
    public Completable registerAll(String loadBalancerId, Collection<String> ipAddresses) {
        if (CollectionsExt.isNullOrEmpty(ipAddresses)) {
            return Completable.complete();
        }

        // TODO: retry logic
        // TODO: handle partial failures in the batch

        final RegisterTargetsRequest request = new RegisterTargetsRequest()
                .withTargetGroupArn(loadBalancerId)
                .withTargets(ipAddresses.stream().map(
                        ipAddress -> new TargetDescription().withId(ipAddress)
                ).collect(Collectors.toSet()));

        // force observeOn(scheduler) since the callback will be called from the AWS SDK threadpool
        return Completable.create(subscriber -> {
            final Future<RegisterTargetsResult> future = client.registerTargetsAsync(request, asyncHandler(subscriber,
                    (req, resp) -> logger.debug("Registered targets {}", resp),
                    (t) -> logger.error("Error registering targets on " + loadBalancerId, t)
            ));
            subscriber.onSubscribe(Subscriptions.create(() -> future.cancel(true)));
        }).observeOn(scheduler);
    }

    @Override
    public Completable deregisterAll(String loadBalancerId, Collection<String> ipAddresses) {
        if (CollectionsExt.isNullOrEmpty(ipAddresses)) {
            return Completable.complete();
        }

        // TODO: retry logic
        // TODO: handle partial failures in the batch

        final DeregisterTargetsRequest request = new DeregisterTargetsRequest()
                .withTargetGroupArn(loadBalancerId)
                .withTargets(ipAddresses.stream().map(
                        ipAddress -> new TargetDescription().withId(ipAddress)
                ).collect(Collectors.toSet()));

        // force observeOn(scheduler) since the callback will be called from the AWS SDK threadpool
        return Completable.create(subscriber -> {
            final Future<DeregisterTargetsResult> future = client.deregisterTargetsAsync(request, asyncHandler(subscriber,
                    (req, resp) -> logger.debug("Deregistered targets {}", resp),
                    (t) -> logger.error("Error deregistering targets on " + loadBalancerId, t)
            ));
            subscriber.onSubscribe(Subscriptions.create(() -> future.cancel(true)));
        }).observeOn(scheduler);
    }

    private <REQ extends AmazonWebServiceRequest, RES> AsyncHandler<REQ, RES> asyncHandler(
            CompletableSubscriber subscriber, Action2<REQ, RES> onSuccessAction, Action1<Exception> onErrorAction) {
        return new AsyncHandler<REQ, RES>() {
            @Override
            public void onError(Exception exception) {
                onErrorAction.call(exception);
                subscriber.onError(exception);
            }

            @Override
            public void onSuccess(REQ request, RES result) {
                onSuccessAction.call(request, result);
                subscriber.onCompleted();
            }
        };
    }
}
