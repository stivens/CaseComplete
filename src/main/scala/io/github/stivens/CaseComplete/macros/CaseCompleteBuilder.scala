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
class CaseCompleteBuilder[SOURCE_TYPE <: Product, TARGET_TYPE, Handled <: Tuple](
    val handlers: Map[String, SOURCE_TYPE => TARGET_TYPE]
) {

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

/**
 * Companion object providing factory methods and extensions for CaseCompleteBuilder.
 * 
 * This object contains the main entry point for creating CaseCompleteBuilder instances
 * and provides extension methods for handling optional fields.
 */
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
   * Extension methods for CaseCompleteBuilder instances that handle optional target types.
   * 
   * These extensions provide convenient methods for working with optional fields and
   * optional target types.
   */
  extension [SOURCE_TYPE <: Product, TARGET_TYPE, Handled <: Tuple](
      builderToOptional: CaseCompleteBuilder[SOURCE_TYPE, Option[TARGET_TYPE], Handled]
  ) {

    /**
   * Registers a handler for an optional field, automatically handling the None case.
   * 
   * This method is useful when the source field is optional (Option[T]) and you want
   * to provide a handler that only processes the Some case, automatically returning
   * None for None values.
   * 
   * @param field A field selector that extracts an Option[FIELD] from the source type
   * @param handler A function that transforms the field value to the target type
   * @tparam FIELD The type of the field when it's present
   * @return A new CaseCompleteBuilder with the updated handlers
   * 
   * @example
   * {{{
   * handler.usingNonEmpty(_.releaseYear)(year => s"releaseYear = $year")
   * }}}
     */
    transparent inline def usingNonEmpty[FIELD](
        inline field: SOURCE_TYPE => Option[FIELD]
    )(handler: FIELD => TARGET_TYPE): CaseCompleteBuilder[SOURCE_TYPE, Option[TARGET_TYPE], ?] =
      builderToOptional.using[Option[FIELD]](field)(_.map(handler))
  }

  /**
   * Macro implementation for the `using` method.
   * 
   * This macro extracts the field name from the field selector expression at compile time
   * and constructs a new CaseCompleteBuilder with the updated handlers and type tracking.
   * 
   * @param builder The current builder expression
   * @param field The field selector expression
   * @param fieldHandler The handler function expression
   * @tparam SOURCE_TYPE The source case class type
   * @tparam TARGET_TYPE The target type
   * @tparam Handled The current handled fields tuple type
   * @tparam FIELD The field type
   * @return An expression for the new CaseCompleteBuilder
   */
  def usingImpl[
      SOURCE_TYPE <: Product: Type,
      TARGET_TYPE: Type,
      Handled <: Tuple: Type,
      FIELD: Type
  ](
      builder: Expr[CaseCompleteBuilder[SOURCE_TYPE, TARGET_TYPE, Handled]],
      field: Expr[SOURCE_TYPE => FIELD],
      fieldHandler: Expr[FIELD => TARGET_TYPE]
  )(using q: Quotes): Expr[CaseCompleteBuilder[SOURCE_TYPE, TARGET_TYPE, ?]] = {
    import q.reflect.*

    /**
     * Extracts the field name from a field selector term.
     * 
     * This function recursively traverses the term tree to find the actual field name
     * being selected, handling various AST transformations that might be applied.
     * 
     * @param term The term to extract the field name from
     * @return Some(fieldName) if successful, None otherwise
     */
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
    val fieldName = extractFieldName(fieldAsTerm) match {
      case Some(name) => name
      case None       => report.errorAndAbort(s"Illegal expression: ${fieldAsTerm.show}, expected a field selector, e.g. `_.foo`")
    }

    // Check if this field has already been handled
    val handledFields = getHandledFields(Type.of[Handled])
    if handledFields.contains(fieldName) then {
      report.errorAndAbort(s"Field '$fieldName' has already been handled. Each field can only be handled once.")
    }

    val fieldNameSingletonTypeRepr = ConstantType(StringConstant(fieldName))
    val handledTupleTypeRepr       = TypeRepr.of[Handled]
    val AppliedType(tycon, _)      = TypeRepr.of[*:[?, ?]]: @unchecked
    val newHandledTupleTypeRepr    = AppliedType(tycon, List(fieldNameSingletonTypeRepr, handledTupleTypeRepr))

    newHandledTupleTypeRepr.asType match {
      case '[t] =>
        // Get TypeTrees for the type arguments [A, B, t]
        val typeSource_TT  = TypeTree.of[SOURCE_TYPE]
        val typeTarget_TT  = TypeTree.of[TARGET_TYPE]
        val typeHandled_TT = TypeTree.of[t]

        // Get the symbol for the HandleAllFieldsBuilder type
        val builderSymbol = TypeRepr.of[CaseCompleteBuilder].typeSymbol
        // Get the constructor symbol
        val constructor = builderSymbol.primaryConstructor

        // Create the type `HandleAllFieldsBuilder[A, B, t]`
        val builderTypeTree = Applied(TypeIdent(builderSymbol), List(typeSource_TT, typeTarget_TT, typeHandled_TT))

        // Construct the expression for the `newHandlers` map argument
        val newHandlersExpr = '{ $builder.handlers + (${ Expr(fieldName) } -> ((s: SOURCE_TYPE) => $fieldHandler($field(s)))) }

        // Build the `new HandleAllFieldsBuilder[A, B, t](newHandlers)` expression tree
        val newBuilderTerm = Apply(
          TypeApply(Select(New(builderTypeTree), constructor), List(typeSource_TT, typeTarget_TT, typeHandled_TT)),
          List(newHandlersExpr.asTerm)
        )

        // Convert the constructed Term back to an Expr and coerce its type to match the method signature
        newBuilderTerm.asExprOf[CaseCompleteBuilder[SOURCE_TYPE, TARGET_TYPE, ?]]
      case _ =>
        report.errorAndAbort("Internal macro error: Could not create a valid tuple type for handled fields.")
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
    if missingFields.nonEmpty then report.errorAndAbort(s"Missing handlers for fields: ${missingFields.mkString(", ")}")

    // If all checks pass, generate the code for the final HandleAllFieldsImpl instance.
    '{ new CaseCompleteImpl($builder.handlers) }
  }

  /**
   * Recursively unpacks the tuple type to get a Set of handled field names.
   * 
   * This function traverses the `Handled` type parameter, which is a tuple of
   * singleton string types representing the field names that have been handled.
   * 
   * @param t The tuple type to unpack
   * @return A Set containing all the field names that have been handled
   */
  private def getHandledFields(t: Type[?])(using q: Quotes): Set[String] = {
    import q.reflect.*
    t match {
      case '[EmptyTuple] => Set.empty
      case '[(head *: tail)] =>
        val headStr = Type.valueOfConstant[head].get.asInstanceOf[String]
        getHandledFields(Type.of[tail]) + headStr
      case _ =>
        report.errorAndAbort(s"Internal error: HandledFields type was not a tuple.")
    }
  }
}
