package org.eqasim.papers.gpe2025.utils;

import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.CommandLine;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.*;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class ExportTransitScheduleToTimeTable {

    static class TimeTableElement {
        TransitStopFacility origin;
        TransitStopFacility destination;
        double arrivalTime;
        double departureTime;
        TransitLine transitLine;
        TransitRoute transitRoute;
        Departure departure;

        public TimeTableElement(TransitStopFacility origin, TransitStopFacility destination, double departureTime, double arrivalTime, TransitLine transitLine, TransitRoute transitRoute, Departure departure) {
            this.origin = origin;
            this.destination = destination;
            this.departureTime = departureTime;
            this.arrivalTime = arrivalTime;
            this.transitLine = transitLine;
            this.transitRoute = transitRoute;
            this.departure = departure;
        }
    }

    public static Collection<TimeTableElement> transitScheduleToTimeTable(TransitSchedule transitSchedule, Set<String> modes) {
        List<TimeTableElement> elements = new ArrayList<>();
        for(TransitLine transitLine: transitSchedule.getTransitLines().values()) {
            for(TransitRoute transitRoute: transitLine.getRoutes().values()) {
                if(!modes.isEmpty() && !modes.contains(transitRoute.getTransportMode())) {
                    continue;
                }
                for(Departure departure: transitRoute.getDepartures().values()) {
                    for(int i=0; i<transitRoute.getStops().size(); i++) {
                        TransitRouteStop originRouteStop = transitRoute.getStops().get(i);
                        for(int j=i+1; j<transitRoute.getStops().size(); j++) {
                            TransitRouteStop destinationRouteStop = transitRoute.getStops().get(j);
                            elements.add(new TimeTableElement(originRouteStop.getStopFacility(), destinationRouteStop.getStopFacility(),
                                    departure.getDepartureTime() + originRouteStop.getDepartureOffset().seconds(),
                                    departure.getDepartureTime() + destinationRouteStop.getArrivalOffset().seconds(),
                                    transitLine,
                                    transitRoute,
                                    departure));
                        }
                    }
                }
            }
        }
        return elements;
    }

    public static void writeTimeTable(String outputPath, Collection<TimeTableElement> timeTable) throws IOException {
        BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath));
        String[] header = new String[]{"origin_stop_id",
                "destination_stop_id",
                "origin_stop_name",
                "destination_stop_name",
                "departure_id",
                "departure_time",
                "arrival_time",
                "line_id",
                "line_name",
                "transit_mode"};
        writer.write(String.join(";", header) + "\n");
        for(TimeTableElement element: timeTable) {
            writer.write(String.join(";", new String[]{
                    element.origin.getId().toString(),
                    element.destination.getId().toString(),
                    element.origin.getName(),
                    element.destination.getName(),
                    element.departure.getId().toString(),
                    String.valueOf(element.departureTime),
                    String.valueOf(element.arrivalTime),
                    element.transitLine.getId().toString(),
                    element.transitLine.getName(),
                    element.transitRoute.getTransportMode()
            }));
        }
        writer.close();
    }

    public static void main(String args[]) throws CommandLine.ConfigurationException, IOException {
        CommandLine commandLine = new CommandLine.Builder(args)
                .requireOptions("schedule-path", "output-path").build();


        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new TransitScheduleReader(scenario).readFile(commandLine.getOptionStrict("schedule-path"));

        writeTimeTable(commandLine.getOptionStrict("output-path"),
                transitScheduleToTimeTable(scenario.getTransitSchedule(), Set.of("rail", "tram", "subway")));
    }
}
