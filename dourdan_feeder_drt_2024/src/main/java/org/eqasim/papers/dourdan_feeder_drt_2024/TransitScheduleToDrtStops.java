package org.eqasim.papers.dourdan_feeder_drt_2024;

import org.eqasim.core.scenario.cutter.extent.ScenarioExtent;
import org.eqasim.core.scenario.cutter.extent.ShapeScenarioExtent;
import org.matsim.api.core.v01.IdSet;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.common.util.DistanceUtils;
import org.matsim.core.config.CommandLine;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.TransportModeNetworkFilter;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.*;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;

public class TransitScheduleToDrtStops {

    private static TransitStopFacility projectTransitStopFacilityOnNetwork(TransitStopFacility transitStopFacility, Network network, TransitScheduleFactory transitScheduleFactory, double maxDistance) {
        Link link = NetworkUtils.getNearestLinkExactly(network, transitStopFacility.getCoord());
        if(link == null || DistanceUtils.calculateDistance(transitStopFacility.getCoord(), link.getCoord()) > maxDistance) {
            throw new IllegalStateException(String.format("Cannot find close enough link to stop %s with name %s", transitStopFacility.getId().toString(), transitStopFacility.getName()));
        }
        TransitStopFacility newFacility = transitScheduleFactory.createTransitStopFacility(transitStopFacility.getId(), link.getCoord(), transitStopFacility.getIsBlockingLane());
        newFacility.setLinkId(transitStopFacility.getLinkId());
        return newFacility;
    }

    public static void main(String[] args) throws CommandLine.ConfigurationException {
        CommandLine commandLine = new CommandLine.Builder(args)
                .requireOptions("schedule-path", "network-path", "output-path")
                .allowOptions("mode", "max-distance")
                .allowOptions("extent-path")
                .build();

        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new TransitScheduleReader(scenario).readFile(commandLine.getOptionStrict("schedule-path"));
        TransitSchedule transitSchedule = scenario.getTransitSchedule();

        new MatsimNetworkReader(scenario.getNetwork()).readFile(commandLine.getOptionStrict("network-path"));

        ScenarioExtent scenarioExtent = commandLine.getOption("extent-path").map(p -> {
            try {
                return new ShapeScenarioExtent.Builder(new File(p), Optional.empty(), Optional.empty()).build();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).orElse(null);


        // Making sure we do not process stops that are not within an area of interest
        if (scenarioExtent != null) {
            IdSet<TransitStopFacility> toRemove = new IdSet<>(TransitStopFacility.class);
            for(TransitStopFacility transitStopFacility: transitSchedule.getFacilities().values()) {
                if(! scenarioExtent.isInside(transitStopFacility.getCoord())) {
                    toRemove.add(transitStopFacility.getId());
                }
            }
            toRemove.stream().map(transitSchedule.getFacilities()::get).forEach(transitSchedule::removeStopFacility);
        }

        TransitSchedule newSchedule = ScenarioUtils.createScenario(ConfigUtils.createConfig()).getTransitSchedule();

        String networkMode = commandLine.getOption("mode").orElse("car");
        Network network = NetworkUtils.createNetwork();
        new TransportModeNetworkFilter(scenario.getNetwork()).filter(network, Set.of(networkMode));
        NetworkUtils.cleanNetwork(network, Set.of(networkMode));

        double maxDistance = commandLine.getOption("max-distance").map(Double::parseDouble).orElse(500.0);

        transitSchedule.getFacilities().values().stream().map(f -> projectTransitStopFacilityOnNetwork(f, network, transitSchedule.getFactory(), maxDistance)).forEach(newSchedule::addStopFacility);

        new TransitScheduleWriter(transitSchedule).writeFile(commandLine.getOptionStrict("output-path"));
    }
}
