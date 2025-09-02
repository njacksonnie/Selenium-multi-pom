package core.reporting;

import io.qameta.allure.Allure;
import io.qameta.allure.model.Parameter;
import io.qameta.allure.model.Status;
import io.qameta.allure.model.StepResult;
import io.qameta.allure.util.ResultsUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * Allure lifecycle helpers for managing steps, parameters, and labels.
 *
 * Provides a fluent, robust API for common Allure interactions with correct exception handling.
 *
 * Usage:
 *   AllureSteps.step("Open page", () -> driver.get(url));
 *   String id = AllureSteps.parameter("userId", userId);
 *   String value = AllureSteps.step("Compute", () -> expensiveComputation());
 *
 * Notes:
 * - Maps exceptions to FAILED for AssertionError, BROKEN for other Throwable via ResultsUtils.
 * - Adds optional parameters to the current step before execution.
 * - Ensures step lifecycle is always finished to avoid dangling steps.
 */
public final class AllureSteps {
    private AllureSteps() {
        // Utility class: not instantiable.
    }

    // ----------------- Parameter helpers -----------------
    /**
     * Adds a list of parameters to the current test case if present.
     * Each parameter is created via ResultsUtils.createParameter for consistent formatting.
     * Null names are ignored; values are converted via String.valueOf (null-safe).
     */
    public static void addParameters(List<Parameter> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return;
        }
        Optional.ofNullable(Allure.getLifecycle())
                .ifPresent(lc -> lc.updateTestCase(tc -> {
                    List<Parameter> target = tc.getParameters();
                    if (target == null) {
                        target = new ArrayList<>();
                        tc.setParameters(target);
                    }
                    target.addAll(parameters);
                }));
    }

    /**
     * Adds a single parameter to the current test case, returning the stringified value.
     * Useful for fluent usage inside step building.
     */
    public static String parameter(String name, Object value) {
        if (name == null || name.isBlank()) {
            return String.valueOf(value);
        }
        Parameter p = ResultsUtils.createParameter(name, String.valueOf(value));
        addParameters(List.of(p));
        return String.valueOf(value);
    }

    // ----------------- Step execution APIs -----------------
    /**
     * Executes a step with the given name and Runnable action.
     * Exceptions propagate; Allure status is set to PASSED/FAILED/BROKEN.
     */
    public static void step(String name, Runnable action) {
        Objects.requireNonNull(action, "action");
        executeStep(name, Collections.emptyList(), wrapRunnable(action));
    }

    /**
     * Executes a step with the given name, parameters, and Runnable action.
     * Exceptions propagate; Allure status is set to PASSED/FAILED/BROKEN.
     */
    public static void step(String name, List<Parameter> parameters, Runnable action) {
        Objects.requireNonNull(action, "action");
        executeStep(name, parameters, wrapRunnable(action));
    }

    /**
     * Executes a step with the given name and Supplier action returning a value.
     * Exceptions propagate; Allure status is set to PASSED/FAILED/BROKEN.
     */
    public static <T> T step(String name, Supplier<T> supplier) {
        Objects.requireNonNull(supplier, "supplier");
        return executeStep(name, Collections.emptyList(), supplier::get);
    }

    /**
     * Executes a step with the given name, parameters, and Supplier action returning a value.
     * Exceptions propagate; Allure status is set to PASSED/FAILED/BROKEN.
     */
    public static <T> T step(String name, List<Parameter> parameters, Supplier<T> supplier) {
        Objects.requireNonNull(supplier, "supplier");
        return executeStep(name, parameters, supplier::get);
    }

    /**
     * Executes a step with the given name and Callable action that throws checked exceptions.
     * Exceptions propagate; Allure status is set to PASSED/FAILED/BROKEN.
     */
    public static <T> T stepCallable(String name, Callable<T> callable) throws Exception {
        Objects.requireNonNull(callable, "callable");
        return executeStepChecked(name, Collections.emptyList(), callable);
    }

    /**
     * Executes a step with the given name, parameters, and Callable action that throws checked exceptions.
     * Exceptions propagate; Allure status is set to PASSED/FAILED/BROKEN.
     */
    public static <T> T stepCallable(String name, List<Parameter> parameters, Callable<T> callable) throws Exception {
        Objects.requireNonNull(callable, "callable");
        return executeStepChecked(name, parameters, callable);
    }

    // ----------------- Internal execution templates -----------------
    private static Callable<Void> wrapRunnable(Runnable r) {
        return () -> {
            r.run();
            return null;
        };
    }

    private static <T> T executeStep(String name, List<Parameter> params, Callable<T> body) {
        String stepName = (name == null || name.isBlank()) ? "Step" : name.trim();
        String uuid = UUID.randomUUID().toString();
        StepResult step = new StepResult().setName(stepName);
        if (params != null && !params.isEmpty()) {
            step.setParameters(params);
        }
        Allure.getLifecycle().startStep(uuid, step);
        try {
            T result = body.call();
            Allure.getLifecycle().updateStep(uuid, s -> s.setStatus(Status.PASSED));
            return result;
        } catch (Throwable t) {
            Allure.getLifecycle().updateStep(uuid, s ->
                    s.setStatus(ResultsUtils.getStatus(t).orElse(Status.BROKEN))
                            .setStatusDetails(ResultsUtils.getStatusDetails(t).orElse(null)));
            if (t instanceof RuntimeException re) {
                throw re;
            }
            // wrap checked exception in RuntimeException to preserve signature in non-checked paths
            throw new RuntimeException(t);
        } finally {
            Allure.getLifecycle().stopStep(uuid);
        }
    }

    private static <T> T executeStepChecked(String name, List<Parameter> params, Callable<T> body) throws Exception {
        String stepName = (name == null || name.isBlank()) ? "Step" : name.trim();
        String uuid = UUID.randomUUID().toString();
        StepResult step = new StepResult().setName(stepName);
        if (params != null && !params.isEmpty()) {
            step.setParameters(params);
        }
        Allure.getLifecycle().startStep(uuid, step);
        try {
            T result = body.call();
            Allure.getLifecycle().updateStep(uuid, s -> s.setStatus(Status.PASSED));
            return result;
        } catch (Throwable t) {
            Allure.getLifecycle().updateStep(uuid, s ->
                    s.setStatus(ResultsUtils.getStatus(t).orElse(Status.BROKEN))
                            .setStatusDetails(ResultsUtils.getStatusDetails(t).orElse(null)));
            throw t;  // Rethrow original exception to preserve type
        } finally {
            Allure.getLifecycle().stopStep(uuid);
        }
    }
}
