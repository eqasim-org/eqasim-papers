package org.eqasim.papers.dourdan_feeder_drt_2024.mode_choice.costs;

import com.google.inject.Inject;
import org.eqasim.core.simulation.mode_choice.cost.AbstractCostModel;
import org.eqasim.core.simulation.modes.feeder_drt.config.MultiModeFeederDrtConfigGroup;
import org.eqasim.papers.dourdan_feeder_drt_2024.mode_choice.parameters.CostParameters;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.contribs.discrete_mode_choice.model.DiscreteModeChoiceTrip;
import org.matsim.core.config.Config;
import org.matsim.core.utils.geometry.CoordUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FeederDrtCostModel extends AbstractCostModel {

    private final CostParameters costParameters;
    private final Set<String> feederDrtModes;

    @Inject
    protected FeederDrtCostModel(CostParameters costParameters, Config config) {
        super("feederDrt");
        this.costParameters = costParameters;
        this.feederDrtModes = new HashSet<>();
        if(config.getModules().containsKey(MultiModeFeederDrtConfigGroup.GROUP_NAME)) {
            MultiModeFeederDrtConfigGroup multiModeFeederDrtConfigGroup = (MultiModeFeederDrtConfigGroup) config.getModules().get(MultiModeFeederDrtConfigGroup.GROUP_NAME);
            multiModeFeederDrtConfigGroup.modes().forEach(this.feederDrtModes::add);
        }
    }

    @Override
    public double calculateCost_MU(Person person, DiscreteModeChoiceTrip trip, List<? extends PlanElement> elements) {
        double distance = CoordUtils.calcEuclideanDistance(trip.getOriginActivity().getCoord(), trip.getDestinationActivity().getCoord()) / 1000;
        if(elements.stream().filter(planElement -> planElement instanceof Leg).map(planElement -> (Leg) planElement).map(Leg::getRoutingMode).anyMatch(this.feederDrtModes::contains)) {
            return this.costParameters.feederDrtCost_EUR_access + this.costParameters.feederDrtCost_EUR_km * distance;
        } else {
            return (1 + this.costParameters.feederDrtCost_EUR_km) * distance;
        }
    }
}
