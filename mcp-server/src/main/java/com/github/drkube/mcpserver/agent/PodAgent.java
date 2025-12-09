package com.github.drkube.mcpserver.agent;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.LogWatch;
import io.quarkiverse.mcp.server.McpLog;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

@ApplicationScoped
public class PodAgent {

    @Inject
    KubernetesClient client;

    @Tool(name="getPodLogs",description = "Retrieve the logs of a pod in the specified namespace")
    @RunOnVirtualThread
    public String getPodLogs(
            @ToolArg(description="Namespace") String namespace,
            @ToolArg(description="Pod name") String podName,
            @ToolArg(description="Lines of logs", required = false) Integer tailLines,
            McpLog log) {

        log.info("Invoking PodAgent - getPodLogs - namespace %s podName %s tailLines %s", namespace, podName, tailLines);

        try (LogWatch logWatch = client.pods()
                .inNamespace(namespace)
                .withName(podName)
                .tailingLines(tailLines != null ? tailLines : 100)
                .watchLog();
             BufferedReader reader = new BufferedReader(new InputStreamReader(logWatch.getOutput()))) {

            return reader.lines().collect(Collectors.joining("\n"));
        } catch (Exception e) {
            log.error("Error retrieving pod logs: %s", e.getMessage());
            return "Error: " + e.getMessage();
        }
    }

    @Tool(name = "listPodsInNamespace", description = "List the pods in a namespace with their status and restart count.")
    @RunOnVirtualThread
    public String listPodsInNamespace(
            @ToolArg(description="Namespace") String namespace,
            McpLog log) {

        log.info("Invoking PodAgent - listPodsInNamespace - namespace %s", namespace);

        try {
            PodList pods = client.pods().inNamespace(namespace).list();

            if (pods.getItems().isEmpty()) {
                return "No pods found in namespace " + namespace;
            }

            return pods.getItems().stream()
                    .map(p -> String.format(
                            "%s - Status: %s - Restarts: %d",
                            p.getMetadata().getName(),
                            p.getStatus().getPhase(),
                            p.getStatus().getContainerStatuses() != null ?
                                    p.getStatus().getContainerStatuses().stream()
                                            .mapToInt(cs -> cs.getRestartCount() != null ? cs.getRestartCount() : 0)
                                            .sum() : 0))
                    .collect(Collectors.joining("\n"));

        } catch (Exception e) {
            log.error("Error listing pods: %s", e.getMessage());
            return "Error: " + e.getMessage();
        }
    }

    @Tool(name="describePod",description = "Run a describe on a pod to analyze its conditions and events.")
    @RunOnVirtualThread
    public String describePod(
            @ToolArg(description="Namespace") String namespace,
            @ToolArg(description="Pod name") String podName,
            McpLog log) {

        log.info("Invoking PodAgent - describePod - namespace %s podName %s", namespace, podName);

        try {
            Pod pod = client.pods().inNamespace(namespace).withName(podName).get();
            if (pod == null) return "Pod not found.";

            String conditions = pod.getStatus().getConditions() != null ?
                    pod.getStatus().getConditions().stream()
                            .map(c -> c.getType() + "=" + c.getStatus())
                            .collect(Collectors.joining(", "))
                    : "No conditions found.";

            return String.format("Pod: %s%nPhase: %s%nConditions: %s",
                    pod.getMetadata().getName(),
                    pod.getStatus().getPhase(),
                    conditions);

        } catch (Exception e) {
            log.error("Error describing pod: %s", e.getMessage());
            return "Error: " + e.getMessage();
        }
    }

    @Tool(name="checkPodPlacement",description = "Check which node a pod is scheduled on and whether the node is in Ready state.")
    @RunOnVirtualThread
    public String checkPodPlacement(
            @ToolArg(description="Namespace") String namespace,
            @ToolArg(description="Pod name") String podName,
            McpLog log) {

        log.info("Invoking PodAgent - checkPodPlacement - namespace %s podName %s", namespace, podName);

        try {
            Pod pod = client.pods().inNamespace(namespace).withName(podName).get();
            if (pod == null) return "Pod not found.";

            String nodeName = pod.getSpec().getNodeName();
            if (nodeName == null) return "Pod not yet scheduled on any node.";

            var node = client.nodes().withName(nodeName).get();
            if (node == null) return "Node " + nodeName + " not found.";

            var readyCondition = node.getStatus().getConditions().stream()
                    .filter(c -> "Ready".equals(c.getType()))
                    .findFirst()
                    .map(c -> c.getStatus())
                    .orElse("Unknown");

            return String.format("Pod %s is on node %s (Ready=%s)", podName, nodeName, readyCondition);

        } catch (Exception e) {
            log.error("Error checking pod placement: %s", e.getMessage());
            return "Error: " + e.getMessage();
        }
    }
}
