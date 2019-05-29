// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package unused_arg_in_lambdas_jstyle

private var COUNT = 0

fun nextInt() = COUNT++
fun next() = "${nextInt()}".padStart(3, '0')

fun main(args: Array<String>) {
    multiFunctionLambdaFactory(next(), next(), next())
}

private data class Local<out T>(val id: T)

private fun <P1, P2, P3> multiFunctionLambdaFactory(a: P1, b: P2, c:P3) {
    Lambdas.acceptMultiFunction({ x, _, z -> "$x:unused:$z" }, a, b, c)
    Lambdas.acceptMultiFunction({ x, _, z -> "$x:unused:$z" }, c, a, b)
    Lambdas.acceptMultiFunction({ x, _, z -> "$x:unused:$z" }, b, c, a)

    Lambdas.acceptMultiFunction({ x, _, z -> "$x:unused:$z" }, Local(a), b, c)
    Lambdas.acceptMultiFunction({ x, _, z -> "$x:unused:$z" }, Local(b), a, c)
    Lambdas.acceptMultiFunction({ x, _, z -> "$x:unused:$z" }, Local(c), b, a)
}
