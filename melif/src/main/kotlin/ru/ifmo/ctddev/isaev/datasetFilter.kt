package ru.ifmo.ctddev.isaev

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import ru.ifmo.ctddev.isaev.point.Point
import java.util.*

/**
 * @author iisaev
 */

enum class NormalizationMode { NONE, VALUE_BASED, MEASURE_BASED }

fun DoubleArray.normalize(min: Double, max: Double) {
    this.forEachIndexed { i, value ->
        this[i] = (value - min) / (max - min)
    }
}

class DataSetEvaluator(private val normMode: NormalizationMode) {
    constructor() : this(NormalizationMode.VALUE_BASED)

    private fun evaluateMeasuresHelper(original: FeatureDataSet,
                                       measures: Array<out RelevanceMeasure>): List<DoubleArray> {
        return measures.map { m ->
            val evaluated = m.evaluate(original)
            when (normMode) {
                NormalizationMode.NONE -> Unit
                NormalizationMode.VALUE_BASED -> evaluated.normalize(evaluated.min()!!, evaluated.max()!!)
                NormalizationMode.MEASURE_BASED -> evaluated.normalize(m.minValue, m.maxValue)
            }
            return@map evaluated
        }
    }

    private fun evaluateMeasures(original: FeatureDataSet,
                                 measureCosts: Point,
                                 vararg measures: RelevanceMeasure): List<EvaluatedFeature> {
        if (measureCosts.coordinates.size != measures.size) {
            throw IllegalArgumentException("Number of given measures mismatch with measureCosts dimension")
        }

        val features = original.features
        val valuesForEachMeasure = evaluateMeasuresHelper(original, measures)
        val ensembleMeasures = 0.until(features.size)
                .map { i -> measureCosts.coordinates.zip(valuesForEachMeasure.map { it[i] }) }
                .map { it.sumByDouble { (measureCost, measureValue) -> measureCost * measureValue } }

        return features.zip(ensembleMeasures)
                .map { (f, m) -> EvaluatedFeature(f, m) }
    }


    fun evaluateFeatures(original: FeatureDataSet,
                         measureCosts: Point,
                         measures: Array<out RelevanceMeasure>
    ): Sequence<EvaluatedFeature> {
        return evaluateMeasures(original, measureCosts, *measures)
                .sortedBy { it.measure }
                .asSequence()
    }

    fun evaluateMeasures(dataSet: FeatureDataSet,
                         measures: Array<out RelevanceMeasure>): List<DoubleArray> {
        return evaluateMeasuresHelper(dataSet, measures)
    }
}

sealed class DataSetFilter {

    protected val logger: Logger = LoggerFactory.getLogger(this.javaClass)

    abstract fun filterDataSet(original: FeatureDataSet, measureCosts: Point,
                               measures: Array<out RelevanceMeasure>): FeatureDataSet
}

class PercentFilter(private val percents: Int) : DataSetFilter() {

    override fun filterDataSet(original: FeatureDataSet, measureCosts: Point,
                               measures: Array<out RelevanceMeasure>): FeatureDataSet {
        val evaluatedFeatures = DataSetEvaluator().evaluateFeatures(original, measureCosts, measures).toList()
        val featureToSelect = (evaluatedFeatures.size.toDouble() * percents / 100).toInt()
        val filteredFeatures = ArrayList<Feature>(evaluatedFeatures.subList(0, featureToSelect))
        return FeatureDataSet(filteredFeatures, original.classes, original.name)
    }

}

class PreferredSizeFilter(val preferredSize: Int) : DataSetFilter() {

    init {
        logger.info("Initialized dataset filter with preferred size {}", preferredSize)
    }

    override fun filterDataSet(original: FeatureDataSet, measureCosts: Point,
                               measures: Array<out RelevanceMeasure>): FeatureDataSet {
        val filteredFeatures = DataSetEvaluator().evaluateFeatures(original, measureCosts, measures)
                .take(preferredSize)
        return FeatureDataSet(filteredFeatures.toList(), original.classes, original.name)
    }

}

class WyrdCuttingRuleFilter : DataSetFilter() {

    override fun filterDataSet(original: FeatureDataSet, measureCosts: Point,
                               measures: Array<out RelevanceMeasure>): FeatureDataSet {
        val filteredFeatures = DataSetEvaluator().evaluateFeatures(original, measureCosts, measures)
        val mean = filteredFeatures
                .map({ it.measure })
                .average()
        val std = Math.sqrt(
                filteredFeatures
                        .map({ it.measure })
                        .map { x -> Math.pow(x - mean, 2.0) }
                        .average()
        )
        val inRange = filteredFeatures
                .filter { f -> isInRange(f, mean, std) }
                .count()
        val result = filteredFeatures.toList().subList(0, inRange)
        return FeatureDataSet(result, original.classes, original.name)
    }

    private fun isInRange(feature: EvaluatedFeature,
                          mean: Double,
                          std: Double): Boolean {
        return feature.measure > mean - std && feature.measure < mean + std
    }

}
