package org.eqasim.papers.gpe2025.utils;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.CommandLine;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.pt.transitSchedule.api.*;

import java.util.ArrayList;
import java.util.List;

public class RemoveTransfersFromSchedule {
    public static void main(String[] args) throws CommandLine.ConfigurationException {
        CommandLine commandLine = new CommandLine.Builder(args)
                .requireOptions("input-path", "output-path").build();
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new TransitScheduleReader(scenario).readFile(commandLine.getOptionStrict("input-path"));
        TransitSchedule transitSchedule = scenario.getTransitSchedule();


        List<Tuple<Id<TransitStopFacility>, Id<TransitStopFacility>>> toRemove = new ArrayList<>();
        MinimalTransferTimes.MinimalTransferTimesIterator iterator = transitSchedule.getMinimalTransferTimes().iterator();
        while (iterator.hasNext()) {
            iterator.next();
            toRemove.add(new Tuple<>(iterator.getFromStopId(), iterator.getToStopId()));
        }
        toRemove.forEach(t -> transitSchedule.getMinimalTransferTimes().remove(t.getFirst(), t.getSecond()));

        new TransitScheduleWriter(transitSchedule).writeFile(commandLine.getOptionStrict("output-path"));
    }
}
