import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Assignment entry point and shared test-case runner.
 *
 * <p>Schedulers are intentionally supplied to {@link #runCase}; this lets
 * each teammate implement SJF or ACS independently against {@link Scheduler}
 * while Main remains unchanged.</p>
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        int quantum = args.length > 0 ? parsePositive(args[0], "quantum") : 3;
        int contextSwitch = args.length > 1 ? parseNonNegative(args[1], "context switch") : 0;

        List<Process> testCase = Arrays.asList(
                new Process("P1", 0, 4, 2),
                new Process("P2", 1, 7, 3),
                new Process("P3", 3, 2, 1));

        System.out.printf("Cloud-Cluster CPU Scheduling%nQ=%d, CS=%d%n%n", quantum, contextSwitch);
        System.out.println("Configured test case:");
        for (Process process : testCase) {
            System.out.printf("%s: arrival=%d, burst=%d, priority=%d%n",
                    process.getName(), process.getArrivalTime(), process.getBurstTime(),
                    process.getInitialPriority());
        }
        System.out.println();
        System.out.println("Implementations are run by calling Main.runCase(scheduler, ...).");
    }

    /**
     * Runs one test case and prints the trace and all required metrics.
     */
    public static SchedulingResult runCase(
            Scheduler scheduler,
            Collection<Process> processes,
            int quantum,
            int contextSwitchTime) {
        if (scheduler == null) {
            throw new IllegalArgumentException("Scheduler must not be null");
        }
        if (processes == null || processes.isEmpty()) {
            throw new IllegalArgumentException("At least one process is required");
        }
        validateConfiguration(quantum, contextSwitchTime);

        SchedulingResult result = scheduler.schedule(processes, quantum, contextSwitchTime);
        if (result == null) {
            throw new IllegalStateException(scheduler.getName() + " returned no result");
        }
        System.out.printf("=== %s (Q=%d, CS=%d) ===%n", result.getSchedulerName(),
                quantum, contextSwitchTime);
        for (String line : result.getTrace()) {
            System.out.println(line);
        }
        System.out.println(result.formatMetrics());
        return result;
    }

    /**
     * Runs the same test case over a matrix of Q and CS values for report data.
     */
    public static List<SchedulingResult> runMatrix(
            Scheduler scheduler,
            Collection<Process> processes,
            int[] quantums,
            int[] contextSwitchTimes) {
        if (quantums == null || contextSwitchTimes == null
                || quantums.length == 0 || contextSwitchTimes.length == 0) {
            throw new IllegalArgumentException("Q and CS matrices must not be empty");
        }
        List<SchedulingResult> results = new ArrayList<>();
        for (int quantum : quantums) {
            for (int contextSwitchTime : contextSwitchTimes) {
                results.add(runCase(scheduler, processes, quantum, contextSwitchTime));
            }
        }
        return results;
    }

    private static void validateConfiguration(int quantum, int contextSwitchTime) {
        if (quantum <= 0) {
            throw new IllegalArgumentException("Quantum must be > 0, was " + quantum);
        }
        if (contextSwitchTime < 0) {
            throw new IllegalArgumentException(
                    "Context-switch time must be >= 0, was " + contextSwitchTime);
        }
    }

    private static int parsePositive(String value, String label) {
        int parsed = parseInteger(value, label);
        if (parsed <= 0) {
            throw new IllegalArgumentException(label + " must be > 0, was " + parsed);
        }
        return parsed;
    }

    private static int parseNonNegative(String value, String label) {
        int parsed = parseInteger(value, label);
        if (parsed < 0) {
            throw new IllegalArgumentException(label + " must be >= 0, was " + parsed);
        }
        return parsed;
    }

    private static int parseInteger(String value, String label) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + " must be an integer: " + value, exception);
        }
    }
}
