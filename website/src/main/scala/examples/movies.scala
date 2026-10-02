package examples.movies

case class Movie(title: String, director: String, releaseYear: Int, rating: Double)

case class MovieFilter(
  title_like: Option[String] = None,
  director_eq: Option[String] = None,
  releaseYear_eq: Option[Int] = None,
  rating_gte: Option[Double] = None
)

case class MovieUpdate(
  title: Option[String] = None,
  director: Option[String] = None,
  rating: Option[Double] = None
)
