package org.eqasim.papers.dourdan_feeder_drt_2024.analysis.cba.cba.analyzers.ptAnalysis;

import com.google.inject.Inject;
import com.google.inject.Provider;
import org.eqasim.papers.dourdan_feeder_drt_2024.analysis.cba.cba.CbaAnalysis;
import org.eqasim.papers.dourdan_feeder_drt_2024.analysis.cba.cba.CbaConfigGroup;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.events.MobsimScopeEventHandler;
import org.matsim.core.mobsim.qsim.AbstractQSimModule;

public class PtAnalysisModule extends AbstractModule {

    private final PtAnalyzerConfigGroup configGroup;
    private final CbaConfigGroup cbaConfigGroup;

    public PtAnalysisModule(PtAnalyzerConfigGroup configGroup, CbaConfigGroup cbaConfigGroup) {
        this.configGroup = configGroup;
        this.cbaConfigGroup = cbaConfigGroup;
    }

    @Override
    public void install() {
        installQSimModule(new AbstractQSimModule() {
            @Override
            protected void configureQSim() {
                if(this.getIterationNumber() % cbaConfigGroup.getOutputFrequency() != 0) {
                    return;
                }
                addMobsimScopeEventHandlerBinding().toProvider(new Provider<MobsimScopeEventHandler>() {
                    @Inject
                    CbaAnalysis cbaAnalysis;

                    @Inject
                    Network network;

                    @Inject
                    Scenario scenario;

                    @Override
                    public MobsimScopeEventHandler get() {
                        PtAnalyzer ptAnalyzer = new PtAnalyzer(configGroup, network, scenario);
                        cbaAnalysis.addSingleIterationAnalyzer(ptAnalyzer);
                        return ptAnalyzer;
                    }
                });
            }
        });
    }
}
