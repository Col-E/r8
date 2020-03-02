// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph.analysis;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClasspathClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.desugar.DesugaredLibraryAPIConverter;
import com.android.tools.r8.ir.desugar.DesugaredLibraryAPIConverter.Mode;
import com.android.tools.r8.utils.OptionalBool;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class DesugaredLibraryConversionWrapperAnalysis extends EnqueuerAnalysis
    implements EnqueuerInvokeAnalysis {

  private final AppView<?> appView;
  private final DesugaredLibraryAPIConverter converter;
  private boolean callbackGenerated = false;
  private Map<DexProgramClass, DexProgramClass> wrappersToReverseMap = null;

  public DesugaredLibraryConversionWrapperAnalysis(AppView<?> appView) {
    this.appView = appView;
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

  public Set<DexProgramClass> generateWrappers() {
    assert wrappersToReverseMap == null;
    wrappersToReverseMap = converter.synthesizeWrappersAndMapToReverse();
    return wrappersToReverseMap.keySet();
  }

  // Generate a mock classpath class for all vivified types.
  // Types will be available at runtime in the desugared library dex file.
  public List<DexClasspathClass> generateWrappersSuperTypeMock() {
    List<DexClasspathClass> classpathClasses = new ArrayList<>();
    for (DexProgramClass wrapper : wrappersToReverseMap.keySet()) {
      boolean mockIsInterface = wrapper.interfaces.size() == 1;
      DexType mockType = mockIsInterface ? wrapper.interfaces.values[0] : wrapper.superType;
      if (appView.definitionFor(mockType) == null) {
        assert DesugaredLibraryAPIConverter.isVivifiedType(mockType);
        assert wrapper.instanceFields().size() == 1;
        DexType typeToMock = wrapper.instanceFields().get(0).field.type;
        DexClass classToMock = appView.definitionFor(typeToMock);
        assert classToMock != null;
        DexClasspathClass mockedSuperClass =
            converter.synthesizeClasspathMock(classToMock, mockType, mockIsInterface);
        classpathClasses.add(mockedSuperClass);
        for (DexEncodedMethod virtualMethod : wrapper.virtualMethods()) {
          // The mock is generated at the end of the enqueuing phase, so we need to manually set the
          // library override.
          assert mockedSuperClass.lookupVirtualMethod(virtualMethod.method) != null;
          virtualMethod.setLibraryMethodOverride(OptionalBool.TRUE);
        }
      }
    }
    return classpathClasses;
  }

  public DesugaredLibraryConversionWrapperAnalysis registerWrite(
      DexProgramClass wrapper, Consumer<DexEncodedMethod> registration) {
    registration.accept(getInitializer(wrapper));
    return this;
  }

  public DesugaredLibraryConversionWrapperAnalysis registerReads(
      DexProgramClass wrapper, Consumer<DexEncodedMethod> registration) {
    // The field of each wrapper is read exclusively in all virtual methods and the reverse wrapper
    // convert method.
    for (DexEncodedMethod virtualMethod : wrapper.virtualMethods()) {
      registration.accept(virtualMethod);
    }
    DexProgramClass reverseWrapper = wrappersToReverseMap.get(wrapper);
    assert reverseWrapper != null;
    registration.accept(getConvertMethod(reverseWrapper));
    return this;
  }

  private DexEncodedMethod getInitializer(DexProgramClass wrapper) {
    DexEncodedMethod initializer =
        wrapper.lookupDirectMethod(DexEncodedMethod::isInstanceInitializer);
    assert initializer != null;
    return initializer;
  }

  private DexEncodedMethod getConvertMethod(DexProgramClass wrapper) {
    DexEncodedMethod convertMethod = wrapper.lookupDirectMethod(DexEncodedMethod::isStatic);
    assert convertMethod != null;
    assert convertMethod.method.name == appView.dexItemFactory().convertMethodName;
    return convertMethod;
  }
}
