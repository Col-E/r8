// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata.delegated_property_app

import com.android.tools.r8.kotlin.metadata.delegated_property_lib.Delegates
import com.android.tools.r8.kotlin.metadata.delegated_property_lib.ProvidedDelegates
import com.android.tools.r8.kotlin.metadata.delegated_property_lib.Resource
import com.android.tools.r8.kotlin.metadata.delegated_property_lib.User

fun main() {

  val delegates = Delegates()
  delegates.customDelegate = Resource("foo");
  println(delegates.customDelegate)
  println(delegates.customReadOnlyDelegate)
  println(delegates.lazyString)
  println(delegates.localDelegatedProperties { Resource("Hello World!") })

  val user = User(mapOf(
    "name" to "Jane Doe",
    "age"  to 42
  ))

  println(user.name)
  println(user.age)

  val providedDelegates = ProvidedDelegates()
  println(providedDelegates.image)
  println(providedDelegates.text)
}
