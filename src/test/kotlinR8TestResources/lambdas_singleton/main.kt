// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package lambdas_singleton

private var COUNT = 0

fun nextInt() = COUNT++
fun next() = "${nextInt()}".padStart(3, '0')

fun main(args: Array<String>) {
    test()
}

private fun test() {
    test2(listOf(next(), next(), next(), next(), next(), next(), next(), next(), next(), next()))
}

private fun Collection<String>.flatten() =
        this.joinToString(prefix = "(*", postfix = "*)", separator = "*")

private fun Array<String>.flatten() =
        this.joinToString(prefix = "(*", postfix = "*)", separator = "*")

private fun test2(args: Collection<String>) {
    println(args.sortedByDescending { it.length }.flatten())
    println(args.sortedByDescending { -it.length }.flatten())
    process(::println)
    process(::println)
    val lambda: (Array<String>) -> Unit = {}
}

private inline fun process(crossinline f: (String) -> Unit) {
    feed2 { f(it.flatten()) }
    feed3 { f(it.flatten()) }
}

private fun feed3(f: (Array<String>) -> Unit) {
    f(arrayOf(next(), next(), next()))
}

private fun feed2(f: (Array<String>) -> Unit) {
    f(arrayOf(next(), next()))
}

