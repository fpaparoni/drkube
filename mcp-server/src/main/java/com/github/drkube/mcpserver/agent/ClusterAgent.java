package com.github.drkube.mcpserver.agent;

import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.VersionInfo;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkiverse.mcp.server.McpLog;
import io.quarkiverse.mcp.server.Tool;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ClusterAgent — raccoglie informazioni di alto livello sul cluster Kubernetes:
 * versione, nodi, namespace e problemi di scheduling.
 */
@ApplicationScoped
public class ClusterAgent {

    @Inject
    KubernetesClient client;

    @Tool(name = "getClusterInfo", description = "Show general information about the Kubernetes cluster.")
    @RunOnVirtualThread
    public String getClusterInfo(McpLog log) {
        log.info("Invoking ClusterAgent - getClusterInfo");
        try {
            VersionInfo version = client.getKubernetesVersion();
            List<Node> nodes = client.nodes().list().getItems();
            List<Namespace> namespaces = client.namespaces().list().getItems();

            String clusterVersion = version != null
                    ? version.getMajor() + "." + version.getMinor()
                    : "unknown";
            int nodeCount = nodes != null ? nodes.size() : 0;
            long activeNamespaces = namespaces != null
                    ? namespaces.stream()
                    .filter(ns -> "Active".equalsIgnoreCase(ns.getStatus().getPhase()))
                    .count()
                    : 0;

            return String.format(
                    "Cluster version: %s%nNodes: %d%nActive namespaces: %d",
                    clusterVersion, nodeCount, activeNamespaces);

        } catch (Exception e) {
            log.error("Error retrieving cluster info: %s", e.getMessage());
            return "Error retrieving cluster info: " + e.getMessage();
        }
    }

    @Tool(name = "checkControlPlaneHealth", description = "Check the status of the control plane.")
    @RunOnVirtualThread
    public String checkControlPlaneHealth(McpLog log) {
        log.info("Invoking ClusterAgent - checkControlPlaneHealth");

        try {
            // Controlliamo se l'API server risponde correttamente
            VersionInfo version = client.getKubernetesVersion();
            if (version == null) {
                return "Control plane unreachable (cannot fetch version).";
            }

            // Heuristica: verifichiamo se ci sono nodi 'control-plane'
            List<Node> controlPlaneNodes = client.nodes().list().getItems().stream()
                    .filter(n -> n.getMetadata().getLabels() != null &&
                            n.getMetadata().getLabels().containsKey("node-role.kubernetes.io/control-plane"))
                    .collect(Collectors.toList());

            if (controlPlaneNodes.isEmpty()) {
                return "No control-plane nodes found.";
            }

            // Verifica condizioni di salute dei nodi di controllo
            Map<String, String> nodeStatuses = controlPlaneNodes.stream()
                    .collect(Collectors.toMap(
                            n -> n.getMetadata().getName(),
                            n -> n.getStatus().getConditions().stream()
                                    .filter(c -> "Ready".equals(c.getType()))
                                    .findFirst()
                                    .map(c -> c.getStatus())
                                    .orElse("Unknown")
                    ));

            boolean allHealthy = nodeStatuses.values().stream()
                    .allMatch(s -> "True".equalsIgnoreCase(s));

            return String.format(
                    "Control plane nodes: %s%nAll healthy: %s",
                    nodeStatuses, allHealthy ? "YES" : "NO");

        } catch (Exception e) {
            log.error("Error checking control plane health: %s", e.getMessage());
            return "Error checking control plane health: " + e.getMessage();
        }
    }

    @Tool(name = "checkNamespaceHealth", description = "Verify the overall status of the namespaces.")
    @RunOnVirtualThread
    public String checkNamespaceHealth(McpLog log) {
        log.info("Invoking ClusterAgent - checkNamespaceHealth");

        try {
            List<Namespace> namespaces = client.namespaces().list().getItems();
            if (namespaces.isEmpty()) {
                return "No namespaces found in cluster.";
            }

            String summary = namespaces.stream()
                    .map(ns -> String.format("%s: %s",
                            ns.getMetadata().getName(),
                            ns.getStatus().getPhase()))
                    .collect(Collectors.joining("\n"));

            boolean allActive = namespaces.stream()
                    .allMatch(ns -> "Active".equalsIgnoreCase(ns.getStatus().getPhase()));

            return String.format("Namespaces:%n%s%nAll Active: %s", summary, allActive ? "YES" : "NO");

        } catch (Exception e) {
            log.error("Error checking namespace health: %s", e.getMessage());
            return "Error checking namespace health: " + e.getMessage();
        }
    }

    @Tool(name = "detectSchedulingIssues", description = "Analyze any scheduling issues.")
    @RunOnVirtualThread
    public String detectSchedulingIssues(McpLog log) {
        log.info("Invoking ClusterAgent - detectSchedulingIssues");

        try {
            List<Pod> pods = client.pods().inAnyNamespace().list().getItems();
            if (pods.isEmpty()) {
                return "No pods found in cluster.";
            }

            List<Pod> pendingPods = pods.stream()
                    .filter(p -> p.getStatus() != null && "Pending".equalsIgnoreCase(p.getStatus().getPhase()))
                    .collect(Collectors.toList());

            if (pendingPods.isEmpty()) {
                return "No scheduling issues detected (no pending pods).";
            }

            String pendingSummary = pendingPods.stream()
                    .map(p -> String.format("%s/%s (%s)",
                            p.getMetadata().getNamespace(),
                            p.getMetadata().getName(),
                            p.getStatus().getReason() != null ? p.getStatus().getReason() : "Pending"))
                    .collect(Collectors.joining("\n"));

            return String.format("Detected %d pending pods:%n%s",
                    pendingPods.size(), pendingSummary);

        } catch (Exception e) {
            log.error("Error detecting scheduling issues: %s", e.getMessage());
            return "Error detecting scheduling issues: " + e.getMessage();
        }
    }
}
