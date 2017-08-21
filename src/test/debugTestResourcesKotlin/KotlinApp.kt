// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

class KotlinApp {
    companion object {
        @JvmStatic fun main(args: Array<String>) {
            println("Hello world!")
            val instance = KotlinApp()
            instance.processObject(instance, instance::printObject)
            instance.invokeInlinedFunctions()
        }
    }

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
}