package io.github.stivens.casecomplete

import org.scalatest.funspec.AnyFunSpec

import java.time.Year
import scala.compiletime.testing.typeCheckErrors

class CaseCompleteSpec extends AnyFunSpec {
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
        val empty = MovieFilter()
      }

      val buildMovieFilterHandler = CaseComplete.build[MovieFilter, Option[String]]

      it("should not require the extra fields to be handled") {
        val movieFilterHandler = buildMovieFilterHandler
          .using(_.title_like)(_ => None)
          .using(_.director_eq)(_ => None)
          .using(_.releaseYear_eq)(_ => None)
          .using(_.rating_gte)(_ => None)
          .compile

        assert(true) // code compiles
      }
    }

    describe("when validating the chain at compile time") {

      it("should compile a chain that handles every field") {
        assertCompiles("""
          CaseComplete.build[TwoFieldFilter, Option[String]]
            .using(_.a)(identity)
            .using(_.b)(identity)
            .compile
        """)
      }

      // Asserted on the message text, not just on failure: each of these messages exists only to be
      // read, so a test that accepts any compile error would not notice it degrading into the raw
      // compiler diagnostic it was written to replace.
      it("should report the unhandled field when one has no handler") {
        val errors = typeCheckErrors("""
          CaseComplete.build[TwoFieldFilter, Option[String]]
            .using(_.a)(identity)
            .compile
        """)

        assert(errors.exists(_.message.contains("Missing handlers for fields: b")))
      }

      it("should report the field name when the same field is handled twice") {
        val errors = typeCheckErrors("""
          CaseComplete.build[TwoFieldFilter, Option[String]]
            .using(_.a)(identity)
            .using(_.a)(identity)
        """)

        assert(errors.exists(_.message.contains("Field 'a' has already been handled")))
      }

      it("should report the offending expression when the selector is not a plain field access") {
        val errors = typeCheckErrors("""
          CaseComplete.build[TwoFieldFilter, Option[String]]
            .using(filter => filter.a.map(_.trim))(identity)
        """)

        assert(errors.exists(_.message.contains("expected a field selector")))
      }

      it("should point at `using` when usingNonEmpty is applied to a non-Option target") {
        val errors = typeCheckErrors("""
          CaseComplete.build[TwoFieldFilter, String]
            .usingNonEmpty(_.a)(value => value)
        """)

        assert(errors.exists(_.message.contains("usingNonEmpty requires the target type to be an Option")))
      }
    }
  }
}

// Top-level so the type-checking snippets above can name it.
case class TwoFieldFilter(a: Option[String], b: Option[String])
