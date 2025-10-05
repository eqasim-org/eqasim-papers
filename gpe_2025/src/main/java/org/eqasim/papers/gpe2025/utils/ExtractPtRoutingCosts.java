package org.eqasim.papers.gpe2025.utils;

import ch.sbb.matsim.routing.pt.raptor.RaptorParametersForPerson;
import org.eqasim.core.simulation.modes.transit_with_abstract_access.routing.DefaultAbstractAccessRoute;
import org.eqasim.core.simulation.modes.transit_with_abstract_access.routing.TransitWithAbstractAccessRoutingModule;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.router.TripStructureUtils;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.List;

public class ExtractPtRoutingCosts {

    public static void extract(Population population, String outputPath, RaptorParametersForPerson raptorParametersForPerson) throws IOException {
        Writer fileWriter = new BufferedWriter(new FileWriter(outputPath));
        fileWriter.write("person_id;trip_id;mode;routingCost\n");

        for(Person person: population.getPersons().values()) {
            List<TripStructureUtils.Trip> trips = TripStructureUtils.getTrips(person.getSelectedPlan());
            for(int i=0; i<trips.size(); i++) {
                TripStructureUtils.Trip trip = trips.get(i);

                double routingCost = 0;

                boolean skipTrip = false;

                String routingMode = trip.getLegsOnly().getFirst().getRoutingMode();

                if (routingMode.equals("pt") || (routingMode.equals("transitWithAbstractAccess") && trip.getLegsOnly().stream().map(Leg::getMode).noneMatch(TransitWithAbstractAccessRoutingModule.ABSTRACT_ACCESS_LEG_MODE_NAME::equals)) ) {
                   routingCost = TransitWithAbstractAccessRoutingModule.calcPtRoutingCost(trip.getTripElements(), trip.getOriginActivity().getCoord(), trip.getDestinationActivity().getCoord(), person, raptorParametersForPerson);
                } else if(routingMode.equals("transitWithAbstractAccess")) {
                    boolean foundLeg = false;
                    for(Leg leg: trip.getLegsOnly()) {
                        if(leg.getMode().equals(TransitWithAbstractAccessRoutingModule.ABSTRACT_ACCESS_LEG_MODE_NAME)) {
                            if(leg.getRoute() instanceof DefaultAbstractAccessRoute defaultAbstractAccessRoute) {
                                routingCost = defaultAbstractAccessRoute.getTotalRoutingCost();
                                foundLeg = true;
                                if(Double.isNaN(routingCost)) {
                                    throw new IllegalStateException("Unexpected NaN value");
                                }
                                break;
                            } else {
                                throw new IllegalStateException(String.format("Unexpected DefaultAbstractAccessRoute in %s leg",  leg.getMode()));
                            }
                        }
                    }
                    if(!foundLeg) {
                        throw new IllegalStateException("Could not find leg with mode " + TransitWithAbstractAccessRoutingModule.ABSTRACT_ACCESS_LEG_MODE_NAME);
                    }
                } else {
                    skipTrip = true;
                }

                if(!skipTrip) {
                    String s = String.format("%s;%d;%s;%f\n", person.getId().toString(), i, routingMode, routingCost);
                    fileWriter.write(s);
                }
            }
        }
        fileWriter.close();
    }
}
