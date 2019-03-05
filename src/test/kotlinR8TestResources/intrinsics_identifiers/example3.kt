// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package intrinsics_identifiers

import java.net.URI

fun main(args: Array<String>) {
  // By specifying non-null type of variables for library uses,
  // kotlin.jvm.internal.Intrinsics#check*Null(...) is added by kotlinc.
  val uri : URI = URI.create("google.com")
  val host : String = uri.host
  println(host)
}