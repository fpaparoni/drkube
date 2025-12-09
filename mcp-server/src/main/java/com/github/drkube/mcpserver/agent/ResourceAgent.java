package com.github.drkube.mcpserver.agent;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
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
public class ResourceAgent {

    @Inject
    KubernetesClient client;

    @Tool(name = "getPodMetrics", description = "Retrieve CPU and memory metrics of a pod.")
    @RunOnVirtualThread
    public String getPodMetrics(
            @ToolArg(description = "Namespace of the pod") String namespace,
            @ToolArg(description = "Name of the pod") String podName,
            McpLog log) {

        log.info("Invoking ResourceAgent - getPodMetrics - namespace %s podName %s", namespace, podName);

        try {
            Pod pod = client.pods().inNamespace(namespace).withName(podName).get();
            if (pod == null) {
                return "Pod '" + podName + "' not found in namespace '" + namespace + "'";
            }

            CustomResourceDefinitionContext crdContext = new CustomResourceDefinitionContext.Builder()
                    .withGroup("metrics.k8s.io")
                    .withVersion("v1beta1")
                    .withPlural("pods")
                    .withScope("Namespaced")
                    .build();

            GenericKubernetesResource podMetrics = client.genericKubernetesResources(crdContext)
                    .inNamespace(namespace)
                    .withName(podName)
                    .get();

            if (podMetrics == null || podMetrics.getAdditionalProperties() == null) {
                return "Metrics not available for pod '" + podName + "'";
            }

            List<Map<String, Object>> containers = (List<Map<String, Object>>) podMetrics.getAdditionalProperties().get("containers");

            String metrics = containers.stream()
                    .map(c -> c.get("name") + " -> CPU: " + ((Map)c.get("usage")).get("cpu")
                            + ", Memory: " + ((Map)c.get("usage")).get("memory"))
                    .collect(Collectors.joining("; "));

            return String.format("Pod '%s' metrics: %s", podName, metrics);

        } catch (Exception e) {
            log.error("Error retrieving pod metrics: %s", e.getMessage());
            return "Error retrieving pod metrics: " + e.getMessage();
        }
    }

    @Tool(name = "analyzeNamespaceUsage", description = "Analyze resource usage per namespace.")
    @RunOnVirtualThread
    public String analyzeNamespaceUsage(
            @ToolArg(description = "Namespace to analyze") String namespace,
            McpLog log) {

        log.info("Invoking ResourceAgent - analyzeNamespaceUsage - namespace %s", namespace);

        try {
            List<Pod> pods = client.pods().inNamespace(namespace).list().getItems();
            if (pods.isEmpty()) {
                return "No pods found in namespace '" + namespace + "'";
            }

            CustomResourceDefinitionContext crdContext = new CustomResourceDefinitionContext.Builder()
                    .withGroup("metrics.k8s.io")
                    .withVersion("v1beta1")
                    .withPlural("pods")
                    .withScope("Namespaced")
                    .build();

            double totalCpu = 0;
            double totalMem = 0;

            for (Pod pod : pods) {
                GenericKubernetesResource podMetrics = client.genericKubernetesResources(crdContext)
                        .inNamespace(namespace)
                        .withName(pod.getMetadata().getName())
                        .get();

                if (podMetrics == null || podMetrics.getAdditionalProperties() == null) continue;

                List<Map<String, Object>> containers = (List<Map<String, Object>>) podMetrics.getAdditionalProperties().get("containers");

                for (Map<String, Object> c : containers) {
                    Map usage = (Map) c.get("usage");
                    totalCpu += parseCpu(usage.get("cpu").toString());
                    totalMem += parseMemory(usage.get("memory").toString());
                }
            }

            return String.format("Namespace '%s' total usage: CPU %.2f cores, Memory %.2f Mi", namespace, totalCpu, totalMem);

        } catch (Exception e) {
            log.error("Error analyzing namespace usage: %s", e.getMessage());
            return "Error analyzing namespace usage: " + e.getMessage();
        }
    }

    @Tool(name = "checkClusterCapacity", description = "Check the overall cluster capacity.")
    @RunOnVirtualThread
    public String checkClusterCapacity(McpLog log) {

        log.info("Invoking ResourceAgent - checkClusterCapacity");

        try {
            List<String> namespaces = client.namespaces().list().getItems().stream()
                    .map(ns -> ns.getMetadata().getName())
                    .collect(Collectors.toList());

            CustomResourceDefinitionContext crdContext = new CustomResourceDefinitionContext.Builder()
                    .withGroup("metrics.k8s.io")
                    .withVersion("v1beta1")
                    .withPlural("pods")
                    .withScope("Namespaced")
                    .build();

            double totalCpu = 0;
            double totalMem = 0;

            for (String ns : namespaces) {
                List<Pod> pods = client.pods().inNamespace(ns).list().getItems();
                for (Pod pod : pods) {
                    GenericKubernetesResource podMetrics = client.genericKubernetesResources(crdContext)
                            .inNamespace(ns)
                            .withName(pod.getMetadata().getName())
                            .get();

                    if (podMetrics == null || podMetrics.getAdditionalProperties() == null) continue;

                    List<Map<String, Object>> containers = (List<Map<String, Object>>) podMetrics.getAdditionalProperties().get("containers");

                    for (Map<String, Object> c : containers) {
                        Map usage = (Map) c.get("usage");
                        totalCpu += parseCpu(usage.get("cpu").toString());
                        totalMem += parseMemory(usage.get("memory").toString());
                    }
                }
            }

            return String.format("Cluster total usage: CPU %.2f cores, Memory %.2f Mi", totalCpu, totalMem);

        } catch (Exception e) {
            log.error("Error checking cluster capacity: %s", e.getMessage());
            return "Error checking cluster capacity: " + e.getMessage();
        }
    }

    // Helper methods to parse CPU and memory strings
    private double parseCpu(String cpu) {
        if (cpu.endsWith("n")) return Double.parseDouble(cpu.replace("n","")) / 1_000_000_000;
        if (cpu.endsWith("m")) return Double.parseDouble(cpu.replace("m","")) / 1000;
        return Double.parseDouble(cpu);
    }

    private double parseMemory(String mem) {
        if (mem.endsWith("Ki")) return Double.parseDouble(mem.replace("Ki","")) / 1024;
        if (mem.endsWith("Mi")) return Double.parseDouble(mem.replace("Mi",""));
        if (mem.endsWith("Gi")) return Double.parseDouble(mem.replace("Gi","")) * 1024;
        return Double.parseDouble(mem);
    }
}
