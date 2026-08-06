package io.github.stivens.casecomplete

import io.github.stivens.casecomplete.macros.CaseCompleteBuilder

sealed abstract class CaseComplete[SOURCE_TYPE <: Product, TARGET_TYPE] {
  def eval(source: SOURCE_TYPE): List[TARGET_TYPE]
}

object CaseComplete {
  def build[SOURCE_TYPE <: Product, TARGET_TYPE]: CaseCompleteBuilder[SOURCE_TYPE, TARGET_TYPE, EmptyTuple] =
    CaseCompleteBuilder.apply[SOURCE_TYPE, TARGET_TYPE]
}

private[casecomplete] class CaseCompleteImpl[SOURCE_TYPE <: Product, TARGET_TYPE](
    handlers: Map[String, SOURCE_TYPE => TARGET_TYPE]
) extends CaseComplete[SOURCE_TYPE, TARGET_TYPE] {
  private val sortedHandlers: List[SOURCE_TYPE => TARGET_TYPE] =
    handlers.toList
      .sortBy { case (fieldName, _) => fieldName }
      .map { case (_, handler) => handler }

  def eval(source: SOURCE_TYPE): List[TARGET_TYPE] =
    sortedHandlers.map(handler => handler(source))
}
