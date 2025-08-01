package org.eqasim.papers.gpe2025;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.concurrent.ExecutionException;

import org.eqasim.core.scenario.routing.RunPopulationRouting;
import org.eqasim.core.simulation.modes.transit_with_abstract_access.utils.AdaptConfigForTransitWithAbstractAccess;
import org.eqasim.core.simulation.modes.transit_with_abstract_access.utils.CreateAbstractAccessItemsForTransitLines;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.matsim.core.config.CommandLine;

public class Gpe2025Tests {

    @Before
    public void setUp() throws IOException {
        URL fixtureUrl = getClass().getClassLoader().getResource("test_data");
        FileUtils.copyDirectory(new File(fixtureUrl.getPath()), new File("test_data/input"));
        FileUtils.forceMkdir(new File("test_data/output"));
    }

    @After
    public void tearDown() throws IOException {
        FileUtils.deleteDirectory(new File("test_data"));
    }


    @Test
    public void runTest() throws CommandLine.ConfigurationException, InterruptedException, IOException, ExecutionException {
        String basePath = "test_data";
        String prefix = "reduced_";

        String routedPlansPath = Paths.get(basePath, "output/routed_population.xml").toAbsolutePath().toString();

        RunPopulationRouting.main(new String[]{
                "--config-path", Paths.get(basePath, "input", String.format("%sconfig.xml", prefix)).toString(),
                "--output-path", routedPlansPath,
        });

        RunSimulation.main(new String[]{
                "--config-path", Paths.get(basePath, "input", String.format("%sconfig.xml", prefix)).toString(),
                "--config:plans.inputPlansFile", routedPlansPath,
                "--config:controller.outputDirectory", Paths.get(basePath, "output", "simulation").toString(),
                "--config:controller.lastIteration", "0"
        });

        CreateAbstractAccessItemsForTransitLines.main(new String[]{
                "--transit-schedule-path", Paths.get(basePath, "input", String.format("%stransit_schedule.xml.gz", prefix)).toString(),
                "--route-modes", "rail",
                "--radius", "1000",
                "--average-speed", "4.5",
                "--access-type", "bus",
                "--use-routed-distance", "false",
                "--output-path", Paths.get(basePath, "input", String.format("%sfeeders.xml", prefix)).toString()
        });

        AdaptConfigForTransitWithAbstractAccess.main(new String[] {
                "--input-config-path", Paths.get(basePath, "input", String.format("%sconfig.xml", prefix)).toString(),
                "--output-config-path", Paths.get(basePath, "input", String.format("%sconfig_feeders.xml", prefix)).toString(),
                "--mode-name", "transitWithAbstractAccess",
                "--accesses-file-path", Paths.get(basePath, "input", String.format("%sfeeders.xml", prefix)).toString()
        });

        RunSimulation.main(new String[]{
                "--config-path", Paths.get(basePath, "input", String.format("%sconfig_feeders.xml", prefix)).toString(),
                "--config:controller.outputDirectory", Paths.get(basePath, "output", "simulation_feeders").toString(),
                "--config:controller.lastIteration", "1"
        });
    }

}
