package org.eqasim.papers.dourdan_feeder_drt_2024;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.IdSet;
import org.matsim.api.core.v01.Identifiable;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.CommandLine;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.pt.transitSchedule.api.*;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class RemoveTransitLines {

    public static void cleanTransitSchedule(TransitSchedule transitSchedule) {


        // Routes with no departures
        transitSchedule.getTransitLines().values()
                .forEach(l -> l.getRoutes().values().stream().filter(r -> r.getDepartures().isEmpty())
                        .toList()
                        .forEach(l::removeRoute));

        // Lines with no routes
        transitSchedule.getTransitLines().values().stream()
                .filter(l -> l.getRoutes().isEmpty())
                .toList()
                .forEach(transitSchedule::removeTransitLine);

        // Unused stops
        IdSet<TransitStopFacility> usedFacilities = new IdSet<>(TransitStopFacility.class);
        List<TransitStopFacility> unusedFacilities = new ArrayList<>();

        transitSchedule.getTransitLines().values().stream()
                .flatMap(l -> l.getRoutes().values().stream())
                .flatMap(r -> r.getStops().stream())
                .map(TransitRouteStop::getStopFacility)
                .map(Identifiable::getId)
                .forEach(usedFacilities::add);

        transitSchedule.getFacilities().values().stream().filter(f -> usedFacilities.contains(f.getId())).forEach(unusedFacilities::add);

        // Transfers with non existing facilities
        List<Tuple<Id<TransitStopFacility>, Id<TransitStopFacility>>> transfersToRemove = new ArrayList<>();
        MinimalTransferTimes.MinimalTransferTimesIterator iterator = transitSchedule.getMinimalTransferTimes().iterator();
        while(iterator.hasNext()) {
            // We check with used facilities to also handle transfers that were invalid even before running this program
            if(!usedFacilities.contains(iterator.getFromStopId()) || !usedFacilities.contains(iterator.getToStopId())) {
                transfersToRemove.add(new Tuple<>(iterator.getFromStopId(), iterator.getToStopId()));
            }
        }

        transfersToRemove.forEach(t -> transitSchedule.getMinimalTransferTimes().remove(t.getFirst(), t.getSecond()));
        unusedFacilities.forEach(transitSchedule::removeStopFacility);
    }

    public static void main(String[] args) throws CommandLine.ConfigurationException, IOException {
        CommandLine commandLine = new CommandLine.Builder(args)
                .requireOptions("schedule-path", "lines-path", "output-path")
                .build();

        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());

        new TransitScheduleReader(scenario).readFile(commandLine.getOptionStrict("schedule-path"));
        TransitSchedule transitSchedule = scenario.getTransitSchedule();
        IdSet<TransitLine> linesToRemove = new IdSet<>(TransitLine.class);

        BufferedReader reader = new BufferedReader(new FileReader(commandLine.getOptionStrict("lines-path")));
        boolean readHeader = false;
        String line;
        while((line = reader.readLine()) != null) {
            if(!readHeader) {
                assert line.equals("transit_line_id");
                readHeader = true;
                continue;
            }
            Id<TransitLine> transitLineId = Id.create(line, TransitLine.class);
            if(!transitSchedule.getTransitLines().containsKey(transitLineId)) {
                throw new IllegalStateException(String.format("TransitLine with id %s not found", transitLineId));
            }
            linesToRemove.add(transitLineId);
        }

        linesToRemove.stream().map(transitSchedule.getTransitLines()::get).forEach(transitSchedule::removeTransitLine);

        cleanTransitSchedule(transitSchedule);

        new TransitScheduleWriter(transitSchedule).writeFile(commandLine.getOptionStrict("output-path"));
    }
}
