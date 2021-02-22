// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

class KotlinInline {

    fun processObject(obj: Any, func: (Any) -> Unit) {
        func(obj)
    }

    fun printObject(obj: Any) {
        println(obj)
    }

    fun invokeInlinedFunctions() {
        inlinedA {
            val inA = 1
            inlinedB {
                val inB = 2
                foo(inA, inB)
            }
        }
    }

    inline fun inlinedA(f: () -> Unit) {
        f()
    }

    inline fun inlinedB(f: () -> Unit) {
        f()
    }

    fun foo(a: Int, b: Int) {
        println("a=$a, b=$b")
    }

    fun emptyMethod(unused: Int) {
    }

    fun singleInline() {
        emptyMethod(0)
        inlined()
        emptyMethod(1)
    }

    inline fun inlined() {
        emptyMethod(-1)
    }

    // Double inlining
    fun testNestedInlining() {
        val l1 = Int.MAX_VALUE
        val l2 = Int.MIN_VALUE
        inlinee1(l1, l2)
    }
    inline fun inlinee1(a: Int, b: Int) {
        val c = a - 2
        inlinee2(1) {
            val left = a + b
            val right = a - b
            foo(left, right)
        }
        inlinee2(c) {
            foo(b, a)
        }
    }

    inline fun inlinee2(p: Int, block: () -> Unit) {
        if (p > 0) {
            block()
        }
    }

    companion object {
        @JvmStatic fun main(args: Array<String>) {
            println("Hello world!")
            val instance = KotlinInline()
            instance.processObject(instance, instance::printObject)
            instance.invokeInlinedFunctions()
            instance.singleInline()
            instance.testNestedInlining()
        }
    }
}