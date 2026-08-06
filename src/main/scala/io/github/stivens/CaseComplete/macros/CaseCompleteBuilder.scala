package io.github.stivens.casecomplete.macros

import io.github.stivens.casecomplete.*

import scala.quoted.*

/**
 * Builds a [[CaseComplete]] by registering one handler per field. `Handled` accumulates the handled
 * field names as a tuple of singleton string types, so `compile` can verify completeness.
 *
 * {{{
 * val movieFilterHandler = CaseCompleteBuilder[MovieFilter, Option[String]]
 *   .usingNonEmpty(_.title_like)(title => s"title ILIKE $title")
 *   .usingNonEmpty(_.releaseYear)(year => s"releaseYear = $year")
 *   .ignoring(_.internalId)
 *   .compile
 * }}}
 */
class CaseCompleteBuilder[SOURCE_TYPE <: Product, TARGET_TYPE, Handled <: Tuple] private[casecomplete] (
    private[casecomplete] val handlers: Map[String, SOURCE_TYPE => TARGET_TYPE]
) {

  // Package-private, together with the constructor, so users cannot forge a Handled claim for a field
  // that has no handler. Quoted calls resolve at macro-definition site, so generated code still
  // reaches these -- see ExternalAccessSpec.
  private[casecomplete] def addHandler[NewHandled <: Tuple](
      name: String,
      handler: SOURCE_TYPE => TARGET_TYPE
  ): CaseCompleteBuilder[SOURCE_TYPE, TARGET_TYPE, NewHandled] =
    new CaseCompleteBuilder(handlers + (name -> handler))

  private[casecomplete] def markHandled[NewHandled <: Tuple]: CaseCompleteBuilder[SOURCE_TYPE, TARGET_TYPE, NewHandled] =
    new CaseCompleteBuilder(handlers)

  /**
   * Registers a handler for one field. The selector must be a plain field access, e.g. `_.title_like`.
   *
   * @example
   * {{{
   * builder.using(_.title_like)(_.map(title => s"title ILIKE $title"))
   * }}}
   */
  transparent inline def using[FIELD](
      inline field: SOURCE_TYPE => FIELD
  )(
      handler: FIELD => TARGET_TYPE
  ): CaseCompleteBuilder[SOURCE_TYPE, TARGET_TYPE, ?] = // The '?' hides the complex result type from the user
    ${ CaseCompleteBuilder.usingImpl('this, 'field, 'handler) }

  // Keep this and its sibling methods on the class. A `transparent inline` extension method binds its
  // receiver to a parameter proxy carrying the refined type of the whole preceding chain, which makes
  // compiling a chain exponential in its length -- see LongChainSpec.
  /**
   * Registers a handler for an optional field, automatically handling the None case.
   *
   * Equivalent to `using(_.field)(_.map(handler))`. Fails compilation with a dedicated error when
   * the target type is not an `Option`.
   *
   * @example
   * {{{
   * builder.usingNonEmpty(_.releaseYear)(year => s"releaseYear = $year")
   * }}}
   */
  transparent inline def usingNonEmpty[FIELD](
      inline field: SOURCE_TYPE => Option[FIELD]
  )(
      handler: FIELD => CaseCompleteBuilder.OptionPayload[TARGET_TYPE]
  ): CaseCompleteBuilder[SOURCE_TYPE, TARGET_TYPE, ?] =
    ${ CaseCompleteBuilder.usingNonEmptyImpl('this, 'field, 'handler) }

  /**
   * Marks a field as handled without registering a handler for it.
   *
   * @example
   * {{{
   * builder.ignoring(_.deprecatedField)
   * }}}
   */
  transparent inline def ignoring[FIELD](
      inline field: SOURCE_TYPE => FIELD
  ): CaseCompleteBuilder[SOURCE_TYPE, TARGET_TYPE, ?] = // The '?' hides the complex result type from the user
    ${ CaseCompleteBuilder.ignoringImpl('this, 'field) }

  /**
   * Produces the final [[CaseComplete]], failing compilation with the list of unhandled fields if
   * any field of SOURCE_TYPE has neither a handler nor an `ignoring` mark.
   */
  inline def compile: CaseComplete[SOURCE_TYPE, TARGET_TYPE] =
    ${ CaseCompleteBuilder.compileImpl[SOURCE_TYPE, TARGET_TYPE, Handled]('this) }
}

object CaseCompleteBuilder {

  def apply[SOURCE_TYPE <: Product, TARGET_TYPE]: CaseCompleteBuilder[SOURCE_TYPE, TARGET_TYPE, EmptyTuple] =
    new CaseCompleteBuilder(Map.empty[String, SOURCE_TYPE => TARGET_TYPE])

  /**
   * The `Any` fallback keeps this reducible for non-`Option` targets, so `usingNonEmptyImpl` gets to
   * report the mismatch instead of the compiler's raw "match type reduction failed".
   */
  type OptionPayload[T] = T match {
    case Option[payload] => payload
    case _               => Any
  }

  def usingImpl[
      SOURCE_TYPE <: Product: Type,
      TARGET_TYPE: Type,
      Handled <: Tuple: Type,
      FIELD: Type
  ](
      builder: Expr[CaseCompleteBuilder[SOURCE_TYPE, TARGET_TYPE, Handled]],
      field: Expr[SOURCE_TYPE => FIELD],
      handler: Expr[FIELD => TARGET_TYPE]
  )(using Quotes): Expr[CaseCompleteBuilder[SOURCE_TYPE, TARGET_TYPE, ?]] =
    registerField(builder, field, Some('{ (s: SOURCE_TYPE) => $handler($field(s)) }))

  def usingNonEmptyImpl[
      SOURCE_TYPE <: Product: Type,
      TARGET_TYPE: Type,
      Handled <: Tuple: Type,
      FIELD: Type
  ](
      builder: Expr[CaseCompleteBuilder[SOURCE_TYPE, TARGET_TYPE, Handled]],
      field: Expr[SOURCE_TYPE => Option[FIELD]],
      handler: Expr[FIELD => OptionPayload[TARGET_TYPE]]
  )(using q: Quotes): Expr[CaseCompleteBuilder[SOURCE_TYPE, TARGET_TYPE, ?]] = {
    import q.reflect.*

    Type.of[TARGET_TYPE] match {
      // The pattern alone also admits strict subtypes like `Some[String]`, for which the asExprOf
      // below would crash the expansion; the =:= guard sends them to the readable error instead.
      case '[Option[payload]] if TypeRepr.of[TARGET_TYPE] =:= TypeRepr.of[Option[payload]] =>
        // Inside this case OptionPayload[TARGET_TYPE] is known to reduce to `payload`, but the
        // quote below cannot see that -- hence the two casts.
        val fullHandler =
          '{ (s: SOURCE_TYPE) => $field(s).map(${ handler.asExprOf[FIELD => payload] }) }
            .asExprOf[SOURCE_TYPE => TARGET_TYPE]

        registerField(builder, field, Some(fullHandler))
      case _ =>
        report.errorAndAbort(
          s"usingNonEmpty requires the target type to be an Option, but it is ${TypeRepr.of[TARGET_TYPE].show(using Printer.TypeReprShortCode)}. Use `using` instead."
        )
    }
  }

  def ignoringImpl[
      SOURCE_TYPE <: Product: Type,
      TARGET_TYPE: Type,
      Handled <: Tuple: Type
  ](
      builder: Expr[CaseCompleteBuilder[SOURCE_TYPE, TARGET_TYPE, Handled]],
      field: Expr[SOURCE_TYPE => ?]
  )(using Quotes): Expr[CaseCompleteBuilder[SOURCE_TYPE, TARGET_TYPE, ?]] =
    registerField(builder, field, None)

  // Owns the shared pipeline -- selector extraction, duplicate check, emit under the extended
  // `Handled` type -- so a new validation or a change to the type encoding lands in one place.
  private def registerField[
      SOURCE_TYPE <: Product: Type,
      TARGET_TYPE: Type,
      Handled <: Tuple: Type
  ](
      builder: Expr[CaseCompleteBuilder[SOURCE_TYPE, TARGET_TYPE, Handled]],
      field: Expr[SOURCE_TYPE => ?],
      handler: Option[Expr[SOURCE_TYPE => TARGET_TYPE]]
  )(using q: Quotes): Expr[CaseCompleteBuilder[SOURCE_TYPE, TARGET_TYPE, ?]] = {
    import q.reflect.*

    val fieldName = extractFieldNameOrAbort(field)
    if getHandledFields[Handled].contains(fieldName) then {
      report.errorAndAbort(s"Field '$fieldName' has already been handled. Each field can only be handled once.")
    }

    ConstantType(StringConstant(fieldName)).asType match {
      case '[name] =>
        handler match {
          case Some(h) => '{ $builder.addHandler[name *: Handled](${ Expr(fieldName) }, $h) }
          case None    => '{ $builder.markHandled[name *: Handled] }
        }
    }
  }

  private def extractFieldNameOrAbort(field: Expr[?])(using q: Quotes): String = {
    import q.reflect.*

    // The receiver must be the lambda's own parameter: accepting any Select would let `_.a.b`
    // register the *source type's* field "b" and silently defeat the completeness check.
    val fieldName = field.asTerm.underlyingArgument match {
      case Lambda(List(param), body) =>
        body.underlyingArgument match {
          case Select(receiver: Ident, name) if receiver.symbol == param.symbol => Some(name)
          case _                                                                => None
        }
      case _ => None
    }

    fieldName.getOrElse(
      report.errorAndAbort(s"Illegal expression: ${field.asTerm.show}, expected a field selector, e.g. `_.foo`")
    )
  }

  def compileImpl[
      SOURCE_TYPE <: Product: Type,
      TARGET_TYPE: Type,
      Handled <: Tuple: Type
  ](
      builder: Expr[CaseCompleteBuilder[SOURCE_TYPE, TARGET_TYPE, Handled]]
  )(using q: Quotes): Expr[CaseComplete[SOURCE_TYPE, TARGET_TYPE]] = {
    import q.reflect.*

    val handledFields   = getHandledFields[Handled]
    val caseClassFields = TypeRepr.of[SOURCE_TYPE].typeSymbol.caseFields.map(_.name).toSet

    val missingFields = caseClassFields -- handledFields

    if missingFields.nonEmpty then report.errorAndAbort(s"""
        |CaseComplete compilation failed: Missing handlers for ${missingFields.size} field(s) in class ${Type.show[SOURCE_TYPE]}.
        |
        |Missing handlers for fields: ${missingFields.mkString(", ")}
        |
        |To fix this, add handlers for the missing fields.
        |Example:
        |  CaseCompleteBuilder[${Type.show[SOURCE_TYPE]}, ?]
        |    .using(_.${missingFields.head})(value => /* your handler logic */)
        |    // ... other handlers
        |    .compile""")

    '{ new CaseCompleteImpl($builder.handlers) }
  }

  // Decoded structurally rather than with quoted type patterns ('[head *: tail]): every chain step
  // walks the whole accumulated tuple, and the type comparer those patterns invoke made this ~10% of
  // typer time at 96 fields.
  private def getHandledFields[Handled <: Tuple: Type](using q: Quotes): Set[String] = {
    import q.reflect.*

    val consSymbol       = TypeRepr.of[Any *: Tuple].typeSymbol
    val emptyTupleSymbol = TypeRepr.of[EmptyTuple].dealias.typeSymbol

    def loop(repr: TypeRepr, acc: Set[String]): Set[String] = repr.dealias match {
      case AppliedType(tycon, List(ConstantType(StringConstant(name)), tail)) if tycon.typeSymbol == consSymbol =>
        loop(tail, acc + name)
      case empty if empty.typeSymbol == emptyTupleSymbol => acc
      case other => report.errorAndAbort(s"Internal error: HandledFields type was not a tuple: ${other.show}")
    }

    loop(TypeRepr.of[Handled], Set.empty)
  }
}
