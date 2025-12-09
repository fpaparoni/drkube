package com.github.drkube.queryserver.resource;

import com.github.drkube.queryserver.service.DrKubeAssistantService;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/issue")
@Produces(MediaType.TEXT_PLAIN)
public class IssueResource {

    @Inject
    DrKubeAssistantService assistant;

    @GET
    public String ask(@QueryParam("q") String q) {
        return assistant.chat(q == null ? "How many pods there are in kube-system namespace?" : q);
    }
}