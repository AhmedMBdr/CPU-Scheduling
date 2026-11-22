import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable output of one scheduler run.
 */
public final class SchedulingResult {

    private final String schedulerName;
    private final List<Process> processes;
    private final List<String> trace;

    public SchedulingResult(String schedulerName, List<Process> processes, List<String> trace) {
        if (schedulerName == null || schedulerName.trim().isEmpty()) {
            throw new IllegalArgumentException("Scheduler name must not be empty");
        }
        if (processes == null || trace == null) {
            throw new IllegalArgumentException("Processes and trace must not be null");
        }
        this.schedulerName = schedulerName.trim();
        this.processes = Collections.unmodifiableList(new ArrayList<>(processes));
        this.trace = Collections.unmodifiableList(new ArrayList<>(trace));
    }

    public String getSchedulerName() {
        return schedulerName;
    }

    public List<Process> getProcesses() {
        return processes;
    }

    public List<String> getTrace() {
        return trace;
    }

    public double getAverageWaitingTime() {
        return averageMetric(true);
    }

    public double getAverageTurnaroundTime() {
        return averageMetric(false);
    }

    public double getAverageResponseTime() {
        if (processes.isEmpty()) {
            return 0.0;
        }
        long total = 0;
        for (Process process : processes) {
            total += process.getResponseTime();
        }
        return (double) total / processes.size();
    }

    private double averageMetric(boolean waiting) {
        if (processes.isEmpty()) {
            return 0.0;
        }
        long total = 0;
        for (Process process : processes) {
            total += waiting ? process.getWaitingTime() : process.getTurnaroundTime();
        }
        return (double) total / processes.size();
    }

    /**
     * Formats the required per-process metrics and averages for console/report output.
     */
    public String formatMetrics() {
        StringBuilder output = new StringBuilder();
        output.append("Process | Arrival | Burst | Finish | Waiting | Turnaround | Response\n");
        output.append("--------|---------|-------|--------|---------|------------|---------\n");
        for (Process process : processes) {
            output.append(String.format(
                    "%-7s | %7d | %5d | %6d | %7d | %10d | %8d%n",
                    process.getName(),
                    process.getArrivalTime(),
                    process.getBurstTime(),
                    process.getFinishTime(),
                    process.getWaitingTime(),
                    process.getTurnaroundTime(),
                    process.getResponseTime()));
        }
        output.append(String.format(
                "Average waiting: %.2f%nAverage turnaround: %.2f%nAverage response: %.2f%n",
                getAverageWaitingTime(), getAverageTurnaroundTime(), getAverageResponseTime()));
        return output.toString();
    }
}
