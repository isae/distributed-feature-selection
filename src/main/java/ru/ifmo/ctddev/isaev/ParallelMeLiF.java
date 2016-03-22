package ru.ifmo.ctddev.isaev;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.ifmo.ctddev.isaev.dataset.DataSetPair;
import ru.ifmo.ctddev.isaev.dataset.FeatureDataSet;
import ru.ifmo.ctddev.isaev.dataset.InstanceDataSet;

import java.util.*;
import java.util.concurrent.*;


/**
 * @author iisaev
 */
public class ParallelMeLiF extends SimpleMeLiF {
    private final ExecutorService executorService;

    private static final Logger LOGGER = LoggerFactory.getLogger(ParallelMeLiF.class);

    public ExecutorService getExecutorService() {
        return executorService;
    }

    protected final Set<Point> visitedPoints = new ConcurrentSkipListSet<>();

    public ParallelMeLiF(AlgorithmConfig config, int threads) {
        this(config, Executors.newFixedThreadPool(threads));
    }

    public ParallelMeLiF(AlgorithmConfig config, ExecutorService executorService) {
        super(config);
        this.executorService = executorService;
    }

    protected SelectionResult performCoordinateDescend(Point point, RunStats runStats) {
        SelectionResult bestScore = getSelectionResult(point, runStats);
        visitedPoints.add(new Point(point));

        boolean smthChanged = true;

        while (smthChanged) {
            smthChanged = false;
            double[] coordinates = point.getCoordinates();
            for (int i = 0; i < coordinates.length; i++) {

                Point plusDelta = new Point(point);
                plusDelta.getCoordinates()[i] += config.getDelta();
                CountDownLatch latch = new CountDownLatch(2);
                final SelectionResult finalBestScore = bestScore;
                Future<SelectionResult> plusDeltaScore = executorService.submit(() -> {
                    SelectionResult result = visitPoint(plusDelta, runStats, finalBestScore);
                    latch.countDown();
                    return result;
                });

                Point minusDelta = new Point(point);
                minusDelta.getCoordinates()[i] -= config.getDelta();
                Future<SelectionResult> minusDeltaScore = executorService.submit(() -> {
                    SelectionResult result = visitPoint(minusDelta, runStats, finalBestScore);
                    latch.countDown();
                    return result;
                });

                try {
                    latch.await();
                    if (plusDeltaScore.get() != null) {
                        bestScore = plusDeltaScore.get();
                        smthChanged = true;
                        break;
                    }
                    if (minusDeltaScore.get() != null) {
                        bestScore = minusDeltaScore.get();
                        smthChanged = true;
                        break;
                    }
                } catch (InterruptedException e) {
                    throw new IllegalArgumentException("Waiting on latch interrupted!");
                } catch (ExecutionException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return bestScore;
    }

    protected SelectionResult getSelectionResult(Point point, RunStats stats) {
        FeatureDataSet filteredDs = datasetFilter.filterDataset(config.getInitialDataset().toFeatureSet(), config.getFeatureCount(), point, stats);
        InstanceDataSet instanceDataSet = filteredDs.toInstanceSet();
        List<DataSetPair> dataSetPairs = datasetSplitter.splitRandomly(instanceDataSet, config.getTestPercent(), config.getFolds());
        CountDownLatch latch = new CountDownLatch(dataSetPairs.size());
        List<Double> f1Scores = Collections.synchronizedList(new ArrayList<>(dataSetPairs.size()));
        dataSetPairs.forEach(ds -> {
            executorService.submit(() -> {
                double score = getF1Score(ds);
                f1Scores.add(score);
                latch.countDown();
            });
        });
        try {
            latch.await();
            double f1Score = f1Scores.stream().mapToDouble(d -> d).average().getAsDouble();
            LOGGER.debug("Point {}; F1 score: {}", Arrays.toString(point.getCoordinates()), f1Score);
            SelectionResult result = new SelectionResult(filteredDs.getFeatures(), point, f1Score);
            stats.updateBestResult(result);
            return result;
        } catch (InterruptedException e) {
            throw new IllegalStateException("Waiting on latch interrupted! ", e);
        }
    }
}