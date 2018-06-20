// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package class_inliner_lambda_j_style

private var COUNT = 0

fun nextInt() = COUNT++
fun next() = "${nextInt()}".padStart(3, '0')

fun main(args: Array<String>) {
    testStateless()
    testStateful()
    testStateful2()
    testStateful3()
}

@Synchronized
fun testStateless() {
    SamIface.Consumer.consume { "123" }
    SamIface.Consumer.consume { "ABC" }
    SamIface.Consumer.consume {
        var x = 0
        println("A: ${x++}")
        println("B: ${x++}")
        println("C: ${x++}")
        println("D: ${x++}")
        println("E: ${x++}")
        "outer + $x"
    }
}

@Synchronized
fun testStateful() {
    var someVariable = 0

    SamIface.Consumer.consume {
        println("A: someVariable = $someVariable")
        someVariable += 1
        "B: someVariable = $someVariable"
    }
    SamIface.Consumer.consume {
        SamIface.Consumer.consume {
            println("E: someVariable = $someVariable")
            someVariable += 1
            "F: someVariable = $someVariable"
        }
        for (i in 1..20) {
            someVariable += 1
            if (i % 4 == 0) {
                println("G: someVariable = $someVariable")
            }
        }
        someVariable += 1
        "H: someVariable = $someVariable"
    }
    SamIface.Consumer.consumeBig {
        println("I: someVariable = $someVariable")
        someVariable += 1
        "J: someVariable = $someVariable"
    }
}

@Synchronized
fun testStateful2() {
    var someVariable = 0
    SamIface.Consumer.consumeBig {
        println("[Z] A: someVariable = $someVariable")
        someVariable += 1
        println("[Z] B: someVariable = $someVariable")
        someVariable += 1
        println("[Z] C: someVariable = $someVariable")
        someVariable += 1
        println("[Z] D: someVariable = $someVariable")
        someVariable += 1
        println("[Z] E: someVariable = $someVariable")
        someVariable += 1
        "[Z] F: someVariable = $someVariable"
    }
}

@Synchronized
fun testStateful3() {
    var someVariable = 0
    SamIface.Consumer.consumeBig {
        println("[W] A: someVariable = $someVariable")
        someVariable += 1
        println("[W] B: someVariable = $someVariable")
        someVariable += 1
        println("[W] C: someVariable = $someVariable")
        someVariable += 1
        println("[W] D: someVariable = $someVariable")
        someVariable += 1
        println("[W] E: someVariable = $someVariable")
        someVariable += 1
        "[W] F: someVariable = $someVariable"
    }
}
