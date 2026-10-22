package org.eqasim.papers.gpe2025.utils;

import ch.sbb.matsim.routing.pt.raptor.InitialStop;

import java.util.List;
import java.util.stream.Collectors;

public class FormatUtils {

    public static String formatList(List list) {
        String result = "[";
        result += list.stream().map(FormatUtils::formatObject).sorted().collect(Collectors.joining(", "));
        result += "]";
        return result;
    }

    public static String formatObject(Object o) {
        if (o == null) {
            return "null";
        }
        if(o instanceof List list) {
            return formatList(list);
        }
        return o.toString();
    }
}
