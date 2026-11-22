
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class Process {

    /** Value of a time that has not been set yet (start time, finish time, waiting-since). */
    public static final int UNSET = -1;

    // ---- input data from the test case: never changes ----
    private final String name;
    private final int arrivalTime , burstTime , initialPriority;

    // ---- run-time state: changed while simulating, restored by reset() ----
    private int remainingTime , priority , waitingSince , startTime , finishTime;
    private boolean batchJob;


    /**
     * @param name        e.g. "P1"
     * @param arrivalTime when the process arrives (&gt;= 0)
     * @param burstTime   CPU time it needs (&gt; 0)
     * @param priority    initial priority; lower number = higher priority
     * @throws IllegalArgumentException if the name is blank, arrivalTime &lt; 0 or burstTime &lt;= 0
     */
    public Process(String name, int arrivalTime, int burstTime, int priority) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Process name must not be empty");
        }
        if (arrivalTime < 0) {
            throw new IllegalArgumentException(name + ": arrival time must be >= 0, was " + arrivalTime);
        }
        if (burstTime <= 0) {
            throw new IllegalArgumentException(name + ": burst time must be > 0, was " + burstTime);
        }
        this.name = name.trim();
        this.arrivalTime = arrivalTime;
        this.burstTime = burstTime;
        this.initialPriority = priority;
        reset();
    }

    // ------------------------------------------------------------------ input data

    public String getName() {
        return name;
    }

    public int getArrivalTime() {
        return arrivalTime;
    }

    /** Total CPU time the process needs (its "service time"). */
    public int getBurstTime() {
        return burstTime;
    }

    /** Priority from the test case (lower number = higher priority). Aging never changes it. */
    public int getInitialPriority() {
        return initialPriority;
    }

    // ------------------------------------------------------------------ run-time state

    /** CPU time still needed. Starts at the burst time; 0 means the process is finished. */
    public int getRemainingTime() {
        return remainingTime;
    }

    /**
     * Current (effective) priority: the initial priority, improved by ACS Tier 3 aging.
     * Lower number = higher priority. This is the value the sample traces print.
     */
    public int getPriority() {
        return priority;
    }

    /**
     * Sets the current priority (ACS Tier 3 aging); {@link #getInitialPriority()} is kept.
     *
     * <p>Do not call this while the process sits inside a {@code java.util.PriorityQueue}: the
     * queue would not re-sort. Keep Tier 2 in a {@code List} and pick the best process when the
     * CPU becomes free.
     */
    public void setPriority(int priority) {
        this.priority = priority;
    }

    /**
     * ACS: true once the process used a whole Tier 1 quantum without finishing, i.e. it was
     * flagged as a CPU-bound "batch job" and moved to Tier 2.
     */
    public boolean isBatchJob() {
        return batchJob;
    }

    public void setBatchJob(boolean batchJob) {
        this.batchJob = batchJob;
    }

    /**
     * ACS aging: the time the process started waiting in Tier 2 (the moment it was demoted),
     * or {@link #UNSET} before that. Time waited at time {@code now} = {@code now - getWaitingSince()}.
     */
    public int getWaitingSince() {
        return waitingSince;
    }

    public void setWaitingSince(int time) {
        this.waitingSince = time;
    }

    /** Time of the first CPU unit this process got, or {@link #UNSET} if it has not run yet. */
    public int getStartTime() {
        return startTime;
    }

    /** Time the process finished (last executed unit + 1), or {@link #UNSET} if it is not finished. */
    public int getFinishTime() {
        return finishTime;
    }

    public boolean hasArrived(int time) {
        return arrivalTime <= time;
    }

    public boolean hasStarted() {
        return startTime != UNSET;
    }

    public boolean isFinished() {
        return remainingTime == 0;
    }

    // ------------------------------------------------------------------ running

    /**
     * Runs this process on the CPU for up to {@code units} time units, starting at time
     * {@code now}. This is the only place that sets the first-start and finish times, so every
     * scheduler measures them the same way.
     * Sets the start time on the first call (response time = start - arrival).
     * Never runs past the burst: if less than {@code units} is left, it just finishes.
     * When the process completes, finish time = {@code now} + units actually run.
     * Typical use:
     * @code
     * ran = p.execute(t, quantum);          // ACS Tier 1 (then demote if !p.isFinished())
     * t += p.execute(t, p.getRemainingTime());  // ACS Tier 2, non-preemptive
     * t += p.execute(t, 1);                 // preemptive SJF, one time unit at a time
     * The caller advances its own clock by the returned value; a context switch is not part of
     * this call.
     *
     * @return the number of time units actually run (at most {@code units})
     * @throws IllegalArgumentException if {@code units <= 0} or {@code now} is before the arrival time
     * @throws IllegalStateException    if the process has already finished
     */
    public int execute(int now, int units) {
        if (units <= 0) {
            throw new IllegalArgumentException(name + ": units must be > 0, was " + units);
        }
        if (now < arrivalTime) {
            throw new IllegalArgumentException(name + " cannot run at t=" + now
                    + ", it arrives at t=" + arrivalTime);
        }
        if (isFinished()) {
            throw new IllegalStateException(name + " has already finished");
        }
        int ran = Math.min(units, remainingTime);
        if (startTime == UNSET) {
            startTime = now;
        }
        remainingTime -= ran;
        if (remainingTime == 0) {
            finishTime = now + ran;
        }
        return ran;
    }

    // ------------------------------------------------------------------ metrics (Req 2)

    /**
     * Finish time - arrival time.
     *
     * @throws IllegalStateException if the process has not finished yet
     */
    public int getTurnaroundTime() {
        if (!isFinished()) {
            throw new IllegalStateException(name + " has not finished yet");
        }
        return finishTime - arrivalTime;
    }

    /**
     * Turnaround - burst: all the time the process was in the system but not running
     * (waiting in a queue or during a context switch).
     *
     * @throws IllegalStateException if the process has not finished yet
     */
    public int getWaitingTime() {
        return getTurnaroundTime() - burstTime;
    }

    /**
     * First start - arrival: how long the process waited for its first CPU time.
     *
     * @throws IllegalStateException if the process has not started yet
     */
    public int getResponseTime() {
        if (!hasStarted()) {
            throw new IllegalStateException(name + " has not started yet");
        }
        return startTime - arrivalTime;
    }

    // ------------------------------------------------------------------ copies

    /** Puts the run-time state back to the initial state (nothing run yet, initial priority). */
    public void reset() {
        remainingTime = burstTime;
        priority = initialPriority;
        batchJob = false;
        waitingSince = UNSET;
        startTime = UNSET;
        finishTime = UNSET;
    }

    /**
     * A new process with the same input data and a fresh run-time state. The run-time state of
     * this process is NOT copied.
     */
    public Process copy() {
        return new Process(name, arrivalTime, burstTime, initialPriority);
    }

    /**
     * Fresh copies of all the given processes, in the same order. Schedulers call this at the
     * start of {@code schedule(...)} so the caller's objects are never modified.
     */
    public static List<Process> copyAll(Collection<Process> processes) {
        List<Process> copies = new ArrayList<>(processes.size());
        for (Process p : processes) {
            copies.add(p.copy());
        }
        return copies;
    }

    /**
     * The sample traces' queue entry format, e.g. {@code P1 (remaining: 4, Priority: 2)}.
     * Printing a {@code List<Process>} therefore gives {@code [P2 (remaining: 7, Priority: 3), ...]}.
     */
    @Override
    public String toString() {
        return name + " (remaining: " + remainingTime + ", Priority: " + priority + ")";
    }
}
