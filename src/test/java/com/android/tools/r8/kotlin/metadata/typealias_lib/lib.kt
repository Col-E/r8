// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata.typealias_lib

// KmTypeAlias {
//   name: Jude
//   underlyingType {
//     classifier: Class(name=kotlin/Long)
//   }
//   expandedType {
//     classifier: Class(name=kotlin/Long)
//   }
// },
typealias Jude = Long

interface Itf {
  fun foo() : Itf
  fun hey() : Jude
}

// KmTypeAlias {
//   name: API
//   underlyingType {
//     classifier: Class(name=com/android/tools/r8/kotlin/metadata/typealias_lib/Itf)
//   }
//   expandedType {
//     classifier: Class(name=com/android/tools/r8/kotlin/metadata/typealias_lib/Itf)
//   }
// },
typealias API = Itf

// KmTypeAlias {
//   name: myAliasedArray
//   typeParameters: T
//   underlyingType {
//     classifier: Class(name=kotlin/Array)
//     arguments: TypeParameter(id=0)
//   }
//   expandedType {
//     classifier: Class(name=kotlin/Array)
//     arguments: TypeParameter(id=0)
//   }
// },
typealias myAliasedArray<T> = Array<T>

// KmTypeAlias {
//   name: APIs
//   underlyingType {
//     classifier: TypeAlias(name=com/android/tools/r8/kotlin/metadata/typealias_lib/myAliasedArray)
//     arguments: TypeAlias(name=com/android/tools/r8/kotlin/metadata/typealias_lib/API)
//   }
//   expandedType {
//     classifier: Class(name=kotlin/Array)
//     arguments: Class(name=com/android/tools/r8/kotlin/metadata/typealias_lib/Itf)
//   }
// },
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
