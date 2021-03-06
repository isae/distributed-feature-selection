package ru.ifmo.ctddev.isaev

import org.knowm.xchart.BitmapEncoder
import org.knowm.xchart.SwingWrapper
import org.knowm.xchart.XYChart
import org.knowm.xchart.XYChartBuilder
import org.knowm.xchart.style.markers.None
import ru.ifmo.ctddev.isaev.feature.measure.VDM
import ru.ifmo.ctddev.isaev.space.*
import java.awt.BasicStroke
import java.awt.Color
import java.time.LocalDateTime
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin


/**
 * @author iisaev
 */

fun main(args: Array<String>) {
    val measures = arrayOf(SpearmanRankCorrelation(), VDM())
    val epsilon = 1000
    val cutSize = 50
    val dataSet = KnownDatasets.DLBCL.read()

    logToConsole { "Started the processing" }
    val angles = 0.rangeTo(epsilon).map { getAngle(epsilon, it) }
    logToConsole { "Angles: $angles" }
    val pointsInProjectiveCoords = angles.map { getPointOnUnitSphere(it) }
    logToConsole { "${pointsInProjectiveCoords.size} points to calculate measures on" }
    val (evaluatedData, lastFeatureInAllCuts, cutsForAllPoints) = processAllPointsFastOld(pointsInProjectiveCoords, dataSet, measures, cutSize)
    val cuttingLineY = lastFeatureInAllCuts.mapIndexed { index, f -> evaluatedData[index][f] }
    logToConsole { "Evaluated data, calculated cutting line and cuts for all points" }
    val sometimesInCut = cutsForAllPoints
            .flatMap { it }
            .toSet()
    logToConsole { "Sometimes in cut: ${sometimesInCut.size} features" }
    val alwaysInCut = sometimesInCut.filter { featureNum ->
        cutsForAllPoints.all { it.contains(featureNum) }
    }
    logToConsole { "Always in cut: ${alwaysInCut.size} features: $alwaysInCut" }
    val needToProcess = sometimesInCut - alwaysInCut
    logToConsole { "Need to process: ${needToProcess.size} features" }

    fun feature(i: Int) = getFeaturePositions(i, evaluatedData)

    val features = needToProcess.map { feature(it) }

    //val cuttingLineY = lines.sortedBy { it.from.y }[cutSize - 1].from.y
    //lines.forEachIndexed({ index, line -> addLine("Feature $index", line, chart) })
    val lastFeatureInCutSwitchPositions = lastFeatureInAllCuts
            .mapIndexed { index, i -> Pair(index, i) }
            .filter { it.first != 0 && it.second != lastFeatureInAllCuts[it.first - 1] }
            .map { it.first }
    val pointsToTry = lastFeatureInCutSwitchPositions
            .map {
                val angle = getAngle(epsilon, it)
                getPointOnUnitSphere(angle)
            }
    println(pointsToTry)

    val bottomFrontOfCuttingRule = lastFeatureInCutSwitchPositions
            .map {
                val angle = getAngle(epsilon, it)
                val d = cuttingLineY[it]
                sin(angle) * d
            }


    logToConsole { "Found ${pointsToTry.size} points to try" }

    val chartBuilder = XYChartBuilder()
            .width(1024)
            .height(768)
            .xAxisTitle("Measure Proportion (${measures[0].javaClass.simpleName} to ${measures[1].javaClass.simpleName})")
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
}

private fun drawChart(chart: XYChart) {
    logToConsole { "Finished calculations; visualizing..." }
    SwingWrapper(chart).displayChart()
    logToConsole { "Finished visualization" }

    BitmapEncoder.saveBitmapWithDPI(chart, "./charts/Test_Chart_${LocalDateTime.now()}", BitmapEncoder.BitmapFormat.PNG, 200);
}