package io.github.stivens.casecomplete

import io.github.stivens.casecomplete.macros.CaseCompleteBuilder

sealed abstract class CaseComplete[SOURCE_TYPE <: Product, TARGET_TYPE] {

  /**
   * Applies every registered handler, ordered by the source type's field declaration order.
   * Handled fields that are not primary-constructor fields come last, in registration order.
   */
  def eval(source: SOURCE_TYPE): List[TARGET_TYPE]
}

object CaseComplete {
  def build[SOURCE_TYPE <: Product, TARGET_TYPE]: CaseCompleteBuilder[SOURCE_TYPE, TARGET_TYPE, EmptyTuple] =
    CaseCompleteBuilder.apply[SOURCE_TYPE, TARGET_TYPE]
}

private[casecomplete] class CaseCompleteImpl[SOURCE_TYPE <: Product, TARGET_TYPE](
    orderedHandlers: List[SOURCE_TYPE => TARGET_TYPE]
) extends CaseComplete[SOURCE_TYPE, TARGET_TYPE] {
  def eval(source: SOURCE_TYPE): List[TARGET_TYPE] =
    orderedHandlers.map(_(source))
}
