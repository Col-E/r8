// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package class_inliner_lambda_k_style

private var COUNT = 0

fun next() = "${COUNT++}".padStart(3, '0')

fun main(args: Array<String>) {
    testKotlinSequencesStateless(produceSequence(10))
    testKotlinSequencesStateful(5, 2, produceSequence(10))
    testBigExtraMethod()
    testBigExtraMethodReturningLambda()
}

data class Record(val foo: String, val good: Boolean)

fun testKotlinSequencesStateless(strings: Sequence<String>) {
    useRecord()
    // Stateless k-style lambda
    strings.map { Record(it, false) }.forEach { println(it) }
}

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

fun testBigExtraMethod() {
    useRecord()
    bigUserWithNotNullChecksAndTwoCalls(next()) { next() }
    testBigExtraMethod2()
    testBigExtraMethod3()
}

fun testBigExtraMethod2() {
    bigUserWithNotNullChecksAndTwoCalls(next()) { next() }
}

fun testBigExtraMethod3() {
    bigUserWithNotNullChecksAndTwoCalls(next()) { next() }
}

fun bigUserWithNotNullChecksAndTwoCalls(id: String, lambda: () -> String): String {
    useRecord()
    println("[A] logging call#$id returning ${lambda()}")
    return "$id: ${lambda()}"
}

fun testBigExtraMethodReturningLambda() {
    useRecord()
    bigUserReturningLambda(next()) { next() } // Not used
    testBigExtraMethodReturningLambda2()
    testBigExtraMethodReturningLambda3()
}

fun testBigExtraMethodReturningLambda2() {
    bigUserReturningLambda(next()) { next() } // Not used
}

fun testBigExtraMethodReturningLambda3() {
    bigUserReturningLambda(next()) { next() } // Not used
}

fun bigUserReturningLambda(id: String, lambda: () -> String): () -> String {
    useRecord()
    println("[B] logging call#$id returning ${lambda()}")
    println("[C] logging call#$id returning ${lambda()}")
    return lambda
}

fun produceSequence(size: Int): Sequence<String> {
    var count = size
    return generateSequence { if (count-- > 0) next() else null }
}

// Need this to make sure testKotlinSequenceXXX is not processed
// concurrently with invoke() on lambdas.
@Synchronized fun useRecord() = useRecord2()
@Synchronized fun useRecord2() = Record("", true)
