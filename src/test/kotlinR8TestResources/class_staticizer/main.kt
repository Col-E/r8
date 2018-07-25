// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package class_staticizer

private var COUNT = 0

fun next() = "${COUNT++}".padStart(3, '0')

fun main(args: Array<String>) {
    println(Regular.foo)
    println(Regular.bar)
    println(Regular.blah(next()))
    println(Derived.foo)
    println(Derived.bar)
    println(Derived.blah(next()))
    println(Util.foo)
    println(Util.bar)
    println(Util.blah(next()))
}

open class Regular {
    companion object {
        var foo: String = "Regular::CC::foo[${next()}]"
        var bar: String = blah(next())
        fun blah(p: String) = "Regular::CC::blah($p)[${next()}]"
    }
}

open class Derived : Regular() {
    companion object {
        var foo: String = "Derived::CC::foo[${next()}]"
        var bar: String = blah(next())
        fun blah(p: String) = "Derived::CC::blah($p)[${next()}]"
    }
}

object Util {
    var foo: String = "Util::foo[${next()}]"
    var bar: String = Regular.blah(next()) + Derived.blah(next())
    fun blah(p: String) = "Util::blah($p)[${next()}]"
}
