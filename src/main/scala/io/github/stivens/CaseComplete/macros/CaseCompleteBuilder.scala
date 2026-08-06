package io.github.stivens.casecomplete.macros

import io.github.stivens.casecomplete.*

import scala.quoted.*

/**
   * Builder class for creating CaseComplete instances with compile-time field completeness checking.
   * 
   * The builder tracks which fields have been handled through the type parameter `Handled`, which is
   * a tuple of field names. This enables compile-time verification that all case class fields have
   * corresponding handlers.
   * 
   * Usage examples:
   * {{{
   * case class MovieFilter(
   *   title_like: Option[String] = None,
   *   director_eq: Option[String] = None,
   *   releaseYear: Option[Year] = None,
   *   rating_gte: Option[Double] = None
   * )
   * 
   * val movieFilterHandler = CaseCompleteBuilder[MovieFilter, Option[String]]
   *   .usingNonEmpty(_.title_like)(title => s"title ILIKE $title")
   *   .usingNonEmpty(_.director_eq)(director => s"director = $director")
   *   .usingNonEmpty(_.releaseYear)(year => s"releaseYear = $year")
   *   .usingNonEmpty(_.rating_gte)(rating => s"rating >= $rating")
   *   .compile
   * 
   * val filter = MovieFilter(releaseYear = Some(Year.of(1999)), rating_gte = Some(7.0))
   * val result = movieFilterHandler.eval(filter).toSet.flatten
   * // Returns: Set("releaseYear = 1999", "rating >= 7.0")
   * }}}
   * 
   * @tparam SOURCE_TYPE The source case class type that must be a Product
   * @tparam TARGET_TYPE The target type that each field handler produces
   * @tparam Handled A tuple type representing the field names that have been handled so far
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
   * Registers a handler for a specific field of the source case class.
   * 
   * This method extracts the field name at compile time and adds it to the `Handled` type parameter
   * to track which fields have been processed. The field selector must be a simple field access
   * expression like `_.fieldName`.
   * 
   * @param field A field selector function that extracts a field from the source type
   * @param handler A function that transforms the field value to the target type
   * @tparam FIELD The type of the field being handled
   * @return A new CaseCompleteBuilder with the updated handlers and type tracking
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
   * Explicitly ignores a specific field of the source case class.
   * 
   * This method marks a field as handled without creating a handler for it. This is useful
   * when you want to explicitly indicate that a field should be ignored during processing.
   * The field selector must be a simple field access expression like `_.fieldName`.
   * 
   * @param field A field selector function that extracts a field from the source type
   * @tparam FIELD The type of the field being ignored
   * @return A new CaseCompleteBuilder with the updated type tracking (no handler added)
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
   * Compiles the handler, verifying at compile time that all fields have been handled.
   * 
   * This method performs compile-time validation to ensure that every field in the source
   * case class has a corresponding handler. If any fields are missing, compilation will
   * fail with a detailed error message listing the unhandled fields.
   * 
   * @return A CaseComplete instance that can process source objects
   * @throws Compilation error if any case class fields are missing handlers
   * 
   * @example
   * {{{
   * val handler = CaseCompleteBuilder[MovieFilter, Option[String]]
   *   .usingNonEmpty(_.title_like)(title => s"title ILIKE $title")
   *   .usingNonEmpty(_.director_eq)(director => s"director = $director")
   *   .compile // Will fail if releaseYear or rating_gte fields are not handled
   * }}}
   */
  inline def compile: CaseComplete[SOURCE_TYPE, TARGET_TYPE] =
    ${ CaseCompleteBuilder.compileImpl[SOURCE_TYPE, TARGET_TYPE, Handled]('this) }
}

object CaseCompleteBuilder {

  /**
 * Creates a new CaseCompleteBuilder instance for the specified source and target types.
 * 
 * This is the main entry point for creating CaseCompleteBuilder instances. The returned
 * builder starts with no handlers and an empty tuple for the `Handled` type parameter.
 * 
 * @tparam SOURCE_TYPE The source case class type that must be a Product
 * @tparam TARGET_TYPE The target type that each field handler produces
 * @return A new CaseCompleteBuilder instance ready for field handler registration
 * 
 * @example
 * {{{
 * val builder = CaseCompleteBuilder[MovieFilter, Option[String]]
 * // builder is ready to accept field handlers via .using() calls
 * }}}
 */
  def apply[SOURCE_TYPE <: Product, TARGET_TYPE]: CaseCompleteBuilder[SOURCE_TYPE, TARGET_TYPE, EmptyTuple] =
    new CaseCompleteBuilder(Map.empty[String, SOURCE_TYPE => TARGET_TYPE])

  /**
   * The `Any` fallback keeps this reducible for non-`Option` targets so that `usingNonEmptyImpl`
   * reports the mismatch; without it the user gets a raw "match type reduction failed" instead.
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
      case '[Option[payload]] =>
        // OptionPayload[TARGET_TYPE] reduces to `payload` exactly here, but only after TARGET_TYPE
        // has been matched, which the compiler cannot see through in the quote below.
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

  // `builder` is the whole preceding chain, so it must be spliced exactly once -- a second splice
  // copies that tree.
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

  /**
   * Macro implementation for the `compile` method.
   * 
   * This macro performs compile-time validation to ensure all case class fields have
   * corresponding handlers. It compares the set of handled fields (from the `Handled`
   * type parameter) with the actual case class fields and reports any missing handlers.
   * 
   * @param builder The current builder expression
   * @tparam SOURCE_TYPE The source case class type
   * @tparam TARGET_TYPE The target type
   * @tparam Handled The handled fields tuple type
   * @return An expression for the final CaseComplete instance
   * @throws Compilation error if any case class fields are missing handlers
   */
  def compileImpl[
      SOURCE_TYPE <: Product: Type,
      TARGET_TYPE: Type,
      Handled <: Tuple: Type
  ](
      builder: Expr[CaseCompleteBuilder[SOURCE_TYPE, TARGET_TYPE, Handled]]
  )(using q: Quotes): Expr[CaseComplete[SOURCE_TYPE, TARGET_TYPE]] = {
    import q.reflect.*

    // Get the set of fields handled so far from the `Handled` type parameter.
    val handledFields = getHandledFields(Type.of[Handled])
    // Get the set of all fields defined on the case class `A`.
    val caseClassFields = TypeRepr.of[SOURCE_TYPE].typeSymbol.caseFields.map(_.name).toSet

    // Find the difference.
    val missingFields = caseClassFields -- handledFields

    // If there are any missing fields, abort compilation with an error.
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

    // If all checks pass, generate the code for the final HandleAllFieldsImpl instance.
    '{ new CaseCompleteImpl($builder.handlers) }
  }

  /**
   * Unpacks `Handled` -- a tuple of singleton string types -- into the set of field names it records.
   *
   * Decoded structurally rather than with quoted type patterns (`'[head *: tail]`): every chain step
   * walks the whole accumulated tuple, so this is quadratic over a chain, and the type comparer those
   * patterns invoke made it ~10% of typer time at 96 fields.
   */
  private def getHandledFields(t: Type[?])(using q: Quotes): Set[String] = {
    import q.reflect.*

    def loop(repr: TypeRepr, acc: Set[String]): Set[String] = repr.dealias match {
      case AndType(left, _) => loop(left, acc) // the `t & Tuple` bound recovered at the call sites
      case AppliedType(tycon, List(ConstantType(StringConstant(name)), tail)) if tycon.typeSymbol.name == "*:" =>
        loop(tail, acc + name)
      case empty if empty =:= TypeRepr.of[EmptyTuple] => acc
      case other => report.errorAndAbort(s"Internal error: HandledFields type was not a tuple: ${other.show}")
    }

    loop(TypeRepr.of(using t), Set.empty)
  }
}
