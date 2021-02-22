// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

class KotlinApp {

    fun ifElse(cond: Boolean) {
        val a = 10
        if (cond) {
            val b = a * 2
            printInt(b)
        } else {
            val c = a / 2
            print(c)
        }
    }

    fun printInt(i: Int) {
        println(i)
    }

    companion object {
        @JvmStatic fun main(args: Array<String>) {
            val instance = KotlinApp()
            instance.ifElse(true)
            instance.ifElse(false)
        }
    }
}