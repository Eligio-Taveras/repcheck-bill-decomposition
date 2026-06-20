package com.repcheck.decomposition.ml.cluster.flat

/**
 * A trained logistic-regression model. To score an input it standardizes each raw feature with the
 * mean/standard-deviation it was trained with, combines them linearly with the learned weights plus bias, and squashes
 * the result to a 0–1 probability. Construct only via [[LogisticModel.of]], which guarantees every vector has the same
 * expected length.
 *
 * Reuse candidate: domain-agnostic — extract to `repcheck-utils` once that shared library exists.
 */
final case class LogisticModel private (
  featureNames: Vector[String],
  weights: Vector[Double],
  bias: Double,
  featureMeans: Vector[Double],
  featureStdevs: Vector[Double],
) {

  /** Probability of the positive class for a raw feature vector (length must equal `weights.size`). */
  def predict(rawFeatures: IndexedSeq[Double]): Double = {
    val logit = weights.indices.foldLeft(bias) { (runningTotal, featureIndex) =>
      val standardizedFeature =
        if (featureStdevs(featureIndex) == 0.0) 0.0
        else (rawFeatures(featureIndex) - featureMeans(featureIndex)) / featureStdevs(featureIndex)
      runningTotal + weights(featureIndex) * standardizedFeature
    }
    LogisticModel.logistic(logit)
  }

}

object LogisticModel {
  // Clamp the logit before exp() so an extreme value can't overflow to infinity; ±30 is already
  // saturated (logistic(±30) ≈ 0 or 1), so this never affects real outputs.
  private val LogitClamp = 30.0

  def logistic(logit: Double): Double = {
    val clamped = math.max(-LogitClamp, math.min(LogitClamp, logit))
    1.0 / (1.0 + math.exp(-clamped))
  }

  /** Build a model only if every vector has exactly `expectedFeatureCount` entries. */
  def of(
    featureNames: Vector[String],
    weights: Vector[Double],
    bias: Double,
    featureMeans: Vector[Double],
    featureStdevs: Vector[Double],
    expectedFeatureCount: Int,
  ): Either[String, LogisticModel] =
    if (
      weights.sizeIs == expectedFeatureCount && featureMeans.sizeIs == expectedFeatureCount &&
      featureStdevs.sizeIs == expectedFeatureCount && featureNames.sizeIs == expectedFeatureCount
    ) Right(new LogisticModel(featureNames, weights, bias, featureMeans, featureStdevs))
    else
      Left(
        s"model dimensions must all equal $expectedFeatureCount: weights=${weights.size} " +
          s"means=${featureMeans.size} stdevs=${featureStdevs.size} names=${featureNames.size}"
      )

}
