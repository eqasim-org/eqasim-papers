package org.eqasim.papers.dourdan_feeder_drt_2024;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.IdSet;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.CommandLine;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.pt.transitSchedule.api.TransitScheduleWriter;

import java.io.*;

public class RemoveTransitLines {

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

        new TransitScheduleWriter(transitSchedule).writeFile(commandLine.getOptionStrict("output-path"));
    }
}
