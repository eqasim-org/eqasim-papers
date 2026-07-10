package org.eqasim.papers.dourdan_feeder_drt_2024;

import org.eqasim.ile_de_france.IDFConfigurator;
import org.eqasim.papers.dourdan_feeder_drt_2024.mode_choice.ModeChoiceModule;
import org.matsim.core.config.CommandLine;

public class ModeChoiceConfigurator extends IDFConfigurator {
    public ModeChoiceConfigurator(CommandLine cmd) {
        super(cmd);

        registerModule(new ModeChoiceModule(cmd));
    }
}
