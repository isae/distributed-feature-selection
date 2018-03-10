package ru.ifmo.ctddev.isaev

import org.knowm.xchart.BitmapEncoder
import org.knowm.xchart.SwingWrapper
import org.knowm.xchart.XYChart
import org.knowm.xchart.XYChartBuilder
import org.knowm.xchart.style.markers.None
import org.knowm.xchart.style.markers.SeriesMarkers
import ru.ifmo.ctddev.isaev.feature.measure.VDM
import ru.ifmo.ctddev.isaev.point.Point
import ru.ifmo.ctddev.isaev.space.Line
import ru.ifmo.ctddev.isaev.space.LinePoint
import ru.ifmo.ctddev.isaev.space.getFeaturePositions
import ru.ifmo.ctddev.isaev.space.processAllPointsFast
import java.awt.BasicStroke
import java.awt.Color
import java.time.LocalDateTime
import java.util.*
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.reflect.KClass


/**
 * @author iisaev
 */

fun main(args: Array<String>) {
    val measures = listOf(SpearmanRankCorrelation::class, VDM::class)
    val epsilon = 1000
    val cutSize = 50
    val dataSet = KnownDatasets.DLBCL.read()

    logToConsole("Started the processing")
    val angles = 0.rangeTo(epsilon).map { getAngle(epsilon, it) }
    val pointsInProjectiveCoords = angles.map { getPoint(it) }
    logToConsole("${pointsInProjectiveCoords.size} points to calculate measures on")
    val (evaluatedData, cuttingLineY, cutsForAllPoints) = processAllPointsFast(pointsInProjectiveCoords, dataSet, measures, cutSize)
    val lastFeatureInAllCuts = cutsForAllPoints.map { it.last() }
    logToConsole("Evaluated data, calculated cutting line and cuts for all points")
    val sometimesInCut = cutsForAllPoints
            .flatMap { it }
            .toSet()
    logToConsole("Sometimes in cut: ${sometimesInCut.size} features")
    val alwaysInCut = sometimesInCut.filter { featureNum ->
        cutsForAllPoints.all { it.contains(featureNum) }
    }
    logToConsole("Always in cut: ${alwaysInCut.size} features: $alwaysInCut")
    val needToProcess = sometimesInCut - alwaysInCut
    logToConsole("Need to process: ${needToProcess.size} features")

    fun feature(i: Int) = getFeaturePositions(i, evaluatedData)

    val features = needToProcess.map { feature(it) }

    //val cuttingLineY = lines.sortedBy { it.from.y }[cutSize - 1].from.y
    //lines.forEachIndexed({ index, line -> addLine("Feature $index", line, chart) })
    val lastFeatureInCutSwitchPositions = lastFeatureInAllCuts
            .mapIndexed { index, i -> Pair(index, i) }
            .filter { it.first == 0 || it.second != lastFeatureInAllCuts[it.first - 1] }
            .map { it.first }
    val pointsToTry = lastFeatureInCutSwitchPositions
            .map {
                val angle = getAngle(epsilon, it)
                getPoint(angle)
            }
    println(pointsToTry)

    val bottomFrontOfCuttingRule = lastFeatureInCutSwitchPositions
            .map {
                val angle = getAngle(epsilon, it)
                val d = cuttingLineY[it]
                sin(angle) * d
            }


    logToConsole("Found ${pointsToTry.size} points to try")
    //pointsToTry.forEach { println("(%.3f, %.3f)".format(it.coordinates[0], it.coordinates[1])) }

    val chartBuilder = XYChartBuilder()
            .width(1024)
            .height(768)
            .xAxisTitle("Measure Proportion (${measures[0].simpleName} to ${measures[1].simpleName})")
            .yAxisTitle("Ensemble feature measure")
    val chart = XYChart(chartBuilder)
    val semiSphereXData = 0.rangeTo(epsilon).map { -1 + (2 * it).toDouble() / epsilon }
    chart.addSeries("Sphere",
            semiSphereXData,
            semiSphereXData.map { Math.sqrt(1 - it.pow(2)) }
    ).apply {
        this.lineColor = Color.BLACK
        this.marker = None()
        this.lineStyle = BasicStroke(3.0f)
    }
    chart.addSeries("X", doubleArrayOf(0.0, 0.0), doubleArrayOf(0.0, 1.05)).apply {
        this.lineColor = Color.BLACK
        this.marker = None()
        this.lineStyle = BasicStroke(2.0f)
    }
    val xDataForFeatures = angles.map { cos(it) }
    features.forEachIndexed { index, doubles ->
        val yDataForFeature = doubles.zip(angles)
                .map { (d, angle) ->
                    sin(angle) * d // y coord by definition of sinus
                }
        chart.addSeries("Feature $index", xDataForFeatures, yDataForFeature)
                .apply {
                    this.marker = None()
                    this.lineStyle = BasicStroke(1.0f)
                }
    }
    val cuttingLineDataToDraw = cuttingLineY.zip(angles)
            .map { (d, angle) ->
                sin(angle) * d // y coord by definition of sinus
            }
    chart.addSeries("Bottom front", xDataForFeatures, cuttingLineDataToDraw).apply {
        this.marker = None()
        this.lineColor = Color.BLACK
        this.lineStyle = BasicStroke(3.0f)

    }
    drawChart(chart)
    //draw(measures, lines, pointsInCartesianCoords, bottomFrontOfCuttingRule, cuttingLineY)
}

private fun getPoint(angle: Double): Point {
    return Point.fromRawCoords(Math.cos(angle), Math.sin(angle))
}

private fun getAngle(epsilon: Int, x: Int): Double {
    val fractionOfPi = Math.PI / epsilon
    val angle = Math.PI - (fractionOfPi * x) //from left to right
    return angle
}

private fun draw(measures: List<KClass<out RelevanceMeasure>>,
                 lines: List<Line>,
                 xData: List<Point>,
                 intersections: List<LinePoint>,
                 cuttingLineY: List<Double>) {
    val chartBuilder = XYChartBuilder()
            .width(1024)
            .height(768)
            .xAxisTitle("Measure Proportion (${measures[0].simpleName} to ${measures[1].simpleName})")
            .yAxisTitle("Ensemble feature measure")
    val chart = XYChart(chartBuilder)
    lines.forEachIndexed({ index, line -> addLine("Feature $index", line, chart) })
    chart.addSeries("Cutting line", xData.map { it.coordinates[0] }, cuttingLineY)
            .apply {
                this.marker = SeriesMarkers.NONE
                this.lineWidth = 3.0f
                this.lineColor = Color.BLACK
                this.lineStyle = BasicStroke(1.5f)
            }
    intersections.forEach { point ->
        val x = point.x
        val y = point.y
        val series = chart.addSeries(UUID.randomUUID().toString().take(10), listOf(x, x), listOf(0.0, y))
        series.lineColor = Color.BLACK
        series.markerColor = Color.BLACK
        series.marker = None()
        series.lineStyle = BasicStroke(0.1f)
    }
    // Show it
    drawChart(chart)
}

private fun drawChart(chart: XYChart) {
    logToConsole("Finished calculations; visualizing...")
    SwingWrapper(chart).displayChart()
    logToConsole("Finished visualization")

    // or save it in high-res
    BitmapEncoder.saveBitmapWithDPI(chart, "./charts/Test_Chart_${LocalDateTime.now()}", BitmapEncoder.BitmapFormat.PNG, 200);
}

private fun addLine(name: String, line: Line, chart: XYChart) {
    chart.addSeries(name, listOf(line.from.x, line.to.x), listOf(line.from.y, line.to.y)).marker = SeriesMarkers.NONE
}

private fun addLine(name: String, xData: List<Point>, line: DoubleArray, chart: XYChart) {
    chart.addSeries(name, xData.map { it.coordinates[0] }.toDoubleArray(), line).marker = SeriesMarkers.NONE
}