package org.eqasim.papers.dourdan_feeder_drt_2024;

import org.eqasim.ile_de_france.IDFConfigurator;
import org.eqasim.papers.dourdan_feeder_drt_2024.analysis.cba.cba.CbaConfigGroup;
import org.eqasim.papers.dourdan_feeder_drt_2024.analysis.cba.cba.CbaModule;
import org.eqasim.papers.dourdan_feeder_drt_2024.prebooking.CustomPrebookingLogic;
import org.matsim.contrib.drt.prebooking.logic.PrebookingLogic;
import org.matsim.contrib.drt.run.MultiModeDrtConfigGroup;
import org.matsim.contrib.dvrp.run.AbstractDvrpModeQSimModule;
import org.matsim.core.config.CommandLine;
import org.matsim.core.controler.AbstractModule;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class Configurator extends IDFConfigurator {

    public Configurator(CommandLine commandLine) {
        super(commandLine);
        this.registerConfigGroup(new CbaConfigGroup(), true);
        this.registerModule(new CbaModule(), CbaConfigGroup.GROUP_NAME);


        registerModule(new AbstractModule() {
            @Override
            public void install() {
                MultiModeDrtConfigGroup configGroup = MultiModeDrtConfigGroup.get(getConfig());
                if(configGroup.getModalElements().size() != 1) {
                    throw new IllegalStateException();
                }

                configGroup.getModalElements().forEach(element -> {
                    if(!element.getMode().equals("drt")) {
                        throw new IllegalStateException();
                    }

                    Map<String, Float> prebookingHorizonPerRoutingMode = new HashMap<>();
                    Optional<Float> unimodalPrebookingHorizon = commandLine.getOption("unimodal-prebooking").map(Float::parseFloat);
                    Optional<Float> intermodalPrebookingHorizon = commandLine.getOption("intermodal-prebooking").map(Float::parseFloat);
                    unimodalPrebookingHorizon.ifPresent(h -> prebookingHorizonPerRoutingMode.put("drt", h));
                    intermodalPrebookingHorizon.ifPresent(h -> prebookingHorizonPerRoutingMode.put("feeder_drt", h));

                    installQSimModule(new AbstractDvrpModeQSimModule(element.getMode()) {
                        @Override
                        protected void configureQSim() {
                            bindModal(PrebookingLogic.class).to(CustomPrebookingLogic.class);
                        }
                    });
                    installQSimModule(CustomPrebookingLogic.createModule(element, prebookingHorizonPerRoutingMode));
                });
            }
        }, MultiModeDrtConfigGroup.GROUP_NAME);
    }
}
