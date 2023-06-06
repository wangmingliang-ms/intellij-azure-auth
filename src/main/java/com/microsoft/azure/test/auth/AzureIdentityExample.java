/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.test.auth;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.SimpleTokenCache;
import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;
import com.azure.core.http.policy.FixedDelay;
import com.azure.core.http.policy.HttpLogDetailLevel;
import com.azure.core.http.policy.RetryPolicy;
import com.azure.core.http.rest.PagedIterable;
import com.azure.core.management.AzureEnvironment;
import com.azure.core.management.profile.AzureProfile;
import com.azure.identity.AzureCliCredentialBuilder;
import com.azure.identity.ChainedTokenCredentialBuilder;
import com.azure.identity.ClientCertificateCredentialBuilder;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.identity.DeviceCodeCredentialBuilder;
import com.azure.identity.InteractiveBrowserCredentialBuilder;
import com.azure.identity.ManagedIdentityCredentialBuilder;
import com.azure.identity.SharedTokenCacheCredential;
import com.azure.identity.SharedTokenCacheCredentialBuilder;
import com.azure.identity.TokenCachePersistenceOptions;
import com.azure.identity.implementation.MsalToken;
import com.azure.identity.implementation.util.ScopeUtil;
import com.azure.resourcemanager.appservice.AppServiceManager;
import com.azure.resourcemanager.resources.ResourceManager;
import com.azure.resourcemanager.resources.models.Subscription;
import com.azure.resourcemanager.resources.models.Tenant;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.stream.Collectors;

@Slf4j
public class AzureIdentityExample {
    static final String tenantIdVs = "89079aa2-8a7f-42b5-aea6-212a757c0299"; // Visual Studio Enterprise Subscription
    static final String subscriptionIdVs = "7b6e5a3d-921c-4518-9d29-aa28f1c4bcb7"; // Visual Studio Enterprise Subscription
    public static final String tenantId7d = "72f988bf-86f1-41af-91ab-2d7cd011db47"; // Java Tooling Tests with TTL = 7 Days
    static final String subscriptionId7d = "685ba005-af8d-4b04-8f16-a7bf38b2eb5a"; // Java Tooling Tests with TTL = 7 Days
    static final String tenantId = tenantIdVs;
    static final String subscriptionId = subscriptionIdVs;
    static final String clientId = "04b07795-8ddb-461a-bbee-02f9e1bf7b46";

    public static final Map<String, SimpleTokenCache> resourceTokenCache = new ConcurrentHashMap<>();
    public static final AzureEnvironment environment = AzureEnvironment.AZURE;
    public static final String username = "wangmi@microsoft.com";
    public static final String tokenCacheName = "azure-toolkit.cache";
    public static final TokenCachePersistenceOptions persistenceOptions = new TokenCachePersistenceOptions().setName(tokenCacheName);

    public static void main(String[] args) {
        final ThreadFactory namedThreadFactory = new ThreadFactoryBuilder().setNameFormat("azure-toolkit-auth-%d").build();
        final ExecutorService executorService = Executors.newFixedThreadPool(2, namedThreadFactory);
        final TokenCredential credential = signWithOAuth(tenantId7d);
        List<Tenant> tenants = listTenants(credential, new AzureProfile(environment));
        for (Tenant tenant : tenants) {
            log.warn("#####################################################" + tenant.innerModel().displayName());
            String tid = tenant.tenantId();
            final TokenCredential c = getTenantTokenCredential(credential, tid);
            AzureProfile profile = new AzureProfile(tid, null, environment);
            listSubscriptions(c, profile);
        }
        executorService.shutdown();
    }

    public static List<Tenant> listTenants(TokenCredential credential, AzureProfile azureProfile) {
        ResourceManager.Authenticated authenticated = ResourceManager.configure()
            .withPolicy((httpPipelineCallContext, httpPipelineNextPolicy) -> {
                final String previousUserAgent = httpPipelineCallContext.getHttpRequest().getHeaders().getValue("User-Agent");
                httpPipelineCallContext.getHttpRequest().setHeader("User-Agent", String.format("wangmi/0.1 %s", previousUserAgent));
                return httpPipelineNextPolicy.process();
            })
            .withRetryPolicy(new RetryPolicy(new FixedDelay(0, Duration.ofSeconds(0))))
            .authenticate(credential, azureProfile);
        return authenticated.tenants().list().stream().collect(Collectors.toList());
    }

    public static List<Subscription> listSubscriptions(TokenCredential credential, AzureProfile azureProfile) {
        ResourceManager.Authenticated authenticated = ResourceManager.configure()
            .withLogLevel(HttpLogDetailLevel.HEADERS)
            .withPolicy((httpPipelineCallContext, httpPipelineNextPolicy) -> {
                final String previousUserAgent = httpPipelineCallContext.getHttpRequest().getHeaders().getValue("User-Agent");
                httpPipelineCallContext.getHttpRequest().setHeader("User-Agent", String.format("wangmi/0.1 %s", previousUserAgent));
                return httpPipelineNextPolicy.process();
            })
            .withRetryPolicy(new RetryPolicy(new FixedDelay(0, Duration.ofSeconds(0))))
            .authenticate(credential, azureProfile);
        final PagedIterable<Subscription> list = authenticated.subscriptions().list();
        log.warn("subscriptions:" + list.stream().count());
        return list.stream().toList();
    }

    public static void listWebApps(TokenCredential credential, AzureProfile azureProfile) {
        final AppServiceManager manager = AppServiceManager.configure()
            .withLogLevel(HttpLogDetailLevel.HEADERS)
            .withPolicy((httpPipelineCallContext, httpPipelineNextPolicy) -> {
                final String previousUserAgent = httpPipelineCallContext.getHttpRequest().getHeaders().getValue("User-Agent");
                httpPipelineCallContext.getHttpRequest().setHeader("User-Agent", String.format("wangmi/0.1 %s", previousUserAgent));
                return httpPipelineNextPolicy.process();
            }) // set user agent with policy
            .authenticate(credential, azureProfile);
        log.warn("webapps:" + manager.webApps().list().stream().count());
    }

    public static void printToken(TokenCredential credential) {
        log.warn("credential ready!");
        final String[] scopes = ScopeUtil.resourceToScopes(environment.getManagementEndpoint());
        // final String scope = "https://management.core.windows.net/.default";
        TokenRequestContext request = new TokenRequestContext().addScopes(scopes);
        AccessToken accessToken = credential.getToken(request).retry(3L).blockOptional()
            .orElseThrow(() -> new RuntimeException("Failed to retrieve token"));
        if (accessToken instanceof MsalToken) {
            log.warn(((MsalToken) accessToken).getAccount().username());
        }
        log.warn(accessToken.getToken());
    }

    public static SharedTokenCacheCredential restoreSigning(String tenantId) {
        return new SharedTokenCacheCredentialBuilder()
            .clientId(clientId)
            .tenantId(null)
            .tokenCachePersistenceOptions(persistenceOptions)
            .username(username)
            .build();
    }

    public static TokenCredential getTenantTokenCredential(TokenCredential credential, String tenantId) {
        return request -> {
            request.setTenantId(tenantId);
            return credential.getToken(request);
        };
    }

    public static TokenCredential signWithAzureCli(String tenantId) {
        return new AzureCliCredentialBuilder()
            .tenantId(tenantId)
            .build();
    }

    public static TokenCredential signWithOAuth(String tenantId) {
        TokenCachePersistenceOptions persistenceOptions = new TokenCachePersistenceOptions().setName(tokenCacheName);
        String clientId = "04b07795-8ddb-461a-bbee-02f9e1bf7b46";
        return new InteractiveBrowserCredentialBuilder()
            .clientId(clientId)
            .tokenCachePersistenceOptions(persistenceOptions)
            .redirectUrl("http://localhost:1101")
            .build();
    }

    public static TokenCredential signWithDeviceCode(String tenantId, ExecutorService executorService) {
        return new DeviceCodeCredentialBuilder()
            .clientId(clientId)
            .tenantId(tenantId)
            .tokenCachePersistenceOptions(persistenceOptions)
            .authorityHost(environment.getActiveDirectoryEndpoint())
            .executorService(executorService)
            .challengeConsumer(challenge -> log.warn(challenge.getMessage())).build();
    }

    public static TokenCredential signWithServicePrincipal(String tenantId) {
        return new ClientCertificateCredentialBuilder()
            .clientId(clientId)
            .tenantId(tenantId)
            .tokenCachePersistenceOptions(persistenceOptions)
            .pfxCertificate("", "")
            // choose between either a PEM certificate or a PFX certificate
            //.pfxCertificate("<PATH TO PFX CERTIFICATE>", "PFX CERTIFICATE PASSWORD")
            .build();
    }

    public static TokenCredential signWithClientSecret(String tenantId) {
        return new ClientSecretCredentialBuilder()
            .clientId(clientId)
            .tenantId(tenantId)
            .tokenCachePersistenceOptions(persistenceOptions)
            .clientSecret("")
            .build();
    }

    public static TokenCredential signWithManagedIdentity() {
        return new ManagedIdentityCredentialBuilder().build();
    }

    public static TokenCredential signWithAvailableCredential(String tenantId) {
        ChainedTokenCredentialBuilder builder = new ChainedTokenCredentialBuilder();
        builder.addLast(signWithManagedIdentity());
        builder.addLast(signWithOAuth(tenantId));
        return builder.build();
    }
}
