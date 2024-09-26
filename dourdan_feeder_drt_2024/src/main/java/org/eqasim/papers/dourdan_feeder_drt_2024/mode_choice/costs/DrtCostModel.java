package org.eqasim.papers.dourdan_feeder_drt_2024.mode_choice.costs;

import com.google.inject.Inject;
import org.eqasim.core.simulation.mode_choice.cost.AbstractCostModel;
import org.eqasim.papers.dourdan_feeder_drt_2024.mode_choice.parameters.CostParameters;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.contribs.discrete_mode_choice.model.DiscreteModeChoiceTrip;
import org.matsim.core.utils.geometry.CoordUtils;

import java.util.List;

public class DrtCostModel extends AbstractCostModel {
    private final CostParameters costParameters;
    @Inject
    protected DrtCostModel(CostParameters costParameters) {
        super("drt");
        this.costParameters = costParameters;

    }

    @Override
    public double calculateCost_MU(Person person, DiscreteModeChoiceTrip trip, List<? extends PlanElement> list) {
        double distance = CoordUtils.calcEuclideanDistance(trip.getOriginActivity().getCoord(), trip.getDestinationActivity().getCoord()) / 1000;
        return this.costParameters.drtCost_EUR_access + this.costParameters.drtCost_EUR_km * distance;
    }
}
