package ru.ifmo.ctddev.isaev.executable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import ru.ifmo.ctddev.isaev.*;
import ru.ifmo.ctddev.isaev.feature.measure.SymmetricUncertainty;
import ru.ifmo.ctddev.isaev.feature.measure.VDM;
import ru.ifmo.ctddev.isaev.melif.impl.BasicMeLiF;
import ru.ifmo.ctddev.isaev.point.Point;
import ru.ifmo.ctddev.isaev.results.RunStats;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


/**
 * @author iisaev
 */
public class ParallelRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(ParallelRunner.class);

    public static void main(String[] args) {

        String startTimeString = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd_MM_yyyy_HH:mm"));
        MDC.put("fileName", startTimeString + "/COMMON");

        DataSetReader dataSetReader = new DataSetReader();
        DataSet dataSet = dataSetReader.readCsv(args[0]);
        Point[] points = new Point[] {
                new Point(1.0, 0.0, 0.0, 0.0),
                new Point(0.0, 1.0, 0.0, 0.0),
                new Point(0.0, 0.0, 1.0, 0.0),
                new Point(0.0, 0.0, 0.0, 1.0),
                new Point(1.0, 1.0, 1.0, 1.0)
        };
        RelevanceMeasure[] measures = new RelevanceMeasure[] {new VDM(), new FitCriterion(), new SymmetricUncertainty(), new SpearmanRankCorrelation()};
        List<Integer> order = IntStream.range(0, dataSet.getInstanceCount()).mapToObj(i -> i).collect(Collectors.toList());
        Collections.shuffle(order);
        FoldsEvaluator foldsEvaluator = new SequentalEvaluator(
                Classifiers.SVM,
                new PreferredSizeFilter(100), new OrderSplitter(10, order), new F1Score()
        );
        AlgorithmConfig config = new AlgorithmConfig(0.1, foldsEvaluator, measures);
        LocalDateTime startTime = LocalDateTime.now();
        BasicMeLiF meLif = new BasicMeLiF(config, dataSet);
        RunStats runStats = meLif.run(points);
        LocalDateTime starFinish = LocalDateTime.now();
        LOGGER.info("Finished BasicMeLiF at {}", starFinish);
        long starWorkTime = ChronoUnit.SECONDS.between(startTime, starFinish);
        LOGGER.info("Star work time: {} seconds", starWorkTime);
        LOGGER.info("Visited {} points; best point is {} with score {}", new Object[] {
                runStats.getVisitedPoints(),
                runStats.getBestResult().getPoint(),
                runStats.getBestResult().getScore()
        });
    }
}
