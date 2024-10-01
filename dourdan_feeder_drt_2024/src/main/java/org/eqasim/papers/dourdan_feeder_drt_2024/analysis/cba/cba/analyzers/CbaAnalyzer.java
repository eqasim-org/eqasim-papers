package org.eqasim.papers.dourdan_feeder_drt_2024.analysis.cba.cba.analyzers;

import org.apache.poi.ss.usermodel.Sheet;

import java.util.List;

public interface CbaAnalyzer {
    public String[] getSheetsNames();

    public void fillSheets(List<Sheet> sheets);
}
