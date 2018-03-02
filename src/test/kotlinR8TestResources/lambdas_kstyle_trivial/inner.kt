// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package lambdas.kstyle.trivial.inner

import lambdas.kstyle.trivial.consumeEmpty
import lambdas.kstyle.trivial.consumeOne
import lambdas.kstyle.trivial.consumeTwo

fun testInner() {
    testInnerStateless()
}

private fun testInnerStateless() {
    println(consumeEmpty { "first empty" })
    println(consumeEmpty { "second empty" })

    println(consumeOne { _ -> "first single" })
    println(consumeOne { _ -> "second single" })
    println(consumeOne { _ -> "third single" })
    println(consumeOne { x -> x })

    println(consumeTwo { x, y -> x + "-" + y })
}

