# CaseComplete

A Scala 3 library that provides compile-time guarantees for complete case class field handling. CaseComplete ensures that all fields of a case class are processed by your transformation logic, preventing runtime errors from forgotten fields.

![CaseComplete Demo - Compile-time field validation](screenshots/casecomplete.gif "CaseComplete in action")


## Features

- **Compile-time Safety**: Ensures all case class fields have corresponding handlers
- **Optional Field Support**: Built-in support for `Option` fields with `usingNonEmpty`
- **Builder Pattern**: Fluent API for building handlers
- **Macro-based**: Leverages Scala 3 macros for compile-time validation

## Installation

[![Maven Central](https://maven-badges.sml.io/sonatype-central/io.github.stivens/casecomplete_3/badge.svg?style=social)](https://maven-badges.sml.io/sonatype-central/io.github.stivens/casecomplete_3)

`build.sbt`:

```scala
libraryDependencies += "io.github.stivens" %% "casecomplete" % "0.2.1"
```

`scala-cli`:

```scala
//> using lib "io.github.stivens::casecomplete:0.2.1"
```

`scala-cli REPL`:

```bash
scala-cli repl --dep io.github.stivens::casecomplete:0.2.1
```

## Quick Start

```scala
import io.github.stivens.casecomplete.CaseComplete

import doobie.*

import java.time.Year

case class MovieFilter(
  title_like: Option[String] = None,
  director_eq: Option[String] = None,
  releaseYear_eq: Option[Year] = None,
  rating_gte: Option[Double] = None
)

// Create a handler that transforms MovieFilter to SQL conditions
val movieFilterHandler = CaseComplete.build[MovieFilter, Option[Fragment]]
  .usingNonEmpty(_.title_like)(title => fr"title ILIKE $title")
  .usingNonEmpty(_.director_eq)(director => fr"director = $director")
  .usingNonEmpty(_.releaseYear_eq)(year => fr"release_year = $year")
  .usingNonEmpty(_.rating_gte)(rating => fr"rating >= $rating")
  .compile

// Use the handler
val filter = MovieFilter(
  releaseYear_eq = Some(Year.of(1999)),
  rating_gte = Some(7.0)
)

val conditions = movieFilterHandler.eval(filter).toSet.flatten
// Returns: Set(fr"release_year = ${1999}", fr"rating >= ${7.0}")
```

## How It Works

CaseComplete uses Scala 3's macro system to:

1. **Track Handled Fields**: The builder tracks which fields have been handled through type parameters
2. **Compile-time Validation**: When you call `.compile()`, it verifies all case class fields have handlers
3. **Field Name Extraction**: Extracts field names from selectors like `_.fieldName` at compile time

## CaseComplete vs Pattern Matching

While pattern matching on case classes is a powerful Scala feature, it has limitations when it comes to ensuring complete field handling. CaseComplete provides **dual-purpose functionality**: it not only allows you to implement transformations that are validated at compile-time, but also provides an interface that guarantees every implementation will have these properties.

### The Problem with Pattern Matching

Pattern matching on case classes is just a specific implementation of `A => B` functions. **You cannot enforce the use of pattern matching at the interface level** - the interface only specifies the function signature, not how it should be implemented. This means there's no compile-time guarantee that all fields will be handled.

```scala
abstract class AbstractRepository[ENTITY_TYPE, FILTER_TYPE, UPDATE_TYPE](
  tableName: String,
  evalFilter: FILTER_TYPE => Set[Fragment],
  evalUpdate: UPDATE_TYPE => Set[Fragment]
) {
  // some methods etc
}

case class MovieFilter(
  title_like: Option[String] = None,
  director_eq: Option[String] = None,
  releaseYear_eq: Option[Year] = None,
  rating_gte: Option[Double] = None
)

case class MovieUpdate(
  title: Option[String],
  rating: Option[Double],
  cast: Option[List[Person]]
)

object MovieRepository extends AbstractRepository[Movie, MovieFilter, MovieUpdate] (
  tableName = "movies",
  evalFilter = {
    case MovieFilter(director_eq, title_like, releaseYear_eq, rating_gte) => List(
      title_like.map(title => fr"title ILIKE $title"),
      director_eq.map(director => fr"director = $director"),
      releaseYear_eq.map(year => fr"release_year = $year"),
      rating_gte.map(rating => fr"rating >= $rating")
    ).flatten.toSet
  }, // This looks good at first glance, but notice the order mismatch:
  // - Pattern has: director_eq, title_like, releaseYear_eq, rating_gte
  // - Usage has: title_like, director_eq, releaseYear_eq, rating_gte
  // This will cause runtime bugs: fr"title ILIKE 'some director name'" and fr"director = 'some movie title'"
  evalUpdate = update => {
    List(
      update.title.map(title => fr"title = $title"),
      update.cast.map(cast => fr"cast = $cast")
    ).flatten.toSet
  } // Whoops - the `rating` field is not handled, but it still compiles!
)
```

### The CaseComplete Solution

With CaseComplete, you can define the `AbstractRepository` to enforce complete field handling:

```scala
abstract class AbstractRepository[ENTITY_TYPE, FILTER_TYPE, UPDATE_TYPE](
  tableName: String,
  evalFilter: CaseComplete[FILTER_TYPE, Option[Fragment]],
  evalUpdate: CaseComplete[UPDATE_TYPE, Option[Fragment]]
) {
  // some methods etc
}
```

Now, providing an implementation that isn't validated at compile-time is **impossible**. All classes that inherit from `AbstractRepository` must provide implementations that handle every field:

```scala
object MovieRepository extends AbstractRepository[Movie, MovieFilter, MovieUpdate] (
  tableName = "movies",
  evalFilter = CaseComplete.build[MovieFilter, Option[Fragment]]
    .usingNonEmpty(_.title_like)(title => fr"title ILIKE $title")
    .usingNonEmpty(_.director_eq)(director => fr"director = $director")
    .usingNonEmpty(_.releaseYear_eq)(year => fr"release_year = $year")
    .usingNonEmpty(_.rating_gte)(rating => fr"rating >= $rating")
    .compile,
  evalUpdate = CaseComplete.build[MovieUpdate, Option[Fragment]]
    .usingNonEmpty(_.title)(title => fr"title = $title")
    .usingNonEmpty(_.rating)(rating => fr"rating = $rating")
    .usingNonEmpty(_.cast)(cast => fr"cast = $cast")
    .compile
)
```

## Pro tip: Make interfaces more expressive with type aliases

```scala
type AsFragments[A <: Product] = CaseComplete[A, Option[Fragment]]
def toFragments[A <: Product]: CaseCompleteBuilder[A, Option[Fragment], EmptyTuple] = CaseComplete.build[A, Option[Fragment]]


abstract class AbstractRepository[ENTITY_TYPE, FILTER_TYPE, UPDATE_TYPE](
  tableName: String,
  evalFilter: AsFragments[FILTER_TYPE],
  evalUpdate: AsFragments[UPDATE_TYPE]
)

object MovieRepository extends AbstractRepository[Movie, MovieFilter, MovieUpdate] (
  tableName = "movies",
  evalFilter = toFragments[MovieFilter]
    .usingNonEmpty(_.title_like)(title => fr"title ILIKE $title")
    .usingNonEmpty(_.director_eq)(director => fr"director = $director")
    .usingNonEmpty(_.releaseYear_eq)(year => fr"release_year = $year")
    .usingNonEmpty(_.rating_gte)(rating => fr"rating >= $rating")
    .compile,
  evalUpdate = toFragments[MovieUpdate]
    .usingNonEmpty(_.title)(title => fr"title = $title")
    .usingNonEmpty(_.rating)(rating => fr"rating = $rating")
    .usingNonEmpty(_.cast)(cast => fr"cast = $cast")
    .compile
)
```


## API Reference

### CaseComplete.build

Creates a new builder instance:

```scala
object CaseComplete {
  def build[SOURCE_TYPE <: Product, TARGET_TYPE]: CaseCompleteBuilder[SOURCE_TYPE, TARGET_TYPE, EmptyTuple]
```

### Builder Methods

#### `using(_.field)(handler)`

Registers a handler for a specific field:

```scala
builder.using(_.fieldName)(value => transformedValue)
```

#### `usingNonEmpty(_.field)(handler)` (for Option fields)

Registers a handler for optional fields, automatically handling `None`:

```scala
builder.usingNonEmpty(_.optionalField)(value => transformedValue)
// equivalant to builder.using(_.optionalField)((_: Option[F]).map((value: F) => transformedValue))
```

#### `ignoring(_.field)`

Explicitly marks a field as ignored during processing. This is useful when you want to intentionally skip a field (e.g., deprecated fields) while ensuring compile-time validation that you didn't forget to handle it:

```scala
builder.ignoring(_.deprecatedField)
```

**Why use `ignoring`?** When you have fields that you intentionally don't want to process (like deprecated fields, internal fields, or fields that don't apply to your use case), `ignoring` provides a clear, explicit way to indicate this intention. It guarantees that you made a conscious decision to ignore the field rather than accidentally forgetting to handle it.

#### `compile`

Compiles the handler and validates all fields are handled:

```scala
val builder: CaseCompleteBuilder[A, B, _] = ???
val handler: CaseComplete[A, B] = builder.compile
```

### Handler Usage

```scala
val result = handler.eval(sourceInstance)
// Returns: List[TargetType]
```

## Requirements

- Scala >= 3.3

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This project is licensed under the MIT License - see the LICENSE file for details.
