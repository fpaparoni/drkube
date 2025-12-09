package com.github.drkube.mcpserver.agent;

import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import io.quarkiverse.mcp.server.McpLog;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class ServiceAgent {

    @Inject
    KubernetesClient client;

    @Tool(name = "checkServiceEndpoints", description = "Verify that a Service has active and reachable endpoints.")
    @RunOnVirtualThread
    public String checkServiceEndpoints(
            @ToolArg(description = "Namespace of the service") String namespace,
            @ToolArg(description = "Service name") String serviceName,
            McpLog log) {

        log.info("Invoking ServiceAgent - checkServiceEndpoints - namespace %s serviceName %s", namespace, serviceName);

        try {
            Endpoints endpoints = client.endpoints().inNamespace(namespace).withName(serviceName).get();
            if (endpoints == null || endpoints.getSubsets() == null || endpoints.getSubsets().isEmpty()) {
                return "Service '" + serviceName + "' has no active endpoints.";
            }

            long activeEndpoints = endpoints.getSubsets().stream()
                    .flatMap(s -> s.getAddresses() != null ? s.getAddresses().stream() : null)
                    .count();

            return "Service '" + serviceName + "' has " + activeEndpoints + " active endpoints.";

        } catch (Exception e) {
            log.error("Error checking service endpoints: %s", e.getMessage());
            return "Error checking service endpoints: " + e.getMessage();
        }
    }

    @Tool(name = "checkIngressConnectivity", description = "Check the status and connectivity of an Ingress.")
    @RunOnVirtualThread
    public String checkIngressConnectivity(
            @ToolArg(description = "Namespace of the Ingress") String namespace,
            @ToolArg(description = "Ingress name") String ingressName,
            McpLog log) {

        log.info("Invoking ServiceAgent - checkIngressConnectivity - namespace %s ingressName %s", namespace, ingressName);

        try {
            Ingress ingress = client.network().v1().ingresses().inNamespace(namespace).withName(ingressName).get();
            if (ingress == null) {
                return "Ingress '" + ingressName + "' not found in namespace '" + namespace + "'";
            }

            // Provo a collegarmi agli host delle rules
            List<String> hosts = ingress.getSpec().getRules().stream()
                    .map(r -> r.getHost())
                    .collect(Collectors.toList());

            StringBuilder result = new StringBuilder();
            for (String host : hosts) {
                String url = "http://" + host;
                try {
                    HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                    conn.setConnectTimeout(3000);
                    conn.setReadTimeout(3000);
                    conn.setRequestMethod("GET");
                    int code = conn.getResponseCode();
                    result.append(String.format("Ingress host %s responds with HTTP %d%n", host, code));
                } catch (Exception e) {
                    result.append(String.format("Ingress host %s not reachable: %s%n", host, e.getMessage()));
                }
            }

            return result.toString();

        } catch (Exception e) {
            log.error("Error checking ingress connectivity: %s", e.getMessage());
            return "Error checking ingress connectivity: " + e.getMessage();
        }
    }

    @Tool(name = "testPodConnectivity", description = "Perform a TCP connection from a pod to a host/port.")
    @RunOnVirtualThread
    public String testPodConnectivity(
            @ToolArg(description = "Pod name") String podName,
            @ToolArg(description = "Namespace of the pod") String namespace,
            @ToolArg(description = "Host to connect") String host,
            @ToolArg(description = "Port to connect") String port,
            McpLog log) {

        log.info("Invoking ServiceAgent - testPodConnectivity - pod %s namespace %s host %s port %s", podName, namespace, host, port);

        try {
            Pod pod = client.pods().inNamespace(namespace).withName(podName).get();
            if (pod == null) return "Pod '" + podName + "' not found in namespace '" + namespace + "'";

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteArrayOutputStream err = new ByteArrayOutputStream();

            try (ExecWatch execWatch = client.pods().inNamespace(namespace)
                    .withName(podName)
                    .writingOutput(out)
                    .writingError(err)
                    .exec("sh", "-c", "nc -zv " + host + " " + port)) {

                // Aspetto 5 secondi per completare la connessione TCP
                Thread.sleep(5000);
            }

            String output = out.toString();
            String error = err.toString();

            if (!error.isEmpty()) {
                return "TCP test failed: " + error;
            }
            return "TCP test output: " + output;

        } catch (Exception e) {
            log.error("Error testing pod connectivity: %s", e.getMessage());
            return "Error testing pod connectivity: " + e.getMessage();
        }
    }

    @Tool(name = "checkClusterDNS", description = "Check internal DNS resolution of a service from a pod.")
    @RunOnVirtualThread
    public String checkClusterDNS(
            @ToolArg(description = "Pod name to use for DNS query") String podName,
            @ToolArg(description = "Namespace of the pod") String namespace,
            @ToolArg(description = "Service name to resolve") String serviceName,
            McpLog log) {

        log.info("Invoking ServiceAgent - checkClusterDNS - pod %s namespace %s service %s", podName, namespace, serviceName);

        try {
            Pod pod = client.pods().inNamespace(namespace).withName(podName).get();
            if (pod == null) return "Pod '" + podName + "' not found in namespace '" + namespace + "'";

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteArrayOutputStream err = new ByteArrayOutputStream();

            try (ExecWatch execWatch = client.pods().inNamespace(namespace)
                    .withName(podName)
                    .writingOutput(out)
                    .writingError(err)
                    .exec("nslookup", serviceName)) {

                Thread.sleep(5000); // attesa per l’output
            }

            String output = out.toString();
            String error = err.toString();

            if (!error.isEmpty()) {
                return "DNS resolution failed: " + error;
            }

            return "DNS resolution result:\n" + output;

        } catch (Exception e) {
            log.error("Error checking cluster DNS: %s", e.getMessage());
            return "Error checking cluster DNS: " + e.getMessage();
        }
    }

}
