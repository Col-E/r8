// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package intrinsics_identifiers

fun main(args: Array<String>) {
  val instance = ToBeRenamedClass()
  println(instance.toBeRenamedField)
  println(instance.toBeRenamedMethod("arg1"))

  if (instance.toBeRenamedField.equals("arg2")) {
    instance.updateField("arg3")
    println(instance.toBeRenamedField)
    println(instance.toBeRenamedMethod("arg4"))
  }
}

