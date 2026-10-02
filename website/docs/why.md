# Why not pattern matching?

A pattern match on a case class can handle every field, but nothing makes it.
To the compiler, a `MovieUpdate => Set[Fragment]` parameter is just a function, so an interface can't require that its implementations look at every field.
CaseComplete's `CaseComplete[A, B]` type can only be produced by a builder that has seen every field, so the requirement lives in the signature.

## The problem

Here's a repository base class that takes plain functions:

```scala mdoc:invisible
import io.github.stivens.casecomplete.CaseComplete

import doobie.*
import doobie.implicits.*

import examples.movies.*
```

```scala mdoc:silent
abstract class AbstractRepository[ENTITY, FILTER, UPDATE](
  tableName: String,
  evalFilter: FILTER => Set[Fragment],
  evalUpdate: UPDATE => Set[Fragment]
)
```

Both implementations below compile, and both are wrong:

```scala mdoc:silent
object MovieRepository extends AbstractRepository[Movie, MovieFilter, MovieUpdate](
  tableName = "movies",
  evalFilter = {
    case MovieFilter(director_eq, title_like, releaseYear_eq, rating_gte) =>
      List(
        title_like.map(title => fr"title ILIKE $title"),
        director_eq.map(director => fr"director = $director"),
        releaseYear_eq.map(year => fr"release_year = $year"),
        rating_gte.map(rating => fr"rating >= $rating")
      ).flatten.toSet
  },
  evalUpdate = update =>
    List(
      update.title.map(title => fr"title = $title"),
      update.director.map(director => fr"director = $director")
    ).flatten.toSet
)
```

- **`evalFilter`** binds the fields positionally, and the first two names are swapped. A title search ends up filtering on `director`. Both fields are `Option[String]`, so the types can't catch it.
- **`evalUpdate`** never reads `rating`. Updating a movie's rating silently does nothing.

## The fix

Declare the parameters as `CaseComplete` instead:

```scala mdoc:reset:invisible
import io.github.stivens.casecomplete.CaseComplete

import doobie.*
import doobie.implicits.*

import examples.movies.*
```

```scala mdoc:silent
abstract class AbstractRepository[ENTITY, FILTER <: Product, UPDATE <: Product](
  tableName: String,
  evalFilter: CaseComplete[FILTER, Option[Fragment]],
  evalUpdate: CaseComplete[UPDATE, Option[Fragment]]
)
```

Every subclass now has to hand over a compiled builder, and a builder only compiles once each field has a handler.
Handlers are matched by field name, never by position, so the swap above can't happen.
The forgotten `rating` is now a compile error:

```scala mdoc:fail
object MovieRepository extends AbstractRepository[Movie, MovieFilter, MovieUpdate](
  tableName = "movies",
  evalFilter = CaseComplete.build[MovieFilter, Option[Fragment]]
    .usingNonEmpty(_.title_like)(title => fr"title ILIKE $title")
    .usingNonEmpty(_.director_eq)(director => fr"director = $director")
    .usingNonEmpty(_.releaseYear_eq)(year => fr"release_year = $year")
    .usingNonEmpty(_.rating_gte)(rating => fr"rating >= $rating")
    .compile,
  evalUpdate = CaseComplete.build[MovieUpdate, Option[Fragment]]
    .usingNonEmpty(_.title)(title => fr"title = $title")
    .usingNonEmpty(_.director)(director => fr"director = $director")
    .compile
)
```

Add the missing handler and the repository compiles:

```scala mdoc:silent
object MovieRepository extends AbstractRepository[Movie, MovieFilter, MovieUpdate](
  tableName = "movies",
  evalFilter = CaseComplete.build[MovieFilter, Option[Fragment]]
    .usingNonEmpty(_.title_like)(title => fr"title ILIKE $title")
    .usingNonEmpty(_.director_eq)(director => fr"director = $director")
    .usingNonEmpty(_.releaseYear_eq)(year => fr"release_year = $year")
    .usingNonEmpty(_.rating_gte)(rating => fr"rating >= $rating")
    .compile,
  evalUpdate = CaseComplete.build[MovieUpdate, Option[Fragment]]
    .usingNonEmpty(_.title)(title => fr"title = $title")
    .usingNonEmpty(_.director)(director => fr"director = $director")
    .usingNonEmpty(_.rating)(rating => fr"rating = $rating")
    .compile
)
```

If someone later adds a field to `MovieUpdate`, every repository that uses it stops compiling until the new field has a handler or an explicit [`ignoring`](api.md#ignoring).
To make signatures like these shorter, see [Type aliases](recipes.md).
