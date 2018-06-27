// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package class_inliner_lambda_k_style

private var COUNT = 0

fun next() = "${COUNT++}".padStart(3, '0')

fun main(args: Array<String>) {
    testKotlinSequencesStateless(produceSequence(10))
    testKotlinSequencesStateful(5, 2, produceSequence(10))
}

data class Record(val foo: String, val good: Boolean)

@Synchronized
fun testKotlinSequencesStateless(strings: Sequence<String>) {
    useRecord()
    // Stateless k-style lambda
    strings.map { Record(it, false) }.forEach { println(it) }
}

@Synchronized
fun testKotlinSequencesStateful(a: Int, b: Int, strings: Sequence<String>) {
    useRecord()
    // Big stateful k-style lambda
    val capture = next()
    strings.map {
        val x = it.toInt()
        val y = a + x
        val z = capture.toInt() + b
        println("logging $x/$y/$z") // Intentional
        Record(it, y % z == 0)
    }.forEach {
        println(it)
    }
}

private fun produceSequence(size: Int): Sequence<String> {
    var count = size
    return generateSequence { if (count-- > 0) next() else null }
}

// Need this to make sure testKotlinSequenceXXX is not processed
// concurrently with invoke() on lambdas.
fun useRecord() = useRecord2()
fun useRecord2() = Record("", true)
