// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexApplication;
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
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.StringDiagnostic;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

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

  static final String VIVIFIED_PREFIX = "$-vivified-$.";

  private final AppView<?> appView;
  private final DexItemFactory factory;
  private final DesugaredLibraryWrapperSynthesizer wrapperSynthesizor;

  public DesugaredLibraryAPIConverter(AppView<?> appView) {
    this.appView = appView;
    this.factory = appView.dexItemFactory();
    this.wrapperSynthesizor = new DesugaredLibraryWrapperSynthesizer(appView, this);
  }

  public void desugar(IRCode code) {

    if (wrapperSynthesizor.hasSynthesized(code.method.method.holder)) {
      return;
    }

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
      // Library methods do not understand desugared types, hence desugared types have to be
      // converted around non desugared library calls for the invoke to resolve.
      if (appView.rewritePrefix.hasRewrittenType(invokedMethod.proto.returnType)) {
        rewriteLibraryInvoke(code, invokeMethod, iterator);
        continue;
      }
      for (int i = 0; i < invokedMethod.proto.parameters.values.length; i++) {
        DexType argType = invokedMethod.proto.parameters.values[i];
        if (appView.rewritePrefix.hasRewrittenType(argType)) {
          rewriteLibraryInvoke(code, invokeMethod, iterator);
          continue;
        }
      }
    }
  }

  public void generateWrappers(
      DexApplication.Builder<?> builder, IRConverter irConverter, ExecutorService executorService)
      throws ExecutionException {
    wrapperSynthesizor.finalizeWrappers(builder, irConverter, executorService);
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

  public DexType vivifiedTypeFor(DexType type) {
    DexType vivifiedType =
        factory.createType(DescriptorUtils.javaTypeToDescriptor(VIVIFIED_PREFIX + type.toString()));
    appView.rewritePrefix.rewriteType(vivifiedType, type);
    return vivifiedType;
  }

  private void rewriteLibraryInvoke(
      IRCode code, InvokeMethod invokeMethod, InstructionListIterator iterator) {
    DexMethod invokedMethod = invokeMethod.getInvokedMethod();

    // Create return conversion if required.
    Instruction returnConversion = null;
    DexType newReturnType;
    DexType returnType = invokedMethod.proto.returnType;
    if (appView.rewritePrefix.hasRewrittenType(returnType)) {
      if (canConvert(returnType)) {
        newReturnType = vivifiedTypeFor(returnType);
        // Return conversion added only if return value is used.
        if (invokeMethod.outValue() != null
            && invokeMethod.outValue().numberOfUsers() + invokeMethod.outValue().numberOfPhiUsers()
                > 0) {
          returnConversion =
              createReturnConversionAndReplaceUses(code, invokeMethod, returnType, newReturnType);
        }
      } else {
        warnInvalidInvoke(returnType, invokeMethod.getInvokedMethod(), "return");
        newReturnType = returnType;
      }
    } else {
      newReturnType = returnType;
    }

    // Create parameter conversions if required.
    List<Instruction> parameterConversions = new ArrayList<>();
    List<Value> newInValues = new ArrayList<>();
    if (invokeMethod.isInvokeMethodWithReceiver()) {
      assert !appView.rewritePrefix.hasRewrittenType(invokedMethod.holder);
      newInValues.add(invokeMethod.asInvokeMethodWithReceiver().getReceiver());
    }
    int receiverShift = BooleanUtils.intValue(invokeMethod.isInvokeMethodWithReceiver());
    DexType[] parameters = invokedMethod.proto.parameters.values;
    DexType[] newParameters = parameters.clone();
    for (int i = 0; i < parameters.length; i++) {
      DexType argType = parameters[i];
      if (appView.rewritePrefix.hasRewrittenType(argType)) {
        if (canConvert(argType)) {
          DexType argVivifiedType = vivifiedTypeFor(argType);
          Value inValue = invokeMethod.inValues().get(i + receiverShift);
          newParameters[i] = argVivifiedType;
          parameterConversions.add(
              createParameterConversion(code, argType, argVivifiedType, inValue));
          newInValues.add(parameterConversions.get(parameterConversions.size() - 1).outValue());
        } else {
          warnInvalidInvoke(argType, invokeMethod.getInvokedMethod(), "parameter");
          newInValues.add(invokeMethod.inValues().get(i + receiverShift));
        }
      } else {
        newInValues.add(invokeMethod.inValues().get(i + receiverShift));
      }
    }

    // Patch the invoke with new types and new inValues.
    DexProto newProto = factory.createProto(newReturnType, newParameters);
    DexMethod newDexMethod =
        factory.createMethod(invokedMethod.holder, newProto, invokedMethod.name);
    Invoke newInvokeMethod =
        Invoke.create(
            invokeMethod.getType(),
            newDexMethod,
            newDexMethod.proto,
            invokeMethod.outValue(),
            newInValues);

    // Insert and reschedule all instructions.
    iterator.previous();
    for (Instruction parameterConversion : parameterConversions) {
      parameterConversion.setPosition(invokeMethod.getPosition());
      iterator.add(parameterConversion);
    }
    assert iterator.peekNext() == invokeMethod;
    iterator.next();
    iterator.replaceCurrentInstruction(newInvokeMethod);
    if (returnConversion != null) {
      returnConversion.setPosition(invokeMethod.getPosition());
      iterator.add(returnConversion);
    }
  }

  private Instruction createParameterConversion(
      IRCode code, DexType argType, DexType argVivifiedType, Value inValue) {
    DexMethod conversionMethod = createConversionMethod(argType, argType, argVivifiedType);
    // The value is null only if the input is null.
    Value convertedValue =
        createConversionValue(code, inValue.getTypeLattice().nullability(), argVivifiedType);
    return new InvokeStatic(conversionMethod, convertedValue, Collections.singletonList(inValue));
  }

  private Instruction createReturnConversionAndReplaceUses(
      IRCode code, InvokeMethod invokeMethod, DexType returnType, DexType returnVivifiedType) {
    DexMethod conversionMethod = createConversionMethod(returnType, returnVivifiedType, returnType);
    Value convertedValue = createConversionValue(code, Nullability.maybeNull(), returnType);
    invokeMethod.outValue().replaceUsers(convertedValue);
    return new InvokeStatic(
        conversionMethod, convertedValue, Collections.singletonList(invokeMethod.outValue()));
  }

  public DexMethod createConversionMethod(DexType type, DexType srcType, DexType destType) {
    // ConversionType holds the methods "rewrittenType convert(type)" and the other way around.
    // But everything is going to be rewritten, so we need to use vivifiedType and type".
    DexType conversionHolder =
        appView.options().desugaredLibraryConfiguration.getCustomConversions().get(type);
    if (conversionHolder == null) {
      conversionHolder =
          type == srcType
              ? wrapperSynthesizor.getTypeWrapper(type)
              : wrapperSynthesizor.getVivifiedTypeWrapper(type);
    }
    assert conversionHolder != null;
    return factory.createMethod(
        conversionHolder, factory.createProto(destType, srcType), factory.convertMethodName);
  }

  private Value createConversionValue(IRCode code, Nullability nullability, DexType valueType) {
    return code.createValue(TypeLatticeElement.fromDexType(valueType, nullability, appView));
  }

  public boolean canConvert(DexType type) {
    return appView.options().desugaredLibraryConfiguration.getCustomConversions().containsKey(type)
        || wrapperSynthesizor.canGenerateWrapper(type);
  }
}
