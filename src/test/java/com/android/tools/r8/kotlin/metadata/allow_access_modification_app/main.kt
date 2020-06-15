// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.metadata.allow_access_modification_app

import com.android.tools.r8.kotlin.metadata.allow_access_modification_lib.LibReference
import com.android.tools.r8.kotlin.metadata.allow_access_modification_lib.extensionInternal
import com.android.tools.r8.kotlin.metadata.allow_access_modification_lib.extensionPrivate
import com.android.tools.r8.kotlin.metadata.allow_access_modification_lib.staticInternalReference
import com.android.tools.r8.kotlin.metadata.allow_access_modification_lib.staticPrivateReference

fun main() {
  val libReference = LibReference(1, 2, 3, 4)
  println(libReference.propPrimaryPrivate)
  println(libReference.propPrimaryInternal)
  println(libReference.propPrivate)
  libReference.propPrimaryPrivate = 5
  libReference.propPrimaryInternal = 6
  libReference.propPrivate = 7
  println(libReference.readOnlyPropPrimaryPrivate)
  println(libReference.readOnlyPropPrimaryInternal)
  println(libReference.readOnlyPropInternal)
  println(libReference.propPrimaryPrivate)
  println(libReference.propPrimaryInternal)
  println(libReference.propPrivate)

  libReference.funPrivate()
  libReference.funInternal()
  libReference.funProtected()

  libReference.extensionPrivate()
  libReference.extensionInternal()

  LibReference.companionPrivate()
  LibReference.companionInternal()

  staticPrivateReference()
  staticInternalReference()
}