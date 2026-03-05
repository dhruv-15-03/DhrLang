package dhrlang.agent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * SC-703 — Multi-Step Agent Planner.
 *
 * <p>Implements planning strategies for complex, multi-step agent tasks.
 * Given a high-level goal the planner decomposes it into discrete steps,
 * tracks execution state, handles failures, and enables several planning
 * strategies from simple sequential to ReAct-style reasoning loops.</p>
 *
 * <h3>Supported Strategies</h3>
 * <ul>
 *   <li>{@link Strategy#SEQUENTIAL} — execute steps in order</li>
 *   <li>{@link Strategy#CHAIN_OF_THOUGHT} — model reasons step-by-step</li>
 *   <li>{@link Strategy#TREE_OF_THOUGHT} — explore branching alternatives</li>
 *   <li>{@link Strategy#REACT} — Reason + Act loop (observe → think → act)</li>
 * </ul>
 */
public final class AgentPlanner {

    // ── Planning Strategy ──────────────────────────────────────────────

    public enum Strategy {
        /** Steps executed one after another. */
        SEQUENTIAL("sequential"),
        /** Model produces chain-of-thought reasoning before each step. */
        CHAIN_OF_THOUGHT("chain-of-thought"),
        /** Model explores multiple branches and picks the best. */
        TREE_OF_THOUGHT("tree-of-thought"),
        /** Observe → Think → Act loop. */
        REACT("react");

        private final String label;
        Strategy(String label) { this.label = label; }
        public String getLabel() { return label; }

        public static Strategy fromLabel(String label) {
            for (Strategy s : values()) if (s.label.equals(label)) return s;
            throw new IllegalArgumentException("Unknown strategy: " + label);
        }
    }

    // ── Plan Step ──────────────────────────────────────────────────────

    public enum StepStatus {
        PENDING, IN_PROGRESS, COMPLETED, FAILED, SKIPPED
    }

    /**
     * An individual step in an execution plan.
     */
    public static final class PlanStep {
        private final int id;
        private final String description;
        private final String toolName;       // nullable — not all steps need tools
        private final List<Integer> dependsOn;
        private StepStatus status;
        private String output;
        private String reasoning;
        private long durationMs;

        public PlanStep(int id, String description, String toolName,
                        List<Integer> dependsOn) {
            this.id = id;
            this.description = Objects.requireNonNull(description);
            this.toolName = toolName;
            this.dependsOn = dependsOn == null
                    ? Collections.emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(dependsOn));
            this.status = StepStatus.PENDING;
        }

        public PlanStep(int id, String description) {
            this(id, description, null, null);
        }

        public int getId()                 { return id; }
        public String getDescription()     { return description; }
        public String getToolName()        { return toolName; }
        public List<Integer> getDependsOn() { return dependsOn; }
        public StepStatus getStatus()      { return status; }
        public String getOutput()          { return output; }
        public String getReasoning()       { return reasoning; }
        public long getDurationMs()        { return durationMs; }

        public void setStatus(StepStatus s)    { this.status = s; }
        public void setOutput(String o)        { this.output = o; }
        public void setReasoning(String r)     { this.reasoning = r; }
        public void setDurationMs(long ms)     { this.durationMs = ms; }

        public boolean isTerminal() {
            return status == StepStatus.COMPLETED || status == StepStatus.FAILED
                    || status == StepStatus.SKIPPED;
        }

        @Override public String toString() {
            return "Step " + id + " [" + status + "] " + description;
        }
    }

    // ── Execution Plan ─────────────────────────────────────────────────

    /**
     * A complete execution plan: an ordered list of steps with metadata.
     */
    public static final class ExecutionPlan {
        private final String goal;
        private final Strategy strategy;
        private final List<PlanStep> steps;
        private final Map<String, String> metadata;
        private PlanStatus planStatus;

        public ExecutionPlan(String goal, Strategy strategy, List<PlanStep> steps) {
            this.goal = Objects.requireNonNull(goal);
            this.strategy = Objects.requireNonNull(strategy);
            this.steps = new ArrayList<>(steps);
            this.metadata = new LinkedHashMap<>();
            this.planStatus = PlanStatus.CREATED;
        }

        public String getGoal()             { return goal; }
        public Strategy getStrategy()       { return strategy; }
        public List<PlanStep> getSteps()    { return Collections.unmodifiableList(steps); }
        public PlanStatus getPlanStatus()   { return planStatus; }
        public Map<String, String> getMetadata() { return Collections.unmodifiableMap(metadata); }

        public void setPlanStatus(PlanStatus s) { this.planStatus = s; }
        public void setMetadata(String k, String v) { metadata.put(k, v); }

        /** Number of steps in a given status. */
        public int countByStatus(StepStatus s) {
            int n = 0;
            for (PlanStep step : steps) if (step.getStatus() == s) n++;
            return n;
        }

        /** All steps that are ready to execute (dependencies met). */
        public List<PlanStep> readySteps() {
            List<PlanStep> ready = new ArrayList<>();
            for (PlanStep step : steps) {
                if (step.getStatus() != StepStatus.PENDING) continue;
                boolean depsOk = true;
                for (int depId : step.getDependsOn()) {
                    PlanStep dep = findStep(depId);
                    if (dep == null || dep.getStatus() != StepStatus.COMPLETED) {
                        depsOk = false;
                        break;
                    }
                }
                if (depsOk) ready.add(step);
            }
            return ready;
        }

        /** Find a step by ID. */
        public PlanStep findStep(int id) {
            for (PlanStep s : steps) if (s.getId() == id) return s;
            return null;
        }

        /** Percentage of steps completed (0.0–1.0). */
        public double progress() {
            if (steps.isEmpty()) return 1.0;
            int done = 0;
            for (PlanStep s : steps) if (s.isTerminal()) done++;
            return (double) done / steps.size();
        }

        /** True if all steps are terminal. */
        public boolean isFinished() {
            for (PlanStep s : steps) if (!s.isTerminal()) return false;
            return true;
        }

        /** True if any step failed. */
        public boolean hasFailed() {
            for (PlanStep s : steps) if (s.getStatus() == StepStatus.FAILED) return true;
            return false;
        }

        public String formatPlan() {
            StringBuilder sb = new StringBuilder();
            sb.append("Goal: ").append(goal).append('\n');
            sb.append("Strategy: ").append(strategy.getLabel()).append('\n');
            sb.append("Status: ").append(planStatus).append('\n');
            sb.append("Progress: ").append(String.format("%.0f%%", progress() * 100)).append('\n');
            sb.append("Steps:\n");
            for (PlanStep step : steps) {
                sb.append("  ").append(step).append('\n');
                if (step.getReasoning() != null) {
                    sb.append("    Reasoning: ").append(step.getReasoning()).append('\n');
                }
                if (step.getOutput() != null) {
                    sb.append("    Output: ").append(step.getOutput()).append('\n');
                }
            }
            return sb.toString();
        }
    }

    public enum PlanStatus {
        CREATED, RUNNING, COMPLETED, FAILED, CANCELLED
    }

    // ── Plan Builders ──────────────────────────────────────────────────

    /**
     * Create a sequential plan from a goal and a list of step descriptions.
     * Each step depends on the previous one.
     */
    public static ExecutionPlan sequentialPlan(String goal, List<String> stepDescriptions) {
        List<PlanStep> steps = new ArrayList<>();
        for (int i = 0; i < stepDescriptions.size(); i++) {
            List<Integer> deps = (i > 0) ? List.of(i - 1) : Collections.emptyList();
            steps.add(new PlanStep(i, stepDescriptions.get(i), null, deps));
        }
        return new ExecutionPlan(goal, Strategy.SEQUENTIAL, steps);
    }

    /**
     * Create a plan with explicit tool assignments.
     */
    public static ExecutionPlan toolPlan(String goal, Strategy strategy,
                                          List<StepSpec> specs) {
        List<PlanStep> steps = new ArrayList<>();
        for (StepSpec spec : specs) {
            steps.add(new PlanStep(spec.id, spec.description, spec.toolName, spec.dependsOn));
        }
        return new ExecutionPlan(goal, strategy, steps);
    }

    /** Specification for building a plan step. */
    public static final class StepSpec {
        public final int id;
        public final String description;
        public final String toolName;
        public final List<Integer> dependsOn;

        public StepSpec(int id, String description, String toolName,
                        List<Integer> dependsOn) {
            this.id = id;
            this.description = description;
            this.toolName = toolName;
            this.dependsOn = dependsOn;
        }

        public StepSpec(int id, String description) {
            this(id, description, null, Collections.emptyList());
        }
    }

    // ── Plan Execution (synchronous, single-threaded) ──────────────────

    /**
     * Execute a plan step-by-step. Each step is run through the provided
     * executor function, which receives the step and returns an output string
     * (or throws to signal failure).
     *
     * @param plan the execution plan
     * @param stepExecutor function that executes a single step
     * @return the completed plan
     */
    public static ExecutionPlan execute(ExecutionPlan plan,
                                         StepExecutor stepExecutor) {
        plan.setPlanStatus(PlanStatus.RUNNING);

        while (!plan.isFinished()) {
            List<PlanStep> ready = plan.readySteps();
            if (ready.isEmpty()) {
                // Deadlock or all remaining steps depend on failed ones
                for (PlanStep s : plan.getSteps()) {
                    if (s.getStatus() == StepStatus.PENDING) {
                        s.setStatus(StepStatus.SKIPPED);
                        s.setOutput("Skipped — dependency not met");
                    }
                }
                break;
            }

            for (PlanStep step : ready) {
                step.setStatus(StepStatus.IN_PROGRESS);
                long t0 = System.currentTimeMillis();
                try {
                    StepResult result = stepExecutor.execute(step, plan);
                    step.setDurationMs(System.currentTimeMillis() - t0);
                    step.setOutput(result.output);
                    step.setReasoning(result.reasoning);
                    step.setStatus(StepStatus.COMPLETED);
                } catch (Exception e) {
                    step.setDurationMs(System.currentTimeMillis() - t0);
                    step.setOutput("Error: " + e.getMessage());
                    step.setStatus(StepStatus.FAILED);
                }
            }
        }

        plan.setPlanStatus(plan.hasFailed() ? PlanStatus.FAILED : PlanStatus.COMPLETED);
        return plan;
    }

    /** Callback interface for executing a single plan step. */
    @FunctionalInterface
    public interface StepExecutor {
        StepResult execute(PlanStep step, ExecutionPlan plan) throws Exception;
    }

    /** Result of executing one step. */
    public static final class StepResult {
        public final String output;
        public final String reasoning;

        public StepResult(String output, String reasoning) {
            this.output = output;
            this.reasoning = reasoning;
        }

        public StepResult(String output) {
            this(output, null);
        }
    }

    // ── ReAct Loop Support ─────────────────────────────────────────────

    /**
     * A single ReAct iteration: Observation → Thought → Action.
     */
    public static final class ReactStep {
        private final int iteration;
        private final String observation;
        private final String thought;
        private final String action;
        private final String actionResult;

        public ReactStep(int iteration, String observation, String thought,
                         String action, String actionResult) {
            this.iteration = iteration;
            this.observation = observation;
            this.thought = thought;
            this.action = action;
            this.actionResult = actionResult;
        }

        public int getIteration()       { return iteration; }
        public String getObservation()   { return observation; }
        public String getThought()       { return thought; }
        public String getAction()        { return action; }
        public String getActionResult()  { return actionResult; }

        @Override public String toString() {
            return "React[" + iteration + "] O=" + observation 
                   + " T=" + thought + " A=" + action;
        }
    }

    /**
     * Execute a ReAct-style reasoning loop.
     *
     * @param goal the agent's goal
     * @param initialObservation starting observation
     * @param reactor produces thought+action from observation
     * @param actionExecutor executes the action and returns observation
     * @param maxIterations safety limit
     * @return ordered list of ReAct steps
     */
    public static List<ReactStep> reactLoop(
            String goal,
            String initialObservation,
            ReactThinkAction reactor,
            ReactActionExecutor actionExecutor,
            int maxIterations) {

        List<ReactStep> trace = new ArrayList<>();
        String observation = initialObservation;

        for (int i = 1; i <= maxIterations; i++) {
            ThinkActionResult tar = reactor.thinkAndAct(goal, observation, trace);
            if (tar == null || "FINISH".equalsIgnoreCase(tar.action)) {
                trace.add(new ReactStep(i, observation, tar != null ? tar.thought : "done",
                        "FINISH", null));
                break;
            }

            String actionResult;
            try {
                actionResult = actionExecutor.executeAction(tar.action, tar.actionInput);
            } catch (Exception e) {
                actionResult = "Error: " + e.getMessage();
            }

            trace.add(new ReactStep(i, observation, tar.thought, tar.action, actionResult));
            observation = actionResult;  // next observation = result of action
        }

        return Collections.unmodifiableList(trace);
    }

    /** Callback: given observation + history, produce a thought and action. */
    @FunctionalInterface
    public interface ReactThinkAction {
        ThinkActionResult thinkAndAct(String goal, String observation, List<ReactStep> history);
    }

    /** Callback: execute a named action with input. */
    @FunctionalInterface
    public interface ReactActionExecutor {
        String executeAction(String action, String input) throws Exception;
    }

    /** The thought + chosen action from a ReAct iteration. */
    public static final class ThinkActionResult {
        public final String thought;
        public final String action;
        public final String actionInput;

        public ThinkActionResult(String thought, String action, String actionInput) {
            this.thought = thought;
            this.action = action;
            this.actionInput = actionInput;
        }
    }

    // ── Chain-of-Thought Support ───────────────────────────────────────

    /**
     * A chain-of-thought trace — ordered reasoning steps leading to a conclusion.
     */
    public static final class ChainOfThought {
        private final List<String> steps;
        private final String conclusion;

        public ChainOfThought(List<String> steps, String conclusion) {
            this.steps = Collections.unmodifiableList(new ArrayList<>(steps));
            this.conclusion = conclusion;
        }

        public List<String> getSteps()   { return steps; }
        public String getConclusion()     { return conclusion; }

        public String format() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < steps.size(); i++) {
                sb.append("Step ").append(i + 1).append(": ").append(steps.get(i)).append('\n');
            }
            sb.append("Conclusion: ").append(conclusion).append('\n');
            return sb.toString();
        }
    }

    /**
     * Build a chain-of-thought from a goal using the provided reasoning function.
     *
     * @param goal the problem to solve
     * @param reasoner produces the next thought given history
     * @param maxSteps maximum reasoning steps
     * @return completed chain of thought
     */
    public static ChainOfThought buildChainOfThought(
            String goal,
            ChainReasoner reasoner,
            int maxSteps) {
        List<String> steps = new ArrayList<>();

        for (int i = 0; i < maxSteps; i++) {
            ChainReasonerResult result = reasoner.nextThought(goal, steps);
            steps.add(result.thought);
            if (result.isFinal) {
                return new ChainOfThought(steps, result.thought);
            }
        }

        // Reached max steps — last step is conclusion
        String conclusion = steps.isEmpty() ? "No conclusion reached" : steps.get(steps.size() - 1);
        return new ChainOfThought(steps, conclusion);
    }

    /** Callback: produce the next thought in a chain. */
    @FunctionalInterface
    public interface ChainReasoner {
        ChainReasonerResult nextThought(String goal, List<String> previousSteps);
    }

    /** Result from a chain reasoner — one thought that may be final. */
    public static final class ChainReasonerResult {
        public final String thought;
        public final boolean isFinal;

        public ChainReasonerResult(String thought, boolean isFinal) {
            this.thought = thought;
            this.isFinal = isFinal;
        }
    }
}
