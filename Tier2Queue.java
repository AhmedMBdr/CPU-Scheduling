import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * ACS Tier 2 (non-preemptive priority queue) and Tier 3 (aging).
 *
 * <p>Tier 2 holds "batch jobs": processes that used a whole Tier 1 quantum without finishing.
 * It is kept in a plain {@link List} (not a PriorityQueue) because aging changes priorities
 * while processes are waiting, and a PriorityQueue would not re-sort.</p>
 *
 * <p><b>Aging rule (Tier 3):</b> every {@link #AGING_INTERVAL} time units a process has waited
 * in Tier 2, its priority improves by {@link #AGING_STEP} level (lower number = higher
 * priority). The priority is recomputed from the <i>absolute</i> waiting time
 * ({@code now - waitingSince}) and the initial priority, so calling {@link #applyAging(int)}
 * several times at the same or later times never double-counts. It never goes better than
 * {@link #BEST_PRIORITY}.</p>
 *
 * <p><b>Tie-breaking (documented):</b> best (lowest) effective priority, then the process that
 * was demoted to Tier 2 earlier, then earlier arrival, then name.</p>
 */
public final class Tier2Queue {

    /** Time units waited in Tier 2 per aging step (sample: 1 level per 4 time units). */
    public static final int AGING_INTERVAL = 4;
    /** Priority levels gained per aging step. */
    public static final int AGING_STEP = 1;
    /** Aging never improves a priority past this value (1 = highest priority). */
    public static final int BEST_PRIORITY = 1;

    private static final Comparator<Process> PICK_ORDER = Comparator
            .comparingInt(Process::getPriority)
            .thenComparingInt(Process::getWaitingSince)
            .thenComparingInt(Process::getArrivalTime)
            .thenComparing(Process::getName);

    private final List<Process> waiting = new ArrayList<>();

    /** Demotes a process into Tier 2 at time {@code now}; aging starts counting from here. */
    public void add(Process process, int now) {
        process.setBatchJob(true);
        process.setWaitingSince(now);
        waiting.add(process);
    }

    public boolean isEmpty() {
        return waiting.isEmpty();
    }

    public int size() {
        return waiting.size();
    }

    /** Effective priority of {@code p} at time {@code now} after aging (does not modify p). */
    public static int agedPriority(Process p, int now) {
        if (p.getWaitingSince() == Process.UNSET) {
            return p.getPriority();
        }
        int waited = Math.max(0, now - p.getWaitingSince());
        int levels = (waited / AGING_INTERVAL) * AGING_STEP;
        int aged = Math.max(BEST_PRIORITY, p.getInitialPriority() - levels);
        return Math.min(p.getInitialPriority(), aged); // never worse than the initial priority
    }

    /** Tier 3: updates the effective priority of every waiting process for time {@code now}. */
    public void applyAging(int now) {
        for (Process p : waiting) {
            p.setPriority(agedPriority(p, now));
        }
    }

    /**
     * Applies aging at {@code now}, then removes and returns the best process.
     * Call only when the CPU is free and Tier 1 is empty. Returns null if Tier 2 is empty.
     * The returned process runs to completion (non-preemptive).
     */
    public Process pickNext(int now) {
        if (waiting.isEmpty()) {
            return null;
        }
        applyAging(now);
        Process best = waiting.get(0);
        for (Process p : waiting) {
            if (PICK_ORDER.compare(p, best) < 0) {
                best = p;
            }
        }
        waiting.remove(best);
        return best;
    }

    /**
     * Queue contents at time {@code now} with aged priorities, in the order they would be
     * picked, e.g. {@code [P1 (remaining: 1, Priority: 1), P2 (remaining: 4, Priority: 3)]}.
     */
    public String snapshot(int now) {
        applyAging(now);
        List<Process> sorted = new ArrayList<>(waiting);
        sorted.sort(PICK_ORDER);
        return sorted.toString();
    }
}
