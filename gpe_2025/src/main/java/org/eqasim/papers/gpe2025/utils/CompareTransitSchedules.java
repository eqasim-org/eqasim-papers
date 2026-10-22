package org.eqasim.papers.gpe2025.utils;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.IdSet;
import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.common.util.DistanceUtils;
import org.matsim.core.config.CommandLine;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.*;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class CompareTransitSchedules {

    public static TransitSchedule readTransitSchedule(String path) {
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new TransitScheduleReader(scenario).readFile(path);
        return scenario.getTransitSchedule();
    }

    private static boolean compareStopFacilities(TransitStopFacility baselineFacility, TransitStopFacility scenarioFacility) {
        return baselineFacility.getId().equals(scenarioFacility.getId())
                && baselineFacility.getName().equals(scenarioFacility.getName())
                && baselineFacility.getStopAreaId().equals(scenarioFacility.getStopAreaId())
                && baselineFacility.getLinkId().equals(scenarioFacility.getLinkId())
                && DistanceUtils.calculateDistance(baselineFacility.getCoord(), scenarioFacility.getCoord()) < 10;
    }

    private static void compareStops(TransitSchedule baselineSchedule, TransitSchedule scenarioSchedule) {
        long newStops = 0;
        long modifiedStops = 0;
        long removedStops = 0;

        for(TransitStopFacility baselineFacility: baselineSchedule.getFacilities().values()) {
            TransitStopFacility scenarioFacility;
            if((scenarioFacility = scenarioSchedule.getFacilities().getOrDefault(baselineFacility.getId(), null)) != null) {
                if(!compareStopFacilities(baselineFacility, scenarioFacility)) {
                    modifiedStops++;
                }
            } else {
                removedStops++;
                System.out.println("====");
                for(TransitStopFacility otherScenarioFacility: scenarioSchedule.getFacilities().values()) {
                    if(baselineFacility.getCoord().equals(otherScenarioFacility.getCoord())) {
                        //System.out.println(String.format("Facility %s might have been replaced by Facility %s on exact coordinates", baselineFacility.getName(), otherScenarioFacility.getName()));
                    }
                    if(DistanceUtils.calculateDistance(otherScenarioFacility.getCoord(), baselineFacility.getCoord()) < 10) {
                        //System.out.println(String.format("Facility %s might have been replaced by Facility %s", baselineFacility.getName(), otherScenarioFacility.getName()));
                    }
                }
            }
        }
        newStops = scenarioSchedule.getFacilities().keySet().stream().filter(id -> !baselineSchedule.getFacilities().containsKey(id)).count();

        System.out.println(String.format("%d stops in baseline vs %d stops in scenario", baselineSchedule.getFacilities().size(), scenarioSchedule.getFacilities().size()));
        System.out.println("removed stops: " + removedStops);
        System.out.println("new stops: " + newStops);
        System.out.println("modified stops: " + modifiedStops);
    }

    private static void compareTransfers(TransitSchedule baselineSchedule, TransitSchedule scenarioSchedule) {
        MinimalTransferTimes.MinimalTransferTimesIterator iterator = baselineSchedule.getMinimalTransferTimes().iterator();
        while(iterator.hasNext()) {
            iterator.next();
            if(!scenarioSchedule.getFacilities().containsKey(iterator.getFromStopId()) ||  !scenarioSchedule.getFacilities().containsKey(iterator.getToStopId())) {
                continue;
            }
            double newValue = scenarioSchedule.getMinimalTransferTimes().get(iterator.getFromStopId(), iterator.getToStopId());
            if(Double.isNaN(newValue)) {
                System.out.println("Transfer removed");
            } else if (newValue != iterator.getSeconds()) {
                System.out.println("Transfer time changed");
            }
        }
    }

    public static void compareAreas(TransitSchedule baselineSchedule, TransitSchedule scenarioSchedule) {
        IdSet<TransitStopArea> baselineAreas = new IdSet<>(TransitStopArea.class);
        IdSet<TransitStopArea> scenarioAreas = new IdSet<>(TransitStopArea.class);
        baselineSchedule.getFacilities().values().stream().map(TransitStopFacility::getStopAreaId).forEach(baselineAreas::add);
        scenarioSchedule.getFacilities().values().stream().map(TransitStopFacility::getStopAreaId).forEach(scenarioAreas::add);

        long newAreas = scenarioAreas.stream().filter(id -> !baselineAreas.contains(id)).count();
        long removedAreas = baselineAreas.stream().filter(id -> !scenarioAreas.contains(id)).count();

        System.out.println("new areas: " + newAreas);
        System.out.println("removed areas: " + removedAreas);
    }

    public static void compareLinesAndRoutes(TransitSchedule baselineSchedule, TransitSchedule scenarioSchedule) {
        long newLines = 0;
        long newRoutes = 0;
        long removedLines = 0;
        long removedRoutes = 0;
        long modifiedRoutes = 0;
        for(TransitLine baselineLine: baselineSchedule.getTransitLines().values()) {
            TransitLine scenarioLine;
            if((scenarioLine = scenarioSchedule.getTransitLines().getOrDefault(baselineLine.getId(), null)) != null) {
                removedRoutes += baselineLine.getRoutes().keySet().stream().filter(id -> !scenarioLine.getRoutes().containsKey(id)).count();
                newRoutes += scenarioLine.getRoutes().keySet().stream().filter(id -> !baselineLine.getRoutes().containsKey(id)).count();
                for(TransitRoute baselineRoute: baselineLine.getRoutes().values()) {
                    TransitRoute scenarioRoute;
                    if((scenarioRoute = scenarioLine.getRoutes().get(baselineRoute.getId())) == null) {
                        continue;
                    }
                    List<TransitRouteStop> baselineRouteStops = baselineRoute.getStops();
                    List<TransitRouteStop> scenarioRouteStops = scenarioRoute.getStops();
                    if(baselineRouteStops.size() > scenarioRouteStops.size()) {
                        throw new IllegalStateException();
                    }
                    if(scenarioRouteStops.size() > baselineRouteStops.size()) {
                        modifiedRoutes ++;
                    }

                    List<Departure> baselineDepartures = baselineRoute.getDepartures().values().stream().sorted(Comparator.comparing(Departure::getDepartureTime)).toList();
                    List<Departure> scenarioDepartures = scenarioRoute.getDepartures().values().stream().sorted(Comparator.comparing(Departure::getDepartureTime)).toList();

                    if(baselineDepartures.size() != scenarioDepartures.size()) {
                        System.out.println("Skipped route because of different number of departures in line: " + baselineLine.getName());
                        continue;
                        //throw new IllegalStateException();
                    }
                    for(int i=0; i<baselineDepartures.size(); i++) {
                        Departure baselineDeparture = baselineDepartures.get(i);
                        Departure scenarioDeparture = scenarioDepartures.get(i);
                        int currentScenarioRouteStopsIndex = -1;
                        for(int j=0; j<baselineRouteStops.size(); j++) {
                            TransitRouteStop baselineRouteStop = baselineRouteStops.get(j);
                            TransitRouteStop scenarioRouteStop = null;
                            int scenarioRouteStopIndex = -1;
                            for(int k=currentScenarioRouteStopsIndex+1; k<scenarioRouteStops.size(); k++) {
                                TransitRouteStop routeStop = scenarioRouteStops.get(k);
                                if(routeStop.getStopFacility().getId().equals(baselineRouteStop.getStopFacility().getId())
                                        || DistanceUtils.calculateDistance(routeStop.getStopFacility().getCoord(), baselineRouteStop.getStopFacility().getCoord()) < 10) {
                                    scenarioRouteStop = routeStop;
                                    scenarioRouteStopIndex = k;
                                    currentScenarioRouteStopsIndex = k;
                                    break;
                                }
                            }
                            if(scenarioRouteStop == null) {
                                throw new IllegalStateException("Corresponding stop not found");
                            }
                            double baselineTime = baselineDeparture.getDepartureTime() + baselineRouteStop.getArrivalOffset().seconds();
                            double scenarioTime = scenarioDeparture.getDepartureTime() + scenarioRouteStop.getArrivalOffset().seconds();
                            if(!Objects.equals(baselineTime, scenarioTime)) {
                                throw new IllegalStateException();
                            }
                        }
                    }
                }
            } else {
                removedLines++;
            }
        }
        newLines = scenarioSchedule.getTransitLines().keySet().stream().filter(id -> !baselineSchedule.getTransitLines().containsKey(id)).count();

        System.out.println("New Lines: " + newLines);
        System.out.println("New Routes: " + newRoutes);

        System.out.println("Removed Lines " + removedLines);
        System.out.println("Removed Routes " + removedRoutes);
        System.out.println("Modified Routes " + modifiedRoutes);
    }

    public static void main(String[] args) throws CommandLine.ConfigurationException {
        CommandLine commandLine = new CommandLine.Builder(args)
                .requireOptions("baseline-schedule-path", "scenario-schedule-path")
                .build();

        TransitSchedule baselineSchedule = readTransitSchedule(commandLine.getOptionStrict("baseline-schedule-path"));
        TransitSchedule scenarioSchedule = readTransitSchedule(commandLine.getOptionStrict("scenario-schedule-path"));

        compareAreas(baselineSchedule, scenarioSchedule);
        compareStops(baselineSchedule, scenarioSchedule);
        compareLinesAndRoutes(baselineSchedule, scenarioSchedule);
        compareTransfers(baselineSchedule, scenarioSchedule);
    }
}
