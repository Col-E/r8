// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.conversion.MethodConversionOptions.MutableMethodConversionOptions;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.RetracerForCodePrinting;
import javax.annotation.Nonnull;

public class InvalidCode extends Code {

  private static final InvalidCode INSTANCE = new InvalidCode();

  public static Code getInstance() {
    return INSTANCE;
  }

  @SuppressWarnings("ReferenceEquality")
  public static boolean isInvalidCode(Code code) {
    return code == INSTANCE;
  }

  private InvalidCode() {}

  @Override
  public IRCode buildIR(
      ProgramMethod method,
      AppView<?> appView,
      Origin origin,
      MutableMethodConversionOptions conversionOptions) {
    throw new Unreachable();
  }

  @Override
  public void registerCodeReferences(ProgramMethod method, UseRegistry registry) {
    throw new Unreachable();
  }

  @Override
  public void registerCodeReferencesForDesugaring(ClasspathMethod method, UseRegistry registry) {
    throw new Unreachable();
  }

  @Override
  public String toString() {
    return "<invalid-code>";
  }

  @Override
  public String toString(DexEncodedMethod method, RetracerForCodePrinting retracer) {
    StringBuilder builder = new StringBuilder();
    if (method != null) {
      builder.append(method.toSourceString()).append("\n");
    }
    return builder.append(this).toString();
  }

  @Override
  public int estimatedDexCodeSizeUpperBoundInBytes() {
    throw new Unreachable();
  }

  @Override
  public boolean isEmptyVoidMethod() {
    throw new Unreachable();
  }

  @Nonnull
  @Override
  public Code copySubtype() {
    return this;
  }

  @Override
  protected int computeHashCode() {
    return System.identityHashCode(this);
  }

  @Override
  protected boolean computeEquals(Object other) {
    return this == other;
  }
}
