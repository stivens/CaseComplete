package externaluser

import io.github.stivens.casecomplete.CaseComplete
import org.scalatest.funspec.AnyFunSpec
import testsupport.CompileErrorAssertions

case class Filter(a: Option[String], b: Option[String])

/**
 * Deliberately outside `io.github.stivens.casecomplete` -- the only vantage point where
 * `private[casecomplete]` differs from public. Pins both directions: generated code reaches the
 * package-private members, users cannot.
 */
class ExternalAccessSpec extends AnyFunSpec with CompileErrorAssertions {

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
      assertInaccessible(
        """
        CaseComplete.build[Filter, Option[String]]
          .using(_.a)(identity)
          .markHandled[("b", "a")]
          .compile
        """,
        "markHandled"
      )
    }

    it("should not let a builder be constructed directly") {
      assertInaccessible(
        """
        new io.github.stivens.casecomplete.macros.CaseCompleteBuilder[Filter, Option[String], ("a", "b")](Map.empty)
        """,
        "CaseCompleteBuilder"
      )
    }

    it("should not let a handler be registered under a forged field name") {
      assertInaccessible(
        """
        CaseComplete.build[Filter, Option[String]]
          .using(_.a)(identity)
          .addHandler[("b", "a")]("b", _ => None)
          .compile
        """,
        "addHandler"
      )
    }

    it("should not expose the handler map") {
      assertInaccessible(
        """CaseComplete.build[Filter, Option[String]].handlers""",
        "handlers"
      )
    }
  }

  private inline def assertInaccessible(inline code: String, member: String): Unit =
    assertErrorContains(code, member, "cannot be accessed")
}
