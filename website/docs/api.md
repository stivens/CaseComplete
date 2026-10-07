# API reference

The whole public API is one entry point, three ways to register a field, `compile`, and `eval`.
The examples on this page use this class:

```scala mdoc:silent
import io.github.stivens.casecomplete.CaseComplete

case class User(
  name: String,
  email: String,
  nickname: Option[String],
  legacyId: Long
)
```

## `CaseComplete.build`

```scala
def build[SOURCE_TYPE <: Product, TARGET_TYPE]: CaseCompleteBuilder[SOURCE_TYPE, TARGET_TYPE, EmptyTuple]
```

Starts an empty builder. `SOURCE_TYPE` is the case class whose fields must all be handled, and `TARGET_TYPE` is what every handler returns.

## `using`

```scala
builder.using(_.field)(value => result)
```

Registers a handler for one field. The handler gets the field's value and returns a `TARGET_TYPE`.

## `usingNonEmpty`

```scala
builder.usingNonEmpty(_.optionalField)(value => result)
// same as: builder.using(_.optionalField)(_.map(value => result))
```

For `Option` fields when `TARGET_TYPE` is itself an `Option`. The handler only runs for `Some`; `None` maps to `None`.

## `ignoring`

```scala
builder.ignoring(_.field)
```

Marks a field as handled without registering a handler for it, so it contributes nothing to `eval`.
Use it for deprecated or internal fields. It records that leaving the field out was a decision and not an oversight.

## `compile`

Checks at compile time that every field of `SOURCE_TYPE` has been handled or ignored, then produces the `CaseComplete[SOURCE_TYPE, TARGET_TYPE]`.

## `eval`

```scala
def eval(source: SOURCE_TYPE): List[TARGET_TYPE]
```

Runs every registered handler. Results come back in the order the fields are declared in the case class, whatever order the handlers were registered in.

```scala mdoc:silent
val describe = CaseComplete.build[User, Option[String]]
  .usingNonEmpty(_.nickname)(nick => s"aka $nick")
  .using(_.email)(email => Some(s"<$email>"))
  .using(_.name)(Some(_))
  .ignoring(_.legacyId)
  .compile
```

```scala mdoc
describe.eval(User("Ada", "ada@example.com", Some("countess"), 42L))
```

A selector may also name a member that isn't a constructor field, such as a `val` in the class body.
Those handlers run after all constructor fields, in registration order.

## Compile errors

Each of these mistakes is caught by the compiler. The errors below are real compiler output.

### A field has no handler

```scala mdoc:fail
CaseComplete.build[User, Option[String]]
  .using(_.name)(Some(_))
  .using(_.email)(Some(_))
  .compile
```

### A field is handled twice

```scala mdoc:fail
CaseComplete.build[User, String]
  .using(_.name)(identity)
  .using(_.name)(_.toUpperCase)
```

### The selector isn't a plain field access

A nested selector like `_.name.length` would otherwise register `length` as if it were a field of `User`, so it is rejected:

```scala mdoc:fail
CaseComplete.build[User, Int]
  .using(_.name.length)(identity)
```

### `usingNonEmpty` with a non-`Option` target

```scala mdoc:fail
CaseComplete.build[User, String]
  .usingNonEmpty(_.nickname)(identity)
```
