package com.github.drkube.mcpserver.agent;

import io.fabric8.kubernetes.api.model.PersistentVolume;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkiverse.mcp.server.McpLog;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class StorageAgent {

    @Inject
    KubernetesClient client;

    @Tool(name = "listPVCs", description = "List all PersistentVolumeClaims in a namespace.")
    @RunOnVirtualThread
    public String listPVCs(
            @ToolArg(description = "Namespace to list PVCs") String namespace,
            McpLog log) {

        log.info("Invoking StorageAgent - listPVCs - namespace %s", namespace);

        try {
            List<PersistentVolumeClaim> pvcs = client.persistentVolumeClaims()
                    .inNamespace(namespace)
                    .list()
                    .getItems();

            if (pvcs.isEmpty()) {
                return "No PVCs found in namespace '" + namespace + "'";
            }

            List<String> pvcNames = pvcs.stream()
                    .map(pvc -> pvc.getMetadata().getName())
                    .collect(Collectors.toList());

            return "PVCs in namespace '" + namespace + "': " + pvcNames;

        } catch (Exception e) {
            log.error("Error listing PVCs: %s", e.getMessage());
            return "Error listing PVCs: " + e.getMessage();
        }
    }

    @Tool(name = "checkPVCMount", description = "Check if a PVC is mounted by a pod.")
    @RunOnVirtualThread
    public String checkPVCMount(
            @ToolArg(description = "Pod name") String podName,
            @ToolArg(description = "PVC name") String pvcName,
            @ToolArg(description = "Namespace of the pod") String namespace,
            McpLog log) {

        log.info("Invoking StorageAgent - checkPVCMount - pod %s pvc %s namespace %s", podName, pvcName, namespace);

        try {
            Pod pod = client.pods().inNamespace(namespace).withName(podName).get();
            if (pod == null) return "Pod '" + podName + "' not found in namespace '" + namespace + "'";

            boolean mounted = pod.getSpec().getVolumes().stream()
                    .anyMatch(v -> v.getPersistentVolumeClaim() != null &&
                            pvcName.equals(v.getPersistentVolumeClaim().getClaimName()));

            return mounted
                    ? "PVC '" + pvcName + "' is mounted by pod '" + podName + "'"
                    : "PVC '" + pvcName + "' is NOT mounted by pod '" + podName + "'";

        } catch (Exception e) {
            log.error("Error checking PVC mount: %s", e.getMessage());
            return "Error checking PVC mount: " + e.getMessage();
        }
    }

    @Tool(name = "listPVs", description = "List all PersistentVolumes in the cluster.")
    @RunOnVirtualThread
    public String listPVs(McpLog log) {

        log.info("Invoking StorageAgent - listPVs");

        try {
            List<PersistentVolume> pvs = client.persistentVolumes().list().getItems();

            if (pvs.isEmpty()) return "No PersistentVolumes found in the cluster.";

            List<String> pvNames = pvs.stream()
                    .map(pv -> pv.getMetadata().getName() + " (" + pv.getStatus().getPhase() + ")")
                    .collect(Collectors.toList());

            return "PersistentVolumes in cluster: " + pvNames;

        } catch (Exception e) {
            log.error("Error listing PVs: %s", e.getMessage());
            return "Error listing PVs: " + e.getMessage();
        }
    }
}
