package ru.ifmo.ctddev.isaev.executable;

import ru.ifmo.ctddev.isaev.results.RunStats;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ru.ifmo.ctddev.isaev.melif.impl.FeatureSelectionAlgorithm.FORMAT;


/**
 * @author iisaev
 */
class Comparison {

    static double getSpeedImprovementPercent(long prevSeconds, long curSeconds) {
        long diff = prevSeconds - curSeconds;
        return (double) diff / prevSeconds * 100;
    }

    static double getPercentImprovement(double prev, double cur) {
        double diff = prev - cur;
        return diff / prev * 100;
    }

    static String fullCsvRepresentation(List<List<RunStats>> executionResults) {
        StringBuilder sb = new StringBuilder();
        sb.append(csvHeader(executionResults.get(0)));
        executionResults
                .forEach(pr -> sb.append(csvRepresentation(pr)));
        return sb.toString();
    }

    static String csvHeader(List<RunStats> executionResults) {
        StringBuilder sb = new StringBuilder();
        Stream<String> start = Stream.of("Dataset", "Shape", "Features", "Instances");
        Stream<String> header = Stream.concat(start, executionResults.stream().flatMap(
                stats -> Stream.of(
                        String.format("%s time", stats.getAlgorithmName()),
                        String.format("%s best point", stats.getAlgorithmName()),
                        String.format("%s score", stats.getAlgorithmName()),
                        String.format("%s visited points", stats.getAlgorithmName()))
        ));
        String headerStr = String.join(";", header.collect(Collectors.toList()));
        sb.append(headerStr).append("\n");
        return sb.toString();
    }

    static String csvRepresentation(List<RunStats> pr) {
        StringBuilder sb = new StringBuilder();
        Stream<Object> rowStart = Stream.of(
                pr.get(0).getDataSetName(),
                String.format("%dx%d", pr.get(0).getFeatureCount(), pr.get(0).getInstanceCount()),
                pr.get(0).getFeatureCount(),
                pr.get(0).getInstanceCount());
        Stream<Object> row = Stream.concat(rowStart, pr.stream().flatMap(
                stats -> Stream.of(
                        stats.getWorkTime(),
                        stats.getBestResult().getPoint(),
                        FORMAT.format(stats.getBestResult().getScore()),
                        stats.getVisitedPoints())
        ));
        sb.append(String.join(";", row.map(Objects::toString).collect(Collectors.toList()))).append("\n");
        return sb.toString();
    }

}
