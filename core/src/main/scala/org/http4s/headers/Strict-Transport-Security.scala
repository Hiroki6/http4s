/*
 * Copyright 2013 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s
package headers

import cats.parse.{Parser, Parser1}
import org.http4s.parser.{AdditionalRules, Rfc2616BasicRules}
import org.http4s.util.Writer
import scala.concurrent.duration.FiniteDuration

/** Defined by http://tools.ietf.org/html/rfc6797
  */
object `Strict-Transport-Security`
    extends HeaderKey.Internal[`Strict-Transport-Security`]
    with HeaderKey.Singleton {
  private[headers] class StrictTransportSecurityImpl(
      maxAge: Long,
      includeSubDomains: Boolean,
      preload: Boolean)
      extends `Strict-Transport-Security`(maxAge, includeSubDomains, preload)

  def fromLong(
      maxAge: Long,
      includeSubDomains: Boolean = true,
      preload: Boolean = false): ParseResult[`Strict-Transport-Security`] =
    if (maxAge >= 0)
      ParseResult.success(new StrictTransportSecurityImpl(maxAge, includeSubDomains, preload))
    else
      ParseResult.fail(
        "Invalid maxAge value",
        s"Strict-Transport-Security param $maxAge must be more or equal to 0 seconds")

  def unsafeFromDuration(
      maxAge: FiniteDuration,
      includeSubDomains: Boolean = true,
      preload: Boolean = false): `Strict-Transport-Security` =
    fromLong(maxAge.toSeconds, includeSubDomains, preload).fold(throw _, identity)

  def unsafeFromLong(
      maxAge: Long,
      includeSubDomains: Boolean = true,
      preload: Boolean = false): `Strict-Transport-Security` =
    fromLong(maxAge, includeSubDomains, preload).fold(throw _, identity)

  def parse(s: String): ParseResult[`Strict-Transport-Security`] =
    ParseResult.fromParser(parser, "Invalid `Strict-Transport-Security` header")(s)

  private[http4s] val parser: Parser[`Strict-Transport-Security`] = {
    val maxAge: Parser1[`Strict-Transport-Security`] =
      (Parser.ignoreCase1("max-age=") *> AdditionalRules.NonNegativeLong).map { (age: Long) =>
        `Strict-Transport-Security`
          .unsafeFromLong(maxAge = age, includeSubDomains = false, preload = false)
      }

    sealed trait StsAttribute
    case object IncludeSubDomains extends StsAttribute
    case object Preload extends StsAttribute

    val stsAttributes: Parser[StsAttribute] = Parser
      .ignoreCase1("includeSubDomains")
      .as(IncludeSubDomains)
      .orElse(Parser.ignoreCase1("preload").as(Preload))

    (maxAge ~ Parser
      .rep(Parser.string1(";") *> Rfc2616BasicRules.optWs *> stsAttributes)).map {
      case (sts, attributes) =>
        attributes.foldLeft(sts) {
          case (sts, IncludeSubDomains) => sts.withIncludeSubDomains(true)
          case (sts, Preload) => sts.withPreload(true)
        }
    }
  }
}

sealed abstract case class `Strict-Transport-Security`(
    maxAge: Long,
    includeSubDomains: Boolean = true,
    preload: Boolean = false)
    extends Header.Parsed {
  override def key: `Strict-Transport-Security`.type = `Strict-Transport-Security`

  override def renderValue(writer: Writer): writer.type = {
    writer << "max-age=" << maxAge
    if (includeSubDomains) writer << "; includeSubDomains"
    if (preload) writer << "; preload"
    writer
  }

  def withIncludeSubDomains(includeSubDomains: Boolean): `Strict-Transport-Security` =
    new `Strict-Transport-Security`.StrictTransportSecurityImpl(
      this.maxAge,
      includeSubDomains,
      this.preload)

  def withPreload(preload: Boolean): `Strict-Transport-Security` =
    new `Strict-Transport-Security`.StrictTransportSecurityImpl(
      this.maxAge,
      this.includeSubDomains,
      preload)
}
