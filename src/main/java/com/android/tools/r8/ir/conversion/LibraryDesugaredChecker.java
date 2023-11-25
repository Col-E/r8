// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.graph.DexValue.DexValueArray;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistryWithResult;
import com.google.common.collect.Iterables;

public class LibraryDesugaredChecker {
  private final AppView<?> appView;
  private final DexString jDollarDescriptorPrefix;

  LibraryDesugaredChecker(AppView<?> appView) {
    this.appView = appView;
    this.jDollarDescriptorPrefix = appView.dexItemFactory().jDollarDescriptorPrefix;
  }

  public boolean isClassLibraryDesugared(DexProgramClass clazz) {
    IsLibraryDesugaredTracer tracer =
        new IsLibraryDesugaredTracer(appView, jDollarDescriptorPrefix, clazz);
    tracer.run();
    return tracer.isLibraryDesugared();
  }

  private static class IsLibraryDesugaredTracer {

    private final DexString jDollarDescriptorPrefix;
    private final AppView<?> appView;
    private final DexProgramClass clazz;
    private boolean isLibraryDesugared = false;

    public IsLibraryDesugaredTracer(
        AppView<?> appView, DexString jDollarDescriptorPrefix, DexProgramClass clazz) {
      this.jDollarDescriptorPrefix = jDollarDescriptorPrefix;
      this.appView = appView;
      this.clazz = clazz;
    }

    public void run() {
      registerClass(clazz);
    }

    public boolean isLibraryDesugared() {
      return isLibraryDesugared;
    }

    private void registerClass(DexProgramClass clazz) {
      if (clazz.superType != null) {
        registerType(clazz.superType);
      }
      for (DexType implementsType : clazz.interfaces.values) {
        registerType(implementsType);
      }
      if (isLibraryDesugared) {
        return;
      }
      for (DexEncodedMethod method : clazz.methods()) {
        registerMethod(new ProgramMethod(clazz, method));
        if (isLibraryDesugared) {
          return;
        }
      }
      clazz.forEachField(this::registerField);
    }

    private void registerType(DexType type) {
      isLibraryDesugared =
          isLibraryDesugared || type.descriptor.startsWith(jDollarDescriptorPrefix);
    }

    private void registerField(DexField field) {
      registerType(field.getHolderType());
      registerType(field.getType());
    }

    private void registerMethod(DexMethod method) {
      for (DexType type : method.getParameters()) {
        registerType(type);
      }
      registerType(method.getReturnType());
    }

    private void registerField(DexEncodedField field) {
      registerField(field.getReference());
    }

    @SuppressWarnings("ReferenceEquality")
    private void registerMethod(ProgramMethod method) {
      registerMethod(method.getReference());
      for (DexAnnotation annotation : method.getDefinition().annotations().annotations) {
        if (annotation.annotation.type == appView.dexItemFactory().annotationThrows) {
          DexValueArray dexValues = annotation.annotation.elements[0].value.asDexValueArray();
          for (DexValue dexValType : dexValues.getValues()) {
            registerType(dexValType.asDexValueType().value);
          }
        }
      }

      if (!isLibraryDesugared) {
        isLibraryDesugared =
            method.registerCodeReferencesWithResult(
                new IsLibraryDesugaredUseRegistry(appView, method));
      }
    }

    private class IsLibraryDesugaredUseRegistry
        extends UseRegistryWithResult<Boolean, ProgramMethod> {

      public IsLibraryDesugaredUseRegistry(AppView<?> appView, ProgramMethod context) {
        super(appView, context, false);
      }

      private boolean registerField(DexField field) {
        return registerType(field.getHolderType()) || registerType(field.getType());
      }

      private boolean registerMethod(DexMethod method) {
        return registerType(method.getReturnType())
            || Iterables.any(method.getParameters(), this::registerType);
      }

      private boolean registerType(DexType type) {
        if (type.descriptor.startsWith(jDollarDescriptorPrefix)) {
          setResult(true);
          return true;
        }
        return false;
      }

      @Override
      public void registerInitClass(DexType type) {
        registerType(type);
      }

      @Override
      public void registerInvokeVirtual(DexMethod method) {
        registerMethod(method);
      }

      @Override
      public void registerInvokeDirect(DexMethod method) {
        registerMethod(method);
      }

      @Override
      public void registerInvokeStatic(DexMethod method) {
        registerMethod(method);
      }

      @Override
      public void registerInvokeInterface(DexMethod method) {
        registerMethod(method);
      }

      @Override
      public void registerInvokeStatic(DexMethod method, boolean itf) {
        registerMethod(method);
      }

      @Override
      public void registerInvokeSuper(DexMethod method) {
        registerMethod(method);
      }

      @Override
      public void registerInstanceFieldRead(DexField field) {
        registerField(field);
      }

      @Override
      public void registerInstanceFieldWrite(DexField field) {
        registerField(field);
      }

      @Override
      public void registerNewInstance(DexType type) {
        registerType(type);
      }

      @Override
      public void registerStaticFieldRead(DexField field) {
        registerField(field);
      }

      @Override
      public void registerStaticFieldWrite(DexField field) {
        registerField(field);
      }

      @Override
      public void registerTypeReference(DexType type) {
        registerType(type);
      }

      @Override
      public void registerInstanceOf(DexType type) {
        registerType(type);
      }
    }
  }
}
