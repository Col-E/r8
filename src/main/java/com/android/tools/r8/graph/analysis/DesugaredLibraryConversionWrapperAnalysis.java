// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph.analysis;

import com.android.tools.r8.ProgramResource.Kind;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ClassAccessFlags;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexClasspathClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.desugar.DesugaredLibraryAPIConverter;
import com.android.tools.r8.ir.desugar.DesugaredLibraryAPIConverter.Mode;
import com.android.tools.r8.origin.SynthesizedOrigin;
import com.android.tools.r8.utils.BooleanUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DesugaredLibraryConversionWrapperAnalysis extends EnqueuerAnalysis
    implements EnqueuerInvokeAnalysis {
  private final DesugaredLibraryAPIConverter converter;
  private boolean callbackGenerated = false;
  private boolean wrappersGenerated = false;

  public DesugaredLibraryConversionWrapperAnalysis(AppView<?> appView) {
    this.converter =
        new DesugaredLibraryAPIConverter(appView, Mode.GENERATE_CALLBACKS_AND_WRAPPERS);
  }

  @Override
  public void processNewlyLiveMethod(DexEncodedMethod method) {
    converter.registerCallbackIfRequired(method);
  }

  private void traceInvoke(DexMethod invokedMethod) {
    converter.registerWrappersForLibraryInvokeIfRequired(invokedMethod);
  }

  @Override
  public void traceInvokeStatic(DexMethod invokedMethod, ProgramMethod context) {
    this.traceInvoke(invokedMethod);
  }

  @Override
  public void traceInvokeDirect(DexMethod invokedMethod, ProgramMethod context) {
    this.traceInvoke(invokedMethod);
  }

  @Override
  public void traceInvokeInterface(DexMethod invokedMethod, ProgramMethod context) {
    this.traceInvoke(invokedMethod);
  }

  @Override
  public void traceInvokeSuper(DexMethod invokedMethod, ProgramMethod context) {
    this.traceInvoke(invokedMethod);
  }

  @Override
  public void traceInvokeVirtual(DexMethod invokedMethod, ProgramMethod context) {
    this.traceInvoke(invokedMethod);
  }

  public List<DexEncodedMethod> generateCallbackMethods() {
    assert !callbackGenerated;
    callbackGenerated = true;
    return converter.generateCallbackMethods();
  }

  public List<DexProgramClass> generateWrappers() {
    assert !wrappersGenerated;
    wrappersGenerated = true;
    return converter.generateWrappers();
  }

  // Generate a mock classpath class for all vivified types.
  // Types will be available at runtime in the desugared library dex file.
  public List<DexClasspathClass> generateWrappersSuperTypeMock(
      List<DexProgramClass> wrappers, AppView<?> appView) {
    List<DexClasspathClass> classpathClasses = new ArrayList<>();
    for (DexProgramClass wrapper : wrappers) {
      boolean mockIsInterface = wrapper.interfaces.size() == 1;
      DexType mockType = mockIsInterface ? wrapper.interfaces.values[0] : wrapper.superType;
      if (appView.definitionFor(mockType) == null) {
        assert DesugaredLibraryAPIConverter.isVivifiedType(mockType);
        classpathClasses.add(
            new DexClasspathClass(
                mockType,
                Kind.CF,
                new SynthesizedOrigin("Desugared library wrapper super class ", getClass()),
                ClassAccessFlags.fromDexAccessFlags(
                    Constants.ACC_SYNTHETIC
                        | Constants.ACC_PUBLIC
                        | (BooleanUtils.intValue(mockIsInterface) * Constants.ACC_INTERFACE)),
                appView.dexItemFactory().objectType,
                DexTypeList.empty(),
                appView.dexItemFactory().createString("vivified"),
                null,
                Collections.emptyList(),
                null,
                Collections.emptyList(),
                DexAnnotationSet.empty(),
                DexEncodedField.EMPTY_ARRAY,
                DexEncodedField.EMPTY_ARRAY,
                DexEncodedMethod.EMPTY_ARRAY,
                DexEncodedMethod.EMPTY_ARRAY,
                appView.dexItemFactory().getSkipNameValidationForTesting()));
      }
    }
    return classpathClasses;
  }
}
