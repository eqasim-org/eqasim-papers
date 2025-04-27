//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package org.eqasim.papers.gpe2025;

import org.eqasim.core.scenario.validation.VehiclesValidator;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.CommandLine;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;
import org.matsim.core.scenario.ScenarioUtils;

public class RunSimulation {
    public RunSimulation() {
    }

    public static void main(String[] args) throws CommandLine.ConfigurationException {
        CommandLine cmd = (new CommandLine.Builder(args)).requireOptions(new String[]{"config-path"}).allowPrefixes(new String[]{"mode-choice-parameter", "cost-parameter"}).build();
        CustomConfigurator configurator = new CustomConfigurator(cmd);
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
        controller.run();
    }
}
