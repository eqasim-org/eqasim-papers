package org.eqasim.papers.gpe2025;

import org.eqasim.ile_de_france.IDFConfigurator;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.core.config.CommandLine;

import java.util.OptionalDouble;


public class CustomConfigurator extends IDFConfigurator {

    public static final double OFFSET = 3600;

    public CustomConfigurator(CommandLine cmd) {
        super(cmd);
    }

    public void adjustScenario(Scenario scenario) {
        super.adjustScenario(scenario);
        OptionalDouble lastPersonEndTime = scenario.getPopulation().getPersons().values().stream()
                .mapToDouble(p -> p.getSelectedPlan().getPlanElements().stream().mapToDouble(planElement -> {
                    if (planElement instanceof Activity activity) {
                        return activity.getEndTime().orElse(activity.getStartTime().orElse(-1));
                    } else if (planElement instanceof Leg leg) {
                        return leg.getDepartureTime().orElse(-1) + leg.getTravelTime().orElse(0);
                    }
                    throw new IllegalStateException("Unknown planElement type");
                }).max().orElse(-1))
                .filter(t -> t > 0)
                .max();

        if(lastPersonEndTime.isPresent()) {
            scenario.getConfig().qsim().setEndTime(Math.max(scenario.getConfig().qsim().getEndTime().orElse(-1), lastPersonEndTime.getAsDouble() + OFFSET));
        }
    }
}
