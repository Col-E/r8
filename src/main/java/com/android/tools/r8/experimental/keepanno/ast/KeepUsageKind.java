// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.experimental.keepanno.ast;

public abstract class KeepUsageKind {

  /**
   * Symbolically referenced classes, method and fields.
   *
   * <p>A symbolic reference of class, method or field is a reference that may not make use of the
   * item for its "runtime" effect.
   *
   * <p>Symbolic class references could be class constants, checkcast, instanceof as well as the use
   * of the class type in type hierarchies and annotations. The use of a class reference does not
   * imply that the class is ever instantiated as an object instance.
   *
   * <p>For methods, a reference may be in use by the need to retain the method without it actually
   * ever being called. This may be the case for methods with overrides where removal of a method
   * could cause a semantic change to the program. Similar for fields.
   */
  public static KeepUsageKind symbolicReference() {
    return SymbolicReference.getInstance();
  }

  /**
   * Actual usages include instantiated classes, executed methods and accessed fields.
   *
   * <p>An actual use is stronger than "symbolic reference" and thus implies that the item is also
   * "referenced". Thus, the set of all used items is a subset of the referenced items.
   *
   * <p>An actual use of class means that the class has instantiated instances. For methods, it
   * means that the method is invoked and executed in the typical sense. For fields, it means that
   * the field is read from or written to.
   *
   * <p>Note, it is possible for a static method or field to be used but its holder class still only
   * being "referenced" as there are no instances of the class.
   */
  public static KeepUsageKind actualUse() {
    return ActualUse.getInstance();
  }

  private static class SymbolicReference extends KeepUsageKind {
    private static SymbolicReference INSTANCE = null;

    public static SymbolicReference getInstance() {
      if (INSTANCE == null) {
        INSTANCE = new SymbolicReference();
      }
      return INSTANCE;
    }

    private SymbolicReference() {}
  }

  private static class ActualUse extends KeepUsageKind {
    private static ActualUse INSTANCE = null;

    public static ActualUse getInstance() {
      if (INSTANCE == null) {
        INSTANCE = new ActualUse();
      }
      return INSTANCE;
    }

    private ActualUse() {}
  }

  private KeepUsageKind() {}
}
