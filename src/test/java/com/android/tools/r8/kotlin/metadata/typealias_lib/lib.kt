// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata.typealias_lib

// TypeAlias {
//   name: "Jude"
//   underlyingType {
//     classifier: "kotlin/Long"
//   }
// }
typealias Jude = Long

interface Itf {
  fun foo() : Itf
  fun hey() : Jude
}

// TypeAlias {
//   name: "API"
//   underlyingType {
//     classifier: ".../Itf"
//   }
// }
typealias API = Itf
// TypeAlias {
//   typeParameters { KmTypeParameter { name = "T" ... } }
//   name: "myAliasedList"
//   underlyingType {
//     classifier: "kotlin/Array"
//   }
//   expandedType == underlyingType
// }
typealias myAliasedArray<T> = Array<T>
// TypeAlias {
//   underlyingType {
//     classifier: ".../myAliasedArray"
//     arguments {
//       KmTypeProjection { ... type = ".../API" }
//     }
//   }
//   expandedType {
//     classifier: "kotlin/Array"
//     arguments {
//       KmTypeProjection { ... type = ".../Itf" }
//     }
//   }
// }
typealias APIs = myAliasedArray<API>

open class Impl : API {
  override fun foo() : API {
    println("Impl::foo")
    return this
  }

  override fun hey(): Jude {
    return 42L
  }
}

fun seq(vararg itfs : Itf) : APIs {
  return arrayOf(*itfs)
}
