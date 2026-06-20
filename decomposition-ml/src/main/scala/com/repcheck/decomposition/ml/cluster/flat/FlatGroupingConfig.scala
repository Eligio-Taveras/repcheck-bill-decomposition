package com.repcheck.decomposition.ml.cluster.flat

import pureconfig.ConfigReader

/**
 * Operational settings for the flat-bill grouper. These are deployment knobs only — every model parameter (weights, the
 * merge-stop threshold, the top-term count) lives in the bundled artifacts, so tuning these can never silently
 * mis-calibrate the trained model.
 */
final case class FlatGroupingConfig(
  // A bill with more sections than this skips the more expensive "vetoed" agglomeration and uses the
  // cheaper sequential stop, keeping run time bounded on rare oversized bills. Flat bills almost
  // always have only a handful of sections, so this guard rarely triggers.
  maxVetoedSections: Int = 40
) derives ConfigReader
