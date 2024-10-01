package org.eqasim.papers.dourdan_feeder_drt_2024.analysis.cba.cba;

import org.eqasim.papers.dourdan_feeder_drt_2024.analysis.cba.cba.analyzers.drtAnalysis.DrtAnalyzer;
import org.eqasim.papers.dourdan_feeder_drt_2024.analysis.cba.cba.analyzers.ptAnalysis.PtAnalyzer;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.controler.MatsimServices;
import org.matsim.core.controler.events.IterationEndsEvent;
import org.matsim.core.controler.listener.IterationEndsListener;

public class CbaControlerListener implements IterationEndsListener {

    private final MatsimServices matsimServices;
    private Network network;
    private final DrtAnalyzer drtAnalyzer;
    private final PtAnalyzer ptAnalyzer;
    private final CbaAnalysis cbaAnalysis;

    public CbaControlerListener(CbaAnalysis cbaAnalysis, MatsimServices matsimServices, Network network, DrtAnalyzer drtAnalyzer, PtAnalyzer ptAnalyzer) {
        this.matsimServices = matsimServices;
        this.network = network;
        this.drtAnalyzer = drtAnalyzer;
        this.ptAnalyzer = ptAnalyzer;
        this.cbaAnalysis = cbaAnalysis;
    }

    @Override
    public void notifyIterationEnds(IterationEndsEvent event) {
        cbaAnalysis.notifyIterationEnd(event);
    }
}
