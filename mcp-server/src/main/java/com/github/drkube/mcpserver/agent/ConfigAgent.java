package com.github.drkube.mcpserver.agent;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;
import io.quarkiverse.mcp.server.McpLog;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class ConfigAgent {

    @Inject
    KubernetesClient client;

    @Tool(name = "getConfigMap", description = "Retrieve a ConfigMap and display the contained keys")
    @RunOnVirtualThread
    public String getConfigMap(
            @ToolArg(description = "Namespace") String namespace,
            @ToolArg(description = "Config name") String name,
            McpLog log) {

        log.info("Invoking ConfigAgent - getConfigMap - namespace %s name %s", namespace, name);
        ConfigMap cm = client.configMaps().inNamespace(namespace).withName(name).get();
        if (cm == null) {
            return "ConfigMap '" + name + "' not found in namespace '" + namespace + "'";
        }
        Set<String> keys = cm.getData() != null ? cm.getData().keySet() : Collections.emptySet();
        return "ConfigMap '" + name + "' keys: " + String.join(", ", keys);
    }

    @Tool(name = "verifySecretKeys", description = "Check whether a Secret contains the expected keys")
    @RunOnVirtualThread
    public String verifySecretKeys(
            @ToolArg(description = "Namespace") String namespace,
            @ToolArg(description = "Secret name") String name,
            @ToolArg(description = "Expected keys") List<String> expectedKeys,
            McpLog log) {

        log.info("Invoking ConfigAgent - verifySecretKeys - namespace %s name %s expectedKeys %s",
                namespace, name, expectedKeys);

        Secret secret = client.secrets().inNamespace(namespace).withName(name).get();
        if (secret == null) {
            return "Secret '" + name + "' not found in namespace '" + namespace + "'";
        }

        Set<String> actualKeys = secret.getData() != null ? secret.getData().keySet() : Collections.emptySet();
        List<String> missing = expectedKeys.stream()
                .filter(k -> !actualKeys.contains(k))
                .collect(Collectors.toList());

        if (missing.isEmpty()) {
            return "Secret '" + name + "' contains all expected keys: " + expectedKeys;
        } else {
            return "Secret '" + name + "' is missing keys: " + missing + " (found: " + actualKeys + ")";
        }
    }

    @Tool(name = "checkRBACPermissions", description = "Verify the RBAC permissions of a ServiceAccount")
    @RunOnVirtualThread
    public String checkRBACPermissions(
            @ToolArg(description = "Namespace") String namespace,
            @ToolArg(description = "Service account name") String saName,
            @ToolArg(description = "Verb to check") String verb,
            @ToolArg(description = "Resource to check") String resource,
            McpLog log) {

        log.info("Invoking ConfigAgent - checkRBACPermissions - namespace %s saName %s verb %s resource %s",
                namespace, saName, verb, resource);

        try {
            // Creazione contesto per SelfSubjectAccessReview
            ResourceDefinitionContext ctx = new ResourceDefinitionContext.Builder()
                    .withGroup("authorization.k8s.io")
                    .withVersion("v1")
                    .withKind("SelfSubjectAccessReview")
                    .withPlural("selfsubjectaccessreviews")
                    .build();

            // Corpo della richiesta come mappa
            Map<String, Object> reviewMap = Map.of(
                    "apiVersion", "authorization.k8s.io/v1",
                    "kind", "SelfSubjectAccessReview",
                    "spec", Map.of(
                            "resourceAttributes", Map.of(
                                    "namespace", namespace,
                                    "verb", verb,
                                    "resource", resource
                            )
                    )
            );

            // Avvolgo la mappa in GenericKubernetesResource
            GenericKubernetesResource reviewResource = new GenericKubernetesResource();
            reviewResource.setAdditionalProperties(reviewMap);

            // Creo la risorsa nel cluster
            GenericKubernetesResource result = client.genericKubernetesResources(ctx).create(reviewResource);

            // Leggo lo status dalla risorsa creata
            Map<String, Object> status = (Map<String, Object>) result.getAdditionalProperties().get("status");
            Boolean allowed = status != null ? (Boolean) status.get("allowed") : null;

            if (Boolean.TRUE.equals(allowed)) {
                return String.format("✅ ServiceAccount '%s' in namespace '%s' is allowed to %s %s",
                        saName, namespace, verb, resource);
            } else {
                String reason = status != null && status.get("reason") != null ? status.get("reason").toString() : "unknown";
                return String.format("❌ ServiceAccount '%s' in namespace '%s' is NOT allowed to %s %s (reason: %s)",
                        saName, namespace, verb, resource, reason);
            }

        } catch (Exception e) {
            log.error("Error checking RBAC permissions: %s", e.getMessage());
            return "Error checking RBAC permissions: " + e.getMessage();
        }
    }

}
