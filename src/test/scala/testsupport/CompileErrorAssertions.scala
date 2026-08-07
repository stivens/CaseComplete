package testsupport

import org.scalatest.Assertions

import scala.compiletime.testing.typeCheckErrors

trait CompileErrorAssertions extends Assertions {

  /** Asserts that `code` fails to compile with a single error containing every expected substring. */
  inline def assertErrorContains(inline code: String, expected: String*): Unit = {
    val errors = typeCheckErrors(code)
    assert(
      errors.exists(e => expected.forall(e.message.contains)),
      s"no compile error contained ${expected.map(e => s"'$e'").mkString(" and ")}; got: ${errors.map(_.message)}"
    )
  }
}
