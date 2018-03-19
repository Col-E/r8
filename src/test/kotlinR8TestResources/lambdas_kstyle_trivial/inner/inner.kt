// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package lambdas_kstyle_trivial.inner

import lambdas_kstyle_trivial.consumeEmpty
import lambdas_kstyle_trivial.consumeOne
import lambdas_kstyle_trivial.consumeTwo

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

