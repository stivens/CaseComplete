package io.github.stivens.casecomplete

import org.scalatest.funspec.AnyFunSpec
import testsupport.CompileErrorAssertions

import java.time.Year

class CaseCompleteSpec extends AnyFunSpec with CompileErrorAssertions {
  describe("CaseCompleteBuilder") {
    describe("when given a source type and a target type") {

      case class MovieFilter(
          title_like: Option[String] = None,
          director_eq: Option[String] = None,
          releaseYear_eq: Option[Year] = None,
          rating_gte: Option[Double] = None
      )

      val filter = MovieFilter(
        releaseYear_eq = Some(Year.of(1999)),
        rating_gte = Some(7.0)
      )
      val expectedOrder  = List("rating >= 7.0", "releaseYear = 1999")
      val expectedResult = expectedOrder.toSet

      val buildMovieFilterHandler = CaseComplete.build[MovieFilter, Option[String]]

      val movieFilterHandler = buildMovieFilterHandler
        .using(_.title_like)(_.map(title => f"title ILIKE $title"))
        .using(_.director_eq)(_.map(director => f"director = $director"))
        .using(_.releaseYear_eq)(_.map(releaseYear => f"releaseYear = $releaseYear"))
        .using(_.rating_gte)(_.map(rating => f"rating >= $rating"))
        .compile

      it("should properly use all the fields of the source type and compile") {
        val evaulated = movieFilterHandler.eval(filter).toSet.flatten

        assert(evaulated == expectedResult)
      }

      it("should properly use all the non-empty optional fields of the source type and compile") {
        val nonEmptyHandler = buildMovieFilterHandler
          .usingNonEmpty(_.title_like)(title => f"title ILIKE $title")
          .usingNonEmpty(_.director_eq)(director => f"director = $director")
          .usingNonEmpty(_.releaseYear_eq)(releaseYear => f"releaseYear = $releaseYear")
          .usingNonEmpty(_.rating_gte)(rating => f"rating >= $rating")
          .compile

        val evaulated = nonEmptyHandler.eval(filter).toSet.flatten

        assert(evaulated == expectedResult)
      }

      it("should evaluate handlers in alphabetical order of field name, on every call") {
        // Repeated so that caching the ordering as something single-use (a view, an iterator) fails here.
        assert(movieFilterHandler.eval(filter).flatten == expectedOrder)
        assert(movieFilterHandler.eval(filter).flatten == expectedOrder)
      }

      it("should allow to explicitly ignore a field") {
        val movieFilterHandler = buildMovieFilterHandler
          .ignoring(_.title_like)
          .ignoring(_.director_eq)
          .ignoring(_.releaseYear_eq)
          .ignoring(_.rating_gte)
          .compile

        val evaulated = movieFilterHandler.eval(filter).toSet.flatten

        assert(evaulated == Set.empty)
      }
    }

    describe("when given a source type with value which is not defined in the primary constructor") {

      case class MovieFilter(
          title_like: Option[String] = None,
          director_eq: Option[String] = None,
          releaseYear_eq: Option[Year] = None,
          rating_gte: Option[Double] = None
      ) {
        lazy val isEmpty: Boolean = this == MovieFilter.empty
        val foo: String           = "bar"
      }

      object MovieFilter {
        // Explicit `new`: `MovieFilter()` would re-enter this initializer through the companion's apply.
        val empty = new MovieFilter(None, None, None, None)
      }

      val allConstructorFieldsHandled = CaseComplete
        .build[MovieFilter, Option[String]]
        .using(_.title_like)(_ => None)
        .using(_.director_eq)(_ => None)
        .using(_.releaseYear_eq)(_ => None)
        .using(_.rating_gte)(_ => None)

      it("should not require the extra fields to be handled") {
        allConstructorFieldsHandled.compile

        assert(true) // code compiles
      }

      it("should allow the extra fields to be handled") {
        val movieFilterHandler = allConstructorFieldsHandled
          .using(_.foo)(Some(_))
          .compile

        assert(movieFilterHandler.eval(MovieFilter()).flatten == List("bar"))
      }
    }

    describe("when validating the chain at compile time") {

      // Positive control for the negative snippet tests below.
      it("should compile a chain that handles every field") {
        assertCompiles("""
          CaseComplete.build[TwoFieldFilter, Option[String]]
            .using(_.a)(identity)
            .using(_.b)(identity)
            .compile
        """)
      }

      // These assert the message text, not just failure: the messages exist to be read, and a
      // failure-only test would not notice them degrading into raw compiler diagnostics.
      it("should report the unhandled field when one has no handler") {
        assertErrorContains(
          """
          CaseComplete.build[TwoFieldFilter, Option[String]]
            .using(_.a)(identity)
            .compile
          """,
          "Missing handlers for fields: b"
        )
      }

      it("should report the field name when the same field is handled twice") {
        assertErrorContains(
          """
          CaseComplete.build[TwoFieldFilter, Option[String]]
            .using(_.a)(identity)
            .using(_.a)(identity)
          """,
          "Field 'a' has already been handled"
        )
      }

      it("should report the offending expression when the selector is not a plain field access") {
        assertErrorContains(
          """
          CaseComplete.build[TwoFieldFilter, Option[String]]
            .using(filter => filter.a.map(_.trim))(identity)
          """,
          "expected a field selector"
        )
      }

      it("should reject a nested selector, which would register the inner field's name against the source type") {
        assertErrorContains(
          """
          CaseComplete.build[NestedFilter, Option[String]]
            .using(_.a.b)(identity)
          """,
          "expected a field selector"
        )
      }

      it("should report the field name when a field is ignored and then handled") {
        assertErrorContains(
          """
          CaseComplete.build[TwoFieldFilter, Option[String]]
            .ignoring(_.a)
            .using(_.a)(identity)
          """,
          "Field 'a' has already been handled"
        )
      }

      it("should point at `using` when usingNonEmpty is applied to a non-Option target") {
        assertErrorContains(
          """
          CaseComplete.build[TwoFieldFilter, String]
            .usingNonEmpty(_.a)(value => value)
          """,
          "usingNonEmpty requires the target type to be exactly Option"
        )
      }

      it("should reject a target type that is a strict subtype of Option") {
        assertErrorContains(
          """
          CaseComplete.build[TwoFieldFilter, Some[String]]
            .usingNonEmpty(_.a)(identity)
          """,
          "usingNonEmpty requires the target type to be exactly Option"
        )
      }

      it("should not count a handled body val toward the completeness of constructor fields") {
        assertErrorContains(
          """
          CaseComplete.build[BodyValFilter, Option[String]]
            .using(_.derived)(identity)
            .compile
          """,
          "Missing handlers for fields: a"
        )
      }

      it("should explain the fix when compile is called on a builder ascribed a widened type") {
        assertErrorContains(
          """
          val b: macros.CaseCompleteBuilder[TwoFieldFilter, Option[String], ?] =
            CaseComplete.build[TwoFieldFilter, Option[String]]
              .using(_.a)(identity)
              .using(_.b)(identity)
          b.compile
          """,
          "the chain's inferred type"
        )
      }

      // The validations live in registerField, shared by all entry points; these pin that
      // `ignoring` and `usingNonEmpty` route through it rather than just `using`.
      it("should run the shared selector checks for ignoring too") {
        assertErrorContains(
          """CaseComplete.build[NestedFilter, Option[String]].ignoring(_.a.b)""",
          "expected a field selector"
        )
      }

      it("should report a bad selector before usingNonEmpty's target-type check") {
        assertErrorContains(
          """
          CaseComplete.build[NestedFilter, String]
            .usingNonEmpty(_.a.b)(identity)
          """,
          "expected a field selector"
        )
      }
    }
  }
}

// Top-level so the type-checking snippets above can name them.
case class TwoFieldFilter(a: Option[String], b: Option[String])
case class NestedInner(b: Option[String])
case class NestedFilter(a: NestedInner, b: Option[String])
case class BodyValFilter(a: Option[String]) { val derived: Option[String] = a }
