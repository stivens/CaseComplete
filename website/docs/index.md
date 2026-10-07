---
title: CaseComplete
hide:
  - navigation
  - toc
  - footer
---

```scala mdoc:invisible
import io.github.stivens.casecomplete.CaseComplete
```

<div class="cc-hero" markdown>
<div class="cc-hero__pitch" markdown>

# Forgot a field?<br>It won't compile.

CaseComplete is a Scala 3 library for code that turns a case class into something else, such as SQL conditions, log lines or JSON fields.
You register one handler per field, and the build fails if any field doesn't have one.

[Get started](getting-started.md){ .md-button .md-button--primary }
[View on GitHub](https://github.com/stivens/CaseComplete){ .md-button }

</div>
<div class="cc-hero__proof" markdown>

```scala mdoc:fail
case class Order(
  id: Long,
  items: List[String],
  coupon: Option[String]
)

CaseComplete.build[Order, String]
  .using(_.id)(id => s"id=$id")
  .using(_.items)(items => s"items=${items.mkString(",")}")
  .compile
```

<p class="cc-caption">Real compiler output, generated when this page was built.</p>

</div>
</div>

```scala mdoc:invisible
case class Order(id: Long, items: List[String], coupon: Option[String])
```

<div class="cc-section" markdown>

## A plain function can't make this promise

<div class="cc-compare" markdown>
<div markdown>

This compiles, and `coupon` is never read:

```scala mdoc:silent
val describe: Order => List[String] = order =>
  List(s"id=${order.id}", s"items=${order.items.mkString(",")}")
```

</div>
<div markdown>

This parameter only accepts a builder that handled every field of `Order`:

```scala mdoc:compile-only
def checkout(
  order: Order,
  describe: CaseComplete[Order, String]
): Unit = ???
```

</div>
</div>

Make an interface take `CaseComplete[A, B]`, and every implementation has to be complete. When someone later adds a field to `A`, the compiler lists each place that needs a handler. [Why not pattern matching?](why.md)

</div>

<div class="cc-section" markdown>

## Errors show up as you type

![An editor showing CaseComplete reporting a missing field handler](assets/casecomplete.gif){ .cc-demo loading=lazy }

</div>

<div class="cc-section" markdown>

## What you get

<div class="cc-features" markdown>
<div markdown>

### :material-check-all: Every field, checked

`compile` fails and names each field that has no handler.

</div>
<div markdown>

### :material-tag-outline: Matched by name

Each handler is tied to `_.field`. Two fields of the same type can't be swapped by mistake, as they can in a positional pattern match.

</div>
<div markdown>

### :material-help-circle-outline: Built for `Option`

`usingNonEmpty` runs your handler only for `Some`. `None` maps to `None`.

</div>
<div markdown>

### :material-eye-off-outline: Deliberate skips

`ignoring(_.field)` records that leaving a field out was a decision, so it doesn't look like an oversight.

</div>
<div markdown>

### :material-file-tree-outline: Enforced by the signature

A `CaseComplete[A, B]` parameter can't be satisfied by a function that skips fields.

</div>
<div markdown>

### :material-layers-triple-outline: JVM, JS and Native

One dependency for Scala 3.3+ on every platform. MiMa checks binary compatibility between releases.

</div>
</div>

</div>

<div class="cc-section" markdown>

## Install

```scala
libraryDependencies += "io.github.stivens" %% "casecomplete" % "@VERSION@"
```

For Scala.js, Scala Native, scala-cli and the REPL, see [Install](getting-started.md#install). The [getting started guide](getting-started.md) then walks you through your first handler.

</div>
