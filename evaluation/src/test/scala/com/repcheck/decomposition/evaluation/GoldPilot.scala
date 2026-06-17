package com.repcheck.decomposition.evaluation

/**
 * The 12 pilot bills (DP-0) — deliberate spread over bill type, format, and size:
 *   - 3 large Formatted-Text bills carry the multi-section grouping signal (415327 hr · 150314 s · 189669 hjres)
 *   - 2 tiny Formatted-Text bills are the tight single-theme / degenerate case (356142 hr · 344387 hres)
 *   - the two edge formats: 330298 (hr, Formatted XML) · 219039 (hjres, PDF) · 237650 (s, PDF)
 *   - 4 mid-omnibus bills (~60-80 sections, likely >15 subjects) for the silhouette/omnibus cut branch: 148391 hr ·
 *     375702 s · 244276 hr · 150025 s
 */
object GoldPilot {

  val versionIds: List[String] =
    List(
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
    )

}
