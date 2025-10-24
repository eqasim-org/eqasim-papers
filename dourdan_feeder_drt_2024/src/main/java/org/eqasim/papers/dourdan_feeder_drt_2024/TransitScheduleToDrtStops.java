package org.eqasim.papers.dourdan_feeder_drt_2024;

import org.eqasim.core.scenario.cutter.extent.ScenarioExtent;
import org.eqasim.core.scenario.cutter.extent.ShapeScenarioExtent;
import org.matsim.api.core.v01.IdSet;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.CommandLine;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.pt.transitSchedule.api.TransitScheduleWriter;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

public class TransitScheduleToDrtStops {

    public static void main(String[] args) throws CommandLine.ConfigurationException {
        CommandLine commandLine = new CommandLine.Builder(args)
                .requireOptions("schedule-path", "output-path")
                .allowOptions("extent-path")
                .build();

        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new TransitScheduleReader(scenario).readFile(commandLine.getOptionStrict("schedule-path"));
        TransitSchedule transitSchedule = scenario.getTransitSchedule();

        ScenarioExtent scenarioExtent = commandLine.getOption("extent-path").map(p -> {
            try {
                return new ShapeScenarioExtent.Builder(new File(p), Optional.empty(), Optional.empty()).build();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).orElse(null);

        if (scenarioExtent != null) {
            IdSet<TransitStopFacility> toRemove = new IdSet<>(TransitStopFacility.class);
            for(TransitStopFacility transitStopFacility: transitSchedule.getFacilities().values()) {
                if(! scenarioExtent.isInside(transitStopFacility.getCoord())) {
                    toRemove.add(transitStopFacility.getId());
                }
            }
            toRemove.stream().map(transitSchedule.getFacilities()::get).forEach(transitSchedule::removeStopFacility);
        }

        transitSchedule.getTransitLines().values().stream().toList().forEach(transitSchedule::removeTransitLine);

        new TransitScheduleWriter(transitSchedule).writeFile(commandLine.getOptionStrict("output-path"));
    }
}
