// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package non_null

class Foo(val tag: String) {
  var bar: Bar? = null
  fun foo() {
    println("Foo::$tag")
  }
}

class Bar(var bar1: String?, var bar2: String?, var bar3: String?) {
  constructor() : this(null, null, null)

  fun bar() {
    println("Bar::$bar1::$bar2::$bar3")
  }
}

fun neverThrowNPE(a: Foo?) {
  if (a != null) {
    a!!.foo()
    a.bar?.bar1 = a.tag + "$1"
    a.bar?.bar2 = a.tag + "$2"
    a.bar?.bar3 = a.tag + "$3"
    a.bar?.bar()
  } else {
    println("-null-")
  }
}

fun main(args: Array<String>) {
  val foo = Foo("tag")
  neverThrowNPE(foo)
  foo.bar = Bar()
  neverThrowNPE(foo)
  neverThrowNPE(null)
}
