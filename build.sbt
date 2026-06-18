import org.typelevel.scalacoptions.ScalacOption
import sbt.Keys.libraryDependencies
import sbt.Def
import Dependencies.*
import com.repcheck.sbt.ExceptionUniquenessPlugin.autoImport.exceptionUniquenessRootPackages

val isScala212: Def.Initialize[Boolean] = Def.setting {
  VersionNumber(scalaVersion.value).matchesSemVer(SemanticSelector("2.12.x"))
}

ThisBuild / dynverSonatypeSnapshots := true

lazy val commonSettings = Seq(
  organization := "com.repcheck",
  scalaVersion := "3.7.3",
  publishTo := Some(
    "GitHub Packages" at s"https://maven.pkg.github.com/Eligio-Taveras/repcheck-bill-decomposition"
  ),
  publishMavenStyle := true,
  credentials ++= {
    val envCreds = for {
      user  <- sys.env.get("GITHUB_ACTOR")
      token <- sys.env.get("GITHUB_TOKEN")
    } yield Credentials("GitHub Package Registry", "maven.pkg.github.com", user, token)

    val fileCreds = {
      val f = Path.userHome / ".sbt" / ".github-packages-credentials"
      if (f.exists) Some(Credentials(f)) else None
    }

    envCreds.orElse(fileCreds).toSeq
  },
  resolvers ++= Seq(
    "GitHub Packages - shared-models" at "https://maven.pkg.github.com/Eligio-Taveras/repcheck-shared-models",
    "GitHub Packages - pipeline-models" at "https://maven.pkg.github.com/Eligio-Taveras/repcheck-pipeline-models",
    "GitHub Packages - repcheck-utils" at "https://maven.pkg.github.com/Eligio-Taveras/repcheck-utils",
    "GitHub Packages - repcheck-embedding" at "https://maven.pkg.github.com/Eligio-Taveras/repcheck-embedding",
  ),
  libraryDependencies ++= Seq(
    "org.scalatest" %% "scalatest" % "3.2.18" % Test
  ),
  semanticdbEnabled := true,
  tpolecatScalacOptions ++= ScalaCConfig.scalaCOptions,
  tpolecatScalacOptions ++= {
    if (isScala212.value) ScalaCConfig.scalaCOption2_12
    else Set.empty[ScalacOption]
  },

  // Suppress Scala 3 ScalaTest-matcher warnings in TEST sources only (mirrors data-ingestion):
  //   - "is not declared infix" from matcher DSL
  //   - "unused value of type Assertion" from chained assertions (tpolecat -Wnonunit-statement)
  Test / scalacOptions += "-Wconf:msg=is not declared infix:s",
  Test / scalacOptions += "-Wconf:msg=unused value of type:s",

  // E2E tests (live AlloyDB export / network) are excluded from `sbt test` / CI; scoped to the `test` task only so
  // they can still be run explicitly with `sbt "textStructure/testOnly -- -n com.repcheck.tags.E2ETest"`.
  Test / test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-l", "com.repcheck.tags.E2ETest"),

  // WartRemover — enforces FP discipline at compile time
  wartremoverErrors ++= Seq(
    Wart.AsInstanceOf,          // No unsafe casts
    Wart.EitherProjectionPartial, // No .get on Either projections
    Wart.IsInstanceOf,          // No runtime type checks — use pattern matching
    Wart.MutableDataStructures, // No mutable collections
    Wart.Null,                  // No null — use Option
    Wart.OptionPartial,         // No Option.get — use fold/map/getOrElse
    Wart.Return,                // No return statements
    Wart.StringPlusAny,         // No string + any — use interpolation
    Wart.IterableOps,           // No .head/.tail on collections — use headOption
    Wart.TryPartial,            // No Try.get — use fold/recover
    Wart.Var                    // No mutable vars
  ),
  wartremoverWarnings ++= Seq(
    Wart.Throw                  // Warn on bare throw — prefer F.raiseError
  )
)

lazy val root = (project in file("."))
  .aggregate(textStructure, conformance, decompositionMl, evaluation, docGenerator)
  .settings(
    commonSettings,
    name := "repcheck-bill-decomposition-root",
    publish / skip := true
  )

// text-structure — DP: deterministic parsers + SubSplitter + chunk reassembly + PDF text extraction.
// Deterministic (no network/disk I/O); PDFBox operates in-memory on bytes. Zero foundation deps.
// The §4 decomposition-ml / decomposition-llm / decomposition-pipeline subprojects land in later sessions.
lazy val textStructure = (project in file("text-structure"))
  .dependsOn(conformance % "test->test")
  .settings(
    commonSettings,
    name := "text-structure",
    libraryDependencies ++= Seq(
      "org.scala-lang.modules" %% "scala-xml"       % "2.3.0",
      "org.apache.pdfbox"       % "pdfbox"          % "3.0.3",
      "com.repcheck"           %% "repcheck-utils"  % "0.1.1"    % Test, // shared E2ETest tag
      "org.scalatestplus"      %% "scalacheck-1-17" % "3.2.18.0" % Test,
      "org.scalacheck"         %% "scalacheck"      % "1.17.0"   % Test
    )
  )

// conformance — DC: the §10c consistency harness (test-support). The one-corpus fixture (`Corpus`, built from the local
// AlloyDB by scripts/build-corpus.sh and committed under src/main/resources) + the `*Contract` framework
// (`ConformanceContract`, `StructuredCodecLaws`). Consumed by other modules via `% "test->test"`; later phases plug
// their trait contracts in. No main code → no coverage surface; all deps are Test-scoped.
lazy val conformance = (project in file("conformance"))
  .settings(
    commonSettings,
    name := "conformance",
    libraryDependencies ++= Seq(
      "com.repcheck"      %% "repchecksharedmodels" % "0.1.59"   % Test,
      "com.repcheck"      %% "repcheck-utils"       % "0.1.1"    % Test, // shared E2ETest tag for DC-3 live schema check
      "org.scalatestplus" %% "scalacheck-1-17"      % "3.2.18.0" % Test,
      "org.scalacheck"    %% "scalacheck"           % "1.17.0"   % Test,
      "net.reactivecore"  %% "circe-json-schema"    % "0.4.1"    % Test,
      "org.postgresql"     % "postgresql"           % "42.7.3"   % Test  // DC-3 live information_schema introspection
    )
  )

// decomposition-ml — D3b: the PRODUCTION clustering home (DP-0 prototyped it inside `evaluation`; now promoted). The
// DP-0-validated pipeline — SmileHacClusterer + ClusteringConfig + the embedding primitives (cosine, standardize) — plus
// the ConceptClusterer trait (Vector1024 sections + parser breadcrumbs + subject count → concept-group member indices).
// PURE + deterministic (no F[_]). Consumed by `evaluation` (the gold harness) and, later, the decomposition orchestrator.
lazy val decompositionMl = (project in file("decomposition-ml"))
  .dependsOn(conformance % "test->test")
  .settings(
    commonSettings,
    name := "decomposition-ml",
    libraryDependencies += "com.github.haifengl" % "smile-core" % "3.1.1",
    libraryDependencies ++= Dependencies.pureConfig,
    libraryDependencies ++= Dependencies.circe, // parse the bundled standardization-stats artifact
    libraryDependencies ++= Seq(
      "org.scalatestplus" %% "scalacheck-1-17" % "3.2.18.0" % Test,
      "org.scalacheck"    %% "scalacheck"      % "1.17.0"   % Test
    )
  )

// evaluation — DP-0: the empirical-gate harness (master §10b). Throwaway evaluation code, NOT shipped production. The
// gold set (labels over the shared corpus) + metrics + baselines + A/B experiments that authorize D3b–D6. Gold loader +
// generator are Test-scoped (test-support data, like the corpus); the gold fixtures live in src/main/resources/gold.
// Later sub-steps add Ollama-backed wiring + metrics. dependsOn decomposition-ml (production clusterer) + text-structure
// (parser drafts boundaries) + conformance (Corpus + ConformanceContract).
lazy val evaluation = (project in file("evaluation"))
  .dependsOn(decompositionMl, textStructure, conformance % "test->test")
  .settings(
    commonSettings,
    name := "evaluation",
    // Smile is COMPILE-scoped: DP0-5 prediction uses the SAME Smile HAC the production ConceptClusterer (D3b) will, so
    // the D_max / cut config tuned here transfers literally. PureConfig drives the tunable ClusteringConfig knobs.
    libraryDependencies += "com.github.haifengl" % "smile-core" % "3.1.1",
    libraryDependencies ++= Dependencies.pureConfig,
    libraryDependencies ++= (
      Dependencies.circe ++ Dependencies.catsEffect ++ Dependencies.http4sEmber ++ Seq(
        "org.scalatestplus" %% "scalacheck-1-17"    % "3.2.18.0",
        "org.scalacheck"    %% "scalacheck"         % "1.17.0",
        "com.repcheck"      %% "repcheck-embedding" % "0.1.4",  // DP0-3: F3 SemanticEmbeddingService (live Ollama)
        "com.repcheck"      %% "repcheck-utils"     % "0.1.5",  // RetryWrapper (F3 ctor); newer than the 0.1.1 elsewhere
        "com.anthropic"      % "anthropic-java"     % "2.18.0" // DP0-4b: Claude concept judge (reference groupings)
      )
    ).map(_ % Test)
  )

lazy val docGenerator = (project in file("doc-generator"))
  .settings(
    commonSettings,
    libraryDependencies ++= Seq(
      "com.anthropic" % "anthropic-java" % "2.18.0",
      "org.typelevel" %% "cats-effect" % "3.5.4",
      "ch.qos.logback" % "logback-classic" % "1.5.6"
    ),
    // Exclude WartRemover for this utility project — uses Java SDK patterns
    wartremoverErrors := Seq.empty,
    wartremoverWarnings := Seq.empty,
    // Exclude from coverage — utility project with no unit tests
    coverageEnabled := false
  )
