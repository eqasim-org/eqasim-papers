package org.eqasim.papers.gpe2025.utils;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.CommandLine;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.routes.DefaultTransitPassengerRoute;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.List;

public class ExtractPtRoutingCosts {

    public static void extract(Population population, String outputPath) throws IOException {
        Writer fileWriter = new BufferedWriter(new FileWriter(outputPath));
        fileWriter.write("person_id;trip_id;routingCost\n");

        for(Person person: population.getPersons().values()) {
            List<TripStructureUtils.Trip> trips = TripStructureUtils.getTrips(person.getSelectedPlan());
            for(int i=0; i<trips.size(); i++) {
                TripStructureUtils.Trip trip = trips.get(i);
                for(Leg leg: trip.getLegsOnly()) {
                    if(leg.getMode().equals("pt")) {
                        if(leg.getRoute() instanceof DefaultTransitPassengerRoute defaultTransitPassengerRoute) {
                            String s = String.format("%s;%d;%f\n", person.getId().toString(), i, defaultTransitPassengerRoute.totalPtRoutingCost);
                            fileWriter.write(s);
                            break;
                        } else {
                            throw new IllegalStateException("Exptected DefaultTransitPassengerRoute in pt leg");
                        }
                    }
                }
            }
        }
        fileWriter.close();
    }

    public static void main(String[] args) throws CommandLine.ConfigurationException, IOException {
        CommandLine commandLine = new CommandLine.Builder(args)
                .requireOptions("input-path", "output-path")
                .build();

        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new PopulationReader(scenario).readFile(commandLine.getOptionStrict("input-path"));

        extract(scenario.getPopulation(), commandLine.getOptionStrict("output-path"));
    }
}
