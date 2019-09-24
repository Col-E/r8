// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.TypeLatticeElement;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.Invoke;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.StringDiagnostic;
import java.util.Collections;

// TODO(b/134732760): In progress.
// I convert library calls with desugared parameters/return values so they can work normally.
// In the JSON of the desugared library, one can specify conversions between desugared and
// non-desugared types. If no conversion is specified, D8/R8 simply generate wrapper classes around
// the types. Wrappers induce both memory and runtime performance overhead. Wrappers overload
// all potential called APIs.
// Since many types are going to be rewritten, I also need to change the signature of the method
// called so that they are still called with the original types. Hence the vivified types.
// Given a type from the library, the prefix rewriter rewrites (->) as follow:
// vivifiedType -> type;
// type -> desugarType;
// No vivified types can be present in the compiled program (will necessarily be rewritten).
// DesugarType is only a rewritten type (generated through rewriting of type).
// The type, from the library, may either be rewritten to the desugarType,
// or be a rewritten type (generated through rewriting of vivifiedType).
public class DesugaredLibraryAPIConverter {

  private static final String VIVIFIED_PREFIX = "$-vivified-$.";

  private final AppView<?> appView;
  private final DexItemFactory factory;

  public DesugaredLibraryAPIConverter(AppView<?> appView) {
    this.appView = appView;
    this.factory = appView.dexItemFactory();
  }

  public void desugar(IRCode code) {
    // TODO(b/134732760): The current code does not catch library calls into a program override
    //  which gets rewritten. If method signature has rewritten types and method overrides library,
    //  I should convert back.

    InstructionListIterator iterator = code.instructionListIterator();
    while (iterator.hasNext()) {
      Instruction instruction = iterator.next();
      if (!instruction.isInvokeMethod()) {
        continue;
      }
      InvokeMethod invokeMethod = instruction.asInvokeMethod();
      DexMethod invokedMethod = invokeMethod.getInvokedMethod();
      // Rewritting is required only on calls to library methods which are not desugared.
      if (appView.rewritePrefix.hasRewrittenType(invokedMethod.holder)
          || invokedMethod.holder.isArrayType()) {
        continue;
      }
      DexClass dexClass = appView.definitionFor(invokedMethod.holder);
      if (dexClass == null || !dexClass.isLibraryClass()) {
        continue;
      }
      if (appView.rewritePrefix.hasRewrittenType(invokedMethod.proto.returnType)) {
        addReturnConversion(code, invokeMethod, iterator);
      }
      for (int i = 0; i < invokedMethod.proto.parameters.values.length; i++) {
        DexType argType = invokedMethod.proto.parameters.values[i];
        if (appView.rewritePrefix.hasRewrittenType(argType)) {
          addParameterConversion(code, invokeMethod, iterator, argType, i);
        }
      }
    }
  }

  private void warnInvalidInvoke(DexType type, DexMethod invokedMethod, String debugString) {
    DexType desugaredType = appView.rewritePrefix.rewrittenType(type);
    appView
        .options()
        .reporter
        .warning(
            new StringDiagnostic(
                "Invoke to "
                    + invokedMethod.holder
                    + "#"
                    + invokedMethod.name
                    + " may not work correctly at runtime ("
                    + debugString
                    + " type "
                    + desugaredType
                    + " is a desugared type)."));
  }

  private DexType vivifiedTypeFor(DexType type) {
    // doubleDollarTypes are fake types to work around rewriting.
    DexType vivifiedType =
        factory.createType(DescriptorUtils.javaTypeToDescriptor(VIVIFIED_PREFIX + type.toString()));
    appView.rewritePrefix.addPrefix(vivifiedType.toString(), type.toString());
    return vivifiedType;
  }

  private void addParameterConversion(
      IRCode code,
      InvokeMethod invokeMethod,
      InstructionListIterator iterator,
      DexType argType,
      int parameter) {
    if (!appView
        .options()
        .desugaredLibraryConfiguration
        .getCustomConversions()
        .containsKey(argType)) {
      // TODO(b/134732760): Add Wrapper Conversions.
      warnInvalidInvoke(argType, invokeMethod.getInvokedMethod(), "parameter");
      return;
    }

    Value inValue = invokeMethod.inValues().get(parameter);
    DexType argVivifiedType = vivifiedTypeFor(argType);
    DexType conversionHolder =
        appView.options().desugaredLibraryConfiguration.getCustomConversions().get(argType);

    // ConversionType has static method "type convert(rewrittenType)".
    // But everything is going to be rewritten, so we need to call "vivifiedType convert(type)".
    DexMethod conversionMethod =
        factory.createMethod(
            conversionHolder,
            factory.createProto(argVivifiedType, argType),
            factory.convertMethodName);
    Value convertedValue =
        code.createValue(
            TypeLatticeElement.fromDexType(
                argVivifiedType, inValue.getTypeLattice().nullability(), appView));
    InvokeStatic conversionInstruction =
        new InvokeStatic(conversionMethod, convertedValue, Collections.singletonList(inValue));
    conversionInstruction.setPosition(invokeMethod.getPosition());
    iterator.previous();
    iterator.add(conversionInstruction);
    iterator.next();

    // Rewrite invoke (signature and inValue to rewrite).
    DexMethod newDexMethod =
        dexMethodWithDifferentParameter(
            invokeMethod.getInvokedMethod(), argVivifiedType, parameter);
    Invoke newInvokeMethod =
        Invoke.create(
            invokeMethod.getType(),
            newDexMethod,
            newDexMethod.proto,
            invokeMethod.outValue(),
            invokeMethod.inValues());
    newInvokeMethod.replaceValue(parameter, conversionInstruction.outValue());
    iterator.replaceCurrentInstruction(newInvokeMethod);
  }

  private void addReturnConversion(
      IRCode code, InvokeMethod invokeMethod, InstructionListIterator iterator) {
    DexType returnType = invokeMethod.getReturnType();
    if (!appView
        .options()
        .desugaredLibraryConfiguration
        .getCustomConversions()
        .containsKey(returnType)) {
      // TODO(b/134732760): Add Wrapper Conversions.
      warnInvalidInvoke(returnType, invokeMethod.getInvokedMethod(), "return");
      return;
    }

    DexType returnVivifiedType = vivifiedTypeFor(returnType);
    DexType conversionHolder =
        appView.options().desugaredLibraryConfiguration.getCustomConversions().get(returnType);

    // ConversionType has static method "rewrittenType convert(type)".
    // But everything is going to be rewritten, so we need to call "type convert(vivifiedType)".
    DexMethod conversionMethod =
        factory.createMethod(
            conversionHolder,
            factory.createProto(returnType, returnVivifiedType),
            factory.convertMethodName);
    Value convertedValue =
        code.createValue(
            TypeLatticeElement.fromDexType(returnType, Nullability.maybeNull(), appView));
    invokeMethod.outValue().replaceUsers(convertedValue);
    InvokeStatic conversionInstruction =
        new InvokeStatic(
            conversionMethod, convertedValue, Collections.singletonList(invokeMethod.outValue()));
    conversionInstruction.setPosition(invokeMethod.getPosition());

    // Rewrite invoke (signature to rewrite).
    DexMethod newDexMethod =
        dexMethodWithDifferentReturn(invokeMethod.getInvokedMethod(), returnVivifiedType);
    Invoke newInvokeMethod =
        Invoke.create(
            invokeMethod.getType(),
            newDexMethod,
            newDexMethod.proto,
            invokeMethod.outValue(),
            invokeMethod.inValues());
    iterator.replaceCurrentInstruction(newInvokeMethod);
    iterator.add(conversionInstruction);
  }

  private DexMethod dexMethodWithDifferentParameter(
      DexMethod method, DexType newParameterType, int parameter) {
    DexType[] newParameters = method.proto.parameters.values.clone();
    newParameters[parameter] = newParameterType;
    DexProto newProto = factory.createProto(method.proto.returnType, newParameters);
    return factory.createMethod(method.holder, newProto, method.name);
  }

  private DexMethod dexMethodWithDifferentReturn(DexMethod method, DexType newReturnType) {
    DexProto newProto = factory.createProto(newReturnType, method.proto.parameters.values);
    return factory.createMethod(method.holder, newProto, method.name);
  }
}
