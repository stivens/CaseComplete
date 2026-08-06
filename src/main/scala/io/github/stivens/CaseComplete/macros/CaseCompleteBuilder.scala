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
   * Equivalent to `using(_.field)(_.map(handler))`, and only available when the target type is an
   * `Option`.
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
  )(using Quotes): Expr[CaseCompleteBuilder[SOURCE_TYPE, TARGET_TYPE, ?]] = {
    val fieldName = extractFieldNameOrAbort(field)
    checkNotAlreadyHandled[Handled](fieldName)

    addHandlerCall(builder, fieldName, '{ (s: SOURCE_TYPE) => $handler($field(s)) })
  }

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

    val fieldName = extractFieldNameOrAbort(field)
    checkNotAlreadyHandled[Handled](fieldName)

    Type.of[TARGET_TYPE] match {
      // The pattern alone also admits strict subtypes like `Some[String]`, for which the asExprOf
      // below would crash the expansion; the =:= guard sends them to the readable error instead.
      case '[Option[payload]] if TypeRepr.of[TARGET_TYPE] =:= TypeRepr.of[Option[payload]] =>
        // Inside this case OptionPayload[TARGET_TYPE] is known to reduce to `payload`, but the
        // quote below cannot see that -- hence the two casts.
        val fullHandler =
          '{ (s: SOURCE_TYPE) => $field(s).map(${ handler.asExprOf[FIELD => payload] }) }
            .asExprOf[SOURCE_TYPE => TARGET_TYPE]

        addHandlerCall(builder, fieldName, fullHandler)
      case _ =>
        report.errorAndAbort(
          s"usingNonEmpty requires the target type to be an Option, but it is ${Type.show[TARGET_TYPE]}. Use `using` instead."
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
  )(using Quotes): Expr[CaseCompleteBuilder[SOURCE_TYPE, TARGET_TYPE, ?]] = {
    val fieldName = extractFieldNameOrAbort(field)
    checkNotAlreadyHandled[Handled](fieldName)

    newHandledType[Handled](fieldName) match {
      case '[t] => '{ $builder.markHandled[t & Tuple] }
    }
  }

  private def addHandlerCall[
      SOURCE_TYPE <: Product: Type,
      TARGET_TYPE: Type,
      Handled <: Tuple: Type
  ](
      builder: Expr[CaseCompleteBuilder[SOURCE_TYPE, TARGET_TYPE, Handled]],
      fieldName: String,
      handler: Expr[SOURCE_TYPE => TARGET_TYPE]
  )(using Quotes): Expr[CaseCompleteBuilder[SOURCE_TYPE, TARGET_TYPE, ?]] =
    newHandledType[Handled](fieldName) match {
      case '[t] => '{ $builder.addHandler[t & Tuple](${ Expr(fieldName) }, $handler) }
    }

  private def extractFieldNameOrAbort(field: Expr[?])(using q: Quotes): String = {
    import q.reflect.*

    def extractFieldName(term: Term): Option[String] = term match {
      case Select(_, name)      => Some(name)
      case Inlined(_, _, block) => extractFieldName(block)
      case Block(ls, _) =>
        ls match {
          case (defdef: DefDef) :: _ =>
            defdef match {
              case DefDef(_, _, _, Some(body)) => extractFieldName(body)
              case _                           => None
            }
          case _ => None
        }
      case _ => None
    }

    val fieldAsTerm = field.asTerm
    extractFieldName(fieldAsTerm) match {
      case Some(name) => name
      case None       => report.errorAndAbort(s"Illegal expression: ${fieldAsTerm.show}, expected a field selector, e.g. `_.foo`")
    }
  }

  private def checkNotAlreadyHandled[Handled <: Tuple: Type](fieldName: String)(using q: Quotes): Unit = {
    import q.reflect.*
    if getHandledFields(Type.of[Handled]).contains(fieldName) then {
      report.errorAndAbort(s"Field '$fieldName' has already been handled. Each field can only be handled once.")
    }
  }

  // Returns an unbounded `Type[?]` because a quoted type pattern cannot express `<: Tuple` before
  // Scala 3.4; call sites recover the bound with `t & Tuple`.
  private def newHandledType[Handled <: Tuple: Type](fieldName: String)(using q: Quotes): Type[?] = {
    import q.reflect.*
    ConstantType(StringConstant(fieldName)).asType match {
      case '[name] => Type.of[name *: Handled]
    }
  }

  def compileImpl[
      SOURCE_TYPE <: Product: Type,
      TARGET_TYPE: Type,
      Handled <: Tuple: Type
  ](
      builder: Expr[CaseCompleteBuilder[SOURCE_TYPE, TARGET_TYPE, Handled]]
  )(using q: Quotes): Expr[CaseComplete[SOURCE_TYPE, TARGET_TYPE]] = {
    import q.reflect.*

    val handledFields   = getHandledFields(Type.of[Handled])
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
  private def getHandledFields(t: Type[?])(using q: Quotes): Set[String] = {
    import q.reflect.*

    val consSymbol = TypeRepr.of[Any *: Tuple].typeSymbol

    def loop(repr: TypeRepr, acc: Set[String]): Set[String] = repr.dealias match {
      case AndType(left, _) => loop(left, acc) // the `t & Tuple` bound recovered at the call sites
      case AppliedType(tycon, List(ConstantType(StringConstant(name)), tail)) if tycon.typeSymbol == consSymbol =>
        loop(tail, acc + name)
      case empty if empty =:= TypeRepr.of[EmptyTuple] => acc
      case other => report.errorAndAbort(s"Internal error: HandledFields type was not a tuple: ${other.show}")
    }

    loop(TypeRepr.of(using t), Set.empty)
  }
}
