package io.github.stivens.casecomplete

import org.scalatest.funspec.AnyFunSpec

import java.time.Year

class CaseCompleteSpec extends AnyFunSpec {
  describe("CaseCompleteBuilder") {
    describe("when given a source type and a target type") {

      case class MovieFilter(
          title_like: Option[String] = None,
          director_eq: Option[String] = None,
          releaseYear: Option[Year] = None,
          rating_gte: Option[Double] = None
      )

      val filter = MovieFilter(
        releaseYear = Some(Year.of(1999)),
        rating_gte = Some(7.0)
      )
      val expectedResult = Set("releaseYear = 1999", "rating >= 7.0")

      val buildMovieFilterHandler = CaseComplete.build[MovieFilter, Option[String]]

      it("should properly use all the fields of the source type and compile") {
        val movieFilterHandler = buildMovieFilterHandler
          .using(_.title_like)(_.map(title => f"title ILIKE $title"))
          .using(_.director_eq)(_.map(director => f"director = $director"))
          .using(_.releaseYear)(_.map(releaseYear => f"releaseYear = $releaseYear"))
          .using(_.rating_gte)(_.map(rating => f"rating >= $rating"))
          .compile

        val avaulated = movieFilterHandler.eval(filter).toSet.flatten

        assert(avaulated == expectedResult)
      }

      it("should properly use all the non-empty optional fields of the source type and compile") {
        val movieFilterHandler = buildMovieFilterHandler
          .usingNonEmpty(_.title_like)(title => f"title ILIKE $title")
          .usingNonEmpty(_.director_eq)(director => f"director = $director")
          .usingNonEmpty(_.releaseYear)(releaseYear => f"releaseYear = $releaseYear")
          .usingNonEmpty(_.rating_gte)(rating => f"rating >= $rating")
          .compile

        val avaulated = movieFilterHandler.eval(filter).toSet.flatten

        assert(avaulated == expectedResult)
      }
    }

    describe("when given a source type with value which is not defined in the primary constructor") {

      case class MovieFilter(
          title_like: Option[String] = None,
          director_eq: Option[String] = None,
          releaseYear: Option[Year] = None,
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
          .using(_.releaseYear)(_ => None)
          .using(_.rating_gte)(_ => None)
          .compile

        assert(true) // code compiles
      }
    }
  }
}
