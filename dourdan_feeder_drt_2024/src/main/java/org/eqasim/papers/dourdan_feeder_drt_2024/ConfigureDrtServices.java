package org.eqasim.papers.dourdan_feeder_drt_2024;

import org.eqasim.core.components.config.EqasimConfigGroup;
import org.eqasim.core.simulation.mode_choice.constraints.leg_time.LegTimeConstraintConfigGroup;
import org.eqasim.core.simulation.mode_choice.constraints.leg_time.LegTimeConstraintSingleLegConfigGroup;
import org.eqasim.core.simulation.modes.drt.utils.AdaptConfigForDrt;
import org.eqasim.core.simulation.modes.feeder_drt.utils.AdaptConfigForFeederDrt;
import org.eqasim.papers.dourdan_feeder_drt_2024.mode_choice.ModeChoiceModule;
import org.matsim.contrib.drt.prebooking.PrebookingParams;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.drt.run.MultiModeDrtConfigGroup;
import org.matsim.core.config.CommandLine;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.utils.collections.Tuple;

import java.util.*;

public class ConfigureDrtServices {

    public static final String OFF_PEAK_AVAIlABILITY = "off_peak";
    public static final List<Tuple<Double, Double>> OFF_PEAK_TIMES = List.of(Tuple.of(0.0, 3600.0*7), Tuple.of(9.0*3600, 16.0*3600), Tuple.of(19.0*3600, 30.0*3600));

    private static void addOffPeakLegTimeConstraint(Config config, String mainMode) {
        LegTimeConstraintConfigGroup legTimeConstraintConfigGroup = LegTimeConstraintConfigGroup.getOrCreate(config);
        LegTimeConstraintSingleLegConfigGroup legTimeConstraintSingleLegConfigGroup = new LegTimeConstraintSingleLegConfigGroup();
        legTimeConstraintConfigGroup.addParameterSet(legTimeConstraintSingleLegConfigGroup);

        legTimeConstraintSingleLegConfigGroup.legMode = "drt";
        legTimeConstraintSingleLegConfigGroup.mainMode = mainMode;
        legTimeConstraintSingleLegConfigGroup.checkBothDepartureAndArrivalTimes = true;

        for(Tuple<Double, Double> slot: OFF_PEAK_TIMES) {
            LegTimeConstraintSingleLegConfigGroup.TimeSlotConfigGroup timeSlotConfigGroup = new LegTimeConstraintSingleLegConfigGroup.TimeSlotConfigGroup();
            timeSlotConfigGroup.beginTime = slot.getFirst();
            timeSlotConfigGroup.endTime = slot.getSecond();

            legTimeConstraintSingleLegConfigGroup.addParameterSet(timeSlotConfigGroup);
        }
    }

    public static void main(String[] args) throws CommandLine.ConfigurationException {
        CommandLine commandLine = new CommandLine.Builder(args)
                .requireOptions("input-path", "output-path")
                .allowOptions("unimodal-availability")
                .allowOptions("intermodal-availability", "intermodal-transfer-location-modes", "intermodal-transfer-location-ids")
                .allowOptions().build();

        Configurator configurator = new Configurator(commandLine);
        Config config = ConfigUtils.loadConfig(commandLine.getOptionStrict("input-path"), new ConfigGroup[0]);
        configurator.updateConfig(config);

        Optional<String> unimodalAvailability = commandLine.getOption("unimodal-availability");
        Optional<String> intermodalAvailability = commandLine.getOption("intermodal-availability");
        Optional<String> intermodalTransferLocationModes = commandLine.getOption("intermodal-transfer-location-modes");
        Optional<String> intermodalTransferLocationIds = commandLine.getOption("intermodal-transfer-location-ids");

        if(unimodalAvailability.isEmpty() && intermodalAvailability.isEmpty()) {
            throw new IllegalArgumentException("unimodal and/or intermodal availability are required");
        }
        if(intermodalAvailability.isPresent() && intermodalTransferLocationModes.isEmpty() && intermodalTransferLocationIds.isEmpty()) {
            throw new IllegalStateException("One of intermodal-transfer-location-modes and intermodal-transfer-location-ids must be specified for the intermodal service");
        }

        AdaptConfigForDrt.adapt(config, Map.of("drt", "drt_vehicles.xml"), Map.of("drt", "door2door"), new HashMap<>(), Map.of("drt", ModeChoiceModule.FEEDER_DRT_COST_MODEL), new HashMap<>(), "30:00:00", unimodalAvailability.isPresent());


        if(intermodalAvailability.isPresent()) {
            AdaptConfigForFeederDrt.adapt(config, Map.of("feeder_drt", "pt"), Map.of("feeder_drt", "drt"), new HashMap<>(),
                    Map.of("feeder_drt", intermodalTransferLocationModes.orElse("")),
                    Map.of("feeder_drt", intermodalTransferLocationIds.orElse("")),
                    true);

            EqasimConfigGroup.get(config).setCostModel("feeder_drt", ModeChoiceModule.FEEDER_DRT_COST_MODEL);

            if(unimodalAvailability.isPresent()) {
                EqasimConfigGroup.get(config).setAdditionalAvailableModes(Set.of("drt", "feeder_drt"));
            }

            if(OFF_PEAK_AVAIlABILITY.equals(intermodalAvailability.get())) {
                addOffPeakLegTimeConstraint(config, "feeder_drt");
            }
        }

        // We have to add the leg time constraints for DRT at the end because they would otherwise be detected by
        // AdaptConfigForFeederDrt
        if(OFF_PEAK_AVAIlABILITY.equals(unimodalAvailability.orElse("null"))) {
            addOffPeakLegTimeConstraint(config, "drt");
        }

        for(DrtConfigGroup drtConfigGroup: MultiModeDrtConfigGroup.get(config).getModalElements()) {
            PrebookingParams prebookingParams = new PrebookingParams();
            prebookingParams.setAbortRejectedPrebookings(true);
            prebookingParams.setScheduleWaitBeforeDrive(false);
            prebookingParams.setUnschedulingMode(PrebookingParams.UnschedulingMode.Routing);
            drtConfigGroup.addParameterSet(new PrebookingParams());
        }

        commandLine.applyConfiguration(config);

        ConfigUtils.writeConfig(config, commandLine.getOptionStrict("output-path"));
    }
}
