package io.github.stivens.casecomplete

import org.scalatest.funspec.AnyFunSpec

/**
 * Regression guard for the compile-time blowup described in `CaseCompleteBuilder` (the note above
 * `using`):
 * pre-fix the cost was ~1.9x per chain step (16 steps: 4.0 s of posttyper; 20 steps: 54 s), so the
 * 32 steps below would take hours; post-fix the file costs ~0.25 s. All three chaining methods are
 * interleaved because each is equally at risk.
 *
 * A regression fails by hanging, not by assertion -- hence `timeout-minutes` on the CI job.
 */
class LongChainSpec extends AnyFunSpec {

  describe("a builder chain with 32 steps") {

    case class WideFilter(
        f01: Option[String] = None,
        f02: Option[String] = None,
        f03: Option[String] = None,
        f04: Option[String] = None,
        f05: Option[String] = None,
        f06: Option[String] = None,
        f07: Option[String] = None,
        f08: Option[String] = None,
        f09: Option[String] = None,
        f10: Option[String] = None,
        f11: Option[String] = None,
        f12: Option[String] = None,
        f13: Option[String] = None,
        f14: Option[String] = None,
        f15: Option[String] = None,
        f16: Option[String] = None,
        f17: Option[String] = None,
        f18: Option[String] = None,
        f19: Option[String] = None,
        f20: Option[String] = None,
        f21: Option[String] = None,
        f22: Option[String] = None,
        f23: Option[String] = None,
        f24: Option[String] = None,
        f25: Option[String] = None,
        f26: Option[String] = None,
        f27: Option[String] = None,
        f28: Option[String] = None,
        f29: Option[String] = None,
        f30: Option[String] = None,
        f31: Option[String] = None,
        f32: Option[String] = None
    )

    it("should compile and evaluate every step") {
      val handler = CaseComplete
        .build[WideFilter, Option[String]]
        .usingNonEmpty(_.f01)(v => s"f01 = $v")
        .usingNonEmpty(_.f02)(v => s"f02 = $v")
        .usingNonEmpty(_.f03)(v => s"f03 = $v")
        .usingNonEmpty(_.f04)(v => s"f04 = $v")
        .usingNonEmpty(_.f05)(v => s"f05 = $v")
        .usingNonEmpty(_.f06)(v => s"f06 = $v")
        .usingNonEmpty(_.f07)(v => s"f07 = $v")
        .usingNonEmpty(_.f08)(v => s"f08 = $v")
        .usingNonEmpty(_.f09)(v => s"f09 = $v")
        .usingNonEmpty(_.f10)(v => s"f10 = $v")
        .usingNonEmpty(_.f11)(v => s"f11 = $v")
        .usingNonEmpty(_.f12)(v => s"f12 = $v")
        .usingNonEmpty(_.f13)(v => s"f13 = $v")
        .usingNonEmpty(_.f14)(v => s"f14 = $v")
        .usingNonEmpty(_.f15)(v => s"f15 = $v")
        .usingNonEmpty(_.f16)(v => s"f16 = $v")
        .usingNonEmpty(_.f17)(v => s"f17 = $v")
        .usingNonEmpty(_.f18)(v => s"f18 = $v")
        .usingNonEmpty(_.f19)(v => s"f19 = $v")
        .usingNonEmpty(_.f20)(v => s"f20 = $v")
        .usingNonEmpty(_.f21)(v => s"f21 = $v")
        .usingNonEmpty(_.f22)(v => s"f22 = $v")
        .usingNonEmpty(_.f23)(v => s"f23 = $v")
        .usingNonEmpty(_.f24)(v => s"f24 = $v")
        .using(_.f25)(_.map(v => s"f25 = $v"))
        .using(_.f26)(_.map(v => s"f26 = $v"))
        .using(_.f27)(_.map(v => s"f27 = $v"))
        .using(_.f28)(_.map(v => s"f28 = $v"))
        .ignoring(_.f29)
        .ignoring(_.f30)
        .ignoring(_.f31)
        .ignoring(_.f32)
        .compile

      val evaluated = handler.eval(WideFilter(f01 = Some("a"), f26 = Some("b"), f32 = Some("z"))).flatten

      assert(evaluated == List("f01 = a", "f26 = b"))
    }
  }
}
