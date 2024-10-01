package org.eqasim.papers.dourdan_feeder_drt_2024;

import org.eqasim.ile_de_france.IDFConfigurator;
import org.eqasim.papers.dourdan_feeder_drt_2024.analysis.cba.cba.CbaConfigGroup;
import org.eqasim.papers.dourdan_feeder_drt_2024.analysis.cba.cba.CbaModule;

import java.util.List;

public class Configurator extends IDFConfigurator {

    public Configurator() {
        super();
        this.registerOptionalConfigGroup(new CbaConfigGroup(), List.of(new CbaModule()));
    }
}
