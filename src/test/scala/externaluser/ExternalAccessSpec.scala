package externaluser

import io.github.stivens.casecomplete.CaseComplete
import org.scalatest.funspec.AnyFunSpec

import scala.compiletime.testing.typeCheckErrors

case class Filter(a: Option[String], b: Option[String])

/**
 * The rest of the suite lives inside `io.github.stivens.casecomplete`, where `private[casecomplete]`
 * is indistinguishable from public. This spec sits outside that package, so it is the only place
 * that pins both halves of the access story: generated code still reaches the package-private
 * members, and users cannot.
 */
class ExternalAccessSpec extends AnyFunSpec {

  describe("a builder used from outside the library's package") {

    it("should compile and evaluate a chain") {
      val handler = CaseComplete
        .build[Filter, Option[String]]
        .usingNonEmpty(_.a)(value => f"a = $value")
        .ignoring(_.b)
        .compile

      assert(handler.eval(Filter(a = Some("x"), b = None)).flatten == List("a = x"))
    }

    it("should not let a field be marked handled without a handler") {
      val errors = typeCheckErrors("""
        CaseComplete.build[Filter, Option[String]]
          .using(_.a)(identity)
          .markHandled[("b", "a")]
          .compile
      """)

      assert(errors.exists(_.message.contains("markHandled")))
    }

    it("should not let a builder be constructed directly") {
      val errors = typeCheckErrors("""
        new io.github.stivens.casecomplete.macros.CaseCompleteBuilder[Filter, Option[String], ("a", "b")](Map.empty)
      """)

      assert(errors.exists(_.message.contains("CaseCompleteBuilder")))
    }
  }
}
