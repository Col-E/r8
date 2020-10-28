// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.synthetic;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ClasspathMethod;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.NumberGenerator;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.conversion.SourceCode;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.origin.Origin;
import java.util.function.Consumer;

public abstract class AbstractSynthesizedCode extends Code {

  public interface SourceCodeProvider {
    SourceCode get(Position callerPosition);
  }

  public abstract SourceCodeProvider getSourceCodeProvider();

  public abstract Consumer<UseRegistry> getRegistryCallback();

  @Override
  public boolean isEmptyVoidMethod() {
    return false;
  }

  @Override
  public final IRCode buildIR(ProgramMethod method, AppView<?> appView, Origin origin) {
    return IRBuilder.create(method, appView, getSourceCodeProvider().get(null), origin)
        .build(method);
  }

  @Override
  public IRCode buildInliningIR(
      ProgramMethod context,
      ProgramMethod method,
      AppView<?> appView,
      NumberGenerator valueNumberGenerator,
      Position callerPosition,
      Origin origin,
      MethodProcessor methodProcessor) {
    return IRBuilder.createForInlining(
            method,
            appView,
            getSourceCodeProvider().get(callerPosition),
            origin,
            methodProcessor,
            valueNumberGenerator)
        .build(context);
  }

  @Override
  public final String toString() {
    return toString(null, null);
  }

  @Override
  public void registerCodeReferences(ProgramMethod method, UseRegistry registry) {
    internalRegisterCodeReferences(method, registry);
  }

  @Override
  public void registerCodeReferencesForDesugaring(ClasspathMethod method, UseRegistry registry) {
    internalRegisterCodeReferences(method, registry);
  }

  private void internalRegisterCodeReferences(DexClassAndMethod method, UseRegistry registry) {
    getRegistryCallback().accept(registry);
  }

  @Override
  protected final int computeHashCode() {
    throw new Unreachable();
  }

  @Override
  protected final boolean computeEquals(Object other) {
    throw new Unreachable();
  }

  @Override
  public final String toString(DexEncodedMethod method, ClassNameMapper naming) {
    return this.getClass().getSimpleName();
  }

  @Override
  public int estimatedDexCodeSizeUpperBoundInBytes() {
    return Integer.MAX_VALUE;
  }
}
