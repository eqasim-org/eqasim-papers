package org.eqasim.papers.gpe2025.utils;

import ch.sbb.matsim.routing.pt.raptor.RaptorParametersForPerson;
import org.eqasim.core.simulation.modes.transit_with_abstract_access.routing.DefaultAbstractAccessRoute;
import org.eqasim.core.simulation.modes.transit_with_abstract_access.routing.TransitWithAbstractAccessRoutingModule;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.pt.routes.DefaultTransitPassengerRoute;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.List;

public class ExtractPtRoutingCosts {

    public static void extract(RaptorParametersForPerson raptorParameters, Population population, String outputPath) throws IOException {
        Writer fileWriter = new BufferedWriter(new FileWriter(outputPath));
        fileWriter.write("person_id;trip_id;mode;routingCost\n");

        for(Person person: population.getPersons().values()) {
            List<TripStructureUtils.Trip> trips = TripStructureUtils.getTrips(person.getSelectedPlan());
            boolean addedBasePtRoutingCost = false;
            for(int i=0; i<trips.size(); i++) {
                TripStructureUtils.Trip trip = trips.get(i);

                double routingCost = 0;
                int feederTransfers = 0;

                String routingMode = null;
                boolean skipTrip = false;

                for(Leg leg: trip.getLegsOnly()) {
                    routingMode = leg.getRoutingMode();
                    if(!routingMode.equals("pt") && !routingMode.equals("transitWithAbstractAccess")) {
                        skipTrip = true;
                    }
                    if(leg.getMode().equals("pt")) {
                        if(leg.getRoute() instanceof DefaultTransitPassengerRoute defaultTransitPassengerRoute) {
                            if(!addedBasePtRoutingCost ) {
                                routingCost += defaultTransitPassengerRoute.totalPtRoutingCost;
                                addedBasePtRoutingCost = true;
                            }
                        } else {
                            throw new IllegalStateException("Exptected DefaultTransitPassengerRoute in pt leg");
                        }
                    } else if(leg.getMode().equals(TransitWithAbstractAccessRoutingModule.ABSTRACT_ACCESS_LEG_MODE_NAME)) {
                        feederTransfers++;
                        if(leg.getRoute() instanceof DefaultAbstractAccessRoute defaultAbstractAccessRoute) {
                            routingCost += defaultAbstractAccessRoute.getTravelTime().seconds() * raptorParameters.getRaptorParameters(person).getMarginalUtilityOfTravelTime_utl_s("bus");
                            routingCost += defaultAbstractAccessRoute.getWaitTime() * raptorParameters.getRaptorParameters(person).getMarginalUtilityOfWaitingPt_utl_s();
                        } else {
                            throw new IllegalStateException(String.format("Unexpected DefaultAbstractAccessRoute in %s leg",  leg.getMode()));
                        }
                    }
                }

                if(!skipTrip) {
                    if(!addedBasePtRoutingCost) {
                        feederTransfers--;
                    }
                    if(feederTransfers > 0) {
                        routingCost += feederTransfers * raptorParameters.getRaptorParameters(person).getTransferPenaltyFixCostPerTransfer();
                    }
                    String s = String.format("%s;%d;%s;%f\n", person.getId().toString(), i, routingMode, routingCost);
                    fileWriter.write(s);
                }
            }
        }
        fileWriter.close();
    }
}
