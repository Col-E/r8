// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.synthetic;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.GraphLense;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.ValueNumberGenerator;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.ir.conversion.SourceCode;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.InternalOptions;
import java.util.function.Consumer;

public final class SynthesizedCode extends Code {

  public interface SourceCodeProvider {
    SourceCode get(Position callerPosition);
  }

  private final SourceCodeProvider sourceCodeProvider;
  private final Consumer<UseRegistry> registryCallback;

  public SynthesizedCode(SourceCodeProvider sourceCodeProvider) {
    this(sourceCodeProvider, SynthesizedCode::registerReachableDefinitionsDefault);
  }

  public SynthesizedCode(SourceCodeProvider sourceCodeProvider, Consumer<UseRegistry> callback) {
    this.sourceCodeProvider = sourceCodeProvider;
    this.registryCallback = callback;
  }

  @Override
  public boolean isEmptyVoidMethod() {
    return false;
  }

  @Override
  public final IRCode buildIR(
      DexEncodedMethod encodedMethod,
      AppInfo appInfo,
      GraphLense graphLense,
      InternalOptions options,
      Origin origin) {
    assert getOwner() == encodedMethod;
    return new IRBuilder(encodedMethod, appInfo, sourceCodeProvider.get(null), options, origin)
        .build();
  }

  @Override
  public IRCode buildInliningIR(
      DexEncodedMethod encodedMethod,
      AppInfo appInfo,
      GraphLense graphLense,
      InternalOptions options,
      ValueNumberGenerator valueNumberGenerator,
      Position callerPosition,
      Origin origin) {
    assert getOwner() == encodedMethod;
    IRBuilder builder =
        new IRBuilder(
            encodedMethod,
            appInfo,
            sourceCodeProvider.get(callerPosition),
            options,
            origin,
            valueNumberGenerator);
    return builder.build();
  }

  @Override
  public final String toString() {
    return toString(null, null);
  }

  private static void registerReachableDefinitionsDefault(UseRegistry registry) {
    throw new Unreachable();
  }

  @Override
  public void registerCodeReferences(UseRegistry registry) {
    registryCallback.accept(registry);
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
    return "SynthesizedCode";
  }
}
