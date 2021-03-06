/*
 * Copyright 2017 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apiman.plugins.auth3scale.authrep.appid;

import static io.apiman.plugins.auth3scale.Auth3ScaleConstants.REFERRER;
import static io.apiman.plugins.auth3scale.Auth3ScaleConstants.USER_ID;

import io.apiman.gateway.engine.async.AsyncResultImpl;
import io.apiman.gateway.engine.async.IAsyncHandler;
import io.apiman.gateway.engine.async.IAsyncResultHandler;
import io.apiman.gateway.engine.beans.ApiRequest;
import io.apiman.gateway.engine.beans.PolicyFailure;
import io.apiman.gateway.engine.policy.IPolicyContext;
import io.apiman.gateway.engine.threescale.beans.Auth3ScaleBean;
import io.apiman.gateway.engine.threescale.beans.BackendConfiguration;
import io.apiman.plugins.auth3scale.authrep.AuthPrincipal;
import io.apiman.plugins.auth3scale.authrep.strategies.AuthStrategy;
import io.apiman.plugins.auth3scale.util.Auth3ScaleUtils;

/**
 * @author Marc Savy {@literal <msavy@redhat.com>}
 */
@SuppressWarnings("nls")
public class AppIdAuth implements AuthPrincipal {

    private static final AsyncResultImpl<Void> FAIL_NO_ROUTE = AsyncResultImpl.create(new RuntimeException("No valid route"));
    private static final AsyncResultImpl<Void> FAIL_PROVIDE_APP_ID = AsyncResultImpl.create(new RuntimeException("No user app id provided")); // TODO mirror 3scale errors
    private static final AsyncResultImpl<Void> FAIL_PROVIDE_APP_KEY = AsyncResultImpl.create(new RuntimeException("No user app key provided")); // TODO mirror 3scale errors

    private BackendConfiguration config;
    private ApiRequest request;
    private AuthStrategy auth;
    private IAsyncHandler<PolicyFailure> policyFailureHandler;

    public AppIdAuth(Auth3ScaleBean auth3ScaleBean,
            ApiRequest request,
            IPolicyContext context,
            AuthStrategy auth) {
        this.config = auth3ScaleBean.getThreescaleConfig().getProxyConfig().getBackendConfig();
        this.request = request;
        this.auth = auth;
    }

    @Override
    public AuthPrincipal auth(IAsyncResultHandler<Void> resultHandler) {
        String appId = AppIdUtils.getAppId(config, request);
        String appKey = AppIdUtils.getAppKey(config, request);

        if (appId == null) {
            resultHandler.handle(FAIL_PROVIDE_APP_ID);
            return this;
        }

        if (appKey == null) {
            resultHandler.handle(FAIL_PROVIDE_APP_KEY);
            return this;
        }

        if (!Auth3ScaleUtils.hasRoutes(config, request)) {
            resultHandler.handle(FAIL_NO_ROUTE);
            return this;
        }

        AppIdReportData report = new AppIdReportData()
                .setAppId(appId)
                .setAppKey(appKey)
                .setServiceToken(config.getBackendAuthenticationValue())
                .setServiceId(Long.toString(config.getProxy().getServiceId()))
                .setUsage(Auth3ScaleUtils.buildRepMetrics(config, request))
                .setReferrer(request.getHeaders().get(REFERRER))
                .setUserId(request.getHeaders().get(USER_ID));

        auth.setKeyElems(appId, appKey);
        auth.setReport(report);
        auth.policyFailureHandler(policyFailureHandler::handle);
        auth.auth(resultHandler);
        return this;
    }

    @Override
    public AppIdAuth policyFailureHandler(IAsyncHandler<PolicyFailure> policyFailureHandler) {
        this.policyFailureHandler = policyFailureHandler;
        return this;
    }
}
