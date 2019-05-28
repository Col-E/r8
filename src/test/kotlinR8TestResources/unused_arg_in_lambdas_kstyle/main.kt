// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package unused_arg_in_lambdas_kstyle

import kotlin.jvm.internal.TypeIntrinsics

private var COUNT = 11

private fun next() = "${COUNT++}"

fun consumeTwo(l: ((x: Any?, unused: Any?) -> Any)) : Any {
  // This can be implicitly added by kotlinc
  TypeIntrinsics.beforeCheckcastToFunctionOfArity(l, 2)
  return l(next(), next())
}

private fun lambdaFactory() {
  println(consumeTwo { x, _ -> x.toString() + "-" })
  println(consumeTwo { x, _ -> x.toString() + "*" })
  println(consumeTwo { x, _ -> x.toString() + "+" })
}

fun main(args: Array<String>) {
  lambdaFactory()
}
