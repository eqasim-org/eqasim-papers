package org.eqasim.papers.dourdan_feeder_drt_2024;

import org.eqasim.ile_de_france.IDFConfigurator;
import org.eqasim.papers.dourdan_feeder_drt_2024.analysis.cba.cba.CbaConfigGroup;
import org.eqasim.papers.dourdan_feeder_drt_2024.analysis.cba.cba.CbaModule;
import org.matsim.core.config.CommandLine;

import java.util.List;

public class Configurator extends IDFConfigurator {

    public Configurator(CommandLine commandLine) {
        super(commandLine);
        this.registerConfigGroup(new CbaConfigGroup(), true);
        this.registerModule(new CbaModule(), CbaConfigGroup.GROUP_NAME);
    }
}
