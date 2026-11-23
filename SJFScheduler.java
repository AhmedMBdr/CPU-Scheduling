import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public final class SJFScheduler implements Scheduler {

    @Override
    public String getName() {
        return "SJF (preemptive)";
    }

    /** The quantum is ignored: SRTF is not Round Robin. */
    @Override
    public SchedulingResult schedule(Collection<Process> processes, int quantum, int contextSwitchTime) {
        List<Process> procs = Process.copyAll(processes);      // never touch the caller's objects
        Run run = new Run(procs, contextSwitchTime);
        run.execute();
        return new SchedulingResult(getName(), procs, run.trace);
    }

    private static final class Run {
        private final List<Process> procs;
        private final int cs;
        private final List<String> trace = new ArrayList<>();

        private int time = 0;
        private Process lastRan = null;

        Run(List<Process> procs, int cs) {
            this.procs = procs;
            this.cs = cs;
        }

        void execute() {
            int finished = 0;
            while (finished < procs.size()) {
                if (readyAt(time, null).isEmpty()) {              // idle: jump to next arrival
                    int next = nextArrivalAfter(time);
                    trace.add("t=" + time + "-" + next + " | CPU idle");
                    time = next;
                    lastRan = null;
                    continue;
                }

                Process p = pickNext(time);

                // ---- context switch (atomic) ----
                if (cs > 0 && lastRan != null && lastRan != p) {
                    for (int u = time; u < time + cs; u++) {
                        trace.add("t=" + u + " | context switch -> " + p.getName()
                                + " | Ready: " + format(readyAt(u, p)));
                    }
                    time += cs;
                }

                // ---- run one time unit, then re-check (arrival / completion / preemption) ----
                trace.add("t=" + time + " | " + p.getName()
                        + " | Ready: " + format(readyAt(time, p)));
                p.execute(time, 1);
                time++;
                lastRan = p;
                if (p.isFinished()) {
                    finished++;
                    trace.add("t=" + time + " | " + p.getName() + " finished");
                }
            }
        }

        /** Shortest remaining; tie: the process already on the CPU, earlier arrival, name. */
        private Process pickNext(int now) {
            Process best = null;
            for (Process p : readyAt(now, null)) {
                if (best == null || order().compare(p, best) < 0) {
                    best = p;
                }
            }
            return best;
        }

        private Comparator<Process> order() {
    return Comparator
        .comparingInt(Process::getRemainingTime)
        .thenComparingInt(p ->
            cs > 0
                ? (p == lastRan ? 0 : 1)
                : (p == lastRan ? 1 : 0)
        )
        .thenComparingInt(Process::getArrivalTime)
        .thenComparing(Process::getName);
}

        /** Arrived, unfinished processes at {@code now}, except {@code exclude}, best first. */
        private List<Process> readyAt(int now, Process exclude) {
            List<Process> ready = new ArrayList<>();
            for (Process p : procs) {
                if (p.hasArrived(now) && !p.isFinished() && p != exclude) {
                    ready.add(p);
                }
            }
            ready.sort(order());
            return ready;
        }

        private int nextArrivalAfter(int now) {
            int next = Integer.MAX_VALUE;
            for (Process p : procs) {
                if (!p.hasArrived(now)) {
                    next = Math.min(next, p.getArrivalTime());
                }
            }
            return next;
        }

        /** Queue entry format without priority (SJF does not use it): {@code P2 (remaining: 7)}. */
        private static String format(List<Process> ready) {
            if (ready.isEmpty()) {
                return "[Empty]";
            }
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < ready.size(); i++) {
                Process p = ready.get(i);
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(p.getName()).append(" (remaining: ").append(p.getRemainingTime()).append(')');
            }
            return sb.append(']').toString();
        }
    }
}
