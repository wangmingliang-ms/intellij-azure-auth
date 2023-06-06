package com.microsoft.azure.test.auth;

import com.azure.core.credential.TokenCredential;
import com.azure.core.management.profile.AzureProfile;
import com.azure.resourcemanager.resources.models.Tenant;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import static com.microsoft.azure.test.auth.AzureIdentityExample.environment;
import static com.microsoft.azure.test.auth.AzureIdentityExample.getTenantTokenCredential;
import static com.microsoft.azure.test.auth.AzureIdentityExample.listSubscriptions;
import static com.microsoft.azure.test.auth.AzureIdentityExample.listTenants;
import static com.microsoft.azure.test.auth.AzureIdentityExample.signWithOAuth;
import static com.microsoft.azure.test.auth.AzureIdentityExample.tenantId7d;

public class LoginWithOAuthAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull final AnActionEvent e) {
        final ThreadFactory namedThreadFactory = new ThreadFactoryBuilder().setNameFormat("azure-toolkit-auth-%d").build();
        final ExecutorService executorService = Executors.newFixedThreadPool(2, namedThreadFactory);
        final TokenCredential credential = signWithOAuth(tenantId7d);
        List<Tenant> tenants = listTenants(credential, new AzureProfile(environment));
        for (Tenant tenant : tenants) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Azure Notification Group")
                .createNotification(tenant.innerModel().displayName(), NotificationType.INFORMATION)
                .notify(e.getProject());
            String tid = tenant.tenantId();
            if (tenant.tenantId().endsWith("8c07b7d8-d6fa-4e5b-be42-c1a09b2fa19f")) {
                continue;
            }
            final TokenCredential c = getTenantTokenCredential(credential, tid);
            AzureProfile profile = new AzureProfile(tid, null, environment);
            listSubscriptions(c, profile);
        }
        executorService.shutdown();
    }
}
