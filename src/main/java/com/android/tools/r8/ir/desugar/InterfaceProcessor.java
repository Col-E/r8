// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.graph.ClassAccessFlags;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.origin.SynthesizedOrigin;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

// Default and static method interface desugaring processor for interfaces.
//
// Makes default interface methods abstract, moves their implementation to
// a companion class. Removes bridge default methods.
//
// Also moves static interface methods into a companion class.
final class InterfaceProcessor {
  private final InterfaceMethodRewriter rewriter;
  // All created companion classes indexed by interface classes.
  final Map<DexProgramClass, DexProgramClass> companionClasses = new IdentityHashMap<>();

  InterfaceProcessor(InterfaceMethodRewriter rewriter) {
    this.rewriter = rewriter;
  }

  void process(DexProgramClass iface) {
    assert iface.isInterface();

    // The list of methods to be created in companion class.
    List<DexEncodedMethod> companionMethods = new ArrayList<>();

    // Process virtual interface methods first.
    List<DexEncodedMethod> remainingMethods = new ArrayList<>();
    for (DexEncodedMethod virtual : iface.virtualMethods()) {
      if (rewriter.isDefaultMethod(virtual)) {
        // Create a new method in a companion class to represent default method implementation.
        DexMethod companionMethod = rewriter.defaultAsMethodOfCompanionClass(virtual.method);

        Code code = virtual.getCode();
        if (code == null) {
          throw new CompilationError("Code is missing for default "
              + "interface method: " + virtual.method.toSourceString(), iface.origin);
        }

        MethodAccessFlags newFlags = virtual.accessFlags.copy();
        newFlags.unsetBridge();
        newFlags.setStatic();
        DexCode dexCode = code.asDexCode();
        // We cannot name the parameter "this" because the debugger may omit it due to the method
        // actually being static. Instead we prepend it with a special character.
        dexCode.setDebugInfo(dexCode.debugInfoWithAdditionalFirstParameter(
            rewriter.factory.createString("-this")));
        assert (dexCode.getDebugInfo() == null)
            || (companionMethod.getArity() == dexCode.getDebugInfo().parameters.length);

        companionMethods.add(new DexEncodedMethod(companionMethod,
            newFlags, virtual.annotations, virtual.parameterAnnotations, code));

        // Make the method abstract.
        virtual.accessFlags.setAbstract();
        virtual.removeCode();
      }

      // Remove bridge methods.
      if (!virtual.accessFlags.isBridge()) {
        remainingMethods.add(virtual);
      }
    }

    // If at least one bridge methods was removed update the table.
    if (remainingMethods.size() < iface.virtualMethods().length) {
      iface.setVirtualMethods(remainingMethods.toArray(
          new DexEncodedMethod[remainingMethods.size()]));
    }
    remainingMethods.clear();

    // Process static and private methods, move them into companion class as well,
    // make private instance methods static.
    for (DexEncodedMethod direct : iface.directMethods()) {
      MethodAccessFlags originalFlags = direct.accessFlags;
      MethodAccessFlags newFlags = originalFlags.copy();
      if (originalFlags.isPrivate()) {
        newFlags.unsetPrivate();
        newFlags.setPublic();
      }

      if (isStaticMethod(direct)) {
        assert originalFlags.isPrivate() || originalFlags.isPublic()
            : "Static interface method " + direct.toSourceString() + " is expected to "
            + "either be public or private in " + iface.origin;
        companionMethods.add(new DexEncodedMethod(
            rewriter.staticAsMethodOfCompanionClass(direct.method), newFlags,
            direct.annotations, direct.parameterAnnotations, direct.getCode()));

      } else {
        if (originalFlags.isPrivate()) {
          assert !rewriter.factory.isClassConstructor(direct.method)
              : "Unexpected private constructor " + direct.toSourceString()
              + " in " + iface.origin;
          newFlags.setStatic();

          DexMethod companionMethod = rewriter.privateAsMethodOfCompanionClass(direct.method);

          Code code = direct.getCode();
          if (code == null) {
            throw new CompilationError("Code is missing for private instance "
                + "interface method: " + direct.method.toSourceString(), iface.origin);
          }
          DexCode dexCode = code.asDexCode();
          // TODO(ager): Should we give the new first parameter an actual name? Maybe 'this'?
          dexCode.setDebugInfo(dexCode.debugInfoWithAdditionalFirstParameter(null));
          assert (dexCode.getDebugInfo() == null)
              || (companionMethod.getArity() == dexCode.getDebugInfo().parameters.length);

          companionMethods.add(new DexEncodedMethod(companionMethod,
              newFlags, direct.annotations, direct.parameterAnnotations, code));

        } else {
          // Since there are no interface constructors at this point,
          // this should only be class constructor.
          assert rewriter.factory.isClassConstructor(direct.method);
          remainingMethods.add(direct);
        }
      }
    }
    if (remainingMethods.size() < iface.directMethods().length) {
      iface.setDirectMethods(remainingMethods.toArray(
          new DexEncodedMethod[remainingMethods.size()]));
    }

    if (companionMethods.isEmpty()) {
      return; // No methods to create, companion class not needed.
    }

    ClassAccessFlags companionClassFlags = iface.accessFlags.copy();
    companionClassFlags.unsetAbstract();
    companionClassFlags.unsetInterface();
    companionClassFlags.setFinal();
    companionClassFlags.setSynthetic();
    // Companion class must be public so moved methods can be called from anywhere.
    companionClassFlags.setPublic();

    // Create companion class.
    DexType companionClassType = rewriter.getCompanionClassType(iface.type);
    DexProgramClass companionClass =
        new DexProgramClass(
            companionClassType,
            null,
            new SynthesizedOrigin("interface desugaring", getClass()),
            companionClassFlags,
            rewriter.factory.objectType,
            DexTypeList.empty(),
            iface.sourceFile,
            null,
            Collections.emptyList(),
            DexAnnotationSet.empty(),
            DexEncodedField.EMPTY_ARRAY,
            DexEncodedField.EMPTY_ARRAY,
            companionMethods.toArray(new DexEncodedMethod[companionMethods.size()]),
            DexEncodedMethod.EMPTY_ARRAY,
            Collections.singletonList(iface));
    companionClasses.put(iface, companionClass);
  }

  private boolean isStaticMethod(DexEncodedMethod method) {
    if (method.accessFlags.isNative()) {
      throw new Unimplemented("Native interface methods are not yet supported.");
    }
    return method.accessFlags.isStatic() && !rewriter.factory.isClassConstructor(method.method);
  }
}
