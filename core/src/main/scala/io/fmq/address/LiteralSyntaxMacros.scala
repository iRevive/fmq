package io.fmq.address

import scala.reflect.macros.blackbox

/**
  * Taken from https://github.com/Comcast/ip4s/blob/b4f01a4637f2766a8e12668492a3814c478c6a03/shared/src/main/scala/com/comcast/ip4s/LiteralSyntaxMacros.scala
  */
@SuppressWarnings(Array("org.wartremover.warts.StringPlusAny"))
object LiteralSyntaxMacros {

  def tcpIncomplete(c: blackbox.Context)(args: c.Expr[Any]*): c.Expr[Uri.Incomplete.TCP] =
    singlePartInterpolator(c)(
      args,
      "Uri.Incomplete.TCP",
      s => Uri.Incomplete.tcpFromString("tcp" + s),
      s => c.universe.reify(Uri.Incomplete.tcpFromStringUnsafe("tcp" + s.splice))
    )

  def tcpComplete(c: blackbox.Context)(args: c.Expr[Any]*): c.Expr[Uri.Complete.TCP] =
    singlePartInterpolator(c)(
      args,
      "Uri.Complete.TCP",
      s => Uri.Complete.tcpFromString("tcp" + s),
      s => c.universe.reify(Uri.Complete.tcpFromStringUnsafe("tcp" + s.splice))
    )

  def inprocComplete(c: blackbox.Context)(args: c.Expr[Any]*): c.Expr[Uri.Complete.InProc] =
    singlePartInterpolator(c)(
      args,
      "Uri.Complete.InProc",
      s => Uri.Complete.inProcFromString("inproc" + s),
      s => c.universe.reify(Uri.Complete.inProcFromStringUnsafe("inproc" + s.splice))
    )

  private def singlePartInterpolator[A](c: blackbox.Context)(
      args: Seq[c.Expr[Any]],
      typeName: String,
      validate: String => Either[Throwable, A],
      construct: c.Expr[String] => c.Expr[A]
  ): c.Expr[A] = {
    import c.universe._
    val _ = args
    c.prefix.tree match {
      case Apply(_, List(Apply(_, (lcp @ Literal(Constant(p: String))) :: Nil))) =>
        validate(p).fold(
          e => c.abort(c.enclosingPosition, s"invalid $typeName: ${e.getMessage}"),
          _ => construct(c.Expr(lcp))
        )

      case _ =>
        c.abort(c.enclosingPosition, s"$typeName does not support interpolated string with arguments")
    }
  }

}
