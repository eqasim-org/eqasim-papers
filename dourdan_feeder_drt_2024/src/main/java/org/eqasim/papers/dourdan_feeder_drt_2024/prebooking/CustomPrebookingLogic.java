package org.eqasim.papers.dourdan_feeder_drt_2024.prebooking;

import com.google.common.base.Preconditions;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.contrib.drt.prebooking.logic.AttributeBasedPrebookingLogic;
import org.matsim.contrib.drt.prebooking.logic.helpers.PopulationIterator;
import org.matsim.contrib.drt.prebooking.logic.helpers.PrebookingQueue;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.dvrp.run.AbstractDvrpModeQSimModule;
import org.matsim.core.mobsim.framework.events.MobsimInitializedEvent;
import org.matsim.core.mobsim.framework.listeners.MobsimInitializedListener;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.utils.timing.TimeInterpretation;
import org.matsim.core.utils.timing.TimeTracker;

import java.util.Map;

public class CustomPrebookingLogic implements org.matsim.contrib.drt.prebooking.logic.PrebookingLogic, MobsimInitializedListener {

    private final String mode;
    private final PrebookingQueue prebookingQueue;
    private final PopulationIterator.PopulationIteratorFactory populationIteratorFactory;
    private final TimeInterpretation timeInterpretation;

    private final Map<String, Float> prebookingHorizonPerRoutingMode;

    public CustomPrebookingLogic(String mode, PrebookingQueue prebookingQueue,
                                  PopulationIterator.PopulationIteratorFactory populationIteratorFactory, TimeInterpretation timeInterpretation,
                                  Map<String, Float> prebookingHorizonPerRoutingMode) {
        this.prebookingQueue = prebookingQueue;
        this.populationIteratorFactory = populationIteratorFactory;
        this.mode = mode;
        this.timeInterpretation = timeInterpretation;
        this.prebookingHorizonPerRoutingMode =  prebookingHorizonPerRoutingMode;

    }

    @Override
    public void notifyMobsimInitialized(MobsimInitializedEvent e) {
        PopulationIterator populationIterator = populationIteratorFactory.create();

        while (populationIterator.hasNext()) {
            var personItem = populationIterator.next();

            TimeTracker timeTracker = new TimeTracker(timeInterpretation);

            for (TripStructureUtils.Trip trip : TripStructureUtils.getTrips(personItem.plan())) {
                timeTracker.addActivity(trip.getOriginActivity());

                for (PlanElement element : trip.getTripElements()) {
                    if (element instanceof Leg) {
                        Leg leg = (Leg) element;

                        if(!this.prebookingHorizonPerRoutingMode.containsKey(leg.getRoutingMode())) {
                            break;
                        }

                        if (mode.equals(leg.getMode())) {

                            Float prebookingHorizon = prebookingHorizonPerRoutingMode.get(leg.getRoutingMode());
                            if (prebookingHorizon == null) {
                                continue;
                            }
                            Preconditions.checkState(prebookingHorizon > 0,
                                    "Prebooking Horizon must be greater than 0");

                            Double submissionTime = Math.max(0, timeTracker.getTime().seconds() - prebookingHorizon);


                            prebookingQueue.schedule(submissionTime, personItem.agent(), leg, timeTracker.getTime().seconds());
                        }
                    }

                    timeTracker.addElement(element);
                }

            }
        }
        prebookingQueue.performInitialSubmissions();
    }

    static public AbstractDvrpModeQSimModule createModule(DrtConfigGroup drtConfig, Map<String, Float> prebookingHorizonPerRoutingMode) {
        return new AbstractDvrpModeQSimModule(drtConfig.getMode()) {
            @Override
            protected void configureQSim() {
                bindModal(CustomPrebookingLogic.class).toProvider(modalProvider(getter -> {
                    Preconditions.checkState(drtConfig.getPrebookingParams().isPresent());

                    return new CustomPrebookingLogic(drtConfig.getMode(),
                            getter.getModal(PrebookingQueue.class), getter.getModal(PopulationIterator.PopulationIteratorFactory.class),
                            getter.get(TimeInterpretation.class),
                            prebookingHorizonPerRoutingMode);
                }));
                addModalQSimComponentBinding().to(modalKey(CustomPrebookingLogic.class));
            }
        };
    }
}
