package org.eqasim.papers.gpe2025.utils;

import org.eqasim.core.scenario.validation.VehiclesValidator;
import org.eqasim.papers.gpe2025.CustomConfigurator;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.config.CommandLine;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.routes.DefaultTransitPassengerRoute;
import org.matsim.pt.transitSchedule.api.*;

public class CheckPtRoutesAgainstTransitSchedule {

    static class RouteContext {
        DefaultTransitPassengerRoute route;
        TransitLine transitLine;
        TransitRoute transitRoute;
        TransitRouteStop accessTransitRouteStop;
        TransitRouteStop egressTransitRouteStop;
        Departure departure;


        public RouteContext(DefaultTransitPassengerRoute route, TransitLine transitLine, TransitRoute transitRoute, TransitRouteStop accessTransitRouteStop, TransitRouteStop egressTransitRouteStop, Departure departure) {
            this.route = route;
            this.transitLine = transitLine;
            this.transitRoute = transitRoute;
            this.accessTransitRouteStop = accessTransitRouteStop;
            this.egressTransitRouteStop = egressTransitRouteStop;
            this.departure = departure;
        }
    }

    public static RouteContext getRouteContext(DefaultTransitPassengerRoute route, TransitSchedule schedule) {
        TransitLine transitLine;
        TransitRoute transitRoute;
        TransitStopFacility accessStopFacility, egressStopFacility;
        if((transitLine = schedule.getTransitLines().getOrDefault(route.getLineId(), null)) == null) {
            throw new IllegalStateException("Line not found");
        }
        if((transitRoute = transitLine.getRoutes().getOrDefault(route.getRouteId(), null)) == null) {
            throw new IllegalStateException("Route not found");
        }
        if((accessStopFacility = schedule.getFacilities().getOrDefault(route.getAccessStopId(), null)) == null) {
            throw new IllegalStateException("Access stop not found");
        }
        if((egressStopFacility = schedule.getFacilities().getOrDefault(route.getEgressStopId(), null)) == null) {
            throw new IllegalStateException("Egress stop not found");
        }
        TransitRouteStop accessTransitRouteStop;
        TransitRouteStop egressTransitRouteStop;
        if((accessTransitRouteStop = transitRoute.getStop(accessStopFacility)) == null) {
            throw new IllegalStateException("Route doesn't pass by access stop");
        }
        if((egressTransitRouteStop = transitRoute.getStop(egressStopFacility)) == null ){
            throw new IllegalStateException("Route doesn't pass by egress stop");
        }
        double departureTime = route.getBoardingTime().seconds() - accessTransitRouteStop.getArrivalOffset().seconds();
        for(Departure departure: transitRoute.getDepartures().values()) {
            if(departure.getDepartureTime() == departureTime) {
                return new RouteContext(route, transitLine, transitRoute, accessTransitRouteStop, egressTransitRouteStop, departure);
            }
        }
        return new RouteContext(route, transitLine, transitRoute, accessTransitRouteStop, egressTransitRouteStop, null);

    }

    public static void main(String[] args) throws CommandLine.ConfigurationException {
        CommandLine commandLine = new CommandLine.Builder(args)
                .requireOptions("config-path", "plans-path", "schedule-path")
                .build();

        CustomConfigurator configurator = new CustomConfigurator(commandLine);
        Config config = ConfigUtils.loadConfig(commandLine.getOptionStrict("config-path"), new ConfigGroup[0]);
        configurator.updateConfig(config);
        commandLine.applyConfiguration(config);
        config.plans().setInputFile(commandLine.getOptionStrict("plans-path"));
        VehiclesValidator.validate(config);
        Scenario scenario = ScenarioUtils.createScenario(config);
        configurator.configureScenario(scenario);
        ScenarioUtils.loadScenario(scenario);
        configurator.adjustScenario(scenario);

        TransitSchedule schedule = CompareTransitSchedules.readTransitSchedule(commandLine.getOptionStrict("schedule-path"));

        for(Person person: scenario.getPopulation().getPersons().values()) {
            for(PlanElement planElement: person.getSelectedPlan().getPlanElements()) {
                if(planElement instanceof Leg leg && leg.getMode().equals(TransportMode.pt)) {
                    DefaultTransitPassengerRoute route = (DefaultTransitPassengerRoute) leg.getRoute();
                    RouteContext baselineContext =  getRouteContext(route, scenario.getTransitSchedule());
                    RouteContext scenarioContext = getRouteContext(route, schedule);
                    System.out.println("====");
                    System.out.println("Line : " + baselineContext.transitLine.getName());
                    System.out.println("From : " + baselineContext.accessTransitRouteStop.getStopFacility().getName() + ", ID: " + baselineContext.accessTransitRouteStop.getStopFacility().getId().toString());
                    System.out.println("Route ID : " + baselineContext.transitRoute.getId());
                    System.out.println("Going To : " + baselineContext.transitRoute.getStops().getLast().getStopFacility().getName());
                    System.out.println("Stopping at : " + baselineContext.egressTransitRouteStop.getStopFacility().getName() + ", ID: " + baselineContext.egressTransitRouteStop.getStopFacility().getId().toString());
                    System.out.println("Duration : " + (baselineContext.egressTransitRouteStop.getArrivalOffset().seconds() - baselineContext.accessTransitRouteStop.getArrivalOffset().seconds()));
                }
            }
        }
    }
}
