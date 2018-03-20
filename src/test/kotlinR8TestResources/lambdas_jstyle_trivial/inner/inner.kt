// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package lambdas_jstyle_trivial.inner

import lambdas_jstyle_trivial.Lambdas
import lambdas_jstyle_trivial.next
import lambdas_jstyle_trivial.nextInt

fun testInner() {
    testInner1(nextInt(), nextInt(), nextInt(), nextInt())
}

private data class InnerLocal<out T>(val id: T)

private fun testInner1(c0: Int, c1: Int, c2: Int, c3: Int) {
    Lambdas.acceptIntConsumer({ println("{${next()}:$it}") }, 100)
    Lambdas.acceptStringConsumer({ println("${next()}:{$it}:{$c0}") }, next())
    Lambdas.acceptGenericConsumer({ println("${next()}:{$it}:{$c0}:{$c1}") }, next())
    Lambdas.acceptGenericConsumer(
            { println("${next()}:{$it}:{$c0}:{$c1}:{$c2}") }, InnerLocal(next()))
    Lambdas.acceptGenericConsumer(
            { println("${next()}:{$it}:{$c0}:{$c1}:{$c2}:{$c3") }, InnerLocal(InnerLocal(next())))
}

