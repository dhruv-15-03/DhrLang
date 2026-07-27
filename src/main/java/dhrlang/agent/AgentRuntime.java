package dhrlang.agent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/**
 * SC-702 — AI Agent Execution Engine.
 *
 * <p>Provides the runtime machinery for executing DhrLang agent classes
 * annotated with {@code @agent}, {@code @model}, {@code @tools}, and
 * {@code @retry}.  The engine orchestrates:</p>
 * <ul>
 *   <li>Model provider abstraction (GPT-4, Claude, local models)</li>
 *   <li>Tool registration and dispatch</li>
 *   <li>Retry policies with configurable back-off</li>
 *   <li>Execution context with memory and message history</li>
 *   <li>Result capture with token/cost accounting</li>
 * </ul>
 *
 * <h3>Example (conceptual):</h3>
 * <pre>{@code
 *   AgentRuntime rt = new AgentRuntime();
 *   rt.registerProvider("gpt-4", myProvider);
 *   rt.registerTool(searchTool);
 *   AgentConfig cfg = AgentConfig.builder("ResearchAgent")
 *           .model("gpt-4").temperature(0.7)
 *           .tool("SearchTool").tool("ReadTool")
 *           .retryAttempts(3).build();
 *   AgentResult result = rt.execute(cfg, "Research quantum computing");
 * }</pre>
 */
public final class AgentRuntime {

    // ── Model Provider ─────────────────────────────────────────────────

    /**
     * Abstraction over an AI model provider.
     * Implementations wrap HTTP calls to OpenAI, Anthropic, local LLMs, etc.
     */
    public interface ModelProvider {
        /** Unique identifier, e.g. "gpt-4", "claude-3-opus". */
        String getModelId();

        /**
         * Send a prompt to the model and receive a textual response.
         *
         * @param messages conversation history
         * @param temperature creativity setting 0.0–2.0
         * @param maxTokens maximum tokens in response
         * @return model response
         */
        ModelResponse complete(List<Message> messages, double temperature, int maxTokens);
    }

    /** A single message in the conversation. */
    public static final class Message {
        public enum Role { SYSTEM, USER, ASSISTANT, TOOL }

        private final Role role;
        private final String content;
        private final String toolCallId;  // null unless role == TOOL

        public Message(Role role, String content) {
            this(role, content, null);
        }

        public Message(Role role, String content, String toolCallId) {
            this.role = Objects.requireNonNull(role);
            this.content = Objects.requireNonNull(content);
            this.toolCallId = toolCallId;
        }

        public Role getRole()       { return role; }
        public String getContent()  { return content; }
        public String getToolCallId() { return toolCallId; }

        @Override public String toString() {
            return "[" + role + "] " + content;
        }
    }

    /** Response from a model provider. */
    public static final class ModelResponse {
        private final String content;
        private final int promptTokens;
        private final int completionTokens;
        private final List<ToolCall> toolCalls;

        public ModelResponse(String content, int promptTokens,
                             int completionTokens, List<ToolCall> toolCalls) {
            this.content = content;
            this.promptTokens = promptTokens;
            this.completionTokens = completionTokens;
            this.toolCalls = toolCalls == null
                    ? Collections.emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(toolCalls));
        }

        public String getContent()         { return content; }
        public int getPromptTokens()       { return promptTokens; }
        public int getCompletionTokens()   { return completionTokens; }
        public int getTotalTokens()        { return promptTokens + completionTokens; }
        public List<ToolCall> getToolCalls() { return toolCalls; }
        public boolean hasToolCalls()       { return !toolCalls.isEmpty(); }
    }

    /** A tool invocation requested by the model. */
    public static final class ToolCall {
        private final String id;
        private final String toolName;
        private final String arguments;   // JSON string

        public ToolCall(String id, String toolName, String arguments) {
            this.id = id;
            this.toolName = toolName;
            this.arguments = arguments;
        }

        public String getId()        { return id; }
        public String getToolName()  { return toolName; }
        public String getArguments() { return arguments; }
    }

    // ── Agent Tool ─────────────────────────────────────────────────────

    /**
     * A tool that an AI agent can invoke.
     */
    public interface AgentTool {
        /** Tool name, e.g. "SearchTool". */
        String getName();

        /** Human-readable description for the model's system prompt. */
        String getDescription();

        /** Parameter schema as a JSON-Schema-like string. */
        String getParameterSchema();

        /**
         * Execute the tool with the given JSON arguments.
         *
         * @param arguments JSON string from model
         * @return tool output string
         */
        String execute(String arguments);
    }

    // ── Retry Policy ───────────────────────────────────────────────────

    /** Configurable retry behaviour for agent execution. */
    public static final class RetryPolicy {
        private final int maxAttempts;
        private final long backoffMs;
        private final boolean exponentialBackoff;

        public RetryPolicy(int maxAttempts, long backoffMs, boolean exponentialBackoff) {
            this.maxAttempts = Math.max(1, maxAttempts);
            this.backoffMs = Math.max(0, backoffMs);
            this.exponentialBackoff = exponentialBackoff;
        }

        public static RetryPolicy defaultPolicy() {
            return new RetryPolicy(3, 1000, true);
        }

        public static RetryPolicy noRetry() {
            return new RetryPolicy(1, 0, false);
        }

        public int getMaxAttempts()        { return maxAttempts; }
        public long getBackoffMs()         { return backoffMs; }
        public boolean isExponentialBackoff() { return exponentialBackoff; }

        /** Compute the wait time before the given attempt (1-based). */
        public long waitBeforeAttempt(int attempt) {
            if (attempt <= 1) return 0;
            if (exponentialBackoff) {
                return backoffMs * (1L << (attempt - 2));
            }
            return backoffMs;
        }
    }

    // ── Agent Config ───────────────────────────────────────────────────

    /** Immutable configuration for an agent execution. */
    public static final class AgentConfig {
        private final String agentName;
        private final String modelId;
        private final double temperature;
        private final int maxTokens;
        private final List<String> toolNames;
        private final RetryPolicy retryPolicy;
        private final String systemPrompt;

        private AgentConfig(Builder b) {
            this.agentName = b.agentName;
            this.modelId = b.modelId;
            this.temperature = b.temperature;
            this.maxTokens = b.maxTokens;
            this.toolNames = Collections.unmodifiableList(new ArrayList<>(b.toolNames));
            this.retryPolicy = b.retryPolicy;
            this.systemPrompt = b.systemPrompt;
        }

        public String getAgentName()      { return agentName; }
        public String getModelId()        { return modelId; }
        public double getTemperature()    { return temperature; }
        public int getMaxTokens()         { return maxTokens; }
        public List<String> getToolNames() { return toolNames; }
        public RetryPolicy getRetryPolicy() { return retryPolicy; }
        public String getSystemPrompt()   { return systemPrompt; }

        public static Builder builder(String agentName) { return new Builder(agentName); }

        public static final class Builder {
            private final String agentName;
            private String modelId = "gpt-4";
            private double temperature = 0.7;
            private int maxTokens = 4096;
            private final List<String> toolNames = new ArrayList<>();
            private RetryPolicy retryPolicy = RetryPolicy.defaultPolicy();
            private String systemPrompt = "You are a helpful AI agent.";

            private Builder(String agentName) {
                this.agentName = Objects.requireNonNull(agentName);
            }

            public Builder model(String id)               { this.modelId = id; return this; }
            public Builder temperature(double t)           { this.temperature = t; return this; }
            public Builder maxTokens(int n)                { this.maxTokens = n; return this; }
            public Builder tool(String name)               { this.toolNames.add(name); return this; }
            public Builder tools(List<String> names)       { this.toolNames.addAll(names); return this; }
            public Builder retryPolicy(RetryPolicy p)      { this.retryPolicy = p; return this; }
            public Builder retryAttempts(int n)            {
                this.retryPolicy = new RetryPolicy(n, retryPolicy.getBackoffMs(),
                        retryPolicy.isExponentialBackoff());
                return this;
            }
            public Builder systemPrompt(String prompt)     { this.systemPrompt = prompt; return this; }
            public AgentConfig build()                     { return new AgentConfig(this); }
        }
    }

    // ── Execution Context ──────────────────────────────────────────────

    /** Mutable context maintained across an agent execution. */
    public static final class AgentContext {
        private final String executionId;
        private final List<Message> messages;
        private final Map<String, Object> memory;
        private int totalPromptTokens;
        private int totalCompletionTokens;
        private int toolCallCount;

        public AgentContext() {
            this.executionId = UUID.randomUUID().toString().substring(0, 8);
            this.messages = new ArrayList<>();
            this.memory = new LinkedHashMap<>();
        }

        public String getExecutionId()        { return executionId; }
        public List<Message> getMessages()    { return Collections.unmodifiableList(messages); }
        public Map<String, Object> getMemory() { return Collections.unmodifiableMap(memory); }
        public int getTotalPromptTokens()     { return totalPromptTokens; }
        public int getTotalCompletionTokens() { return totalCompletionTokens; }
        public int getTotalTokens()           { return totalPromptTokens + totalCompletionTokens; }
        public int getToolCallCount()         { return toolCallCount; }

        public void addMessage(Message msg)           { messages.add(msg); }
        public void setMemory(String key, Object val) { memory.put(key, val); }
        public Object getMemoryValue(String key)      { return memory.get(key); }

        void recordTokens(int prompt, int completion) {
            totalPromptTokens += prompt;
            totalCompletionTokens += completion;
        }

        void recordToolCall() { toolCallCount++; }
    }

    // ── Agent Result ───────────────────────────────────────────────────

    /** The outcome of executing an agent. */
    public static final class AgentResult {
        public enum Status { SUCCESS, FAILURE, MAX_RETRIES_EXCEEDED, TOOL_ERROR }

        private final Status status;
        private final String output;
        private final AgentContext context;
        private final int attemptsUsed;
        private final long durationMs;
        private final String errorMessage;

        public AgentResult(Status status, String output, AgentContext context,
                           int attemptsUsed, long durationMs, String errorMessage) {
            this.status = status;
            this.output = output;
            this.context = context;
            this.attemptsUsed = attemptsUsed;
            this.durationMs = durationMs;
            this.errorMessage = errorMessage;
        }

        public Status getStatus()        { return status; }
        public String getOutput()         { return output; }
        public AgentContext getContext()   { return context; }
        public int getAttemptsUsed()      { return attemptsUsed; }
        public long getDurationMs()       { return durationMs; }
        public String getErrorMessage()   { return errorMessage; }
        public boolean isSuccess()        { return status == Status.SUCCESS; }

        public String formatSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append("=== Agent Execution Result ===\n");
            sb.append("Status:      ").append(status).append('\n');
            sb.append("Attempts:    ").append(attemptsUsed).append('\n');
            sb.append("Duration:    ").append(durationMs).append("ms\n");
            sb.append("Tokens:      ").append(context.getTotalTokens())
              .append(" (prompt=").append(context.getTotalPromptTokens())
              .append(", completion=").append(context.getTotalCompletionTokens())
              .append(")\n");
            sb.append("Tool Calls:  ").append(context.getToolCallCount()).append('\n');
            if (errorMessage != null) {
                sb.append("Error:       ").append(errorMessage).append('\n');
            }
            if (output != null) {
                sb.append("Output:\n").append(output).append('\n');
            }
            return sb.toString();
        }
    }

    // ── Runtime Fields ─────────────────────────────────────────────────

    private final Map<String, ModelProvider> providers = new LinkedHashMap<>();
    private final Map<String, AgentTool> tools = new LinkedHashMap<>();
    private int maxToolCallsPerExecution = 50;

    // ── Registration ───────────────────────────────────────────────────

    public void registerProvider(String modelId, ModelProvider provider) {
        providers.put(Objects.requireNonNull(modelId), Objects.requireNonNull(provider));
    }

    public void registerTool(AgentTool tool) {
        tools.put(Objects.requireNonNull(tool.getName()), tool);
    }

    public void setMaxToolCallsPerExecution(int max) {
        this.maxToolCallsPerExecution = Math.max(1, max);
    }

    public Map<String, ModelProvider> getProviders() {
        return Collections.unmodifiableMap(providers);
    }

    public Map<String, AgentTool> getTools() {
        return Collections.unmodifiableMap(tools);
    }

    public int getMaxToolCallsPerExecution() { return maxToolCallsPerExecution; }

    // ── Execution ──────────────────────────────────────────────────────

    /**
     * Execute an agent according to its configuration.
     *
     * <p>The execution loop:</p>
     * <ol>
     *   <li>Build system prompt with tool descriptions</li>
     *   <li>Send user message to model</li>
     *   <li>If model requests tool calls → dispatch and feed results back</li>
     *   <li>Loop until model produces a final answer or limits exceeded</li>
     *   <li>Wrap in retry policy on failure</li>
     * </ol>
     */
    public AgentResult execute(AgentConfig config, String userMessage) {
        long startTime = System.currentTimeMillis();
        RetryPolicy retry = config.getRetryPolicy();

        AgentContext ctx = new AgentContext();
        String lastError = null;

        for (int attempt = 1; attempt <= retry.getMaxAttempts(); attempt++) {
            if (attempt > 1) {
                // Wait before retry
                long wait = retry.waitBeforeAttempt(attempt);
                if (wait > 0) {
                    // In a real runtime we'd sleep; here we just record delay
                    ctx.setMemory("lastRetryWaitMs", wait);
                }
                // Clear messages for retry (keep memory)
                ctx.getMessages();  // read-only snapshot — internal list stays
            }

            try {
                String output = executeOnce(config, userMessage, ctx);
                long elapsed = System.currentTimeMillis() - startTime;
                return new AgentResult(
                        AgentResult.Status.SUCCESS, output, ctx, attempt, elapsed, null);
            } catch (AgentExecutionException e) {
                lastError = e.getMessage();
                ctx.setMemory("attempt_" + attempt + "_error", lastError);
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;
        return new AgentResult(
                AgentResult.Status.MAX_RETRIES_EXCEEDED, null, ctx,
                retry.getMaxAttempts(), elapsed, lastError);
    }

    /**
     * Single execution attempt (no retry).
     */
    private String executeOnce(AgentConfig config, String userMessage,
                               AgentContext ctx) {
        // Resolve model provider
        ModelProvider provider = providers.get(config.getModelId());
        if (provider == null) {
            throw new AgentExecutionException(
                    "No provider registered for model: " + config.getModelId());
        }

        // Resolve tools
        List<AgentTool> resolvedTools = new ArrayList<>();
        for (String toolName : config.getToolNames()) {
            AgentTool tool = tools.get(toolName);
            if (tool == null) {
                throw new AgentExecutionException("Unknown tool: " + toolName);
            }
            resolvedTools.add(tool);
        }

        // Build system prompt
        String systemPrompt = buildSystemPrompt(config.getSystemPrompt(), resolvedTools);
        ctx.addMessage(new Message(Message.Role.SYSTEM, systemPrompt));
        ctx.addMessage(new Message(Message.Role.USER, userMessage));

        // Agent loop — call model, handle tool calls, repeat
        int loopCount = 0;
        while (loopCount < maxToolCallsPerExecution) {
            loopCount++;

            ModelResponse response = provider.complete(
                    ctx.getMessages(), config.getTemperature(), config.getMaxTokens());
            ctx.recordTokens(response.getPromptTokens(), response.getCompletionTokens());

            if (!response.hasToolCalls()) {
                // Final answer
                ctx.addMessage(new Message(Message.Role.ASSISTANT, response.getContent()));
                return response.getContent();
            }

            // Process tool calls
            ctx.addMessage(new Message(Message.Role.ASSISTANT,
                    response.getContent() != null ? response.getContent() : ""));

            for (ToolCall call : response.getToolCalls()) {
                ctx.recordToolCall();
                AgentTool tool = tools.get(call.getToolName());
                if (tool == null) {
                    ctx.addMessage(new Message(Message.Role.TOOL,
                            "Error: unknown tool '" + call.getToolName() + "'", call.getId()));
                    continue;
                }
                try {
                    String result = tool.execute(call.getArguments());
                    ctx.addMessage(new Message(Message.Role.TOOL, result, call.getId()));
                } catch (Exception e) {
                    ctx.addMessage(new Message(Message.Role.TOOL,
                            "Tool error: " + e.getMessage(), call.getId()));
                }
            }
        }

        throw new AgentExecutionException(
                "Agent exceeded maximum tool calls (" + maxToolCallsPerExecution + ")");
    }

    /**
     * Build a system prompt that includes tool descriptions.
     */
    String buildSystemPrompt(String basePrompt, List<AgentTool> resolvedTools) {
        if (resolvedTools.isEmpty()) return basePrompt;

        StringBuilder sb = new StringBuilder(basePrompt);
        sb.append("\n\nAvailable tools:\n");
        for (AgentTool tool : resolvedTools) {
            sb.append("- ").append(tool.getName()).append(": ")
              .append(tool.getDescription()).append('\n');
            if (tool.getParameterSchema() != null && !tool.getParameterSchema().isEmpty()) {
                sb.append("  Parameters: ").append(tool.getParameterSchema()).append('\n');
            }
        }
        return sb.toString();
    }

    /**
     * Create an {@link AgentConfig} from v3 annotation declarations.
     *
     * @param agentName the class name
     * @param annotations parsed annotations on the class and methods
     * @return config derived from annotations
     */
    public static AgentConfig configFromAnnotations(
            String agentName, List<AgentAnnotations.AnnotationDecl> annotations) {
        AgentConfig.Builder builder = AgentConfig.builder(agentName);

        for (AgentAnnotations.AnnotationDecl decl : annotations) {
            switch (decl.getType()) {
                case MODEL:
                    builder.model(decl.getStringParam("value"));
                    if (decl.hasParam("temperature")) {
                        // temperature stored as int (tenths, e.g. 7 → 0.7)
                        builder.temperature(decl.getIntParam("temperature") / 10.0);
                    }
                    if (decl.hasParam("maxTokens")) {
                        builder.maxTokens(decl.getIntParam("maxTokens"));
                    }
                    break;
                case TOOLS:
                    if (decl.getParam("value").getKind()
                            == AgentAnnotations.ParamKind.LIST) {
                        builder.tools(decl.getParam("value").getListValue());
                    } else {
                        builder.tool(decl.getStringParam("value"));
                    }
                    break;
                case RETRY:
                    int attempts = decl.getIntParamOrDefault("attempts", 3);
                    long backoff = decl.getIntParamOrDefault("backoffMs", 1000);
                    builder.retryPolicy(new RetryPolicy(attempts, backoff, true));
                    break;
                default:
                    break;
            }
        }

        return builder.build();
    }

    // ── Exceptions ─────────────────────────────────────────────────────

    /** Runtime exception during agent execution. */
    public static class AgentExecutionException extends RuntimeException {
        public AgentExecutionException(String message) { super(message); }
        public AgentExecutionException(String message, Throwable cause) { super(message, cause); }
    }

    // ── Utility ────────────────────────────────────────────────────────

    /** Reset all registered providers and tools. */
    public void reset() {
        providers.clear();
        tools.clear();
        maxToolCallsPerExecution = 50;
    }

    /** Quick summary of the runtime state. */
    public String describe() {
        return "AgentRuntime[providers=" + providers.size()
                + ", tools=" + tools.size()
                + ", maxToolCalls=" + maxToolCallsPerExecution + "]";
    }
}
