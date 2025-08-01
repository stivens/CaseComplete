package io.github.stivens.casecomplete

import io.github.stivens.casecomplete.macros.CaseCompleteBuilder

sealed abstract class CaseComplete[SOURCE_TYPE <: Product, TARGET_TYPE] {
  def eval(source: SOURCE_TYPE): List[TARGET_TYPE]
}

object CaseComplete {
  def build[SOURCE_TYPE <: Product, TARGET_TYPE]: CaseCompleteBuilder[SOURCE_TYPE, TARGET_TYPE, EmptyTuple] =
    CaseCompleteBuilder.apply[SOURCE_TYPE, TARGET_TYPE]
}

/**
 * Implementation of CaseComplete that stores handlers in a Map and evaluates them in sorted order.
 * 
 * This class is used internally by the CaseCompleteBuilder to create the final CaseComplete instance
 * after all field handlers have been registered.
 */
private[casecomplete] class CaseCompleteImpl[SOURCE_TYPE <: Product, TARGET_TYPE](
    handlers: Map[String, SOURCE_TYPE => TARGET_TYPE]
) extends CaseComplete[SOURCE_TYPE, TARGET_TYPE] {
  def eval(source: SOURCE_TYPE): List[TARGET_TYPE] =
    handlers.toList
      .sortBy { case (fieldName, _) => fieldName }
      .map { case (_, handler) => handler(source) }
}
