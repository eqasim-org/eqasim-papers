package org.eqasim.papers.dourdan_feeder_drt_2024.mode_choice.parameters;

import org.eqasim.ile_de_france.mode_choice.parameters.IDFCostParameters;

public class CostParameters extends IDFCostParameters {

    public double drtCost_EUR_access;
    public double drtCost_EUR_km;
    public double feederDrtCost_EUR_access;
    public double feederDrtCost_EUR_km;

    public static CostParameters buildDefault() {
        return new CostParameters();
    }
}
