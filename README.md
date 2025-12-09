# DrKube
DrKube is a Java-based intelligent assistant designed to help diagnose and explore Kubernetes clusters using a modular, agent-driven architecture.
Built on top of the Model Context Protocol (MCP), LangChain4j, and Quarkus, this project demonstrates how AI agents can orchestrate multiple tools to answer complex cluster-related questions.

The assistant exposes a set of specialized tools (e.g., pod discovery, metrics extraction, storage inspection), which an LLM can invoke dynamically through MCP. By decomposing an analytical workflow into fine-grained atomic actions, DrKube showcases how agentic systems can reason about which tools to use and in what order.

You can find the original article about this example project at the following link: [Building Intelligent Kubernetes Assistants with MCP, LangChain4j, and Quarkus](https://fpaparoni.medium.com/building-intelligent-kubernetes-assistants-with-mcp-langchain4j-and-quarkus-8e9055fb9bd6)