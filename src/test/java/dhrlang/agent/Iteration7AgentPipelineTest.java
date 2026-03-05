package dhrlang.agent;

import dhrlang.agent.AgentAnnotations.*;
import dhrlang.agent.AgentPlanner.*;
import dhrlang.agent.AgentRuntime.*;
import dhrlang.pipeline.*;
import dhrlang.pipeline.AgentPipelineIntegration.*;
import dhrlang.pipeline.PipelineConfig.*;
import dhrlang.pipeline.PipelineExecutor.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.BeforeEach;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Iteration 7 — AI Agent &amp; Data Pipeline Framework (v3.0 Foundation).
 *
 * <p>Tests cover all new classes in the {@code dhrlang.agent} and
 * {@code dhrlang.pipeline} packages: AgentAnnotations, AgentRuntime,
 * AgentPlanner, PipelineConfig, PipelineExecutor, and
 * AgentPipelineIntegration.</p>
 *
 * <p>Covers user stories SC-701 through SC-706.</p>
 */
class Iteration7AgentPipelineTest {

    // ════════════════════════════════════════════════════════════════════
    // SC-701: Agent Annotations (v3.0 Parameterized Annotation Model)
    // ════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("SC-701: AgentAnnotations")
    class AgentAnnotationsTests {

        // ── V3Annotation Enum ──

        @Test
        @DisplayName("All 8 v3 annotations are defined")
        void allAnnotationsDefined() {
            assertEquals(8, V3Annotation.values().length);
        }

        @Test
        @DisplayName("Agent annotations: AGENT, MODEL, TOOLS, RETRY")
        void agentAnnotationsGroup() {
            List<V3Annotation> agents = V3Annotation.agentAnnotations();
            assertEquals(4, agents.size());
            assertTrue(agents.contains(V3Annotation.AGENT));
            assertTrue(agents.contains(V3Annotation.MODEL));
            assertTrue(agents.contains(V3Annotation.TOOLS));
            assertTrue(agents.contains(V3Annotation.RETRY));
        }

        @Test
        @DisplayName("Pipeline annotations: PIPELINE, SCHEDULE, SOURCE, SINK")
        void pipelineAnnotationsGroup() {
            List<V3Annotation> pipelines = V3Annotation.pipelineAnnotations();
            assertEquals(4, pipelines.size());
            assertTrue(pipelines.contains(V3Annotation.PIPELINE));
            assertTrue(pipelines.contains(V3Annotation.SCHEDULE));
            assertTrue(pipelines.contains(V3Annotation.SOURCE));
            assertTrue(pipelines.contains(V3Annotation.SINK));
        }

        @Test
        @DisplayName("@agent syntax and properties")
        void agentAnnotation() {
            V3Annotation a = V3Annotation.AGENT;
            assertEquals("@agent", a.getSyntax());
            assertFalse(a.hasParams());
            assertTrue(a.getAllowedTargets().contains(AnnotationTarget.CLASS));
            assertTrue(a.isAgentAnnotation());
            assertFalse(a.isPipelineAnnotation());
        }

        @Test
        @DisplayName("@model has required 'value' param and optional temperature/maxTokens")
        void modelAnnotation() {
            V3Annotation m = V3Annotation.MODEL;
            assertEquals("@model", m.getSyntax());
            assertTrue(m.hasParams());
            assertTrue(m.getRequiredParams().contains("value"));
            assertTrue(m.getOptionalParams().contains("temperature"));
            assertTrue(m.getOptionalParams().contains("maxTokens"));
        }

        @Test
        @DisplayName("@tools allowed on CLASS and METHOD")
        void toolsAnnotation() {
            V3Annotation t = V3Annotation.TOOLS;
            assertTrue(t.getAllowedTargets().contains(AnnotationTarget.CLASS));
            assertTrue(t.getAllowedTargets().contains(AnnotationTarget.METHOD));
        }

        @Test
        @DisplayName("@retry has optional attempts/backoffMs/retryOn")
        void retryAnnotation() {
            V3Annotation r = V3Annotation.RETRY;
            assertEquals("@retry", r.getSyntax());
            assertTrue(r.hasParams());
            assertTrue(r.getRequiredParams().isEmpty());
            assertTrue(r.getOptionalParams().contains("attempts"));
            assertTrue(r.getOptionalParams().contains("backoffMs"));
            assertTrue(r.getOptionalParams().contains("retryOn"));
            assertTrue(r.getAllowedTargets().contains(AnnotationTarget.METHOD));
        }

        @Test
        @DisplayName("@source allowed only on FIELD")
        void sourceAnnotation() {
            V3Annotation s = V3Annotation.SOURCE;
            assertEquals(1, s.getAllowedTargets().size());
            assertTrue(s.getAllowedTargets().contains(AnnotationTarget.FIELD));
        }

        @Test
        @DisplayName("@sink allowed only on FIELD")
        void sinkAnnotation() {
            V3Annotation s = V3Annotation.SINK;
            assertTrue(s.getAllowedTargets().contains(AnnotationTarget.FIELD));
            assertTrue(s.getOptionalParams().contains("batchSize"));
        }

        @Test
        @DisplayName("fromSyntax resolves known annotations")
        void fromSyntax() {
            assertEquals(V3Annotation.AGENT, V3Annotation.fromSyntax("@agent"));
            assertEquals(V3Annotation.PIPELINE, V3Annotation.fromSyntax("@pipeline"));
            assertEquals(V3Annotation.SCHEDULE, V3Annotation.fromSyntax("@schedule"));
        }

        @Test
        @DisplayName("fromSyntax throws on unknown annotation")
        void fromSyntaxUnknown() {
            assertThrows(IllegalArgumentException.class,
                    () -> V3Annotation.fromSyntax("@unknown"));
        }

        // ── AnnotationParam ──

        @Test
        @DisplayName("String param")
        void stringParam() {
            AnnotationParam p = new AnnotationParam("value", "gpt-4");
            assertEquals("value", p.getName());
            assertEquals("gpt-4", p.getStringValue());
            assertEquals(ParamKind.STRING, p.getKind());
        }

        @Test
        @DisplayName("Int param")
        void intParam() {
            AnnotationParam p = new AnnotationParam("attempts", 5);
            assertEquals(5, p.getIntValue());
            assertEquals(ParamKind.INT, p.getKind());
        }

        @Test
        @DisplayName("Boolean param")
        void booleanParam() {
            AnnotationParam p = new AnnotationParam("enabled", true);
            assertTrue(p.getBooleanValue());
            assertEquals(ParamKind.BOOLEAN, p.getKind());
        }

        @Test
        @DisplayName("Identifier param")
        void identifierParam() {
            AnnotationParam p = AnnotationParam.identifier("tool", "SearchTool");
            assertEquals("SearchTool", p.getStringValue());
            assertEquals(ParamKind.IDENTIFIER, p.getKind());
        }

        @Test
        @DisplayName("List param")
        void listParam() {
            AnnotationParam p = AnnotationParam.list("value",
                    List.of("SearchTool", "ReadTool"));
            assertEquals(ParamKind.LIST, p.getKind());
            assertEquals(2, p.getListValue().size());
            assertEquals("SearchTool", p.getListValue().get(0));
        }

        @Test
        @DisplayName("getStringValue on int param throws")
        void stringOnIntThrows() {
            AnnotationParam p = new AnnotationParam("x", 42);
            assertThrows(IllegalStateException.class, p::getStringValue);
        }

        @Test
        @DisplayName("getIntValue on string param throws")
        void intOnStringThrows() {
            AnnotationParam p = new AnnotationParam("x", "hello");
            assertThrows(IllegalStateException.class, p::getIntValue);
        }

        @Test
        @DisplayName("getBooleanValue on list param throws")
        void boolOnListThrows() {
            AnnotationParam p = AnnotationParam.list("x", List.of("a"));
            assertThrows(IllegalStateException.class, p::getBooleanValue);
        }

        @Test
        @DisplayName("getListValue on string param throws")
        void listOnStringThrows() {
            AnnotationParam p = new AnnotationParam("x", "hello");
            assertThrows(IllegalStateException.class, p::getListValue);
        }

        @Test
        @DisplayName("Param toString shows key: value")
        void paramToString() {
            AnnotationParam p = new AnnotationParam("value", "gpt-4");
            assertTrue(p.toString().contains("value"));
            assertTrue(p.toString().contains("gpt-4"));
        }

        // ── AnnotationDecl ──

        @Test
        @DisplayName("Bare annotation decl has no params")
        void bareDecl() {
            AnnotationDecl decl = AgentAnnotations.bare(V3Annotation.AGENT);
            assertEquals(V3Annotation.AGENT, decl.getType());
            assertTrue(decl.getParams().isEmpty());
        }

        @Test
        @DisplayName("Shorthand annotation decl has value param")
        void shorthandDecl() {
            AnnotationDecl decl = AgentAnnotations.shorthand(V3Annotation.MODEL, "gpt-4");
            assertEquals(V3Annotation.MODEL, decl.getType());
            assertTrue(decl.hasParam("value"));
            assertEquals("gpt-4", decl.getStringParam("value"));
        }

        @Test
        @DisplayName("AnnotationDecl with multiple params")
        void multipleParams() {
            AnnotationDecl decl = new AnnotationDecl(V3Annotation.RETRY, List.of(
                    new AnnotationParam("attempts", 5),
                    new AnnotationParam("backoffMs", 2000)
            ));
            assertEquals(5, decl.getIntParam("attempts"));
            assertEquals(2000, decl.getIntParam("backoffMs"));
        }

        @Test
        @DisplayName("getIntParamOrDefault returns default when missing")
        void intParamOrDefault() {
            AnnotationDecl decl = AgentAnnotations.bare(V3Annotation.RETRY);
            assertEquals(3, decl.getIntParamOrDefault("attempts", 3));
        }

        @Test
        @DisplayName("getStringParamOrDefault returns default when missing")
        void stringParamOrDefault() {
            AnnotationDecl decl = AgentAnnotations.bare(V3Annotation.RETRY);
            assertEquals("default", decl.getStringParamOrDefault("foo", "default"));
        }

        @Test
        @DisplayName("getParam throws for unknown param")
        void getParamThrows() {
            AnnotationDecl decl = AgentAnnotations.bare(V3Annotation.AGENT);
            assertThrows(IllegalArgumentException.class, () -> decl.getParam("nonexistent"));
        }

        @Test
        @DisplayName("AnnotationDecl toString includes params")
        void declToString() {
            AnnotationDecl decl = AgentAnnotations.shorthand(V3Annotation.MODEL, "gpt-4");
            String s = decl.toString();
            assertTrue(s.contains("@model"));
            assertTrue(s.contains("gpt-4"));
        }

        // ── Validation ──

        @Test
        @DisplayName("Valid @agent on CLASS passes")
        void validAgentOnClass() {
            AnnotationDecl decl = AgentAnnotations.bare(V3Annotation.AGENT);
            List<ValidationProblem> problems =
                    AgentAnnotations.validate(decl, AnnotationTarget.CLASS);
            assertTrue(problems.isEmpty());
        }

        @Test
        @DisplayName("@agent on METHOD fails (ANN-001)")
        void agentOnMethodFails() {
            AnnotationDecl decl = AgentAnnotations.bare(V3Annotation.AGENT);
            List<ValidationProblem> problems =
                    AgentAnnotations.validate(decl, AnnotationTarget.METHOD);
            assertTrue(problems.stream().anyMatch(p -> "ANN-001".equals(p.getCode())));
        }

        @Test
        @DisplayName("@model without value fails (ANN-002)")
        void modelMissingValueFails() {
            AnnotationDecl decl = AgentAnnotations.bare(V3Annotation.MODEL);
            List<ValidationProblem> problems =
                    AgentAnnotations.validate(decl, AnnotationTarget.CLASS);
            assertTrue(problems.stream().anyMatch(p -> "ANN-002".equals(p.getCode())));
        }

        @Test
        @DisplayName("Unknown param produces warning (ANN-003)")
        void unknownParamWarning() {
            AnnotationDecl decl = new AnnotationDecl(V3Annotation.RETRY, List.of(
                    new AnnotationParam("unknownParam", 1)
            ));
            List<ValidationProblem> problems =
                    AgentAnnotations.validate(decl, AnnotationTarget.METHOD);
            assertTrue(problems.stream().anyMatch(p -> "ANN-003".equals(p.getCode())));
            assertEquals(ValidationProblem.Level.WARNING,
                    problems.stream().filter(p -> "ANN-003".equals(p.getCode()))
                            .findFirst().get().getLevel());
        }

        @Test
        @DisplayName("Params on param-less annotation fails (ANN-004)")
        void paramsOnParamless() {
            AnnotationDecl decl = new AnnotationDecl(V3Annotation.AGENT, List.of(
                    new AnnotationParam("x", "y")
            ));
            List<ValidationProblem> problems =
                    AgentAnnotations.validate(decl, AnnotationTarget.CLASS);
            assertTrue(problems.stream().anyMatch(p -> "ANN-004".equals(p.getCode())));
        }

        @Test
        @DisplayName("@source on FIELD passes")
        void sourceOnFieldPasses() {
            AnnotationDecl decl = AgentAnnotations.shorthand(V3Annotation.SOURCE,
                    "postgres://db/orders");
            List<ValidationProblem> problems =
                    AgentAnnotations.validate(decl, AnnotationTarget.FIELD);
            assertTrue(problems.isEmpty());
        }

        @Test
        @DisplayName("@source on CLASS fails (ANN-001)")
        void sourceOnClassFails() {
            AnnotationDecl decl = AgentAnnotations.shorthand(V3Annotation.SOURCE,
                    "postgres://db/orders");
            List<ValidationProblem> problems =
                    AgentAnnotations.validate(decl, AnnotationTarget.CLASS);
            assertTrue(problems.stream().anyMatch(p -> "ANN-001".equals(p.getCode())));
        }

        // ── Set Validation ──

        @Test
        @DisplayName("@agent + @pipeline conflict (ANN-010)")
        void agentPipelineConflict() {
            List<AnnotationDecl> set = List.of(
                    AgentAnnotations.bare(V3Annotation.AGENT),
                    AgentAnnotations.bare(V3Annotation.PIPELINE)
            );
            List<ValidationProblem> problems =
                    AgentAnnotations.validateSet(set, AnnotationTarget.CLASS);
            assertTrue(problems.stream().anyMatch(p -> "ANN-010".equals(p.getCode())));
        }

        @Test
        @DisplayName("@model without @agent fails (ANN-011)")
        void modelWithoutAgent() {
            List<AnnotationDecl> set = List.of(
                    AgentAnnotations.shorthand(V3Annotation.MODEL, "gpt-4")
            );
            List<ValidationProblem> problems =
                    AgentAnnotations.validateSet(set, AnnotationTarget.CLASS);
            assertTrue(problems.stream().anyMatch(p -> "ANN-011".equals(p.getCode())));
        }

        @Test
        @DisplayName("@tools without @agent fails (ANN-012)")
        void toolsWithoutAgent() {
            List<AnnotationDecl> set = List.of(
                    AgentAnnotations.shorthand(V3Annotation.TOOLS, "SearchTool")
            );
            List<ValidationProblem> problems =
                    AgentAnnotations.validateSet(set, AnnotationTarget.CLASS);
            assertTrue(problems.stream().anyMatch(p -> "ANN-012".equals(p.getCode())));
        }

        @Test
        @DisplayName("@schedule without @pipeline fails (ANN-013)")
        void scheduleWithoutPipeline() {
            List<AnnotationDecl> set = List.of(
                    AgentAnnotations.shorthand(V3Annotation.SCHEDULE, "0 0 * * *")
            );
            List<ValidationProblem> problems =
                    AgentAnnotations.validateSet(set, AnnotationTarget.CLASS);
            assertTrue(problems.stream().anyMatch(p -> "ANN-013".equals(p.getCode())));
        }

        @Test
        @DisplayName("Valid agent set: @agent + @model + @tools passes")
        void validAgentSet() {
            List<AnnotationDecl> set = List.of(
                    AgentAnnotations.bare(V3Annotation.AGENT),
                    AgentAnnotations.shorthand(V3Annotation.MODEL, "gpt-4"),
                    AgentAnnotations.shorthand(V3Annotation.TOOLS, "SearchTool")
            );
            List<ValidationProblem> problems =
                    AgentAnnotations.validateSet(set, AnnotationTarget.CLASS);
            // Should have no errors (only individual annotation checks)
            assertTrue(problems.stream()
                    .noneMatch(p -> p.getLevel() == ValidationProblem.Level.ERROR));
        }

        @Test
        @DisplayName("Valid pipeline set: @pipeline + @schedule")
        void validPipelineSet() {
            List<AnnotationDecl> set = List.of(
                    AgentAnnotations.bare(V3Annotation.PIPELINE),
                    AgentAnnotations.shorthand(V3Annotation.SCHEDULE, "0 0 * * *")
            );
            List<ValidationProblem> problems =
                    AgentAnnotations.validateSet(set, AnnotationTarget.CLASS);
            assertTrue(problems.stream()
                    .noneMatch(p -> p.getLevel() == ValidationProblem.Level.ERROR));
        }

        @Test
        @DisplayName("ValidationProblem toString includes level and code")
        void validationProblemToString() {
            ValidationProblem p = new ValidationProblem(
                    ValidationProblem.Level.ERROR, "ANN-001", "bad target");
            String s = p.toString();
            assertTrue(s.contains("ERROR"));
            assertTrue(s.contains("ANN-001"));
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // SC-702: Agent Runtime
    // ════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("SC-702: AgentRuntime")
    class AgentRuntimeTests {

        private AgentRuntime runtime;

        @BeforeEach
        void setUp() {
            runtime = new AgentRuntime();
        }

        // ── Message ──

        @Test
        @DisplayName("Message creation and getters")
        void messageCreation() {
            Message msg = new Message(Message.Role.USER, "Hello");
            assertEquals(Message.Role.USER, msg.getRole());
            assertEquals("Hello", msg.getContent());
            assertNull(msg.getToolCallId());
        }

        @Test
        @DisplayName("Message with tool call ID")
        void messageWithToolCallId() {
            Message msg = new Message(Message.Role.TOOL, "result", "tc_123");
            assertEquals("tc_123", msg.getToolCallId());
            assertEquals(Message.Role.TOOL, msg.getRole());
        }

        @Test
        @DisplayName("Message toString includes role")
        void messageToString() {
            Message msg = new Message(Message.Role.ASSISTANT, "Hi");
            assertTrue(msg.toString().contains("ASSISTANT"));
        }

        // ── ModelResponse ──

        @Test
        @DisplayName("ModelResponse without tool calls")
        void modelResponseNoTools() {
            ModelResponse resp = new ModelResponse("answer", 100, 50, null);
            assertEquals("answer", resp.getContent());
            assertEquals(100, resp.getPromptTokens());
            assertEquals(50, resp.getCompletionTokens());
            assertEquals(150, resp.getTotalTokens());
            assertFalse(resp.hasToolCalls());
            assertTrue(resp.getToolCalls().isEmpty());
        }

        @Test
        @DisplayName("ModelResponse with tool calls")
        void modelResponseWithTools() {
            ToolCall tc = new ToolCall("tc1", "SearchTool", "{\"q\":\"test\"}");
            ModelResponse resp = new ModelResponse("", 80, 20, List.of(tc));
            assertTrue(resp.hasToolCalls());
            assertEquals(1, resp.getToolCalls().size());
            assertEquals("SearchTool", resp.getToolCalls().get(0).getToolName());
        }

        // ── ToolCall ──

        @Test
        @DisplayName("ToolCall properties")
        void toolCallProperties() {
            ToolCall tc = new ToolCall("id1", "ReadTool", "{\"url\":\"http://x\"}");
            assertEquals("id1", tc.getId());
            assertEquals("ReadTool", tc.getToolName());
            assertEquals("{\"url\":\"http://x\"}", tc.getArguments());
        }

        // ── RetryPolicy ──

        @Test
        @DisplayName("Default retry policy: 3 attempts, 1000ms, exponential")
        void defaultRetryPolicy() {
            RetryPolicy p = RetryPolicy.defaultPolicy();
            assertEquals(3, p.getMaxAttempts());
            assertEquals(1000, p.getBackoffMs());
            assertTrue(p.isExponentialBackoff());
        }

        @Test
        @DisplayName("No retry policy: 1 attempt")
        void noRetryPolicy() {
            RetryPolicy p = RetryPolicy.noRetry();
            assertEquals(1, p.getMaxAttempts());
            assertEquals(0, p.getBackoffMs());
        }

        @Test
        @DisplayName("Exponential backoff calculation")
        void exponentialBackoff() {
            RetryPolicy p = new RetryPolicy(5, 100, true);
            assertEquals(0, p.waitBeforeAttempt(1));
            assertEquals(100, p.waitBeforeAttempt(2));  // 100 * 2^0
            assertEquals(200, p.waitBeforeAttempt(3));  // 100 * 2^1
            assertEquals(400, p.waitBeforeAttempt(4));  // 100 * 2^2
        }

        @Test
        @DisplayName("Linear backoff returns fixed wait")
        void linearBackoff() {
            RetryPolicy p = new RetryPolicy(5, 500, false);
            assertEquals(0, p.waitBeforeAttempt(1));
            assertEquals(500, p.waitBeforeAttempt(2));
            assertEquals(500, p.waitBeforeAttempt(3));
        }

        @Test
        @DisplayName("Negative maxAttempts clamped to 1")
        void negativeAttemptsClamped() {
            RetryPolicy p = new RetryPolicy(-5, 0, false);
            assertEquals(1, p.getMaxAttempts());
        }

        // ── AgentConfig ──

        @Test
        @DisplayName("AgentConfig builder defaults")
        void configDefaults() {
            AgentConfig cfg = AgentConfig.builder("TestAgent").build();
            assertEquals("TestAgent", cfg.getAgentName());
            assertEquals("gpt-4", cfg.getModelId());
            assertEquals(0.7, cfg.getTemperature(), 0.001);
            assertEquals(4096, cfg.getMaxTokens());
            assertTrue(cfg.getToolNames().isEmpty());
            assertNotNull(cfg.getRetryPolicy());
            assertNotNull(cfg.getSystemPrompt());
        }

        @Test
        @DisplayName("AgentConfig builder custom values")
        void configCustom() {
            AgentConfig cfg = AgentConfig.builder("MyAgent")
                    .model("claude-3")
                    .temperature(0.3)
                    .maxTokens(8192)
                    .tool("SearchTool")
                    .tool("ReadTool")
                    .retryAttempts(5)
                    .systemPrompt("Custom prompt")
                    .build();
            assertEquals("MyAgent", cfg.getAgentName());
            assertEquals("claude-3", cfg.getModelId());
            assertEquals(0.3, cfg.getTemperature(), 0.001);
            assertEquals(8192, cfg.getMaxTokens());
            assertEquals(2, cfg.getToolNames().size());
            assertEquals(5, cfg.getRetryPolicy().getMaxAttempts());
            assertEquals("Custom prompt", cfg.getSystemPrompt());
        }

        @Test
        @DisplayName("AgentConfig builder with tool list")
        void configToolsList() {
            AgentConfig cfg = AgentConfig.builder("A")
                    .tools(List.of("T1", "T2", "T3"))
                    .build();
            assertEquals(3, cfg.getToolNames().size());
        }

        // ── AgentContext ──

        @Test
        @DisplayName("AgentContext has execution ID")
        void contextExecutionId() {
            AgentContext ctx = new AgentContext();
            assertNotNull(ctx.getExecutionId());
            assertEquals(8, ctx.getExecutionId().length());
        }

        @Test
        @DisplayName("AgentContext messages and memory")
        void contextMessagesAndMemory() {
            AgentContext ctx = new AgentContext();
            ctx.addMessage(new Message(Message.Role.USER, "hi"));
            ctx.setMemory("key1", "val1");
            assertEquals(1, ctx.getMessages().size());
            assertEquals("val1", ctx.getMemoryValue("key1"));
        }

        @Test
        @DisplayName("AgentContext token tracking")
        void contextTokens() {
            AgentContext ctx = new AgentContext();
            ctx.recordTokens(100, 50);
            ctx.recordTokens(200, 100);
            assertEquals(300, ctx.getTotalPromptTokens());
            assertEquals(150, ctx.getTotalCompletionTokens());
            assertEquals(450, ctx.getTotalTokens());
        }

        @Test
        @DisplayName("AgentContext tool call counter")
        void contextToolCalls() {
            AgentContext ctx = new AgentContext();
            assertEquals(0, ctx.getToolCallCount());
            ctx.recordToolCall();
            ctx.recordToolCall();
            assertEquals(2, ctx.getToolCallCount());
        }

        // ── Registration ──

        @Test
        @DisplayName("Register and retrieve provider")
        void registerProvider() {
            runtime.registerProvider("test-model", createDummyProvider("test-model"));
            assertEquals(1, runtime.getProviders().size());
            assertTrue(runtime.getProviders().containsKey("test-model"));
        }

        @Test
        @DisplayName("Register and retrieve tool")
        void registerTool() {
            runtime.registerTool(createDummyTool("MyTool", "A test tool"));
            assertEquals(1, runtime.getTools().size());
            assertTrue(runtime.getTools().containsKey("MyTool"));
        }

        @Test
        @DisplayName("Max tool calls per execution settable")
        void maxToolCalls() {
            assertEquals(50, runtime.getMaxToolCallsPerExecution());
            runtime.setMaxToolCallsPerExecution(10);
            assertEquals(10, runtime.getMaxToolCallsPerExecution());
        }

        @Test
        @DisplayName("Reset clears providers and tools")
        void reset() {
            runtime.registerProvider("m", createDummyProvider("m"));
            runtime.registerTool(createDummyTool("T", "desc"));
            runtime.setMaxToolCallsPerExecution(5);
            runtime.reset();
            assertTrue(runtime.getProviders().isEmpty());
            assertTrue(runtime.getTools().isEmpty());
            assertEquals(50, runtime.getMaxToolCallsPerExecution());
        }

        @Test
        @DisplayName("Describe returns summary string")
        void describe() {
            String desc = runtime.describe();
            assertTrue(desc.contains("AgentRuntime"));
            assertTrue(desc.contains("providers=0"));
            assertTrue(desc.contains("tools=0"));
        }

        // ── Execution ──

        @Test
        @DisplayName("Execute with no provider fails gracefully")
        void executeNoProvider() {
            AgentConfig cfg = AgentConfig.builder("A")
                    .model("nonexistent")
                    .retryAttempts(1)
                    .build();
            AgentResult result = runtime.execute(cfg, "Hello");
            assertEquals(AgentResult.Status.MAX_RETRIES_EXCEEDED, result.getStatus());
            assertFalse(result.isSuccess());
            assertNotNull(result.getErrorMessage());
        }

        @Test
        @DisplayName("Execute with unknown tool fails")
        void executeUnknownTool() {
            runtime.registerProvider("gpt-4", createDummyProvider("gpt-4"));
            AgentConfig cfg = AgentConfig.builder("A")
                    .tool("NonExistent")
                    .retryAttempts(1)
                    .build();
            AgentResult result = runtime.execute(cfg, "Hello");
            assertEquals(AgentResult.Status.MAX_RETRIES_EXCEEDED, result.getStatus());
            assertTrue(result.getErrorMessage().contains("Unknown tool"));
        }

        @Test
        @DisplayName("Successful execution with simple provider")
        void successfulExecution() {
            runtime.registerProvider("gpt-4", createSimpleProvider());
            AgentConfig cfg = AgentConfig.builder("TestAgent")
                    .retryAttempts(1)
                    .build();
            AgentResult result = runtime.execute(cfg, "What is 2+2?");
            assertEquals(AgentResult.Status.SUCCESS, result.getStatus());
            assertTrue(result.isSuccess());
            assertNotNull(result.getOutput());
            assertEquals(1, result.getAttemptsUsed());
            assertTrue(result.getDurationMs() >= 0);
        }

        @Test
        @DisplayName("Execution with tools that require tool calls")
        void executionWithToolCalls() {
            // Provider that first asks for tool, then gives final answer
            runtime.registerProvider("gpt-4", createToolCallingProvider());
            runtime.registerTool(createDummyTool("SearchTool", "Search"));
            AgentConfig cfg = AgentConfig.builder("Agent")
                    .tool("SearchTool")
                    .retryAttempts(1)
                    .build();
            AgentResult result = runtime.execute(cfg, "Search for DhrLang");
            assertEquals(AgentResult.Status.SUCCESS, result.getStatus());
            assertTrue(result.getContext().getToolCallCount() > 0);
        }

        @Test
        @DisplayName("Result formatSummary contains key fields")
        void resultFormatSummary() {
            runtime.registerProvider("gpt-4", createSimpleProvider());
            AgentConfig cfg = AgentConfig.builder("TestAgent")
                    .retryAttempts(1).build();
            AgentResult result = runtime.execute(cfg, "Hello");
            String summary = result.formatSummary();
            assertTrue(summary.contains("Agent Execution Result"));
            assertTrue(summary.contains("Status"));
            assertTrue(summary.contains("Tokens"));
        }

        @Test
        @DisplayName("buildSystemPrompt includes tool descriptions")
        void systemPromptWithTools() {
            AgentTool tool = createDummyTool("SearchTool", "Search the web");
            String prompt = runtime.buildSystemPrompt("Base prompt", List.of(tool));
            assertTrue(prompt.contains("Base prompt"));
            assertTrue(prompt.contains("SearchTool"));
            assertTrue(prompt.contains("Search the web"));
        }

        @Test
        @DisplayName("buildSystemPrompt without tools returns base prompt")
        void systemPromptNoTools() {
            String prompt = runtime.buildSystemPrompt("Base only", List.of());
            assertEquals("Base only", prompt);
        }

        // ── Config from Annotations ──

        @Test
        @DisplayName("configFromAnnotations derives model and tools")
        void configFromAnnotations() {
            List<AnnotationDecl> annotations = List.of(
                    AgentAnnotations.shorthand(V3Annotation.MODEL, "claude-3"),
                    new AnnotationDecl(V3Annotation.TOOLS, List.of(
                            AnnotationParam.list("value", List.of("SearchTool", "ReadTool"))
                    )),
                    new AnnotationDecl(V3Annotation.RETRY, List.of(
                            new AnnotationParam("attempts", 5)
                    ))
            );
            AgentConfig cfg = AgentRuntime.configFromAnnotations("MyAgent", annotations);
            assertEquals("MyAgent", cfg.getAgentName());
            assertEquals("claude-3", cfg.getModelId());
            assertEquals(2, cfg.getToolNames().size());
            assertEquals(5, cfg.getRetryPolicy().getMaxAttempts());
        }

        @Test
        @DisplayName("configFromAnnotations with single tool string")
        void configFromAnnotationsSingleTool() {
            List<AnnotationDecl> annotations = List.of(
                    AgentAnnotations.shorthand(V3Annotation.MODEL, "gpt-4"),
                    AgentAnnotations.shorthand(V3Annotation.TOOLS, "SearchTool")
            );
            AgentConfig cfg = AgentRuntime.configFromAnnotations("A", annotations);
            assertEquals(1, cfg.getToolNames().size());
            assertEquals("SearchTool", cfg.getToolNames().get(0));
        }

        // ── Helper factories ──

        private ModelProvider createDummyProvider(String modelId) {
            return new ModelProvider() {
                @Override public String getModelId() { return modelId; }
                @Override public ModelResponse complete(List<Message> messages,
                        double temperature, int maxTokens) {
                    return new ModelResponse("dummy response", 10, 5, null);
                }
            };
        }

        private ModelProvider createSimpleProvider() {
            return new ModelProvider() {
                @Override public String getModelId() { return "gpt-4"; }
                @Override public ModelResponse complete(List<Message> messages,
                        double temperature, int maxTokens) {
                    return new ModelResponse("The answer is 4", 50, 10, null);
                }
            };
        }

        private ModelProvider createToolCallingProvider() {
            final int[] callCount = {0};
            return new ModelProvider() {
                @Override public String getModelId() { return "gpt-4"; }
                @Override public ModelResponse complete(List<Message> messages,
                        double temperature, int maxTokens) {
                    callCount[0]++;
                    if (callCount[0] == 1) {
                        // First call: request a tool call
                        ToolCall tc = new ToolCall("tc1", "SearchTool",
                                "{\"query\":\"DhrLang\"}");
                        return new ModelResponse("Let me search...", 30, 10,
                                List.of(tc));
                    }
                    // Second call: final answer
                    return new ModelResponse("DhrLang is a smart contract language",
                            40, 20, null);
                }
            };
        }

        private AgentTool createDummyTool(String name, String description) {
            return new AgentTool() {
                @Override public String getName() { return name; }
                @Override public String getDescription() { return description; }
                @Override public String getParameterSchema() { return "{}"; }
                @Override public String execute(String arguments) {
                    return "Tool result for " + name;
                }
            };
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // SC-703: Agent Planner
    // ════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("SC-703: AgentPlanner")
    class AgentPlannerTests {

        // ── Strategy Enum ──

        @Test
        @DisplayName("All 4 strategies defined")
        void allStrategiesDefined() {
            assertEquals(4, Strategy.values().length);
        }

        @Test
        @DisplayName("Strategy labels")
        void strategyLabels() {
            assertEquals("sequential", Strategy.SEQUENTIAL.getLabel());
            assertEquals("chain-of-thought", Strategy.CHAIN_OF_THOUGHT.getLabel());
            assertEquals("tree-of-thought", Strategy.TREE_OF_THOUGHT.getLabel());
            assertEquals("react", Strategy.REACT.getLabel());
        }

        @Test
        @DisplayName("Strategy fromLabel")
        void strategyFromLabel() {
            assertEquals(Strategy.REACT, Strategy.fromLabel("react"));
            assertEquals(Strategy.SEQUENTIAL, Strategy.fromLabel("sequential"));
        }

        @Test
        @DisplayName("Strategy fromLabel unknown throws")
        void strategyFromLabelUnknown() {
            assertThrows(IllegalArgumentException.class,
                    () -> Strategy.fromLabel("unknown"));
        }

        // ── StepStatus ──

        @Test
        @DisplayName("All 5 step statuses defined")
        void allStepStatusesDefined() {
            assertEquals(5, StepStatus.values().length);
        }

        // ── PlanStep ──

        @Test
        @DisplayName("PlanStep creation with all fields")
        void planStepCreation() {
            PlanStep step = new PlanStep(0, "Search for info", "SearchTool",
                    List.of());
            assertEquals(0, step.getId());
            assertEquals("Search for info", step.getDescription());
            assertEquals("SearchTool", step.getToolName());
            assertTrue(step.getDependsOn().isEmpty());
            assertEquals(StepStatus.PENDING, step.getStatus());
        }

        @Test
        @DisplayName("PlanStep simple constructor")
        void planStepSimple() {
            PlanStep step = new PlanStep(1, "Do something");
            assertEquals(1, step.getId());
            assertNull(step.getToolName());
        }

        @Test
        @DisplayName("PlanStep isTerminal for completed/failed/skipped")
        void planStepTerminal() {
            PlanStep step = new PlanStep(0, "test");
            assertFalse(step.isTerminal());
            step.setStatus(StepStatus.COMPLETED);
            assertTrue(step.isTerminal());

            PlanStep step2 = new PlanStep(1, "test2");
            step2.setStatus(StepStatus.FAILED);
            assertTrue(step2.isTerminal());

            PlanStep step3 = new PlanStep(2, "test3");
            step3.setStatus(StepStatus.SKIPPED);
            assertTrue(step3.isTerminal());
        }

        @Test
        @DisplayName("PlanStep setters: output, reasoning, duration")
        void planStepSetters() {
            PlanStep step = new PlanStep(0, "test");
            step.setOutput("result");
            step.setReasoning("because X");
            step.setDurationMs(123);
            assertEquals("result", step.getOutput());
            assertEquals("because X", step.getReasoning());
            assertEquals(123, step.getDurationMs());
        }

        @Test
        @DisplayName("PlanStep toString includes status and description")
        void planStepToString() {
            PlanStep step = new PlanStep(0, "Do thing");
            String s = step.toString();
            assertTrue(s.contains("PENDING"));
            assertTrue(s.contains("Do thing"));
        }

        // ── ExecutionPlan ──

        @Test
        @DisplayName("Sequential plan creates chained dependencies")
        void sequentialPlan() {
            ExecutionPlan plan = AgentPlanner.sequentialPlan("Test goal",
                    List.of("Step A", "Step B", "Step C"));
            assertEquals("Test goal", plan.getGoal());
            assertEquals(Strategy.SEQUENTIAL, plan.getStrategy());
            assertEquals(3, plan.getSteps().size());
            assertTrue(plan.getSteps().get(0).getDependsOn().isEmpty());
            assertEquals(List.of(0), plan.getSteps().get(1).getDependsOn());
            assertEquals(List.of(1), plan.getSteps().get(2).getDependsOn());
        }

        @Test
        @DisplayName("Plan progress starts at 0")
        void planProgressZero() {
            ExecutionPlan plan = AgentPlanner.sequentialPlan("Goal",
                    List.of("S1", "S2"));
            assertEquals(0.0, plan.progress(), 0.001);
        }

        @Test
        @DisplayName("Plan progress updates after step completion")
        void planProgressUpdate() {
            ExecutionPlan plan = AgentPlanner.sequentialPlan("Goal",
                    List.of("S1", "S2"));
            plan.getSteps().get(0).setStatus(StepStatus.COMPLETED);
            assertEquals(0.5, plan.progress(), 0.001);
        }

        @Test
        @DisplayName("Empty plan has progress 1.0")
        void emptyPlanProgress() {
            ExecutionPlan plan = AgentPlanner.sequentialPlan("Goal", List.of());
            assertEquals(1.0, plan.progress(), 0.001);
        }

        @Test
        @DisplayName("Plan isFinished when all steps terminal")
        void planIsFinished() {
            ExecutionPlan plan = AgentPlanner.sequentialPlan("Goal",
                    List.of("S1"));
            assertFalse(plan.isFinished());
            plan.getSteps().get(0).setStatus(StepStatus.COMPLETED);
            assertTrue(plan.isFinished());
        }

        @Test
        @DisplayName("Plan hasFailed when any step fails")
        void planHasFailed() {
            ExecutionPlan plan = AgentPlanner.sequentialPlan("Goal",
                    List.of("S1", "S2"));
            assertFalse(plan.hasFailed());
            plan.getSteps().get(0).setStatus(StepStatus.FAILED);
            assertTrue(plan.hasFailed());
        }

        @Test
        @DisplayName("Plan readySteps returns PENDING with deps met")
        void readySteps() {
            ExecutionPlan plan = AgentPlanner.sequentialPlan("Goal",
                    List.of("S1", "S2", "S3"));
            // Initially only S1 is ready (no deps)
            List<PlanStep> ready = plan.readySteps();
            assertEquals(1, ready.size());
            assertEquals(0, ready.get(0).getId());

            // Complete S1 → S2 should be ready
            plan.getSteps().get(0).setStatus(StepStatus.COMPLETED);
            ready = plan.readySteps();
            assertEquals(1, ready.size());
            assertEquals(1, ready.get(0).getId());
        }

        @Test
        @DisplayName("Plan findStep by ID")
        void findStep() {
            ExecutionPlan plan = AgentPlanner.sequentialPlan("Goal",
                    List.of("S1", "S2"));
            assertNotNull(plan.findStep(0));
            assertNotNull(plan.findStep(1));
            assertNull(plan.findStep(99));
        }

        @Test
        @DisplayName("Plan countByStatus")
        void countByStatus() {
            ExecutionPlan plan = AgentPlanner.sequentialPlan("Goal",
                    List.of("S1", "S2", "S3"));
            assertEquals(3, plan.countByStatus(StepStatus.PENDING));
            plan.getSteps().get(0).setStatus(StepStatus.COMPLETED);
            assertEquals(2, plan.countByStatus(StepStatus.PENDING));
            assertEquals(1, plan.countByStatus(StepStatus.COMPLETED));
        }

        @Test
        @DisplayName("Plan metadata")
        void planMetadata() {
            ExecutionPlan plan = AgentPlanner.sequentialPlan("Goal", List.of("S1"));
            plan.setMetadata("version", "1.0");
            assertEquals("1.0", plan.getMetadata().get("version"));
        }

        @Test
        @DisplayName("Plan formatPlan includes all info")
        void formatPlan() {
            ExecutionPlan plan = AgentPlanner.sequentialPlan("My goal",
                    List.of("Do X", "Do Y"));
            String formatted = plan.formatPlan();
            assertTrue(formatted.contains("My goal"));
            assertTrue(formatted.contains("sequential"));
            assertTrue(formatted.contains("Do X"));
            assertTrue(formatted.contains("Do Y"));
        }

        // ── Tool Plan ──

        @Test
        @DisplayName("Tool plan with explicit specs")
        void toolPlan() {
            List<StepSpec> specs = List.of(
                    new StepSpec(0, "Search", "SearchTool", List.of()),
                    new StepSpec(1, "Read", "ReadTool", List.of(0)),
                    new StepSpec(2, "Summarize", null, List.of(1))
            );
            ExecutionPlan plan = AgentPlanner.toolPlan("Research", Strategy.REACT, specs);
            assertEquals(Strategy.REACT, plan.getStrategy());
            assertEquals(3, plan.getSteps().size());
            assertEquals("SearchTool", plan.getSteps().get(0).getToolName());
        }

        // ── Plan Execution ──

        @Test
        @DisplayName("Execute sequential plan successfully")
        void executeSequential() {
            ExecutionPlan plan = AgentPlanner.sequentialPlan("Count",
                    List.of("Step 1", "Step 2", "Step 3"));

            ExecutionPlan result = AgentPlanner.execute(plan,
                    (step, p) -> new StepResult("Done: " + step.getDescription()));

            assertEquals(PlanStatus.COMPLETED, result.getPlanStatus());
            assertTrue(result.isFinished());
            assertFalse(result.hasFailed());
            for (PlanStep s : result.getSteps()) {
                assertEquals(StepStatus.COMPLETED, s.getStatus());
                assertTrue(s.getOutput().startsWith("Done:"));
            }
        }

        @Test
        @DisplayName("Execute plan with step failure → remaining skipped")
        void executeWithFailure() {
            ExecutionPlan plan = AgentPlanner.sequentialPlan("Fail test",
                    List.of("S1", "S2 - will fail", "S3"));

            ExecutionPlan result = AgentPlanner.execute(plan, (step, p) -> {
                if (step.getId() == 1) throw new RuntimeException("Boom");
                return new StepResult("OK");
            });

            assertEquals(PlanStatus.FAILED, result.getPlanStatus());
            assertEquals(StepStatus.COMPLETED, result.getSteps().get(0).getStatus());
            assertEquals(StepStatus.FAILED, result.getSteps().get(1).getStatus());
            assertEquals(StepStatus.SKIPPED, result.getSteps().get(2).getStatus());
        }

        @Test
        @DisplayName("Execute plan with reasoning")
        void executeWithReasoning() {
            ExecutionPlan plan = AgentPlanner.sequentialPlan("Reason",
                    List.of("Think", "Act"));
            AgentPlanner.execute(plan, (step, p) ->
                    new StepResult("output", "reasoning for " + step.getId()));
            assertEquals("reasoning for 0",
                    plan.getSteps().get(0).getReasoning());
        }

        // ── ReactLoop ──

        @Test
        @DisplayName("React loop terminates on FINISH action")
        void reactFinish() {
            List<ReactStep> trace = AgentPlanner.reactLoop(
                    "Find answer",
                    "Initial observation",
                    (goal, obs, history) -> {
                        if (history.size() >= 2) {
                            return new ThinkActionResult("Done thinking", "FINISH", null);
                        }
                        return new ThinkActionResult(
                                "Thinking step " + (history.size() + 1),
                                "search",
                                "query " + (history.size() + 1));
                    },
                    (action, input) -> "Result of " + action + " with " + input,
                    10
            );

            assertFalse(trace.isEmpty());
            assertEquals("FINISH", trace.get(trace.size() - 1).getAction());
        }

        @Test
        @DisplayName("React loop respects max iterations")
        void reactMaxIterations() {
            List<ReactStep> trace = AgentPlanner.reactLoop(
                    "Loop forever",
                    "start",
                    (goal, obs, history) ->
                            new ThinkActionResult("Thinking", "search", "x"),
                    (action, input) -> "result",
                    3
            );
            assertEquals(3, trace.size());
        }

        @Test
        @DisplayName("React step properties accessible")
        void reactStepProperties() {
            List<ReactStep> trace = AgentPlanner.reactLoop(
                    "Goal",
                    "obs1",
                    (goal, obs, history) -> {
                        if (history.isEmpty())
                            return new ThinkActionResult("thought1", "act1", "input1");
                        return new ThinkActionResult("done", "FINISH", null);
                    },
                    (action, input) -> "result1",
                    5
            );
            ReactStep first = trace.get(0);
            assertEquals(1, first.getIteration());
            assertEquals("obs1", first.getObservation());
            assertEquals("thought1", first.getThought());
            assertEquals("act1", first.getAction());
            assertEquals("result1", first.getActionResult());
        }

        @Test
        @DisplayName("React loop handles action errors")
        void reactActionError() {
            List<ReactStep> trace = AgentPlanner.reactLoop(
                    "Goal", "obs",
                    (goal, obs, history) -> {
                        if (history.size() >= 1)
                            return new ThinkActionResult("done", "FINISH", null);
                        return new ThinkActionResult("try", "badAction", "x");
                    },
                    (action, input) -> { throw new RuntimeException("Action failed"); },
                    5
            );
            assertTrue(trace.get(0).getActionResult().contains("Error"));
        }

        @Test
        @DisplayName("ReactStep toString")
        void reactStepToString() {
            ReactStep step = new ReactStep(1, "obs", "thought", "act", "result");
            String s = step.toString();
            assertTrue(s.contains("React"));
            assertTrue(s.contains("obs"));
        }

        // ── Chain of Thought ──

        @Test
        @DisplayName("Chain of thought reaches conclusion")
        void chainOfThought() {
            ChainOfThought cot = AgentPlanner.buildChainOfThought(
                    "What is 2+2?",
                    (goal, prev) -> {
                        if (prev.size() == 0)
                            return new ChainReasonerResult("2+2 involves addition", false);
                        if (prev.size() == 1)
                            return new ChainReasonerResult("2+2 = 4", true);
                        return new ChainReasonerResult("unreachable", true);
                    },
                    10
            );
            assertEquals(2, cot.getSteps().size());
            assertEquals("2+2 = 4", cot.getConclusion());
        }

        @Test
        @DisplayName("Chain of thought respects max steps")
        void chainOfThoughtMaxSteps() {
            ChainOfThought cot = AgentPlanner.buildChainOfThought(
                    "Think forever",
                    (goal, prev) -> new ChainReasonerResult(
                            "Step " + (prev.size() + 1), false),
                    5
            );
            assertEquals(5, cot.getSteps().size());
        }

        @Test
        @DisplayName("Chain of thought format includes steps")
        void chainOfThoughtFormat() {
            ChainOfThought cot = AgentPlanner.buildChainOfThought(
                    "Test",
                    (goal, prev) -> new ChainReasonerResult("Done", true),
                    5
            );
            String formatted = cot.format();
            assertTrue(formatted.contains("Step 1"));
            assertTrue(formatted.contains("Conclusion"));
        }

        @Test
        @DisplayName("Empty chain of thought has default conclusion")
        void emptyChainOfThought() {
            ChainOfThought cot = AgentPlanner.buildChainOfThought(
                    "Goal",
                    (goal, prev) -> new ChainReasonerResult("unreachable", true),
                    0
            );
            assertTrue(cot.getSteps().isEmpty());
            assertEquals("No conclusion reached", cot.getConclusion());
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // SC-704: Pipeline Config
    // ════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("SC-704: PipelineConfig")
    class PipelineConfigTests {

        // ── SourceType ──

        @Test
        @DisplayName("SourceType from URI")
        void sourceTypeFromUri() {
            assertEquals(SourceType.POSTGRES, SourceType.fromUri("postgres://db/orders"));
            assertEquals(SourceType.MYSQL, SourceType.fromUri("mysql://localhost/db"));
            assertEquals(SourceType.KAFKA, SourceType.fromUri("kafka://broker/topic"));
            assertEquals(SourceType.S3, SourceType.fromUri("s3://bucket/path"));
            assertEquals(SourceType.HTTP, SourceType.fromUri("http://api.example.com"));
            assertEquals(SourceType.CUSTOM, SourceType.fromUri("unknown://x"));
        }

        @Test
        @DisplayName("SourceType from URI without scheme returns CUSTOM")
        void sourceTypeNoScheme() {
            assertEquals(SourceType.CUSTOM, SourceType.fromUri("no-scheme"));
        }

        @Test
        @DisplayName("SourceType protocol string")
        void sourceTypeProtocol() {
            assertEquals("postgres", SourceType.POSTGRES.getProtocol());
            assertEquals("mongodb", SourceType.MONGODB.getProtocol());
        }

        // ── SourceConfig ──

        @Test
        @DisplayName("SourceConfig builder defaults")
        void sourceConfigDefaults() {
            SourceConfig src = SourceConfig.builder("orders", "postgres://db/orders").build();
            assertEquals("orders", src.getName());
            assertEquals("postgres://db/orders", src.getUri());
            assertEquals(SourceType.POSTGRES, src.getType());
            assertEquals("json", src.getFormat());
            assertNull(src.getSchema());
            assertTrue(src.getOptions().isEmpty());
        }

        @Test
        @DisplayName("SourceConfig builder with all options")
        void sourceConfigFull() {
            SourceConfig src = SourceConfig.builder("data", "s3://bucket/data")
                    .format("parquet")
                    .schema("public.orders")
                    .option("compression", "gzip")
                    .build();
            assertEquals("parquet", src.getFormat());
            assertEquals("public.orders", src.getSchema());
            assertEquals("gzip", src.getOptions().get("compression"));
        }

        @Test
        @DisplayName("SourceConfig toString")
        void sourceConfigToString() {
            SourceConfig src = SourceConfig.builder("orders", "postgres://db/orders").build();
            assertTrue(src.toString().contains("orders"));
            assertTrue(src.toString().contains("postgres"));
        }

        // ── SinkType ──

        @Test
        @DisplayName("SinkType from URI")
        void sinkTypeFromUri() {
            assertEquals(SinkType.SNOWFLAKE, SinkType.fromUri("snowflake://analytics"));
            assertEquals(SinkType.BIGQUERY, SinkType.fromUri("bigquery://project.dataset"));
            assertEquals(SinkType.KAFKA, SinkType.fromUri("kafka://broker/topic"));
            assertEquals(SinkType.CUSTOM, SinkType.fromUri("unknown://x"));
        }

        // ── SinkConfig ──

        @Test
        @DisplayName("SinkConfig builder defaults")
        void sinkConfigDefaults() {
            SinkConfig sink = SinkConfig.builder("output", "snowflake://analytics").build();
            assertEquals("output", sink.getName());
            assertEquals(SinkType.SNOWFLAKE, sink.getType());
            assertEquals("json", sink.getFormat());
            assertEquals(1000, sink.getBatchSize());
        }

        @Test
        @DisplayName("SinkConfig builder with custom batch size")
        void sinkConfigCustom() {
            SinkConfig sink = SinkConfig.builder("out", "s3://bucket/output")
                    .format("csv")
                    .batchSize(500)
                    .option("partitionBy", "date")
                    .build();
            assertEquals("csv", sink.getFormat());
            assertEquals(500, sink.getBatchSize());
            assertEquals("date", sink.getOptions().get("partitionBy"));
        }

        @Test
        @DisplayName("SinkConfig toString")
        void sinkConfigToString() {
            SinkConfig sink = SinkConfig.builder("output", "snowflake://analytics").build();
            assertTrue(sink.toString().contains("output"));
            assertTrue(sink.toString().contains("snowflake"));
        }

        // ── ScheduleConfig ──

        @Test
        @DisplayName("Valid cron expression parsed")
        void validCron() {
            ScheduleConfig sc = new ScheduleConfig("0 0 * * *");
            assertEquals("0", sc.getMinute());
            assertEquals("0", sc.getHour());
            assertEquals("*", sc.getDayOfMonth());
            assertEquals("*", sc.getMonth());
            assertEquals("*", sc.getDayOfWeek());
            assertEquals("UTC", sc.getTimezone());
        }

        @Test
        @DisplayName("Cron with custom timezone")
        void cronTimezone() {
            ScheduleConfig sc = new ScheduleConfig("30 14 * * 1-5", "US/Eastern");
            assertEquals("US/Eastern", sc.getTimezone());
            assertEquals("30", sc.getMinute());
            assertEquals("14", sc.getHour());
        }

        @Test
        @DisplayName("Invalid cron expression throws")
        void invalidCron() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ScheduleConfig("bad cron"));
        }

        @Test
        @DisplayName("Predefined daily schedule")
        void dailySchedule() {
            ScheduleConfig sc = ScheduleConfig.daily();
            assertEquals("0 0 * * *", sc.getCronExpression());
            assertTrue(sc.describe().contains("Daily at midnight"));
        }

        @Test
        @DisplayName("Predefined hourly schedule")
        void hourlySchedule() {
            ScheduleConfig sc = ScheduleConfig.hourly();
            assertEquals("0 * * * *", sc.getCronExpression());
        }

        @Test
        @DisplayName("Predefined everyFiveMinutes")
        void everyFiveMinutes() {
            ScheduleConfig sc = ScheduleConfig.everyFiveMinutes();
            assertEquals("*/5 * * * *", sc.getCronExpression());
        }

        @Test
        @DisplayName("Predefined weekdaysAt9")
        void weekdaysAt9() {
            ScheduleConfig sc = ScheduleConfig.weekdaysAt9();
            assertEquals("0 9 * * 1-5", sc.getCronExpression());
        }

        @Test
        @DisplayName("Schedule describe for every-minute")
        void describeEveryMinute() {
            ScheduleConfig sc = new ScheduleConfig("* * * * *");
            assertTrue(sc.describe().contains("Every minute"));
        }

        @Test
        @DisplayName("Schedule describe fallback for complex cron")
        void describeComplex() {
            ScheduleConfig sc = new ScheduleConfig("30 14 * * 1-5");
            assertTrue(sc.describe().contains("Cron"));
        }

        @Test
        @DisplayName("ScheduleConfig toString")
        void scheduleToString() {
            ScheduleConfig sc = ScheduleConfig.daily();
            assertTrue(sc.toString().contains("0 0 * * *"));
        }

        // ── StageConfig ──

        @Test
        @DisplayName("StageConfig creation")
        void stageConfig() {
            StageConfig stage = new StageConfig("filter_recent", StageKind.FILTER,
                    "o.date >= today()");
            assertEquals("filter_recent", stage.getName());
            assertEquals(StageKind.FILTER, stage.getKind());
            assertEquals("o.date >= today()", stage.getExpression());
            assertTrue(stage.getParams().isEmpty());
        }

        @Test
        @DisplayName("StageConfig with params")
        void stageConfigWithParams() {
            StageConfig stage = new StageConfig("agg", StageKind.AGGREGATE, "sum(amount)",
                    Map.of("groupBy", "category"));
            assertEquals("category", stage.getParams().get("groupBy"));
        }

        @Test
        @DisplayName("StageKind has 8 kinds")
        void stageKindCount() {
            assertEquals(8, StageKind.values().length);
        }

        // ── PipelineConfig (top-level) ──

        @Test
        @DisplayName("PipelineConfig builder with schedule, sources, sinks, stages")
        void fullPipelineConfig() {
            PipelineConfig config = PipelineConfig.builder("SalesAnalytics")
                    .schedule("0 0 * * *")
                    .source("orders", "postgres://db/orders")
                    .sink("output", "snowflake://analytics")
                    .stage(new StageConfig("filter", StageKind.FILTER, "o.active == true"))
                    .stage(new StageConfig("aggregate", StageKind.AGGREGATE, "sum(amount)"))
                    .metadata("version", "1.0")
                    .build();
            assertEquals("SalesAnalytics", config.getPipelineName());
            assertTrue(config.isScheduled());
            assertEquals(1, config.getSources().size());
            assertEquals(1, config.getSinks().size());
            assertEquals(2, config.getStages().size());
            assertEquals("1.0", config.getMetadata().get("version"));
        }

        @Test
        @DisplayName("Pipeline without schedule is not scheduled")
        void notScheduled() {
            PipelineConfig config = PipelineConfig.builder("Adhoc")
                    .source("s", "http://api")
                    .sink("out", "console://stdout")
                    .build();
            assertFalse(config.isScheduled());
        }

        @Test
        @DisplayName("Find source by name")
        void findSource() {
            PipelineConfig config = PipelineConfig.builder("P")
                    .source("orders", "postgres://db/orders")
                    .source("customers", "postgres://db/customers")
                    .sink("out", "console://stdout")
                    .build();
            assertNotNull(config.findSource("orders"));
            assertNotNull(config.findSource("customers"));
            assertNull(config.findSource("nonexistent"));
        }

        @Test
        @DisplayName("Find sink by name")
        void findSink() {
            PipelineConfig config = PipelineConfig.builder("P")
                    .source("s", "http://api")
                    .sink("primary", "snowflake://db")
                    .sink("backup", "s3://bucket")
                    .build();
            assertNotNull(config.findSink("primary"));
            assertNotNull(config.findSink("backup"));
            assertNull(config.findSink("none"));
        }

        // ── Validation ──

        @Test
        @DisplayName("Valid pipeline passes validation")
        void validPipeline() {
            PipelineConfig config = PipelineConfig.builder("P")
                    .source("s", "postgres://db")
                    .sink("out", "console://stdout")
                    .build();
            assertTrue(config.validate().isEmpty());
        }

        @Test
        @DisplayName("No sources fails validation")
        void noSources() {
            PipelineConfig config = PipelineConfig.builder("P")
                    .sink("out", "console://stdout")
                    .build();
            List<String> problems = config.validate();
            assertTrue(problems.stream().anyMatch(p -> p.contains("no sources")));
        }

        @Test
        @DisplayName("No sinks fails validation")
        void noSinks() {
            PipelineConfig config = PipelineConfig.builder("P")
                    .source("s", "http://api")
                    .build();
            List<String> problems = config.validate();
            assertTrue(problems.stream().anyMatch(p -> p.contains("no sinks")));
        }

        @Test
        @DisplayName("Duplicate source names detected")
        void duplicateSourceNames() {
            PipelineConfig config = PipelineConfig.builder("P")
                    .source("orders", "postgres://db1")
                    .source("orders", "postgres://db2")
                    .sink("out", "console://stdout")
                    .build();
            List<String> problems = config.validate();
            assertTrue(problems.stream().anyMatch(p -> p.contains("Duplicate source")));
        }

        @Test
        @DisplayName("Duplicate sink names detected")
        void duplicateSinkNames() {
            PipelineConfig config = PipelineConfig.builder("P")
                    .source("s", "http://api")
                    .sink("output", "snowflake://x")
                    .sink("output", "s3://y")
                    .build();
            List<String> problems = config.validate();
            assertTrue(problems.stream().anyMatch(p -> p.contains("Duplicate sink")));
        }

        @Test
        @DisplayName("formatSummary includes all sections")
        void formatSummary() {
            PipelineConfig config = PipelineConfig.builder("AnalyticsPipeline")
                    .schedule("0 0 * * *")
                    .source("orders", "postgres://db/orders")
                    .sink("output", "snowflake://analytics")
                    .stage(new StageConfig("filter", StageKind.FILTER, "active"))
                    .metadata("owner", "team-data")
                    .build();
            String summary = config.formatSummary();
            assertTrue(summary.contains("AnalyticsPipeline"));
            assertTrue(summary.contains("Sources"));
            assertTrue(summary.contains("Sinks"));
            assertTrue(summary.contains("Stages"));
            assertTrue(summary.contains("Metadata"));
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // SC-705: Pipeline Executor
    // ════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("SC-705: PipelineExecutor")
    class PipelineExecutorTests {

        private PipelineExecutor executor;

        @BeforeEach
        void setUp() {
            executor = new PipelineExecutor();
        }

        // ── DataRecord ──

        @Test
        @DisplayName("DataRecord creation and field access")
        void dataRecordBasics() {
            DataRecord r = PipelineExecutor.record("name", "Alice", "age", 30);
            assertEquals("Alice", r.getString("name"));
            assertEquals(30, r.getInt("age"));
            assertTrue(r.hasField("name"));
            assertFalse(r.hasField("missing"));
        }

        @Test
        @DisplayName("DataRecord getDouble")
        void dataRecordDouble() {
            DataRecord r = PipelineExecutor.record("price", 9.99);
            assertEquals(9.99, r.getDouble("price"), 0.001);
        }

        @Test
        @DisplayName("DataRecord getInt on non-number throws")
        void dataRecordIntThrows() {
            DataRecord r = PipelineExecutor.record("name", "text");
            assertThrows(IllegalStateException.class, () -> r.getInt("name"));
        }

        @Test
        @DisplayName("DataRecord with adds field")
        void dataRecordWith() {
            DataRecord r = PipelineExecutor.record("a", 1);
            DataRecord r2 = r.with("b", 2);
            assertTrue(r2.hasField("a"));
            assertTrue(r2.hasField("b"));
            assertFalse(r.hasField("b")); // original unchanged
        }

        @Test
        @DisplayName("DataRecord project selects specific fields")
        void dataRecordProject() {
            DataRecord r = PipelineExecutor.record("a", 1, "b", 2, "c", 3);
            DataRecord projected = r.project(List.of("a", "c"));
            assertTrue(projected.hasField("a"));
            assertFalse(projected.hasField("b"));
            assertTrue(projected.hasField("c"));
        }

        @Test
        @DisplayName("DataRecord toString")
        void dataRecordToString() {
            DataRecord r = PipelineExecutor.record("x", 1);
            assertTrue(r.toString().contains("x"));
        }

        // ── DataBatch ──

        @Test
        @DisplayName("DataBatch empty")
        void emptyBatch() {
            DataBatch batch = DataBatch.empty();
            assertTrue(batch.isEmpty());
            assertEquals(0, batch.size());
        }

        @Test
        @DisplayName("DataBatch of records")
        void batchOfRecords() {
            DataBatch batch = DataBatch.of(
                    PipelineExecutor.record("x", 1),
                    PipelineExecutor.record("x", 2)
            );
            assertEquals(2, batch.size());
            assertEquals(1, batch.get(0).getInt("x"));
        }

        @Test
        @DisplayName("DataBatch toString shows count")
        void batchToString() {
            DataBatch batch = PipelineExecutor.batch(
                    PipelineExecutor.record("a", 1));
            assertTrue(batch.toString().contains("1 records"));
        }

        // ── Transform: Filter ──

        @Test
        @DisplayName("Filter removes non-matching records")
        void filterTransform() {
            DataBatch input = PipelineExecutor.batch(
                    PipelineExecutor.record("age", 20),
                    PipelineExecutor.record("age", 35),
                    PipelineExecutor.record("age", 15)
            );
            Transform filter = Transform.filter("adults",
                    r -> r.getInt("age") >= 18);
            DataBatch result = filter.apply(input);
            assertEquals(2, result.size());
        }

        // ── Transform: Map ──

        @Test
        @DisplayName("Map transforms all records")
        void mapTransform() {
            DataBatch input = PipelineExecutor.batch(
                    PipelineExecutor.record("name", "alice"),
                    PipelineExecutor.record("name", "bob")
            );
            Transform map = Transform.map("uppercase",
                    r -> r.with("name", r.getString("name").toUpperCase()));
            DataBatch result = map.apply(input);
            assertEquals("ALICE", result.get(0).getString("name"));
            assertEquals("BOB", result.get(1).getString("name"));
        }

        // ── Transform: Limit ──

        @Test
        @DisplayName("Limit truncates batch")
        void limitTransform() {
            DataBatch input = PipelineExecutor.batch(
                    PipelineExecutor.record("i", 1),
                    PipelineExecutor.record("i", 2),
                    PipelineExecutor.record("i", 3)
            );
            Transform limit = Transform.limit("top2", 2);
            DataBatch result = limit.apply(input);
            assertEquals(2, result.size());
        }

        // ── Transform: Sort ──

        @Test
        @DisplayName("Sort ascending by key")
        void sortAscending() {
            DataBatch input = PipelineExecutor.batch(
                    PipelineExecutor.record("val", 30),
                    PipelineExecutor.record("val", 10),
                    PipelineExecutor.record("val", 20)
            );
            Transform sort = Transform.sort("byVal", "val", true);
            DataBatch result = sort.apply(input);
            assertEquals(10, result.get(0).getInt("val"));
            assertEquals(20, result.get(1).getInt("val"));
            assertEquals(30, result.get(2).getInt("val"));
        }

        @Test
        @DisplayName("Sort descending")
        void sortDescending() {
            DataBatch input = PipelineExecutor.batch(
                    PipelineExecutor.record("val", 10),
                    PipelineExecutor.record("val", 30),
                    PipelineExecutor.record("val", 20)
            );
            Transform sort = Transform.sort("byValDesc", "val", false);
            DataBatch result = sort.apply(input);
            assertEquals(30, result.get(0).getInt("val"));
        }

        // ── Transform: Distinct ──

        @Test
        @DisplayName("Distinct removes duplicate records")
        void distinctTransform() {
            DataBatch input = PipelineExecutor.batch(
                    PipelineExecutor.record("x", 1),
                    PipelineExecutor.record("x", 2),
                    PipelineExecutor.record("x", 1)
            );
            Transform distinct = Transform.distinct("dedup");
            DataBatch result = distinct.apply(input);
            assertEquals(2, result.size());
        }

        // ── Transform: Project ──

        @Test
        @DisplayName("Project selects specific fields from all records")
        void projectTransform() {
            DataBatch input = PipelineExecutor.batch(
                    PipelineExecutor.record("a", 1, "b", 2, "c", 3)
            );
            Transform proj = Transform.project("pickAB", List.of("a", "b"));
            DataBatch result = proj.apply(input);
            assertTrue(result.get(0).hasField("a"));
            assertTrue(result.get(0).hasField("b"));
            assertFalse(result.get(0).hasField("c"));
        }

        // ── Transform: Aggregate ──

        @Test
        @DisplayName("Aggregate COUNT")
        void aggregateCount() {
            DataBatch input = PipelineExecutor.batch(
                    PipelineExecutor.record("cat", "A", "val", 10),
                    PipelineExecutor.record("cat", "B", "val", 20),
                    PipelineExecutor.record("cat", "A", "val", 30)
            );
            AggregateSpec spec = new AggregateSpec("cat", "val",
                    AggregateFunction.COUNT, "count");
            Transform agg = Transform.aggregate("count_by_cat", spec);
            DataBatch result = agg.apply(input);
            assertEquals(2, result.size()); // two groups: A and B
        }

        @Test
        @DisplayName("Aggregate SUM")
        void aggregateSum() {
            DataBatch input = PipelineExecutor.batch(
                    PipelineExecutor.record("cat", "A", "val", 10.0),
                    PipelineExecutor.record("cat", "A", "val", 20.0),
                    PipelineExecutor.record("cat", "B", "val", 5.0)
            );
            AggregateSpec spec = new AggregateSpec("cat", "val",
                    AggregateFunction.SUM, "total");
            Transform agg = Transform.aggregate("sum_by_cat", spec);
            DataBatch result = agg.apply(input);
            // Find group A
            DataRecord groupA = null;
            for (DataRecord r : result.getRecords()) {
                if ("A".equals(r.get("cat"))) groupA = r;
            }
            assertNotNull(groupA);
            assertEquals(30.0, groupA.getDouble("total"), 0.001);
        }

        @Test
        @DisplayName("Aggregate AVG")
        void aggregateAvg() {
            DataBatch input = PipelineExecutor.batch(
                    PipelineExecutor.record("val", 10.0),
                    PipelineExecutor.record("val", 20.0),
                    PipelineExecutor.record("val", 30.0)
            );
            AggregateSpec spec = new AggregateSpec(null, "val",
                    AggregateFunction.AVG, "average");
            Transform agg = Transform.aggregate("avg_all", spec);
            DataBatch result = agg.apply(input);
            assertEquals(1, result.size());
            assertEquals(20.0, result.get(0).getDouble("average"), 0.001);
        }

        @Test
        @DisplayName("Aggregate MIN")
        void aggregateMin() {
            DataBatch input = PipelineExecutor.batch(
                    PipelineExecutor.record("val", 30.0),
                    PipelineExecutor.record("val", 10.0),
                    PipelineExecutor.record("val", 20.0)
            );
            AggregateSpec spec = new AggregateSpec(null, "val",
                    AggregateFunction.MIN, "minimum");
            Transform agg = Transform.aggregate("min_all", spec);
            DataBatch result = agg.apply(input);
            assertEquals(10.0, result.get(0).getDouble("minimum"), 0.001);
        }

        @Test
        @DisplayName("Aggregate MAX")
        void aggregateMax() {
            DataBatch input = PipelineExecutor.batch(
                    PipelineExecutor.record("val", 10.0),
                    PipelineExecutor.record("val", 50.0),
                    PipelineExecutor.record("val", 30.0)
            );
            AggregateSpec spec = new AggregateSpec(null, "val",
                    AggregateFunction.MAX, "maximum");
            Transform agg = Transform.aggregate("max_all", spec);
            DataBatch result = agg.apply(input);
            assertEquals(50.0, result.get(0).getDouble("maximum"), 0.001);
        }

        @Test
        @DisplayName("Transform getOp and getName")
        void transformProperties() {
            Transform f = Transform.filter("myFilter", r -> true);
            assertEquals("myFilter", f.getName());
            assertEquals(TransformOp.FILTER, f.getOp());
        }

        // ── Pipeline Execution ──

        @Test
        @DisplayName("Execute full pipeline: filter → map → limit")
        void executeFullPipeline() {
            PipelineConfig config = PipelineConfig.builder("TestPipeline")
                    .source("s", "http://api")
                    .sink("out", "console://stdout")
                    .build();

            DataBatch input = PipelineExecutor.batch(
                    PipelineExecutor.record("name", "Alice", "age", 25),
                    PipelineExecutor.record("name", "Bob", "age", 17),
                    PipelineExecutor.record("name", "Charlie", "age", 30),
                    PipelineExecutor.record("name", "Diana", "age", 22)
            );

            List<Transform> transforms = List.of(
                    Transform.filter("adults", r -> r.getInt("age") >= 18),
                    Transform.map("addTag", r -> r.with("tag", "adult")),
                    Transform.limit("top2", 2)
            );

            PipelineResult result = executor.execute(config, transforms, input);
            assertEquals(PipelineResult.Status.SUCCESS, result.getStatus());
            assertTrue(result.isSuccess());
            assertEquals(3, result.getStageResults().size());
            assertEquals(2, result.getFinalOutput().size());
            assertNotNull(result.getExecutionId());
            assertNotNull(result.getStartTime());
        }

        @Test
        @DisplayName("Pipeline with failing stage stops on error")
        void pipelineFailStopOnError() {
            PipelineConfig config = PipelineConfig.builder("FailPipeline")
                    .source("s", "http://x")
                    .sink("out", "console://stdout")
                    .build();

            DataBatch input = PipelineExecutor.batch(
                    PipelineExecutor.record("x", "not-a-number")
            );

            List<Transform> transforms = List.of(
                    Transform.map("crash", r -> {
                        r.getInt("x"); // will throw
                        return r;
                    }),
                    Transform.filter("after", r -> true)
            );

            executor.setStopOnError(true);
            PipelineResult result = executor.execute(config, transforms, input);
            assertEquals(PipelineResult.Status.FAILED, result.getStatus());
            assertEquals(1, result.getStageResults().size()); // stopped at first stage
        }

        @Test
        @DisplayName("Pipeline with stopOnError=false continues")
        void pipelineContinueOnError() {
            PipelineConfig config = PipelineConfig.builder("ContinuePipeline")
                    .source("s", "http://x")
                    .sink("out", "console://stdout")
                    .build();

            DataBatch input = PipelineExecutor.batch(
                    PipelineExecutor.record("x", "not-a-number")
            );

            List<Transform> transforms = List.of(
                    Transform.map("crash", r -> {
                        r.getInt("x");
                        return r;
                    }),
                    Transform.filter("after", r -> true)
            );

            executor.setStopOnError(false);
            PipelineResult result = executor.execute(config, transforms, input);
            assertEquals(PipelineResult.Status.PARTIAL_SUCCESS, result.getStatus());
            assertEquals(2, result.getStageResults().size());
        }

        @Test
        @DisplayName("executeTransforms convenience method")
        void executeTransforms() {
            DataBatch input = PipelineExecutor.batch(
                    PipelineExecutor.record("v", 1),
                    PipelineExecutor.record("v", 2),
                    PipelineExecutor.record("v", 3)
            );
            DataBatch result = executor.executeTransforms(
                    List.of(Transform.filter("gt1", r -> r.getInt("v") > 1)),
                    input
            );
            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("StageResult properties")
        void stageResultProperties() {
            StageResult sr = new StageResult("filter", StageResult.Status.SUCCESS,
                    DataBatch.empty(), 10, 5, 42, null);
            assertEquals("filter", sr.getStageName());
            assertEquals(StageResult.Status.SUCCESS, sr.getStatus());
            assertEquals(10, sr.getInputCount());
            assertEquals(5, sr.getOutputCount());
            assertEquals(42, sr.getDurationMs());
            assertNull(sr.getErrorMessage());
        }

        @Test
        @DisplayName("PipelineResult formatSummary includes key sections")
        void pipelineResultFormat() {
            PipelineConfig config = PipelineConfig.builder("P")
                    .source("s", "http://x").sink("out", "console://stdout").build();
            DataBatch input = PipelineExecutor.batch(PipelineExecutor.record("x", 1));
            PipelineResult result = executor.execute(config,
                    List.of(Transform.filter("f", r -> true)), input);
            String summary = result.formatSummary();
            assertTrue(summary.contains("Pipeline Execution"));
            assertTrue(summary.contains("Status"));
            assertTrue(summary.contains("Duration"));
        }

        @Test
        @DisplayName("record() utility throws on odd args")
        void recordOddArgs() {
            assertThrows(IllegalArgumentException.class,
                    () -> PipelineExecutor.record("key"));
        }

        @Test
        @DisplayName("stopOnError default is true")
        void stopOnErrorDefault() {
            assertTrue(executor.isStopOnError());
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // SC-706: Agent-Pipeline Integration
    // ════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("SC-706: AgentPipelineIntegration")
    class AgentPipelineIntegrationTests {

        private AgentRuntime runtime;

        @BeforeEach
        void setUp() {
            runtime = new AgentRuntime();
        }

        // ── AgentTransform ──

        @Test
        @DisplayName("AgentTransform PER_RECORD adds agent_processed field")
        void agentTransformPerRecord() {
            AgentTransform at = new AgentTransform("Classify", "Classify items",
                    AgentTransform.TransformMode.PER_RECORD);
            DataBatch input = PipelineExecutor.batch(
                    PipelineExecutor.record("item", "laptop"),
                    PipelineExecutor.record("item", "banana")
            );
            DataBatch result = at.apply(input, runtime);
            assertEquals(2, result.size());
            assertTrue((Boolean) result.get(0).get("agent_processed"));
            assertEquals("Classify", result.get(0).getString("agent_transform"));
        }

        @Test
        @DisplayName("AgentTransform BATCH adds batch_size field")
        void agentTransformBatch() {
            AgentTransform at = new AgentTransform("Enrich", "Enrich batch",
                    AgentTransform.TransformMode.BATCH);
            DataBatch input = PipelineExecutor.batch(
                    PipelineExecutor.record("x", 1),
                    PipelineExecutor.record("x", 2),
                    PipelineExecutor.record("x", 3)
            );
            DataBatch result = at.apply(input, runtime);
            assertEquals(3, result.size());
            assertTrue((Boolean) result.get(0).get("agent_batch_processed"));
            assertEquals(3, result.get(0).getInt("batch_size"));
        }

        @Test
        @DisplayName("AgentTransform toString")
        void agentTransformToString() {
            AgentTransform at = new AgentTransform("T", "desc",
                    AgentTransform.TransformMode.PER_RECORD);
            assertTrue(at.toString().contains("T"));
            assertTrue(at.toString().contains("PER_RECORD"));
        }

        @Test
        @DisplayName("AgentTransform properties")
        void agentTransformProperties() {
            AgentTransform at = new AgentTransform("Summarize", "Sum up data",
                    AgentTransform.TransformMode.BATCH);
            assertEquals("Summarize", at.getName());
            assertEquals("Sum up data", at.getAgentPrompt());
            assertEquals(AgentTransform.TransformMode.BATCH, at.getMode());
        }

        // ── QualityGate ──

        @Test
        @DisplayName("QualityGate all rules pass → PASS")
        void qualityGatePass() {
            QualityRule rule = new QualityRule("QR-001", "Non-empty batch",
                    QualityGate.Verdict.FAIL, batch -> !batch.isEmpty());
            QualityGate gate = new QualityGate("BasicGate", "Basic checks",
                    List.of(rule));
            DataBatch data = PipelineExecutor.batch(PipelineExecutor.record("x", 1));
            QualityGateResult result = gate.evaluate(data);
            assertEquals(QualityGate.Verdict.PASS, result.getVerdict());
            assertTrue(result.passed());
        }

        @Test
        @DisplayName("QualityGate rule fails → overall FAIL")
        void qualityGateFail() {
            QualityRule rule = new QualityRule("QR-002", "Max 5 records",
                    QualityGate.Verdict.FAIL, batch -> batch.size() <= 5);
            QualityGate gate = new QualityGate("SizeGate", "Size check",
                    List.of(rule));
            // 10 records
            List<DataRecord> records = new ArrayList<>();
            for (int i = 0; i < 10; i++) records.add(PipelineExecutor.record("i", i));
            DataBatch data = new DataBatch(records);
            QualityGateResult result = gate.evaluate(data);
            assertEquals(QualityGate.Verdict.FAIL, result.getVerdict());
            assertFalse(result.passed());
        }

        @Test
        @DisplayName("QualityGate rule warns → overall WARN")
        void qualityGateWarn() {
            QualityRule warnRule = new QualityRule("QR-003", "Latest data",
                    QualityGate.Verdict.WARN, batch -> false);  // always fails to WARN
            QualityGate gate = new QualityGate("WarnGate", "Warning check",
                    List.of(warnRule));
            DataBatch data = PipelineExecutor.batch(PipelineExecutor.record("x", 1));
            QualityGateResult result = gate.evaluate(data);
            assertEquals(QualityGate.Verdict.WARN, result.getVerdict());
        }

        @Test
        @DisplayName("QualityGate rule exception → FAIL")
        void qualityGateException() {
            QualityRule rule = new QualityRule("QR-ERR", "Crash rule",
                    QualityGate.Verdict.FAIL, batch -> {
                throw new RuntimeException("Boom");
            });
            QualityGate gate = new QualityGate("ErrorGate", "Errors",
                    List.of(rule));
            DataBatch data = PipelineExecutor.batch(PipelineExecutor.record("x", 1));
            QualityGateResult result = gate.evaluate(data);
            assertEquals(QualityGate.Verdict.FAIL, result.getVerdict());
            assertTrue(result.getRuleResults().get(0).getReasoning().contains("error"));
        }

        @Test
        @DisplayName("QualityGate multiple rules: most severe wins")
        void qualityGateMultipleRules() {
            QualityRule passRule = new QualityRule("QR-P", "passes",
                    QualityGate.Verdict.FAIL, batch -> true);
            QualityRule warnRule = new QualityRule("QR-W", "warns",
                    QualityGate.Verdict.WARN, batch -> false);
            QualityGate gate = new QualityGate("MultiGate", "Multiple rules",
                    List.of(passRule, warnRule));
            DataBatch data = PipelineExecutor.batch(PipelineExecutor.record("x", 1));
            QualityGateResult result = gate.evaluate(data);
            assertEquals(QualityGate.Verdict.WARN, result.getVerdict());
            assertEquals(2, result.getRuleResults().size());
        }

        @Test
        @DisplayName("QualityGateResult formatReport")
        void qualityGateResultFormat() {
            QualityRule rule = new QualityRule("QR-001", "test",
                    QualityGate.Verdict.FAIL, batch -> true);
            QualityGate gate = new QualityGate("G", "Desc", List.of(rule));
            DataBatch data = PipelineExecutor.batch(PipelineExecutor.record("x", 1));
            QualityGateResult result = gate.evaluate(data);
            String report = result.formatReport();
            assertTrue(report.contains("Quality Gate"));
            assertTrue(report.contains("PASS"));
        }

        @Test
        @DisplayName("QualityGate properties")
        void qualityGateProperties() {
            QualityGate gate = new QualityGate("G", "Gate desc", List.of());
            assertEquals("G", gate.getName());
            assertEquals("Gate desc", gate.getDescription());
            assertTrue(gate.getRules().isEmpty());
        }

        @Test
        @DisplayName("QualityRuleResult properties")
        void qualityRuleResultProps() {
            QualityRuleResult rr = new QualityRuleResult("QR-1", "desc",
                    QualityGate.Verdict.PASS, "OK");
            assertEquals("QR-1", rr.getRuleId());
            assertEquals("desc", rr.getDescription());
            assertEquals(QualityGate.Verdict.PASS, rr.getVerdict());
            assertEquals("OK", rr.getReasoning());
        }

        // ── AnomalyDetector ──

        @Test
        @DisplayName("NULL_CHECK detects missing fields")
        void anomalyNullCheck() {
            AnomalyDetector detector = new AnomalyDetector("NullDetect",
                    "price", 0, AnomalyDetector.DetectionMethod.NULL_CHECK);
            DataBatch data = PipelineExecutor.batch(
                    PipelineExecutor.record("price", 10),
                    PipelineExecutor.record("name", "no-price"),  // missing price
                    PipelineExecutor.record("price", 20)
            );
            AnomalyReport report = detector.detect(data);
            assertTrue(report.hasAnomalies());
            assertEquals(1, report.getAnomalies().size());
            assertEquals(1, report.getAnomalies().get(0).getRecordIndex());
        }

        @Test
        @DisplayName("ABSOLUTE_RANGE detects outliers")
        void anomalyAbsoluteRange() {
            AnomalyDetector detector = new AnomalyDetector("RangeDetect",
                    "val", 5.0, AnomalyDetector.DetectionMethod.ABSOLUTE_RANGE);
            // Mean = (10+11+12+100)/4 = 33.25, so records outside [28.25, 38.25]
            DataBatch data = PipelineExecutor.batch(
                    PipelineExecutor.record("val", 10),
                    PipelineExecutor.record("val", 11),
                    PipelineExecutor.record("val", 12),
                    PipelineExecutor.record("val", 100)
            );
            AnomalyReport report = detector.detect(data);
            assertTrue(report.hasAnomalies());
            assertTrue(report.getAnomalies().size() >= 1);
        }

        @Test
        @DisplayName("Z_SCORE detects statistical outliers")
        void anomalyZScore() {
            AnomalyDetector detector = new AnomalyDetector("ZDetect",
                    "val", 2.0, AnomalyDetector.DetectionMethod.Z_SCORE);
            // Many normal values + one extreme outlier to ensure z > 2.0
            List<DataRecord> records = new ArrayList<>();
            for (int i = 0; i < 20; i++) records.add(PipelineExecutor.record("val", 10.0));
            records.add(PipelineExecutor.record("val", 1000.0));  // extreme outlier
            DataBatch data = new DataBatch(records);
            AnomalyReport report = detector.detect(data);
            assertTrue(report.hasAnomalies());
        }

        @Test
        @DisplayName("AnomalyReport with no anomalies")
        void noAnomalies() {
            AnomalyDetector detector = new AnomalyDetector("D",
                    "val", 100.0, AnomalyDetector.DetectionMethod.ABSOLUTE_RANGE);
            DataBatch data = PipelineExecutor.batch(
                    PipelineExecutor.record("val", 10),
                    PipelineExecutor.record("val", 11)
            );
            AnomalyReport report = detector.detect(data);
            assertFalse(report.hasAnomalies());
            assertEquals(0.0, report.anomalyRate(), 0.001);
        }

        @Test
        @DisplayName("AnomalyReport anomalyRate")
        void anomalyRate() {
            AnomalyDetector detector = new AnomalyDetector("D",
                    "val", 0, AnomalyDetector.DetectionMethod.NULL_CHECK);
            DataBatch data = PipelineExecutor.batch(
                    PipelineExecutor.record("val", 1),
                    PipelineExecutor.record("other", 2),  // missing val
                    PipelineExecutor.record("val", 3),
                    PipelineExecutor.record("other", 4)   // missing val
            );
            AnomalyReport report = detector.detect(data);
            assertEquals(0.5, report.anomalyRate(), 0.001);
        }

        @Test
        @DisplayName("AnomalyReport formatReport")
        void anomalyReportFormat() {
            AnomalyDetector detector = new AnomalyDetector("D",
                    "val", 0, AnomalyDetector.DetectionMethod.NULL_CHECK);
            DataBatch data = PipelineExecutor.batch(
                    PipelineExecutor.record("other", 1));
            AnomalyReport report = detector.detect(data);
            String formatted = report.formatReport();
            assertTrue(formatted.contains("Anomaly Report"));
            assertTrue(formatted.contains("Records scanned"));
        }

        @Test
        @DisplayName("Anomaly properties")
        void anomalyProperties() {
            Anomaly a = new Anomaly(5, "price", 999.0, "Too high");
            assertEquals(5, a.getRecordIndex());
            assertEquals("price", a.getField());
            assertEquals(999.0, a.getValue());
            assertEquals("Too high", a.getReason());
        }

        @Test
        @DisplayName("AnomalyDetector properties")
        void anomalyDetectorProperties() {
            AnomalyDetector d = new AnomalyDetector("D", "f", 2.0,
                    AnomalyDetector.DetectionMethod.Z_SCORE);
            assertEquals("D", d.getName());
            assertEquals("f", d.getField());
            assertEquals(2.0, d.getThreshold(), 0.001);
            assertEquals(AnomalyDetector.DetectionMethod.Z_SCORE, d.getMethod());
        }

        @Test
        @DisplayName("AnomalyDetector toString")
        void anomalyDetectorToString() {
            AnomalyDetector d = new AnomalyDetector("D", "f", 2.0,
                    AnomalyDetector.DetectionMethod.Z_SCORE);
            String s = d.toString();
            assertTrue(s.contains("Z_SCORE"));
        }

        // ── IntelligentPipeline ──

        @Test
        @DisplayName("IntelligentPipeline successful execution")
        void intelligentPipelineSuccess() {
            PipelineConfig config = PipelineConfig.builder("SmartETL")
                    .source("s", "postgres://db")
                    .sink("out", "snowflake://dw")
                    .build();

            AgentTransform at = new AgentTransform("Classify", "Classify data",
                    AgentTransform.TransformMode.PER_RECORD);
            QualityRule passRule = new QualityRule("QR-1", "non-empty",
                    QualityGate.Verdict.FAIL, batch -> !batch.isEmpty());
            QualityGate gate = new QualityGate("BasicGate", "Basic", List.of(passRule));
            AnomalyDetector detector = new AnomalyDetector("NullCheck",
                    "x", 0, AnomalyDetector.DetectionMethod.NULL_CHECK);

            IntelligentPipeline ip = IntelligentPipeline.builder("SmartETL")
                    .pipelineConfig(config)
                    .agentRuntime(runtime)
                    .addAgentTransform(at)
                    .addQualityGate(gate)
                    .addAnomalyDetector(detector)
                    .build();

            DataBatch input = PipelineExecutor.batch(
                    PipelineExecutor.record("x", 1),
                    PipelineExecutor.record("x", 2)
            );
            IntelligentPipelineResult result = ip.execute(input);
            assertTrue(result.isSuccess());
            assertEquals(IntelligentPipelineResult.Status.SUCCESS, result.getStatus());
            assertEquals(2, result.getOutput().size());
            assertFalse(result.getQualityGateResults().isEmpty());
            assertFalse(result.getAnomalyReports().isEmpty());
            assertFalse(result.getExecutionLog().isEmpty());
            assertNull(result.getErrorMessage());
        }

        @Test
        @DisplayName("IntelligentPipeline halts on quality gate failure")
        void intelligentPipelineQualityGateFail() {
            PipelineConfig config = PipelineConfig.builder("P")
                    .source("s", "http://x")
                    .sink("out", "console://stdout")
                    .build();

            QualityRule failRule = new QualityRule("QR-FAIL", "Always fails",
                    QualityGate.Verdict.FAIL, batch -> false);
            QualityGate gate = new QualityGate("FailGate", "Fails", List.of(failRule));

            IntelligentPipeline ip = IntelligentPipeline.builder("FailPipeline")
                    .pipelineConfig(config)
                    .agentRuntime(runtime)
                    .addQualityGate(gate)
                    .build();

            DataBatch input = PipelineExecutor.batch(PipelineExecutor.record("x", 1));
            IntelligentPipelineResult result = ip.execute(input);
            assertEquals(IntelligentPipelineResult.Status.QUALITY_GATE_FAILED,
                    result.getStatus());
            assertFalse(result.isSuccess());
            assertNotNull(result.getErrorMessage());
            assertTrue(result.getErrorMessage().contains("FailGate"));
        }

        @Test
        @DisplayName("IntelligentPipeline with no gates or detectors succeeds")
        void intelligentPipelineMinimal() {
            PipelineConfig config = PipelineConfig.builder("Minimal")
                    .source("s", "http://x")
                    .sink("out", "console://stdout")
                    .build();

            IntelligentPipeline ip = IntelligentPipeline.builder("Minimal")
                    .pipelineConfig(config)
                    .agentRuntime(runtime)
                    .build();

            DataBatch input = PipelineExecutor.batch(PipelineExecutor.record("x", 1));
            IntelligentPipelineResult result = ip.execute(input);
            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("IntelligentPipeline builder properties")
        void intelligentPipelineProperties() {
            PipelineConfig config = PipelineConfig.builder("P")
                    .source("s", "http://x").sink("out", "console://stdout").build();
            IntelligentPipeline ip = IntelligentPipeline.builder("TestPipe")
                    .pipelineConfig(config)
                    .agentRuntime(runtime)
                    .build();
            assertEquals("TestPipe", ip.getName());
            assertEquals(config, ip.getConfig());
            assertEquals(runtime, ip.getAgentRuntime());
            assertTrue(ip.getAgentTransforms().isEmpty());
            assertTrue(ip.getQualityGates().isEmpty());
            assertTrue(ip.getAnomalyDetectors().isEmpty());
        }

        @Test
        @DisplayName("IntelligentPipelineResult formatSummary")
        void intelligentPipelineResultFormat() {
            PipelineConfig config = PipelineConfig.builder("P")
                    .source("s", "http://x").sink("out", "console://stdout").build();

            IntelligentPipeline ip = IntelligentPipeline.builder("SummaryTest")
                    .pipelineConfig(config)
                    .agentRuntime(runtime)
                    .build();

            DataBatch input = PipelineExecutor.batch(PipelineExecutor.record("x", 1));
            IntelligentPipelineResult result = ip.execute(input);
            String summary = result.formatSummary();
            assertTrue(summary.contains("Intelligent Pipeline"));
            assertTrue(summary.contains("SummaryTest"));
            assertTrue(summary.contains("Execution Log"));
        }

        @Test
        @DisplayName("IntelligentPipelineResult has timing info")
        void intelligentPipelineResultTiming() {
            PipelineConfig config = PipelineConfig.builder("P")
                    .source("s", "http://x").sink("out", "console://stdout").build();
            IntelligentPipeline ip = IntelligentPipeline.builder("TimingTest")
                    .pipelineConfig(config).agentRuntime(runtime).build();
            DataBatch input = PipelineExecutor.batch(PipelineExecutor.record("x", 1));
            IntelligentPipelineResult result = ip.execute(input);
            assertTrue(result.getDurationMs() >= 0);
            assertNotNull(result.getStartTime());
            assertEquals("TimingTest", result.getPipelineName());
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // Integration Tests
    // ════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Integration: End-to-End Workflows")
    class IntegrationTests {

        @Test
        @DisplayName("Full workflow: annotations → config → agent → pipeline → quality check")
        void fullAgentPipelineWorkflow() {
            // 1. Define annotations (only class-level ones for set validation)
            List<AnnotationDecl> classAnnotations = List.of(
                    AgentAnnotations.bare(V3Annotation.AGENT),
                    AgentAnnotations.shorthand(V3Annotation.MODEL, "gpt-4"),
                    new AnnotationDecl(V3Annotation.TOOLS, List.of(
                            AnnotationParam.list("value", List.of("SearchTool", "ReadTool"))
                    ))
            );

            // 2. Validate class-level annotations
            List<ValidationProblem> problems =
                    AgentAnnotations.validateSet(classAnnotations, AnnotationTarget.CLASS);
            assertTrue(problems.stream()
                    .noneMatch(p -> p.getLevel() == ValidationProblem.Level.ERROR));

            // @retry is method-level, validate separately
            AnnotationDecl retryDecl = new AnnotationDecl(V3Annotation.RETRY, List.of(
                    new AnnotationParam("attempts", 3)
            ));
            assertTrue(AgentAnnotations.validate(retryDecl, AnnotationTarget.METHOD).isEmpty());

            // 3. Build agent config (pass all annotations including @retry)
            List<AnnotationDecl> allAnnotations = new ArrayList<>(classAnnotations);
            allAnnotations.add(retryDecl);
            AgentConfig config = AgentRuntime.configFromAnnotations("ResearchAgent",
                    allAnnotations);
            assertEquals("ResearchAgent", config.getAgentName());
            assertEquals("gpt-4", config.getModelId());
            assertEquals(2, config.getToolNames().size());
            assertEquals(3, config.getRetryPolicy().getMaxAttempts());

            // 4. Build pipeline config
            PipelineConfig pipeConfig = PipelineConfig.builder("DataPipeline")
                    .schedule("0 0 * * *")
                    .source("orders", "postgres://db/orders")
                    .sink("output", "snowflake://analytics")
                    .build();
            assertTrue(pipeConfig.validate().isEmpty());

            // 5. Execute pipeline with transforms
            PipelineExecutor executor = new PipelineExecutor();
            DataBatch input = PipelineExecutor.batch(
                    PipelineExecutor.record("product", "DhrLang Pro", "price", 99.99),
                    PipelineExecutor.record("product", "DhrLang Basic", "price", 29.99),
                    PipelineExecutor.record("product", "DhrLang Edu", "price", 0.0)
            );

            PipelineResult pResult = executor.execute(pipeConfig,
                    List.of(
                            Transform.filter("paid_only",
                                    r -> r.getDouble("price") > 0),
                            Transform.sort("by_price", "price", false)
                    ), input);

            assertTrue(pResult.isSuccess());
            assertEquals(2, pResult.getFinalOutput().size());

            // 6. Quality gate check
            QualityRule nonEmpty = new QualityRule("QR-001", "Non-empty output",
                    QualityGate.Verdict.FAIL, batch -> !batch.isEmpty());
            QualityGate gate = new QualityGate("OutputGate", "Check output",
                    List.of(nonEmpty));
            QualityGateResult gResult = gate.evaluate(pResult.getFinalOutput());
            assertTrue(gResult.passed());
        }

        @Test
        @DisplayName("Pipeline annotations validate correctly")
        void pipelineAnnotationWorkflow() {
            List<AnnotationDecl> annotations = List.of(
                    AgentAnnotations.bare(V3Annotation.PIPELINE),
                    AgentAnnotations.shorthand(V3Annotation.SCHEDULE, "0 0 * * *")
            );
            List<ValidationProblem> problems =
                    AgentAnnotations.validateSet(annotations, AnnotationTarget.CLASS);
            assertTrue(problems.stream()
                    .noneMatch(p -> p.getLevel() == ValidationProblem.Level.ERROR));

            // Source/sink annotations validate on FIELD
            AnnotationDecl sourceDecl = AgentAnnotations.shorthand(
                    V3Annotation.SOURCE, "postgres://db/orders");
            assertTrue(AgentAnnotations.validate(sourceDecl, AnnotationTarget.FIELD).isEmpty());
        }

        @Test
        @DisplayName("Agent plan executes through pipeline data")
        void agentPlanWithPipelineData() {
            // Plan: fetch → filter → aggregate
            ExecutionPlan plan = AgentPlanner.sequentialPlan("Analyze sales",
                    List.of("Fetch data", "Filter active", "Aggregate totals"));

            DataBatch[] storage = { PipelineExecutor.batch(
                    PipelineExecutor.record("status", "active", "amount", 100.0),
                    PipelineExecutor.record("status", "inactive", "amount", 50.0),
                    PipelineExecutor.record("status", "active", "amount", 200.0)
            ) };

            PipelineExecutor executor = new PipelineExecutor();

            AgentPlanner.execute(plan, (step, p) -> {
                switch (step.getId()) {
                    case 0: return new StepResult("Fetched " + storage[0].size() + " records");
                    case 1:
                        storage[0] = Transform.filter("active",
                                r -> "active".equals(r.getString("status")))
                                .apply(storage[0]);
                        return new StepResult("Filtered to " + storage[0].size() + " records");
                    case 2:
                        DataBatch aggResult = Transform.aggregate("total",
                                new AggregateSpec(null, "amount",
                                        AggregateFunction.SUM, "total"))
                                .apply(storage[0]);
                        return new StepResult("Total: " + aggResult.get(0).get("total"));
                    default: return new StepResult("Unknown step");
                }
            });

            assertEquals(PlanStatus.COMPLETED, plan.getPlanStatus());
            assertEquals(2, storage[0].size());
        }

        @Test
        @DisplayName("Intelligent pipeline with multiple transforms and detectors")
        void comprehensiveIntelligentPipeline() {
            PipelineConfig config = PipelineConfig.builder("ComprehensiveETL")
                    .schedule("0 0 * * *")
                    .source("orders", "postgres://db/orders")
                    .source("customers", "postgres://db/customers")
                    .sink("warehouse", "snowflake://analytics/sales")
                    .sink("alerts", "kafka://broker/anomalies")
                    .build();

            AgentRuntime rt = new AgentRuntime();
            AgentTransform classify = new AgentTransform("Classify",
                    "Classify orders", AgentTransform.TransformMode.PER_RECORD);
            AgentTransform enrich = new AgentTransform("Enrich",
                    "Enrich data", AgentTransform.TransformMode.BATCH);

            QualityRule sizeRule = new QualityRule("QR-001", "At least 1 record",
                    QualityGate.Verdict.FAIL, b -> b.size() >= 1);
            QualityGate gate = new QualityGate("DataQuality", "Data quality",
                    List.of(sizeRule));

            AnomalyDetector nullDetector = new AnomalyDetector("NullCheck",
                    "amount", 0, AnomalyDetector.DetectionMethod.NULL_CHECK);
            AnomalyDetector rangeDetector = new AnomalyDetector("RangeCheck",
                    "amount", 50.0, AnomalyDetector.DetectionMethod.ABSOLUTE_RANGE);

            IntelligentPipeline ip = IntelligentPipeline.builder("ComprehensiveETL")
                    .pipelineConfig(config)
                    .agentRuntime(rt)
                    .addAgentTransform(classify)
                    .addAgentTransform(enrich)
                    .addQualityGate(gate)
                    .addAnomalyDetector(nullDetector)
                    .addAnomalyDetector(rangeDetector)
                    .build();

            DataBatch input = PipelineExecutor.batch(
                    PipelineExecutor.record("order_id", 1, "amount", 25.0),
                    PipelineExecutor.record("order_id", 2, "amount", 30.0),
                    PipelineExecutor.record("order_id", 3, "amount", 200.0)
            );

            IntelligentPipelineResult result = ip.execute(input);
            assertTrue(result.isSuccess());
            assertEquals(3, result.getOutput().size());
            assertEquals(1, result.getQualityGateResults().size());
            assertEquals(2, result.getAnomalyReports().size());
            // The range detector should find anomalies (200 is far from mean)
            assertTrue(result.getAnomalyReports().stream()
                    .anyMatch(r -> r.getDetectorName().equals("RangeCheck")
                            && r.hasAnomalies()));
        }

        @Test
        @DisplayName("Chain-of-thought reasoning with pipeline result")
        void chainOfThoughtPipeline() {
            // Build a pipeline result
            PipelineExecutor executor = new PipelineExecutor();
            DataBatch data = PipelineExecutor.batch(
                    PipelineExecutor.record("metric", "revenue", "value", 1000000),
                    PipelineExecutor.record("metric", "users", "value", 50000),
                    PipelineExecutor.record("metric", "churn", "value", 5)
            );

            // Chain of thought reasons about the data
            ChainOfThought cot = AgentPlanner.buildChainOfThought(
                    "Analyze business metrics",
                    (goal, prev) -> {
                        if (prev.isEmpty())
                            return new ChainReasonerResult(
                                    "Revenue is $1M, users = 50K", false);
                        if (prev.size() == 1)
                            return new ChainReasonerResult(
                                    "Churn rate is 5%, which is concerning", false);
                        return new ChainReasonerResult(
                                "Revenue healthy, but churn needs attention", true);
                    }, 10
            );

            assertEquals(3, cot.getSteps().size());
            assertTrue(cot.getConclusion().contains("churn"));
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // Edge Cases
    // ════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Empty data batch through all transforms")
        void emptyBatchThroughTransforms() {
            DataBatch empty = DataBatch.empty();
            assertEquals(0, Transform.filter("f", r -> true).apply(empty).size());
            assertEquals(0, Transform.map("m", r -> r).apply(empty).size());
            assertEquals(0, Transform.limit("l", 10).apply(empty).size());
            assertEquals(0, Transform.sort("s", "x", true).apply(empty).size());
            assertEquals(0, Transform.distinct("d").apply(empty).size());
            assertEquals(0, Transform.project("p", List.of("a")).apply(empty).size());
        }

        @Test
        @DisplayName("Limit larger than batch returns all records")
        void limitLargerThanBatch() {
            DataBatch input = PipelineExecutor.batch(
                    PipelineExecutor.record("x", 1),
                    PipelineExecutor.record("x", 2)
            );
            DataBatch result = Transform.limit("big", 100).apply(input);
            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("Single record batch aggregate")
        void singleRecordAggregate() {
            DataBatch input = PipelineExecutor.batch(
                    PipelineExecutor.record("val", 42.0)
            );
            AggregateSpec spec = new AggregateSpec(null, "val",
                    AggregateFunction.AVG, "avg");
            DataBatch result = Transform.aggregate("a", spec).apply(input);
            assertEquals(42.0, result.get(0).getDouble("avg"), 0.001);
        }

        @Test
        @DisplayName("Sort with null values")
        void sortWithNulls() {
            Map<String, Object> m1 = new LinkedHashMap<>();
            m1.put("val", null);
            Map<String, Object> m2 = new LinkedHashMap<>();
            m2.put("val", 10);
            DataBatch input = new DataBatch(List.of(
                    new DataRecord(m1), new DataRecord(m2)));
            // Should not throw
            DataBatch result = Transform.sort("s", "val", true).apply(input);
            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("Z-score with all identical values (stddev=0)")
        void zScoreIdenticalValues() {
            AnomalyDetector detector = new AnomalyDetector("ZD",
                    "val", 2.0, AnomalyDetector.DetectionMethod.Z_SCORE);
            DataBatch data = PipelineExecutor.batch(
                    PipelineExecutor.record("val", 10.0),
                    PipelineExecutor.record("val", 10.0),
                    PipelineExecutor.record("val", 10.0)
            );
            AnomalyReport report = detector.detect(data);
            assertFalse(report.hasAnomalies()); // no anomalies when no variation
        }

        @Test
        @DisplayName("Z-score with single record — no detection")
        void zScoreSingleRecord() {
            AnomalyDetector detector = new AnomalyDetector("ZD",
                    "val", 2.0, AnomalyDetector.DetectionMethod.Z_SCORE);
            DataBatch data = PipelineExecutor.batch(
                    PipelineExecutor.record("val", 10.0)
            );
            AnomalyReport report = detector.detect(data);
            assertFalse(report.hasAnomalies());
        }

        @Test
        @DisplayName("Multiple agent tools registered and used")
        void multipleAgentTools() {
            AgentRuntime rt = new AgentRuntime();
            rt.registerTool(new AgentTool() {
                @Override public String getName() { return "T1"; }
                @Override public String getDescription() { return "Tool 1"; }
                @Override public String getParameterSchema() { return "{}"; }
                @Override public String execute(String args) { return "result1"; }
            });
            rt.registerTool(new AgentTool() {
                @Override public String getName() { return "T2"; }
                @Override public String getDescription() { return "Tool 2"; }
                @Override public String getParameterSchema() { return "{}"; }
                @Override public String execute(String args) { return "result2"; }
            });
            assertEquals(2, rt.getTools().size());
            assertEquals("result1", rt.getTools().get("T1").execute(""));
            assertEquals("result2", rt.getTools().get("T2").execute(""));
        }

        @Test
        @DisplayName("Plan with parallel-ready steps (no inter-dependency)")
        void parallelSteps() {
            List<StepSpec> specs = List.of(
                    new StepSpec(0, "Fetch A"),
                    new StepSpec(1, "Fetch B"),
                    new StepSpec(2, "Merge", null, List.of(0, 1))
            );
            ExecutionPlan plan = AgentPlanner.toolPlan("Parallel", Strategy.SEQUENTIAL, specs);
            // Both 0 and 1 should be ready initially
            List<PlanStep> ready = plan.readySteps();
            assertEquals(2, ready.size());
        }

        @Test
        @DisplayName("Annotation sets on different targets")
        void annotationsOnDifferentTargets() {
            // @retry on METHOD is valid
            AnnotationDecl retry = new AnnotationDecl(V3Annotation.RETRY, List.of(
                    new AnnotationParam("attempts", 3)
            ));
            assertTrue(AgentAnnotations.validate(retry, AnnotationTarget.METHOD).isEmpty());
            // @retry on CLASS is invalid
            assertFalse(AgentAnnotations.validate(retry, AnnotationTarget.CLASS).isEmpty());
        }

        @Test
        @DisplayName("Pipeline with many stages")
        void manyStages() {
            PipelineExecutor executor = new PipelineExecutor();
            DataBatch input = PipelineExecutor.batch(
                    PipelineExecutor.record("v", 100)
            );
            List<Transform> transforms = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                final int idx = i;
                transforms.add(Transform.map("stage" + i,
                        r -> r.with("stage" + idx, true)));
            }
            DataBatch result = executor.executeTransforms(transforms, input);
            assertEquals(1, result.size());
            assertTrue(result.get(0).hasField("stage0"));
            assertTrue(result.get(0).hasField("stage19"));
        }

        @Test
        @DisplayName("AnomalyReport on empty batch")
        void anomalyEmptyBatch() {
            AnomalyDetector detector = new AnomalyDetector("D",
                    "val", 0, AnomalyDetector.DetectionMethod.NULL_CHECK);
            AnomalyReport report = detector.detect(DataBatch.empty());
            assertFalse(report.hasAnomalies());
            assertEquals(0, report.getTotalRecords());
        }

        @Test
        @DisplayName("React loop with null reactor result terminates")
        void reactNullResult() {
            List<ReactStep> trace = AgentPlanner.reactLoop(
                    "Goal", "obs",
                    (goal, obs, history) -> null,
                    (action, input) -> "result",
                    5
            );
            assertEquals(1, trace.size());
            assertEquals("FINISH", trace.get(0).getAction());
        }

        @Test
        @DisplayName("DataRecord project with nonexistent fields")
        void projectNonexistentFields() {
            DataRecord r = PipelineExecutor.record("a", 1);
            DataRecord projected = r.project(List.of("a", "b", "c"));
            assertTrue(projected.hasField("a"));
            assertFalse(projected.hasField("b"));
        }

        @Test
        @DisplayName("Aggregate with no groupBy aggregates all records")
        void aggregateNoGroupBy() {
            DataBatch input = PipelineExecutor.batch(
                    PipelineExecutor.record("val", 10.0),
                    PipelineExecutor.record("val", 20.0),
                    PipelineExecutor.record("val", 30.0)
            );
            AggregateSpec spec = new AggregateSpec(null, "val",
                    AggregateFunction.SUM, "total");
            DataBatch result = Transform.aggregate("sum_all", spec).apply(input);
            assertEquals(1, result.size());
            assertEquals(60.0, result.get(0).getDouble("total"), 0.001);
        }
    }
}
