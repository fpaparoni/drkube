package com.github.drkube.mcpserver.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class KubernetesClientProducer {

    @Produces
    @ApplicationScoped
    public KubernetesClient kubernetesClient() {
        String kubeconfigPath = System.getenv("KUBECONFIG"); // legge la variabile d'ambiente

        if (kubeconfigPath == null || kubeconfigPath.isEmpty()) {
            // fallback al path di default
            kubeconfigPath = System.getProperty("user.home") + "/.kube/config";
        }

        Config config = null;
        try {
            config = Config.fromKubeconfig(null, Files.readString(Path.of(kubeconfigPath)), kubeconfigPath);
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Errore leggendo il kubeconfig: " + kubeconfigPath, e);
        }

        return new KubernetesClientBuilder()
                .withConfig(config)
                .build();
    }


}

