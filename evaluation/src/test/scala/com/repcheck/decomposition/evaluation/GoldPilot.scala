package com.repcheck.decomposition.evaluation

/**
 * The full 25-bill gold set (DP-0) — the entire shared corpus, spanning every bill/resolution type, all three formats
 * (Formatted Text · PDF · Formatted XML), and the full size range:
 *   - 4 mid-omnibus bills (~300-430 sections) for the omnibus cut branch: 148391 hr · 375702 s · 244276 hr · 150025 s
 *   - 8 medium multi-section bills (~14-26 sections) — the tight grouping signal: 415327 hr · 150314 s · 189669 hjres ·
 *     408763 sjres · 357076 hconres · 356661 sconres · 8966 hres · 323852 sres
 *   - 11 tiny / single-section bills — the degenerate single-theme case: 356142 · 344387 · 330298 · 219039 · 237650 ·
 *     177012 · 402717 · 373803 · 208332 · 267921 · 129397 · 272091 · 154389
 * The first 12 were the DP-0 pilot; the remaining 13 complete the corpus for the §10b empirical gate.
 */
object GoldPilot {

  val versionIds: List[String] =
    List(
      // original pilot (12)
      "415327",
      "150314",
      "189669",
      "356142",
      "344387",
      "330298",
      "219039",
      "237650",
      "148391",
      "375702",
      "244276",
      "150025",
      // corpus completion (13)
      "177012",
      "402717",
      "373803",
      "408763",
      "208332",
      "357076",
      "267921",
      "356661",
      "8966",
      "129397",
      "323852",
      "272091",
      "154389",
    )

}
