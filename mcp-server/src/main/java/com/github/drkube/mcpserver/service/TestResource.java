package com.github.drkube.mcpserver.service;

import java.nio.file.Files;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/test")
@Produces(MediaType.TEXT_PLAIN)
public class TestResource {

    @Inject
    KubernetesClient client;

    @GET
    public String test() {
        
        try {
            PodList pods = client.pods().inNamespace("kube-system").list();

            if (pods.getItems().isEmpty()) {
                return "No pods found in namespace ";
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
            e.printStackTrace();
            System.out.println("Error listing pods: "+e.getMessage());
            return "Error: " + e.getMessage();
        }
    }
}