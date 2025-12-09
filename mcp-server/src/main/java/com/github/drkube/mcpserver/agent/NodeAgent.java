package com.github.drkube.mcpserver.agent;

import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.NodeCondition;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;
import io.quarkiverse.mcp.server.McpLog;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class NodeAgent {

    @Inject
    KubernetesClient client;

    @Tool(name = "getNodeStatus", description = "Check the status of a node (Ready/NotReady, hardware conditions).")
    @RunOnVirtualThread
    public String getNodeStatus(
            @ToolArg(description = "Node name") String nodeName,
            McpLog log) {

        log.info("Invoking NodeAgent - getNodeStatus - nodeName %s", nodeName);

        try {
            Node node = client.nodes().withName(nodeName).get();
            if (node == null) {
                return "Node " + nodeName + " not found.";
            }

            List<NodeCondition> conditions = node.getStatus().getConditions();
            if (conditions == null || conditions.isEmpty()) {
                return "No conditions found for node " + nodeName;
            }

            String statusSummary = conditions.stream()
                    .map(c -> String.format("%s=%s", c.getType(), c.getStatus()))
                    .collect(Collectors.joining(", "));

            String readyStatus = conditions.stream()
                    .filter(c -> "Ready".equals(c.getType()))
                    .findFirst()
                    .map(NodeCondition::getStatus)
                    .orElse("Unknown");

            return String.format("Node %s is %s (%s)",
                    nodeName,
                    "True".equalsIgnoreCase(readyStatus) ? "Ready" : "NotReady",
                    statusSummary);

        } catch (Exception e) {
            log.error("Error retrieving node status: %s", e.getMessage());
            return "Error: " + e.getMessage();
        }
    }

    @Tool(name = "getNodeMetrics", description = "Retrieve CPU and memory metrics of a node")
    @RunOnVirtualThread
    public String getNodeMetrics(
            @ToolArg(description = "Node name") String nodeName,
            McpLog log) {

        log.info("Invoking NodeAgent - getNodeMetrics - nodeName %s", nodeName);

        try {
            // ResourceDefinitionContext per metrics.k8s.io
            ResourceDefinitionContext ctx = new ResourceDefinitionContext.Builder()
                    .withGroup("metrics.k8s.io")
                    .withVersion("v1beta1")
                    .withPlural("nodes")
                    .build();

            GenericKubernetesResource nodeMetrics =
                    client.genericKubernetesResources(ctx).withName(nodeName).get();

            if (nodeMetrics == null) {
                return "Metrics not available for node " + nodeName +
                        " (Metrics Server missing or API group metrics.k8s.io not reachable).";
            }

            // Lettura del campo usage (CPU, memoria, ecc.)
            Map<String, Object> usage = (Map<String, Object>) nodeMetrics.getAdditionalProperties().get("usage");
            if (usage == null) {
                return "Metrics object found but usage field missing for " + nodeName;
            }

            String cpu = (String) usage.getOrDefault("cpu", "N/A");
            String memory = (String) usage.getOrDefault("memory", "N/A");

            return String.format("Node %s metrics → CPU: %s, Memory: %s", nodeName, cpu, memory);

        } catch (Exception e) {
            log.error("Error retrieving node metrics: %s", e.getMessage());
            return "Error: " + e.getMessage();
        }
    }


    @Tool(name = "listPodsOnNode", description = "List the pods scheduled on a node.")
    @RunOnVirtualThread
    public String listPodsOnNode(
            @ToolArg(description = "Node name") String nodeName,
            McpLog log) {

        log.info("Invoking NodeAgent - listPodsOnNode - nodeName %s", nodeName);

        try {
            List<Pod> pods = client.pods().inAnyNamespace()
                    .withField("spec.nodeName", nodeName)
                    .list()
                    .getItems();

            if (pods.isEmpty()) {
                return "No pods scheduled on node " + nodeName;
            }

            String podList = pods.stream()
                    .map(p -> String.format("%s/%s", p.getMetadata().getNamespace(), p.getMetadata().getName()))
                    .collect(Collectors.joining("\n"));

            return String.format("Pods on node %s:\n%s", nodeName, podList);

        } catch (Exception e) {
            log.error("Error listing pods on node: %s", e.getMessage());
            return "Error: " + e.getMessage();
        }
    }

    @Tool(name = "checkNodePressure", description = "Check node pressure (memory, disk, PID).")
    @RunOnVirtualThread
    public String checkNodePressure(
            @ToolArg(description = "Node name") String nodeName,
            McpLog log) {

        log.info("Invoking NodeAgent - checkNodePressure - nodeName %s", nodeName);

        try {
            Node node = client.nodes().withName(nodeName).get();
            if (node == null) {
                return "Node " + nodeName + " not found.";
            }

            List<NodeCondition> conditions = node.getStatus().getConditions();
            if (conditions == null || conditions.isEmpty()) {
                return "No pressure conditions available for node " + nodeName;
            }

            var pressureInfo = conditions.stream()
                    .filter(c -> c.getType().endsWith("Pressure"))
                    .map(c -> String.format("%s=%s", c.getType(), c.getStatus()))
                    .collect(Collectors.joining(", "));

            if (pressureInfo.isEmpty()) {
                return "Node " + nodeName + " reports no pressure issues.";
            }

            return String.format("Node %s pressure conditions: %s", nodeName, pressureInfo);

        } catch (Exception e) {
            log.error("Error checking node pressure: %s", e.getMessage());
            return "Error: " + e.getMessage();
        }
    }
}
