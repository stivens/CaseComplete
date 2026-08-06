package io.github.stivens.casecomplete

import org.scalatest.funspec.AnyFunSpec

import java.time.Year

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
      val expectedResult = Set("releaseYear = 1999", "rating >= 7.0")

      val buildMovieFilterHandler = CaseComplete.build[MovieFilter, Option[String]]

      it("should properly use all the fields of the source type and compile") {
        val movieFilterHandler = buildMovieFilterHandler
          .using(_.title_like)(_.map(title => f"title ILIKE $title"))
          .using(_.director_eq)(_.map(director => f"director = $director"))
          .using(_.releaseYear_eq)(_.map(releaseYear => f"releaseYear = $releaseYear"))
          .using(_.rating_gte)(_.map(rating => f"rating >= $rating"))
          .compile

        val evaulated = movieFilterHandler.eval(filter).toSet.flatten

        assert(evaulated == expectedResult)
      }

      it("should properly use all the non-empty optional fields of the source type and compile") {
        val movieFilterHandler = buildMovieFilterHandler
          .usingNonEmpty(_.title_like)(title => f"title ILIKE $title")
          .usingNonEmpty(_.director_eq)(director => f"director = $director")
          .usingNonEmpty(_.releaseYear_eq)(releaseYear => f"releaseYear = $releaseYear")
          .usingNonEmpty(_.rating_gte)(rating => f"rating >= $rating")
          .compile

        val evaulated = movieFilterHandler.eval(filter).toSet.flatten

        assert(evaulated == expectedResult)
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

      it("should fail to compile when a field has no handler") {
        assertDoesNotCompile("""
          CaseComplete.build[TwoFieldFilter, Option[String]]
            .using(_.a)(identity)
            .compile
        """)
      }

      it("should fail to compile when the same field is handled twice") {
        assertDoesNotCompile("""
          CaseComplete.build[TwoFieldFilter, Option[String]]
            .using(_.a)(identity)
            .using(_.a)(identity)
        """)
      }

      it("should fail to compile when the selector is not a plain field access") {
        assertDoesNotCompile("""
          CaseComplete.build[TwoFieldFilter, Option[String]]
            .using(filter => filter.a.map(_.trim))(identity)
        """)
      }

      it("should fail to compile usingNonEmpty when the target type is not an Option") {
        assertDoesNotCompile("""
          CaseComplete.build[TwoFieldFilter, String]
            .usingNonEmpty(_.a)(value => value)
        """)
      }
    }
  }
}

// Top-level so the assertDoesNotCompile snippets below can name it; never instantiated.
case class TwoFieldFilter(a: Option[String], b: Option[String])
