package org.eqasim.papers.dourdan_feeder_drt_2024;

import org.eqasim.core.scenario.validation.VehiclesValidator;
import org.eqasim.core.simulation.analysis.EqasimAnalysisModule;
import org.eqasim.core.simulation.mode_choice.EqasimModeChoiceModule;
import org.eqasim.papers.dourdan_feeder_drt_2024.mode_choice.ModeChoiceModule;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.CommandLine;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;
import org.matsim.core.scenario.ScenarioUtils;

public class RunSimulation {
    public static void main(String[] args) throws CommandLine.ConfigurationException {
        CommandLine cmd = new CommandLine.Builder(args) //
                .requireOptions("config-path") //
                .allowPrefixes("mode-choice-parameter", "cost-parameter") //
                .build();

        Configurator configurator = new Configurator(cmd);
        Config config = ConfigUtils.loadConfig(cmd.getOptionStrict("config-path"), new ConfigGroup[0]);
        configurator.updateConfig(config);
        cmd.applyConfiguration(config);
        VehiclesValidator.validate(config);

        Scenario scenario = ScenarioUtils.createScenario(config);
        configurator.configureScenario(scenario);
        ScenarioUtils.loadScenario(scenario);
        configurator.adjustScenario(scenario);

        Controler controller = new Controler(scenario);
        configurator.configureController(controller);
        controller.addOverridingModule(new EqasimAnalysisModule());
        controller.addOverridingModule(new EqasimModeChoiceModule());
        controller.addOverridingModule(new ModeChoiceModule(cmd));
        controller.run();
    }
}
