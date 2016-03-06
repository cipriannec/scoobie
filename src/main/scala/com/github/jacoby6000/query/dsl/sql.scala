package com.github.jacoby6000.query.dsl

import com.github.jacoby6000.query.ast._
import shapeless.HList

/**
  * Created by jacob.barber on 3/4/16.
  */
object sql {

  def insertInto(table: QueryPath)(columns: QueryPath*): InsertBuilder = InsertBuilder(table, columns.toList)

  case class InsertBuilder(table: QueryPath, columns: List[QueryPath]) {
    def values(values: QueryValue*): Insert = Insert(table, (columns zip values) map (kv => InsertField(kv._1, kv._2)))
  }

  case class SqlQueryFunctionBuilder(f: QueryPath) {
    def apply(params: QueryValue*) = QueryFunction(f, params.toList)
  }

  implicit class StringContextExtensions(val c: StringContext) extends AnyVal {
    def p(): QueryPath = {
      def go(remainingParts: List[String], queryPath: QueryPath): QueryPath = remainingParts match {
        case head :: tail => go(tail, QueryPathCons(head, queryPath))
        case Nil => queryPath
      }

      val parts = c.parts.mkString.split('.').toList.reverse
      go(parts.tail, QueryPathEnd(parts.head))
    }

    def expr(args: String*): QueryRawExpression[String] = {
      QueryRawExpression(c.standardInterpolator(identity,args))
    }

    def func(): SqlQueryFunctionBuilder = SqlQueryFunctionBuilder(p())
  }

  implicit def pathToProjection(f: QueryPath): QueryProjection = QueryProjectOne(f, None)

  implicit class QueryValueExtensions(val f: QueryValue) extends AnyVal {
    def as(alias: String) = QueryProjectOne(f, Some(alias))
  }

  implicit class QueryPathExtensions(val f: QueryPath) extends AnyVal  {
    def as(alias: String) = f match {
      case c: QueryPathCons => QueryProjectOne(c, Some(alias))
      case c: QueryPathEnd => QueryProjectOne(c, Some(alias))
    }

    def asc: QuerySortAsc = QuerySortAsc(f)
    def desc: QuerySortDesc = QuerySortDesc(f)
  }

  val `*` = QueryProjectAll
  val `?` = QueryParameter
  val `null` = QueryNull

  def select(projections: QueryProjection*): SelectBuilder = new SelectBuilder(projections.toList)

  case class SelectBuilder(projections: List[QueryProjection]) {
    def from(path: QueryProjection): QueryBuilder = QueryBuilder(Query(path, projections, List.empty, None, List.empty, List.empty))
  }

  case class QueryBuilder(query: Query) {
    def leftOuterJoin(table: QueryProjectOne): JoinBuilder = new JoinBuilder(query, QueryLeftOuterJoin(table, _))
    def rightOuterJoin(table: QueryProjectOne): JoinBuilder = new JoinBuilder(query, QueryRightOuterJoin(table, _))
    def innerJoin(table: QueryProjectOne): JoinBuilder = new JoinBuilder(query, QueryInnerJoin(table, _))
    def fullOuterJoin(table: QueryProjectOne): JoinBuilder = new JoinBuilder(query, QueryFullOuterJoin(table, _))
    def crossJoin(table: QueryProjectOne): JoinBuilder = new JoinBuilder(query, QueryCrossJoin(table, _))

    def where(comparison: QueryComparison): QueryBuilder = QueryBuilder(query.copy(filters = query.filters.map(_ and comparison) orElse Some(comparison)))
    def orderBy(sorts: QuerySort*): QueryBuilder = QueryBuilder(query.copy(sorts = query.sorts ::: sorts.toList))
    def groupBy(groups: QuerySort*): QueryBuilder = QueryBuilder(query.copy(groupings = query.groupings ::: groups.toList))
  }

  case class JoinBuilder(query: Query, building: QueryComparison => QueryUnion) {
    def on(comp: QueryComparison): QueryBuilder = QueryBuilder(query.copy(unions = query.unions ::: List(building(comp))))
  }

  implicit def queryValueFromableToQueryValue[A](a: A)(implicit arg0: QueryValueFrom[A]): QueryValue = arg0.toQueryValue(a)
  implicit def queryValueFromableToQueryComparison[A](a: A)(implicit arg0: QueryValueFrom[A]): QueryComparison = QueryLit(arg0.toQueryValue(a))

  implicit class QueryValueFromExtensions[A: QueryValueFrom](val a: A) {
    def >[B: QueryValueFrom](b: B) = QueryGreaterThan(a, b)
    def >=[B: QueryValueFrom](b: B) = QueryGreaterThanOrEqual(a, b)
    def <[B: QueryValueFrom](b: B) = QueryLessThan(a, b)
    def <=[B: QueryValueFrom](b: B) = QueryLessThanOrEqual(a, b)

    def ===[B: QueryValueFrom](b: B) = QueryEqual(a, b)
    def !==[B: QueryValueFrom](b: B) = QueryNotEqual(a, b)

    def ++[B: QueryValueFrom](b: B) = QueryAdd(a,b)
    def --[B: QueryValueFrom](b: B) = QuerySub(a,b)
    def /[B: QueryValueFrom](b: B) = QueryDiv(a,b)
    def **[B: QueryValueFrom](b: B) = QueryMul(a,b)
  }

  def !(queryComparison: QueryComparison): QueryNot = queryComparison.not

  implicit class QueryComparisonExtensions(val left: QueryComparison) extends AnyVal {
    def not: QueryNot = QueryNot(left)
    def and(right: QueryComparison) = QueryAnd(left, right)
    def or(right: QueryComparison) = QueryOr(left, right)
  }

  trait QueryValueFrom[A] {
    def toQueryValue(a: A): QueryValue
  }

  object QueryValueFrom {
    def apply[A](f: A => QueryValue) =
      new QueryValueFrom[A] {
        def toQueryValue(a: A): QueryValue = f(a)
      }
  }

  implicit val queryParam = QueryValueFrom[QueryParameter.type](identity)
  implicit val queryNull = QueryValueFrom[QueryNull.type](identity)
  implicit val queryBooleanValue = QueryValueFrom[Boolean](QueryBoolean)
  implicit val queryIntValue = QueryValueFrom[Int](QueryInt)
  implicit val queryDoubleValue = QueryValueFrom[Double](QueryDouble)
  implicit val queryPath = QueryValueFrom[QueryPath] {
    case cons: QueryPathCons => cons
    case end: QueryPathEnd => end
  }
  implicit val queryPathCons = QueryValueFrom[QueryPathCons](identity)
  implicit val queryPathEnd = QueryValueFrom[QueryPathEnd](identity)
  implicit val queryStringValue = QueryValueFrom[String](QueryString)
  implicit val queryFunction = QueryValueFrom[QueryFunction](identity)
}
