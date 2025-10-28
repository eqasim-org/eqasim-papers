package org.eqasim.papers.dourdan_feeder_drt_2024.mode_choice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.inject.Provides;
import org.eqasim.core.components.config.EqasimConfigGroup;
import org.eqasim.core.simulation.mode_choice.AbstractEqasimExtension;
import org.eqasim.core.simulation.mode_choice.ParameterDefinition;
import org.eqasim.ile_de_france.mode_choice.IDFModeChoiceModule;
import org.eqasim.ile_de_france.mode_choice.parameters.IDFCostParameters;
import org.eqasim.papers.dourdan_feeder_drt_2024.mode_choice.costs.FeederDrtCostModel;
import org.eqasim.papers.dourdan_feeder_drt_2024.mode_choice.parameters.CostParameters;
import org.matsim.core.config.CommandLine;
import org.matsim.core.controler.OutputDirectoryHierarchy;

import java.io.File;
import java.io.IOException;

public class ModeChoiceModule extends AbstractEqasimExtension {

    public static String SINGLE_USAGE_FLEETS_MODE_AVAILABILITY = "SingleUsageFleetsModeAvailability";
    public static String DUAL_USAGE_FLEETS_MODE_AVAILABILITY = "DualUsageFleetsModeAvailability";
    public static String FEEDER_DRT_COST_MODEL = "FeederDrtCostModel";

    private final CommandLine commandLine;

    public ModeChoiceModule(CommandLine commandLine) {
        this.commandLine = commandLine;
    }

    @Override
    protected void installEqasimExtension() {

        bindCostModel(FEEDER_DRT_COST_MODEL).to(FeederDrtCostModel.class);
        bind(IDFCostParameters.class).to(CostParameters.class);
    }

    @Provides
    public CostParameters provideCostParameters(EqasimConfigGroup config, OutputDirectoryHierarchy outputDirectoryHierarchy) throws IOException {
        CostParameters parameters = CostParameters.buildDefault();

        if (config.getCostParametersPath() != null) {
            ParameterDefinition.applyFile(new File(config.getCostParametersPath()), parameters);
        }

        ParameterDefinition.applyCommandLine("cost-parameter", commandLine, parameters);
        ObjectMapper om = new ObjectMapper(new YAMLFactory());
        om.writeValue(new File(outputDirectoryHierarchy.getOutputFilename("cost_params.yml")), parameters);

        return parameters;
    }
}
