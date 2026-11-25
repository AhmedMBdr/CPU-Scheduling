import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

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
        String inputMode = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : null;
        if (inputMode != null && !"custom".equals(inputMode) && !"generated".equals(inputMode)) {
            throw new IllegalArgumentException("Third argument must be 'custom' or 'generated'");
        }

        System.out.println("Cloud-Cluster CPU Scheduling");
        System.out.println();
        Scanner input = new Scanner(System.in);
        if (inputMode == null) {
            System.out.println("Choose an input mode:");
            System.out.println("1. Run generated test cases");
            System.out.println("2. Enter a custom test case");
            System.out.print("Selection: ");
            int selection = readInputInteger(input, "selection");
            if (selection == 1) {
                inputMode = "generated";
            } else if (selection == 2) {
                inputMode = "custom";
            } else {
                throw new IllegalArgumentException("Selection must be 1 or 2");
            }
        }

        if ("custom".equals(inputMode)) {
            System.out.printf(Locale.ROOT, "Q=%d, CS=%d%n%n", quantum, contextSwitch);
            runCustomInput(input, quantum, contextSwitch);
        } else {
            runGeneratedTestCases();
        }
    }


    public static void runGeneratedTestCases() {
        List<List<Process>> testCases = Arrays.asList(
                Arrays.asList(
                        new Process("P1", 0, 4, 2),
                        new Process("P2", 1, 7, 3),
                        new Process("P3", 3, 2, 1)),
                Arrays.asList(
                        new Process("P1", 0, 5, 4),
                        new Process("P2", 1, 6, 2),
                        new Process("P3", 4, 4, 3)),
                Arrays.asList(
                        new Process("P1", 0, 4, 3),
                        new Process("P2", 1, 8, 5),
                        new Process("P3", 3, 2, 1),
                        new Process("P4", 10, 6, 4),
                        new Process("P5", 12, 5, 2)),
                Arrays.asList(
                        new Process("P1", 0, 3, 2),
                        new Process("P2", 0, 5, 1),
                        new Process("P3", 15, 4, 3),
                        new Process("P4", 16, 6, 2),
                        new Process("P5", 17, 2, 1)));
        int[] quantums = {3, 3, 4, 5};
        int[] contextSwitchTimes = {0, 1, 2, 3};

        Scheduler acs = new ACSScheduler();
        Scheduler sjf = new SJFScheduler();
        for (int i = 0; i < testCases.size(); i++) {
            Collection<Process> testCase = testCases.get(i);
            int quantum = quantums[i];
            int contextSwitchTime = contextSwitchTimes[i];
            System.out.printf(Locale.ROOT, "Test case %d (Q=%d, CS=%d):%n",
                    i + 1, quantum, contextSwitchTime);
            printProcesses(testCase);
            runCase(acs, testCase, quantum, contextSwitchTime);
            System.out.println();
            runCase(sjf, testCase, quantum, contextSwitchTime);
            if (i < testCases.size() - 1) {
                System.out.println();
            }
        }
    }

    /**
     * Reads a custom process set from standard input and runs both schedulers.
     *
     * <p>Input is the process count followed by each process's name, arrival time,
     * burst time, and initial priority.</p>
     */
    public static void runCustomInput(Scanner input, int quantum, int contextSwitchTime) {
        if (input == null) {
            throw new IllegalArgumentException("Input must not be null");
        }
        validateConfiguration(quantum, contextSwitchTime);

        System.out.print("Number of processes: ");
        int processCount = readInputInteger(input, "number of processes");
        if (processCount <= 0) {
            throw new IllegalArgumentException("Number of processes must be > 0");
        }

        List<Process> processes = new ArrayList<>();
        for (int i = 0; i < processCount; i++) {
            System.out.printf(Locale.ROOT,
                    "Process %d (name arrival-time burst-time initial-priority): ", i + 1);
            if (!input.hasNext()) {
                throw new IllegalArgumentException("Missing input for process " + (i + 1));
            }
            String name = input.next();
            int arrivalTime = readInputInteger(input, "arrival time");
            int burstTime = readInputInteger(input, "burst time");
            int priority = readInputInteger(input, "initial priority");
            processes.add(new Process(name, arrivalTime, burstTime, priority));
        }

        System.out.println("Custom test case:");
        printProcesses(processes);
        runCase(new ACSScheduler(), processes, quantum, contextSwitchTime);
        System.out.println();
        runCase(new SJFScheduler(), processes, quantum, contextSwitchTime);
    }

    private static void printProcesses(Collection<Process> processes) {
        for (Process process : processes) {
            System.out.printf(Locale.ROOT, "%s: arrival=%d, burst=%d, priority=%d%n",
                    process.getName(), process.getArrivalTime(), process.getBurstTime(),
                    process.getInitialPriority());
        }
        System.out.println();
    }

    private static int readInputInteger(Scanner input, String label) {
        if (!input.hasNextInt()) {
            if (!input.hasNext()) {
                throw new IllegalArgumentException("Missing input for " + label);
            }
            throw new IllegalArgumentException(label + " must be an integer: " + input.next());
        }
        return input.nextInt();
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
        System.out.printf(Locale.ROOT, "=== %s (Q=%d, CS=%d) ===%n", result.getSchedulerName(),
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