package io.github.stivens.CaseComplete

import io.github.stivens.CaseComplete.macros.CaseCompleteBuilder

trait CaseComplete[SOURCE_TYPE <: Product, TARGET_TYPE] {
  def eval(source: SOURCE_TYPE): List[TARGET_TYPE]
}

object CaseComplete {
  def build[SOURCE_TYPE <: Product, TARGET_TYPE]: CaseCompleteBuilder[SOURCE_TYPE, TARGET_TYPE, EmptyTuple] =
    CaseCompleteBuilder.apply[SOURCE_TYPE, TARGET_TYPE]
}
