// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package lambdas.jstyle.trivial

import Lambdas
import lambdas.jstyle.trivial.inner.testInner

private var COUNT = 0

fun nextInt() = COUNT++
fun next() = "${nextInt()}".padStart(3, '0')

fun main(args: Array<String>) {
    test()
    testInner()
}

private fun test() {
    test1(nextInt(), nextInt(), nextInt(), nextInt())
    test2(next(), next(), next())
    test3a(next(), next(), next())
    test3a(next(), Local(next()), Local(Local(next())))
    test3b(next(), next(), next())
    test3b(next(), Local(next()), Local(next()))
}

private data class Local<out T>(val id: T)

private fun test1(c0: Int, c1: Int, c2: Int, c3: Int) {
    Lambdas.acceptIntConsumer({ println("{${next()}:$it}") }, nextInt())
    Lambdas.acceptIntConsumer({ println("{${next()}:$it}") }, nextInt())

    Lambdas.acceptStringConsumer({ println("${next()}:{$it}:{$c0}") }, next())
    Lambdas.acceptStringConsumer({ println("${next()}:{$it}:{$c0}") }, next())

    Lambdas.acceptGenericConsumer({ println("${next()}:{$it}:{$c0}:{$c1}") }, next())
    Lambdas.acceptGenericConsumer({ println("${next()}:{$it}:{$c0}:{$c1}") }, next())

    Lambdas.acceptGenericConsumer({ println("${next()}:{$it}:{$c0}:{$c1}:{$c2}") }, Local(next()))
    Lambdas.acceptGenericConsumer({ println("${next()}:{$it}:{$c0}:{$c1}:{$c2}") }, Local(next()))

    Lambdas.acceptGenericConsumer(
            { println("${next()}:{$it}:{$c0}:{$c1}:{$c2}:{$c3}") }, Local(Local(next())))
    Lambdas.acceptGenericConsumer(
            { println("${next()}:{$it}:{$c0}:{$c1}:{$c2}:{$c3}") }, Local(Local(next())))
}

private fun test2(c0: String, c1: String, c2: String) {
    println(Lambdas.acceptIntSupplier { nextInt() })
    println(Lambdas.acceptIntSupplier { nextInt() })

    println(Lambdas.acceptStringSupplier { "${next()}:$c0" })
    println(Lambdas.acceptStringSupplier { "${next()}:$c0" })

    println(Lambdas.acceptGenericSupplier { "${next()}:$c0" })
    println(Lambdas.acceptGenericSupplier { "${next()}:$c0" })

    println(Lambdas.acceptGenericSupplier { "${Local(next())}:$c0:$c1" })
    println(Lambdas.acceptGenericSupplier { "${Local(next())}:$c0:$c1" })

    println(Lambdas.acceptGenericSupplier { "${Local(Local(next()))}:$c0:$c1:$c2" })
    println(Lambdas.acceptGenericSupplier { "${Local(Local(next()))}:$c0:$c1:$c2" })
}

private fun <P1, P2, P3> test3a(a: P1, b: P2, c: P3) {
    Lambdas.acceptMultiFunction({ x, y, z -> "$x:$y:$z" }, a, b, c)
    Lambdas.acceptMultiFunction({ x, y, z -> "$x:$y:$z" }, c, a, b)
    Lambdas.acceptMultiFunction({ x, y, z -> "$x:$y:$z" }, b, c, a)
    Lambdas.acceptMultiFunction({ x, y, z -> "$x:$y:$z" }, Local(a), b, c)
    Lambdas.acceptMultiFunction({ x, y, z -> "$x:$y:$z" }, Local(b), a, c)
    Lambdas.acceptMultiFunction({ x, y, z -> "$x:$y:$z" }, a, Local(b), c)
    Lambdas.acceptMultiFunction({ x, y, z -> "$x:$y:$z" }, a, Local(c), b)
    Lambdas.acceptMultiFunction(
            { x, y, z -> "$x:$y:$z" }, Local(Local(a)), Local(Local(b)), Local(Local(c)))
    Lambdas.acceptMultiFunction(
            { x, y, z -> "$x:$y:$z" }, Local(Local(c)), Local(Local(a)), Local(Local(b)))
}

private fun <P> test3b(a: P, b: P, c: P) {
    Lambdas.acceptMultiFunction({ x, y, z -> "$x:$y:$z" }, a, b, c)
    Lambdas.acceptMultiFunction({ x, y, z -> "$x:$y:$z" }, c, a, b)
    Lambdas.acceptMultiFunction({ x, y, z -> "$x:$y:$z" }, b, c, a)
    Lambdas.acceptMultiFunction({ x, y, z -> "$x:$y:$z" }, Local(a), b, c)
    Lambdas.acceptMultiFunction({ x, y, z -> "$x:$y:$z" }, Local(b), a, c)
}

