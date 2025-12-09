package com.github.drkube.mcpserver.agent;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkiverse.mcp.server.McpLog;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class SecurityAgent {

    @Inject
    KubernetesClient client;

    @Tool(name = "checkImageTags", description = "Check if any container images use the ':latest' tag in the specified namespace.")
    @RunOnVirtualThread
    public String checkImageTags(
            @ToolArg(description = "Namespace to check") String namespace,
            McpLog log) {

        log.info("Invoking SecurityAgent - checkImageTags - namespace %s", namespace);

        try {
            List<io.fabric8.kubernetes.api.model.Pod> pods = client.pods().inNamespace(namespace).list().getItems();

            List<String> podsWithLatest = pods.stream()
                    .flatMap(pod -> pod.getSpec().getContainers().stream()
                            .filter(c -> c.getImage() != null && c.getImage().endsWith(":latest"))
                            .map(c -> pod.getMetadata().getName() + " -> " + c.getName() + ":" + c.getImage())
                    )
                    .collect(Collectors.toList());

            if (podsWithLatest.isEmpty()) {
                return "No containers with ':latest' tag found in namespace '" + namespace + "'";
            } else {
                return "Containers using ':latest' tag in namespace '" + namespace + "': " + podsWithLatest;
            }

        } catch (Exception e) {
            log.error("Error checking image tags: %s", e.getMessage());
            return "Error checking image tags: " + e.getMessage();
        }
    }

    @Tool(name = "checkExpiredCertificates", description = "Verify expired TLS certificates in ingresses and webhook secrets.")
    @RunOnVirtualThread
    public String checkExpiredCertificates(McpLog log) {
        log.info("Invoking SecurityAgent - checkExpiredCertificates");

        try {
            StringBuilder expiredCerts = new StringBuilder();

            // Controllo TLS in ingress (networking.v1)
            List<io.fabric8.kubernetes.api.model.networking.v1.Ingress> ingresses =
                    client.network().v1().ingresses().inAnyNamespace().list().getItems();

            for (io.fabric8.kubernetes.api.model.networking.v1.Ingress ing : ingresses) {
                if (ing.getSpec().getTls() != null) {
                    for (io.fabric8.kubernetes.api.model.networking.v1.IngressTLS tls : ing.getSpec().getTls()) {
                        String secretName = tls.getSecretName();
                        if (secretName == null) continue;

                        Secret secret = client.secrets()
                                .inNamespace(ing.getMetadata().getNamespace())
                                .withName(secretName)
                                .get();

                        if (secret != null && secret.getData() != null && secret.getData().containsKey("tls.crt")) {
                            String crtBase64 = secret.getData().get("tls.crt");
                            byte[] decoded = Base64.getDecoder().decode(crtBase64);
                            CertificateFactory cf = CertificateFactory.getInstance("X.509");
                            X509Certificate cert = (X509Certificate) cf.generateCertificate(
                                    new java.io.ByteArrayInputStream(decoded)
                            );
                            if (cert.getNotAfter().before(new Date())) {
                                expiredCerts.append(String.format(
                                        "Ingress %s/%s: certificate expired%n",
                                        ing.getMetadata().getNamespace(),
                                        ing.getMetadata().getName()
                                ));
                            }
                        }
                    }
                }
            }

            // Controllo TLS in webhook secret (opzionale)
            List<Secret> webhookSecrets = client.secrets().inAnyNamespace().list().getItems().stream()
                    .filter(s -> s.getMetadata().getName().contains("webhook") &&
                            s.getData() != null &&
                            s.getData().containsKey("tls.crt"))
                    .collect(Collectors.toList());

            for (Secret s : webhookSecrets) {
                byte[] decoded = Base64.getDecoder().decode(s.getData().get("tls.crt"));
                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                X509Certificate cert = (X509Certificate) cf.generateCertificate(
                        new java.io.ByteArrayInputStream(decoded)
                );
                if (cert.getNotAfter().before(new Date())) {
                    expiredCerts.append(String.format(
                            "Webhook Secret %s/%s: certificate expired%n",
                            s.getMetadata().getNamespace(),
                            s.getMetadata().getName()
                    ));
                }
            }

            if (expiredCerts.length() == 0) {
                return "All TLS certificates in Ingresses and Webhook secrets are valid.";
            } else {
                return expiredCerts.toString();
            }

        } catch (Exception e) {
            log.error("Error checking expired certificates: %s", e.getMessage());
            return "Error checking expired certificates: " + e.getMessage();
        }
    }

    @Tool(name = "auditServiceAccounts", description = "Analyze ServiceAccounts with elevated privileges.")
    @RunOnVirtualThread
    public String auditServiceAccounts(McpLog log) {

        log.info("Invoking SecurityAgent - auditServiceAccounts");

        try {
            List<ServiceAccount> sas = client.serviceAccounts().inAnyNamespace().list().getItems();

            List<String> riskySAs = sas.stream()
                    .filter(sa -> sa.getMetadata().getName() != null &&
                            (sa.getMetadata().getName().contains("admin") || sa.getMetadata().getName().contains("cluster-admin")))
                    .map(sa -> sa.getMetadata().getNamespace() + "/" + sa.getMetadata().getName())
                    .collect(Collectors.toList());

            if (riskySAs.isEmpty()) {
                return "No ServiceAccounts with unnecessary elevated privileges found.";
            } else {
                return "ServiceAccounts with potential elevated privileges: " + riskySAs;
            }

        } catch (Exception e) {
            log.error("Error auditing ServiceAccounts: %s", e.getMessage());
            return "Error auditing ServiceAccounts: " + e.getMessage();
        }
    }
}
