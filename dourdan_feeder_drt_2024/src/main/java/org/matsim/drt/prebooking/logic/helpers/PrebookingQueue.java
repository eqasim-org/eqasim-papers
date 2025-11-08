//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package org.matsim.contrib.drt.prebooking.logic.helpers;

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.contrib.drt.prebooking.PrebookingManager;
import org.matsim.contrib.dvrp.passenger.PassengerGroupIdentifier;
import org.matsim.core.mobsim.framework.MobsimAgent;
import org.matsim.core.mobsim.framework.MobsimPassengerAgent;
import org.matsim.core.mobsim.framework.events.MobsimBeforeSimStepEvent;
import org.matsim.core.mobsim.framework.listeners.MobsimBeforeSimStepListener;

public class PrebookingQueue implements MobsimBeforeSimStepListener {
    private final PrebookingManager prebookingManager;
    private PriorityQueue<ScheduledSubmission> queue = new PriorityQueue();
    private Integer sequence = 0;
    private double currentTime = Double.NEGATIVE_INFINITY;
    private final PassengerGroupIdentifier groupIdentifier;

    public PrebookingQueue(PrebookingManager prebookingManager, PassengerGroupIdentifier groupIdentifier) {
        this.prebookingManager = prebookingManager;
        this.groupIdentifier = groupIdentifier;
    }

    public void notifyMobsimBeforeSimStep(MobsimBeforeSimStepEvent event) {
        this.performSubmissions(event.getSimulationTime());
    }

    private void performSubmissions(double time) {
        this.currentTime = time;
        Map<Id<PassengerGroupIdentifier.PassengerGroup>, List<ScheduledSubmission>> groups = new LinkedHashMap();

        while(!this.queue.isEmpty() && ((ScheduledSubmission)this.queue.peek()).submissionTime <= time) {
            ScheduledSubmission item = (ScheduledSubmission)this.queue.poll();

            if(item.agent.getState().equals(MobsimAgent.State.ABORT)) {
                continue;
            }

            Optional<Id<PassengerGroupIdentifier.PassengerGroup>> groupId = this.groupIdentifier.getGroupId((MobsimPassengerAgent)item.agent);
            if (groupId.isEmpty()) {
                this.prebookingManager.prebook(item.agent(), item.leg(), item.departureTime());
            } else {
                ((List)groups.computeIfAbsent((Id)groupId.get(), (k) -> new ArrayList())).add(item);
            }
        }

        for(List<ScheduledSubmission> group : groups.values()) {
            List<PrebookingManager.PersonLeg> personsLegs = group.stream().map((entry) -> new PrebookingManager.PersonLeg(entry.agent, entry.leg)).toList();
            this.prebookingManager.prebook(personsLegs, ((ScheduledSubmission)group.get(0)).departureTime);
        }

    }

    public void performInitialSubmissions() {
        this.performSubmissions(Double.NEGATIVE_INFINITY);
    }

    public void schedule(double submissionTime, MobsimAgent agent, Leg leg, double departureTime) {
        Preconditions.checkArgument(submissionTime > this.currentTime, "Can only submit future requests");
        synchronized(this.queue) {
            PriorityQueue var10000 = this.queue;
            Integer var8 = this.sequence;
            this.sequence = this.sequence + 1;
            var10000.add(new ScheduledSubmission(submissionTime, agent, leg, departureTime, var8));
        }
    }

    private static record ScheduledSubmission(double submissionTime, MobsimAgent agent, Leg leg, double departureTime, int sequence) implements Comparable<ScheduledSubmission> {
        public int compareTo(ScheduledSubmission o) {
            int comparison = Double.compare(this.submissionTime, o.submissionTime);
            return comparison != 0 ? comparison : Integer.compare(this.sequence, o.sequence);
        }
    }
}
