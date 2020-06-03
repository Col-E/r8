// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata.delegated_property_app

import com.android.tools.r8.kotlin.metadata.delegated_property_lib.CustomDelegate
import com.android.tools.r8.kotlin.metadata.delegated_property_lib.Delegates
import com.android.tools.r8.kotlin.metadata.delegated_property_lib.Resource
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.jvm.isAccessible

fun main() {
  val delegates = Delegates()
  delegates.customDelegate = Resource("foo");
  println(delegates::customDelegate.getResource())
}

inline fun KMutableProperty0<*>.getResource(): Resource {
  isAccessible = true
  return (getDelegate() as CustomDelegate).resource
}