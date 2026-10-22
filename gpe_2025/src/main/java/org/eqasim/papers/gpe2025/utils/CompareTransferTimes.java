package org.eqasim.papers.gpe2025.utils;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.IdSet;
import org.matsim.api.core.v01.Identifiable;
import org.matsim.core.config.CommandLine;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class CompareTransferTimes {


    public static Collection<TransitStopFacility> filterTransitStopsByMode(TransitSchedule transitSchedule, Set<String> modes) {
        IdSet<TransitStopFacility> transitStopFacilityIdSet = new IdSet<>(TransitStopFacility.class);
        transitSchedule.getTransitLines().values().stream().flatMap(line -> line.getRoutes().values().stream())
                .filter(r -> modes.isEmpty() || modes.contains(r.getTransportMode()))
                .flatMap(r -> r.getStops().stream())
                .map(TransitRouteStop::getStopFacility)
                .map(Identifiable::getId)
                .forEach(transitStopFacilityIdSet::add);

        return transitStopFacilityIdSet.stream().map(transitSchedule.getFacilities()::get).toList();
    }

    public static void main(String[] args) throws CommandLine.ConfigurationException {
        CommandLine commandLine = new CommandLine.Builder(args)
                .requireOptions("left-path", "right-path")
                .build();

        TransitSchedule left = CompareTransitSchedules.readTransitSchedule(commandLine.getOptionStrict("left-path"));
        TransitSchedule right = CompareTransitSchedules.readTransitSchedule(commandLine.getOptionStrict("right-path"));

        long differences = 0;

        long currentIndex = 0;

        Collection<Id<TransitStopFacility>> transitStopFacilities = filterTransitStopsByMode(left, Set.of("rail", "subway", "tram", "bus")).stream().map(Identifiable::getId).toList();
        System.out.println("Number of stops: " + transitStopFacilities.size());
        for(Id<TransitStopFacility> fromId: transitStopFacilities) {
            if(currentIndex % 1000 == 0) {
                System.out.println("Current departure: " + currentIndex);
            }
            if(!right.getFacilities().containsKey(fromId)) {
                continue;
            }
            for(Id<TransitStopFacility> toId: transitStopFacilities)  {
                if(!right.getFacilities().containsKey(toId)) {
                    continue;
                }
                Double leftTransferTime = left.getMinimalTransferTimes().get(fromId, toId);
                Double rightTransferTime = right.getMinimalTransferTimes().get(fromId, toId);
                if(!Objects.equals(leftTransferTime, rightTransferTime)) {
                    System.out.println(String.format("Changing transfer times between stop of %s and stop of %s", left.getFacilities().get(fromId).getName(), right.getFacilities().get(toId).getName()));
                    differences++;
                }
            }
            currentIndex++;
        }
        System.out.println(differences);
    }
}
