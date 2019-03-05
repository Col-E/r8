// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package intrinsics_identifiers

class AnotherClass {
  var anotherField : String = "PREFIX"
  fun anotherMethod(arg: String) : String {
    return anotherField + arg
  }
  fun updateField(arg: String) : Unit {
    anotherField = arg
  }
}

fun main(args: Array<String>) {
  val instance = AnotherClass()
  println(instance.anotherField)
  println(instance.anotherMethod("arg1"))

  if (instance.anotherField.equals("arg2")) {
    instance.updateField("arg3")
    println(instance.anotherField)
    println(instance.anotherMethod("arg4"))
  }
}

