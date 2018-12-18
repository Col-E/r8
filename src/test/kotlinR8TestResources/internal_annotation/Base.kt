// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package internal_annotation

@Annotation(8, "Base::Annotation::field2", [], [])
internal abstract class Base {
  protected abstract fun foo(): Any?

  public override fun toString(): String =
      "${foo() ?: this::class.java.name}"
}

// If we don't adjust annotation values, lack of f(3|4) will trigger errors on legacy VMs.
@Annotation(2, "Impl::Annotation::field2", [3], ["field4"])
internal class Impl(val flag: Boolean) : Base() {
  override fun foo(): Any? {
    return when (flag) {
      true -> null
      false -> this
    }
  }

  public override fun toString(): String =
      if (flag)
        super.toString()
      else
        "Impl::toString"
}