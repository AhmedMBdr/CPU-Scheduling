import java.util.Collection;

/**
 * Common contract implemented by every assignment scheduler.
 *
 * <p>A scheduler must not mutate the caller's processes. The returned
 * {@link SchedulingResult} owns the fresh process copies whose metrics are
 * reported by the scheduler.</p>
 */
public interface Scheduler {

    /**
     * Human-readable scheduler name used in reports.
     */
    String getName();

    /**
     * Runs the scheduler from an empty CPU until every process finishes.
     *
     * @param processes input processes
     * @param quantum Round Robin quantum; must be positive
     * @param contextSwitchTime cost of a process-to-process switch; must not be negative
     * @return completed processes, averages, and per-time-step trace
     */
    SchedulingResult schedule(Collection<Process> processes, int quantum, int contextSwitchTime);
}
