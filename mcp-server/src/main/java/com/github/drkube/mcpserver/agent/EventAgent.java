package com.github.drkube.mcpserver.agent;

import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkiverse.mcp.server.McpLog;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class EventAgent {

    @Inject
    KubernetesClient client;

    @Tool(name = "getRecentClusterEvents", description = "Retrieve the recent events of the cluster.")
    @RunOnVirtualThread
    public String getRecentClusterEvents(McpLog log) {
        log.info("Invoking EventAgent - getRecentClusterEvents");
        try {
            List<Event> events = client.v1().events().inAnyNamespace().list().getItems();
            if (events.isEmpty()) {
                return "No recent events in the cluster.";
            }

            // Ordino gli eventi per ultimo aggiornamento decrescente e prendo i primi 10
            List<String> eventNames = events.stream()
                    .sorted((e1, e2) -> e2.getLastTimestamp().compareTo(e1.getLastTimestamp()))
                    .limit(10)
                    .map(e -> e.getReason() + " (" + e.getInvolvedObject().getKind() + "/" + e.getInvolvedObject().getName() + ")")
                    .collect(Collectors.toList());

            return "Recent events: " + eventNames;

        } catch (Exception e) {
            log.error("Error retrieving cluster events: %s", e.getMessage());
            return "Error retrieving cluster events: " + e.getMessage();
        }
    }

    @Tool(name = "getPodEvents", description = "Retrieve events related to a specific pod.")
    @RunOnVirtualThread
    public String getPodEvents(
            @ToolArg(description = "Namespace") String namespace,
            @ToolArg(description = "Pod name") String podName,
            McpLog log) {

        log.info("Invoking EventAgent - getPodEvents - namespace %s podName %s", namespace, podName);
        try {
            List<Event> events = client.v1().events().inNamespace(namespace).list().getItems().stream()
                    .filter(e -> e.getInvolvedObject() != null &&
                            podName.equals(e.getInvolvedObject().getName()) &&
                            "Pod".equals(e.getInvolvedObject().getKind()))
                    .collect(Collectors.toList());

            if (events.isEmpty()) {
                return "No events found for pod '" + podName + "' in namespace '" + namespace + "'";
            }

            List<String> eventDescriptions = events.stream()
                    .map(e -> e.getReason() + " - " + e.getMessage())
                    .collect(Collectors.toList());

            return "Events for pod '" + podName + "': " + eventDescriptions;

        } catch (Exception e) {
            log.error("Error retrieving pod events: %s", e.getMessage());
            return "Error retrieving pod events: " + e.getMessage();
        }
    }

    @Tool(name = "detectRecurringEvents", description = "Identify recurring events in the last N minutes.")
    @RunOnVirtualThread
    public String detectRecurringEvents(
            @ToolArg(description = "Minutes time window") int minutes,
            McpLog log) {

        log.info("Invoking EventAgent - detectRecurringEvents - minutes %s", minutes);
        try {
            Instant cutoff = Instant.now().minus(minutes, ChronoUnit.MINUTES);

            List<Event> events = client.v1().events().inAnyNamespace().list().getItems().stream()
                    .filter(e -> e.getLastTimestamp() != null &&
                            Instant.parse(e.getLastTimestamp()).isAfter(cutoff))
                    .collect(Collectors.toList());

            // Raggruppo per reason e conto le occorrenze
            List<String> recurring = events.stream()
                    .collect(Collectors.groupingBy(Event::getReason, Collectors.counting()))
                    .entrySet().stream()
                    .filter(entry -> entry.getValue() > 1)
                    .map(entry -> entry.getKey() + " occurred " + entry.getValue() + " times")
                    .collect(Collectors.toList());

            if (recurring.isEmpty()) {
                return "No recurring events in the last " + minutes + " minutes.";
            }

            return "Recurring events in the last " + minutes + " minutes: " + recurring;

        } catch (Exception e) {
            log.error("Error detecting recurring events: %s", e.getMessage());
            return "Error detecting recurring events: " + e.getMessage();
        }
    }
}
