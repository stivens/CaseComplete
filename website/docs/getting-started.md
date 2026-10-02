# Getting started

## Install

CaseComplete needs Scala 3.3 or newer and is published for the JVM, Scala.js and Scala Native.

=== "sbt"

    ```scala
    libraryDependencies += "io.github.stivens" %% "casecomplete" % "@VERSION@"
    ```

=== "sbt (cross-built)"

    ```scala
    libraryDependencies += "io.github.stivens" %%% "casecomplete" % "@VERSION@"
    ```

=== "scala-cli"

    ```scala
    //> using dep io.github.stivens::casecomplete:@VERSION@
    ```

=== "REPL"

    ```bash
    scala-cli repl --dep io.github.stivens::casecomplete:@VERSION@
    ```

## Your first handler

Start a builder with `CaseComplete.build[Source, Target]`, register one handler per field, and finish with `.compile`.
This one turns a search filter into SQL conditions with [doobie](https://typelevel.org/doobie/):

```scala mdoc:silent
import io.github.stivens.casecomplete.CaseComplete

import doobie.*
import doobie.implicits.*

case class MovieFilter(
  title_like: Option[String] = None,
  director_eq: Option[String] = None,
  releaseYear_eq: Option[Int] = None,
  rating_gte: Option[Double] = None
)

val movieFilterHandler = CaseComplete.build[MovieFilter, Option[Fragment]]
  .usingNonEmpty(_.title_like)(title => fr"title ILIKE $title")
  .usingNonEmpty(_.director_eq)(director => fr"director = $director")
  .usingNonEmpty(_.releaseYear_eq)(year => fr"release_year = $year")
  .usingNonEmpty(_.rating_gte)(rating => fr"rating >= $rating")
  .compile
```

`eval` runs every handler and returns the results in field declaration order.
Fields left as `None` produce `None`, so `flatten` keeps only the conditions that apply:

```scala mdoc
val filter = MovieFilter(
  releaseYear_eq = Some(1999),
  rating_gte = Some(7.0)
)

movieFilterHandler.eval(filter).flatten
```

Add a field to `MovieFilter` and this code stops compiling until the new field has a handler.

```scala mdoc:fail
case class MovieFilterV2(
  title_like: Option[String] = None,
  rating_gte: Option[Double] = None,
  genre_eq: Option[String] = None
)

CaseComplete.build[MovieFilterV2, Option[Fragment]]
  .usingNonEmpty(_.title_like)(title => fr"title ILIKE $title")
  .usingNonEmpty(_.rating_gte)(rating => fr"rating >= $rating")
  .compile
```

## Next steps

- [Why not pattern matching?](why.md) explains what a CaseComplete parameter guarantees and a plain function doesn't.
- The [API reference](api.md) covers `using`, `usingNonEmpty`, `ignoring` and evaluation order.
