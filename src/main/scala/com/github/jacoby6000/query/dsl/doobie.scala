package com.github.jacoby6000.query.dsl

import com.github.jacoby6000.query.ast.Query
import com.github.jacoby6000.query.interpreter
import shapeless.{HNil, HList}
import _root_.doobie.hi
import _root_.doobie.imports._
import _root_.doobie.syntax.string._

/**
  * Created by jacob.barber on 3/4/16.
  */
object doobie {

  class Builder[A: Composite](query: Query) {
    def prepare[B: Composite](params: B): scalaz.stream.Process[ConnectionIO, A] =
      HC.process[A](interpreter.interpretSql(query), HPS.set(params))

    def prepare: scalaz.stream.Process[ConnectionIO, A] =
      new SqlInterpolator(new StringContext(interpreter.interpretSql(query))).sql.apply().query[A].process
  }

  implicit class QueryExtensions(query: Query) {
    def apply[A: Composite] = new Builder[A](query)
  }

}