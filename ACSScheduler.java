import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;

/**
 * Adaptive Cloud Scheduler (ACS).
 *
 * <ul>
 *   <li>Tier 1: Round Robin with a static quantum (Mazen's part: the clock loop and Tier 1
 *       are kept minimal here so the whole thing runs; Mazen can replace the marked blocks).</li>
 *   <li>Demotion: a process that uses its whole quantum without finishing goes to Tier 2.</li>
 *   <li>Tier 2 (Sawy): non-preemptive priority queue, used only when Tier 1 is empty.</li>
 *   <li>Tier 3 (Sawy): aging, 1 priority level per 4 time units waited in Tier 2.</li>
 * </ul>
 *
 * <p>Context switch: charged whenever the CPU goes from one process to a different one.
 * It is not charged at the very start or after the CPU was idle. The next process is chosen
 * when the CPU becomes free (before the switch); arrivals during the switch join Tier 1.</p>
 */
public final class ACSScheduler implements Scheduler {

    @Override
    public String getName() {
        return "ACS";
    }

    @Override
    public SchedulingResult schedule(Collection<Process> processes, int quantum, int contextSwitchTime) {
        List<Process> procs = Process.copyAll(processes);      // never touch the caller's objects
        Run run = new Run(procs, quantum, contextSwitchTime);
        run.execute();
        return new SchedulingResult(getName(), procs, run.trace);
    }

    private static final class Run {
        private final List<Process> procs;
        private final List<Process> byArrival;
        private final int quantum;
        private final int cs;

        private final Deque<Process> tier1 = new ArrayDeque<>();
        private final Tier2Queue tier2 = new Tier2Queue();
        private final List<String> trace = new ArrayList<>();

        private int nextArrival = 0;
        private int time = 0;
        private Process lastRan = null;

        Run(List<Process> procs, int quantum, int cs) {
            this.procs = procs;
            this.quantum = quantum;
            this.cs = cs;
            this.byArrival = new ArrayList<>(procs);
            this.byArrival.sort(Comparator.comparingInt(Process::getArrivalTime)); // stable
        }

        void execute() {
            int finished = 0;
            while (finished < procs.size()) {
                admitArrivals(time);

                if (tier1.isEmpty() && tier2.isEmpty()) {           // idle: jump to next arrival
                    int arrival = byArrival.get(nextArrival).getArrivalTime();
                    trace.add("t=" + time + "-" + arrival + " | CPU idle");
                    time = arrival;
                    lastRan = null;
                    continue;
                }

                // ---- choose next process: Tier 1 first, Tier 2 only when Tier 1 is empty ----
                boolean fromTier1 = !tier1.isEmpty();
                Process p;
                if (fromTier1) {
                    p = tier1.poll();
                } else {
                    p = tier2.pickNext(time);                        // Tier 3 aging happens inside
                    trace.add("t=" + time + " | Tier 2 picks " + p.getName()
                            + " (effective priority " + p.getPriority() + ")");
                }

                // ---- context switch ----
                if (cs > 0 && lastRan != null && lastRan != p) {
                    advance(time, time + cs, "context switch -> " + p.getName());
                    time += cs;
                }

                if (fromTier1) {
                    // ---- Tier 1: one quantum ----
                    String label = p.getName() + " [Tier 1]";
                    int ran = p.execute(time, quantum);
                    advance(time, time + ran, label);
                    time += ran;
                    if (p.isFinished()) {
                        finished++;
                        trace.add("t=" + time + " | " + p.getName() + " finished");
                    } else {                                         // used full quantum -> demote
                        tier2.add(p, time);
                        trace.add("t=" + time + " | " + p.getName() + " demoted to Tier 2");
                    }
                } else {
                    // ---- Tier 2: non-preemptive, runs to completion ----
                    String label = p.getName() + " [Tier 2]";
                    int ran = p.execute(time, p.getRemainingTime());
                    advance(time, time + ran, label);
                    time += ran;
                    finished++;
                    trace.add("t=" + time + " | " + p.getName() + " finished");
                }
                lastRan = p;
            }
        }

        /** Moves every process that has arrived by {@code now} into Tier 1. */
        private void admitArrivals(int now) {
            while (nextArrival < byArrival.size()
                    && byArrival.get(nextArrival).hasArrived(now)) {
                tier1.add(byArrival.get(nextArrival++));
            }
        }

        /** One trace line per time unit in [from, to): activity + Tier 1 and Tier 2 contents. */
        private void advance(int from, int to, String activity) {
            for (int u = from; u < to; u++) {
                admitArrivals(u);
                trace.add("t=" + u + " | " + activity
                        + " | Tier1: " + tier1
                        + " | Tier2: " + tier2.snapshot(u));
            }
        }
    }
}
