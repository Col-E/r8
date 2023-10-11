// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.ClasspathMethod;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.conversion.MethodConversionOptions.MutableMethodConversionOptions;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.RetracerForCodePrinting;

public abstract class IncompleteHorizontalClassMergerCode extends Code {

  @Override
  public boolean isHorizontalClassMergerCode() {
    return true;
  }

  @Override
  public boolean isIncompleteHorizontalClassMergerCode() {
    return true;
  }

  public abstract CfCode toCfCode(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      ProgramMethod method,
      HorizontalClassMergerGraphLens lens);

  // Implement Code.

  @Override
  public IRCode buildIR(
      ProgramMethod method,
      AppView<?> appView,
      Origin origin,
      MutableMethodConversionOptions conversionOptions) {
    throw new Unreachable();
  }

  @Override
  protected boolean computeEquals(Object other) {
    throw new Unreachable();
  }

  @Override
  protected int computeHashCode() {
    throw new Unreachable();
  }

  @Override
  public int estimatedDexCodeSizeUpperBoundInBytes() {
    throw new Unreachable();
  }

  @Override
  public boolean isEmptyVoidMethod() {
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
  public abstract String toString();

  @Override
  public String toString(DexEncodedMethod method, RetracerForCodePrinting retracer) {
    return toString();
  }
}
