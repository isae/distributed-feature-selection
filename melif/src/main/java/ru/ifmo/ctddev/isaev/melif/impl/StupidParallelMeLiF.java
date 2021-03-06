package ru.ifmo.ctddev.isaev.melif.impl;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.ifmo.ctddev.isaev.results.RunStats;
import ru.ifmo.ctddev.isaev.AlgorithmConfig;
import ru.ifmo.ctddev.isaev.DataSet;
import ru.ifmo.ctddev.isaev.SelectionResult;
import ru.ifmo.ctddev.isaev.point.Point;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.stream.Collectors;


/**
 * Implementation, that evaluates each point in separate thread
 *
 * @author iisaev
 */
public class StupidParallelMeLiF extends BasicMeLiF {
    private final ExecutorService executorService;

    private static final Logger LOGGER = LoggerFactory.getLogger(StupidParallelMeLiF.class);

    public ExecutorService getExecutorService() {
        return executorService;
    }

    protected final Set<Point> visitedPoints = new ConcurrentSkipListSet<>();

    public StupidParallelMeLiF(AlgorithmConfig config, DataSet dataSet) {
        this(config, dataSet, Executors.newFixedThreadPool(5));
    }

    public StupidParallelMeLiF(AlgorithmConfig config, DataSet dataSet, ExecutorService executorService) {
        super(config, dataSet);
        this.executorService = executorService;
    }

    @Override
    public RunStats run(@NotNull String name, @NotNull Point[] points, int pointsToVisit) {
        return run(name, points, true);
    }

    @Override
    public RunStats run(@NotNull Point[] points) {
        return run("Stupid", points, true);
    }

    public RunStats run(String name, Point[] points, boolean shutdown) {
        Arrays.asList(points).forEach(p -> {
            if (p.getCoordinates().length != config.getMeasures().length) {
                throw new IllegalArgumentException("Each point must have same coordinates number as number of measures");
            }
        });

        RunStats runStats = new RunStats(config, dataSet, name);
        logger.info("Started {} at {}", name, runStats.getStartTime());

        LOGGER.info("Started {} at {}", getClass().getSimpleName(), runStats.getStartTime());
        CountDownLatch pointsLatch = new CountDownLatch(points.length);
        List<Future<SelectionResult>> scoreFutures = Arrays.asList(points).stream()
                .map(p -> executorService.submit(() -> {
                    SelectionResult result = performCoordinateDescend(p, runStats);
                    pointsLatch.countDown();
                    return result;
                })).collect(Collectors.toList());
        try {
            pointsLatch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        scoreFutures.forEach(f -> {
            assert f.isDone();
        });
        List<SelectionResult> scores = scoreFutures.stream().map(f -> {
            try {
                return f.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }).collect(Collectors.toList());
        LOGGER.info("Total scores: ");
        scores.stream().mapToDouble(SelectionResult::getScore).forEach(System.out::println);
        LOGGER.info("Max score: {} at point {}",
                runStats.getBestResult().getScore(),
                runStats.getBestResult().getPoint()
        );
        runStats.setFinishTime(LocalDateTime.now());
        LOGGER.info("Finished {} at {}", getClass().getSimpleName(), runStats.getFinishTime());
        LOGGER.info("Working time: {} seconds", runStats.getWorkTime());
        if (shutdown) {
            getExecutorService().shutdown();
        }
        return runStats;
    }

    protected SelectionResult visitPoint(Point point, RunStats measures, SelectionResult bestResult) {
        if (!visitedPoints.contains(point)) {
            SelectionResult score = foldsEvaluator.getSelectionResult(dataSet, point, measures);
            visitedPoints.add(new Point(point));
            return score;
        }
        return bestResult;
    }

    protected SelectionResult performCoordinateDescend(Point point, RunStats runStats) {
        SelectionResult bestScore = foldsEvaluator.getSelectionResult(dataSet, point, runStats);
        visitedPoints.add(point);
        if (runStats.getBestResult() != null && runStats.getScore() > bestScore.getScore()) {
            bestScore = runStats.getBestResult();
        }

        boolean smthChanged = true;
        double[] coordinates = point.getCoordinates();

        while (smthChanged) {
            smthChanged = false;

            for (int i = 0; i < coordinates.length; i++) {

                Point plusDelta = new Point(coordinates);
                plusDelta.getCoordinates()[i] += config.getDelta();
                SelectionResult plusScore = visitPoint(plusDelta, runStats, bestScore);
                if (plusScore.betterThan(bestScore)) {
                    bestScore = plusScore;
                    coordinates = plusDelta.getCoordinates();
                    smthChanged = true;
                    break;
                }

                Point minusDelta = new Point(coordinates);
                minusDelta.getCoordinates()[i] -= config.getDelta();
                SelectionResult minusScore = visitPoint(minusDelta, runStats, bestScore);
                if (minusScore.betterThan(bestScore)) {
                    bestScore = minusScore;
                    coordinates = minusDelta.getCoordinates();
                    smthChanged = true;
                    break;
                }
            }
        }
        return bestScore;
    }
}
