// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package retrace

inline fun foo() {
  bar {
     throw Exception("foo")
  }
}

inline fun bar(f: () -> Unit) {
  baz { f() }
}

inline fun baz(f: () -> Unit) {
  f()
}

fun main(args: Array<String>) {
  foo()
}