package org.eqasim.papers.dourdan_feeder_drt_2024.analysis.cba.cba.analyzers.rideAnalysis;

import com.google.inject.Inject;
import com.google.inject.Provider;
import org.eqasim.papers.dourdan_feeder_drt_2024.analysis.cba.cba.CbaAnalysis;
import org.eqasim.papers.dourdan_feeder_drt_2024.analysis.cba.cba.CbaConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.events.MobsimScopeEventHandler;
import org.matsim.core.mobsim.qsim.AbstractQSimModule;

public class RideAnalysisModule extends AbstractModule {

    private final CbaConfigGroup cbaConfigGroup;
    private final RideAnalyzerConfigGroup configGroup;

    public RideAnalysisModule(RideAnalyzerConfigGroup configGroup, CbaConfigGroup cbaConfigGroup) {
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

                    @Override
                    public MobsimScopeEventHandler get() {
                        RideAnalyzer rideAnalyzer = new RideAnalyzer(configGroup);
                        cbaAnalysis.addSingleIterationAnalyzer(rideAnalyzer);
                        return rideAnalyzer;
                    }
                });
            }
        });
    }
}
