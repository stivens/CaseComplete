# Type aliases

When a codebase uses the same target type everywhere, naming it once makes signatures read like intent:

```scala mdoc:silent
import io.github.stivens.casecomplete.CaseComplete
import io.github.stivens.casecomplete.macros.CaseCompleteBuilder

import doobie.*
import doobie.implicits.*

type AsFragments[A <: Product] = CaseComplete[A, Option[Fragment]]

def toFragments[A <: Product]: CaseCompleteBuilder[A, Option[Fragment], EmptyTuple] =
  CaseComplete.build[A, Option[Fragment]]
```

```scala mdoc:invisible
import examples.movies.*
```

```scala mdoc:silent
abstract class AbstractRepository[ENTITY, FILTER <: Product, UPDATE <: Product](
  tableName: String,
  evalFilter: AsFragments[FILTER],
  evalUpdate: AsFragments[UPDATE]
)

object MovieRepository extends AbstractRepository[Movie, MovieFilter, MovieUpdate](
  tableName = "movies",
  evalFilter = toFragments[MovieFilter]
    .usingNonEmpty(_.title_like)(title => fr"title ILIKE $title")
    .usingNonEmpty(_.director_eq)(director => fr"director = $director")
    .usingNonEmpty(_.releaseYear_eq)(year => fr"release_year = $year")
    .usingNonEmpty(_.rating_gte)(rating => fr"rating >= $rating")
    .compile,
  evalUpdate = toFragments[MovieUpdate]
    .usingNonEmpty(_.title)(title => fr"title = $title")
    .usingNonEmpty(_.director)(director => fr"director = $director")
    .usingNonEmpty(_.rating)(rating => fr"rating = $rating")
    .compile
)
```

!!! warning "Keep the builder's inferred type"
    The builder's type records which fields have been handled.
    Don't store a half-built chain in a value typed `CaseCompleteBuilder[A, B, ?]`: calling `compile` on it is a compile error, because that list of handled fields is gone.
    Returning a fresh builder typed with `EmptyTuple`, as `toFragments` does, is fine.
