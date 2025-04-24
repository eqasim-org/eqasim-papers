package org.eqasim.papers.gpe2025.utils;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.CommandLine;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;

import java.util.List;

public class OneAgentPerTrip {

    public static void main(String[] args) throws CommandLine.ConfigurationException {
        CommandLine commandLine = new CommandLine.Builder(args)
                .requireOptions("input-path", "output-path")
                .allowOptions("override-mode")
                .build();

        String inputPath = commandLine.getOptionStrict("input-path");
        String outputPath = commandLine.getOptionStrict("output-path");
        String overrideMode = commandLine.getOption("override-mode").orElse(null);

        Scenario inputScenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());

        new PopulationReader(inputScenario).readFile(inputPath);

        Population population = PopulationUtils.createPopulation(inputScenario.getConfig());

        for(Person person: inputScenario.getPopulation().getPersons().values()) {
            List<TripStructureUtils.Trip> trips = TripStructureUtils.getTrips(person.getSelectedPlan());
            for(int tripIndex = 0; tripIndex < trips.size(); tripIndex++) {
                TripStructureUtils.Trip trip = trips.get(tripIndex);
                Person newPerson = inputScenario.getPopulation().getFactory().createPerson(Id.createPersonId(person.getId() + "_" + tripIndex));
                Plan plan = inputScenario.getPopulation().getFactory().createPlan();
                plan.addActivity(trip.getOriginActivity());
                if(overrideMode == null) {
                    for(PlanElement planElement: trip.getTripElements()) {
                        if(planElement instanceof Activity activity) {
                            plan.addActivity(activity);
                        } else if (planElement instanceof Leg leg) {
                            plan.addLeg(leg);
                        }
                    }
                } else {
                    plan.addLeg(inputScenario.getPopulation().getFactory().createLeg(overrideMode));
                }
                plan.addActivity(trip.getDestinationActivity());
                newPerson.addPlan(plan);
                newPerson.setSelectedPlan(plan);
                population.addPerson(newPerson);
                person.getAttributes().getAsMap().forEach(newPerson.getAttributes()::putAttribute);
            }
        }

        new PopulationWriter(population).write(outputPath);
    }
}
