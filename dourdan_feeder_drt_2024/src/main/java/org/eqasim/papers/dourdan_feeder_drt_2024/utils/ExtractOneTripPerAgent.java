package org.eqasim.papers.dourdan_feeder_drt_2024.utils;

import org.eqasim.papers.dourdan_feeder_drt_2024.Configurator;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.CommandLine;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;

import java.util.*;
import java.util.stream.Collectors;

public class ExtractOneTripPerAgent {

    public static void main (String[] args) throws CommandLine.ConfigurationException {
        CommandLine cmd = new CommandLine.Builder(args)
                .requireOptions("config-path", "output-path")
                .allowOptions("modes")
                .build();

        Configurator configurator = new Configurator(cmd);
        Config config = ConfigUtils.loadConfig(cmd.getOptionStrict("config-path"), new ConfigGroup[0]);
        configurator.updateConfig(config);
        cmd.applyConfiguration(config);

        Scenario scenario = ScenarioUtils.createScenario(config);
        configurator.configureScenario(scenario);
        new PopulationReader(scenario).readFile(config.plans().getInputFileURL(config.getContext()).getFile());

        Population newPopulation = PopulationUtils.createPopulation(scenario.getConfig());


        Set<String> modes = cmd.getOption("modes").map(s -> Arrays.stream(s.split(",")).collect(Collectors.toSet())).orElse(new HashSet<>());

        for(Person person: scenario.getPopulation().getPersons().values()) {
            List<TripStructureUtils.Trip> trips = TripStructureUtils.getTrips(person.getSelectedPlan());
            for(int tripIndex = 0; tripIndex < trips.size(); tripIndex++) {
                TripStructureUtils.Trip trip = trips.get(tripIndex);
                if(!modes.isEmpty() && !modes.contains(trip.getLegsOnly().get(0).getRoutingMode())) {
                    continue;
                }
                Person newPerson = scenario.getPopulation().getFactory().createPerson(Id.createPersonId(person.getId() + "_" + tripIndex));
                Plan plan = scenario.getPopulation().getFactory().createPlan();
                plan.addActivity(trip.getOriginActivity());
                for(PlanElement planElement: trip.getTripElements()) {
                    if(planElement instanceof Activity activity) {
                        plan.addActivity(activity);
                    } else if (planElement instanceof Leg leg) {
                        plan.addLeg(leg);
                    }
                }
                plan.addActivity(trip.getDestinationActivity());
                newPerson.addPlan(plan);
                newPerson.setSelectedPlan(plan);
                person.getAttributes().getAsMap().forEach(newPerson.getAttributes()::putAttribute);
                newPopulation.addPerson(newPerson);
            }
        }

        new PopulationWriter(newPopulation).write(cmd.getOptionStrict("output-path"));

    }
}
