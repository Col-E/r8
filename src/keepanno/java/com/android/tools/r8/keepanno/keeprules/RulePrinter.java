// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.keeprules;

import java.util.function.Supplier;

public class RulePrinter {

  public static RulePrinter withoutBackReferences(StringBuilder builder) {
    return new RulePrinter(builder);
  }

  public static BackReferencePrinter withBackReferences(
      StringBuilder builder, Supplier<Integer> nextReferenceGenerator) {
    return new BackReferencePrinter(builder, nextReferenceGenerator);
  }

  private final StringBuilder builder;

  private RulePrinter(StringBuilder builder) {
    this.builder = builder;
  }

  public RulePrinter append(String str) {
    assert !str.contains("*");
    assert !str.contains("(...)");
    return appendWithoutBackReferenceAssert(str);
  }

  public RulePrinter appendWithoutBackReferenceAssert(String str) {
    builder.append(str);
    return this;
  }

  public RulePrinter appendStar() {
    return appendWithoutBackReferenceAssert("*");
  }

  public RulePrinter appendDoubleStar() {
    return appendWithoutBackReferenceAssert("**");
  }

  public RulePrinter appendTripleStar() {
    return appendWithoutBackReferenceAssert("***");
  }

  public RulePrinter appendAnyParameters() {
    return appendWithoutBackReferenceAssert("(...)");
  }

  /** Printer with support for collecting the back-reference printing. */
  public static class BackReferencePrinter extends RulePrinter {

    final Supplier<Integer> nextNumberGenerator;
    final StringBuilder backref = new StringBuilder();

    private BackReferencePrinter(StringBuilder builder, Supplier<Integer> nextNumberGenerator) {
      super(builder);
      this.nextNumberGenerator = nextNumberGenerator;
    }

    public String getBackReference() {
      return backref.toString();
    }

    private RulePrinter addBackRef(String wildcard) {
      backref.append('<').append(nextNumberGenerator.get()).append('>');
      return super.appendWithoutBackReferenceAssert(wildcard);
    }

    @Override
    public RulePrinter appendWithoutBackReferenceAssert(String str) {
      backref.append(str);
      return super.appendWithoutBackReferenceAssert(str);
    }

    @Override
    public RulePrinter appendStar() {
      return addBackRef("*");
    }

    @Override
    public RulePrinter appendDoubleStar() {
      return addBackRef("**");
    }

    @Override
    public RulePrinter appendTripleStar() {
      return addBackRef("***");
    }

    @Override
    public RulePrinter appendAnyParameters() {
      // TODO(b/265892343): R8 does not yet support back reference to `...`.
      return appendWithoutBackReferenceAssert("(...)");
    }
  }
}
