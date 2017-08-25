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

    companion object {
        @JvmStatic fun main(args: Array<String>) {
            println("Hello world!")
            val instance = KotlinInline()
            instance.processObject(instance, instance::printObject)
            instance.invokeInlinedFunctions()
            instance.singleInline()
        }
    }
}