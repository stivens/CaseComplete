package io.github.stivens.CaseComplete.macros

import io.github.stivens.CaseComplete.CaseComplete

import scala.quoted.*

private class CaseCompleteImpl[SOURCE_TYPE <: Product, TARGET_TYPE](
    handlers: Map[String, SOURCE_TYPE => TARGET_TYPE]
) extends CaseComplete[SOURCE_TYPE, TARGET_TYPE] {
  def eval(source: SOURCE_TYPE): List[TARGET_TYPE] =
    handlers.toList
      .sortBy { case (fieldName, _) => fieldName }
      .map { case (_, handler) => handler(source) }
}

// The builder class that tracks handled fields in the type parameter `Handled`
class CaseCompleteBuilder[SOURCE_TYPE <: Product, TARGET_TYPE, Handled <: Tuple](
    val handlers: Map[String, SOURCE_TYPE => TARGET_TYPE]
) {
  transparent inline def using[FIELD](
      inline field: SOURCE_TYPE => FIELD
  )(
      handler: FIELD => TARGET_TYPE
  ): CaseCompleteBuilder[SOURCE_TYPE, TARGET_TYPE, ?] = // The '?' hides the complex result type from the user
    ${ CaseCompleteBuilder.usingImpl('this, 'field, 'handler) }

  /**
   * Compiles the handler, verifying at compile time that all fields have been handled.
   */
  inline def compile: CaseComplete[SOURCE_TYPE, TARGET_TYPE] =
    ${ CaseCompleteBuilder.compileImpl[SOURCE_TYPE, TARGET_TYPE, Handled]('this) }
}

object CaseCompleteBuilder {

  extension [SOURCE_TYPE <: Product, TARGET_TYPE, Handled <: Tuple](
      builderToOptional: CaseCompleteBuilder[SOURCE_TYPE, Option[TARGET_TYPE], Handled]
  ) {
    transparent inline def usingNonEmpty[FIELD](
        inline field: SOURCE_TYPE => Option[FIELD]
    )(handler: FIELD => TARGET_TYPE): CaseCompleteBuilder[SOURCE_TYPE, Option[TARGET_TYPE], ?] =
      builderToOptional.using[Option[FIELD]](field)(_.map(handler))
  }

  def apply[SOURCE_TYPE <: Product, TARGET_TYPE]: CaseCompleteBuilder[SOURCE_TYPE, TARGET_TYPE, EmptyTuple] =
    new CaseCompleteBuilder(Map.empty[String, SOURCE_TYPE => TARGET_TYPE])

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

  def compileImpl[
      SOURCE_TYPE <: Product: Type,
      TARGET_TYPE: Type,
      Handled <: Tuple: Type
  ](
      builder: Expr[CaseCompleteBuilder[SOURCE_TYPE, TARGET_TYPE, Handled]]
  )(using q: Quotes): Expr[CaseComplete[SOURCE_TYPE, TARGET_TYPE]] = {
    import q.reflect.*

    // Recursively unpacks the tuple type to get a Set of handled field names.
    def getHandledFields(t: Type[?]): Set[String] = t match {
      case '[EmptyTuple] => Set.empty
      case '[(head *: tail)] =>
        val headStr = Type.valueOfConstant[head].get.asInstanceOf[String]
        getHandledFields(Type.of[tail]) + headStr
      case _ =>
        report.errorAndAbort(s"Internal error: HandledFields type was not a tuple: ${Type.show[Handled]}")
    }

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
}
