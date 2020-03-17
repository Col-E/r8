// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClasspathClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.TypeLatticeElement;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.Invoke;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.ir.synthetic.DesugaredLibraryAPIConversionCfCodeProvider.APIConverterWrapperCfCodeProvider;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.StringDiagnostic;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
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
  private static final String DESCRIPTOR_VIVIFIED_PREFIX = "L$-vivified-$/";

  private final AppView<?> appView;
  private final DexItemFactory factory;
  // For debugging only, allows to assert that synthesized code in R8 have been synthesized in the
  // Enqueuer and not during IR processing.
  private final Mode mode;
  private final DesugaredLibraryWrapperSynthesizer wrapperSynthesizor;
  private final Map<DexClass, Set<DexEncodedMethod>> callBackMethods = new HashMap<>();
  private final Set<DexMethod> trackedCallBackAPIs;
  private final Set<DexMethod> trackedAPIs;

  public enum Mode {
    GENERATE_CALLBACKS_AND_WRAPPERS,
    ASSERT_CALLBACKS_AND_WRAPPERS_GENERATED;
  }

  public DesugaredLibraryAPIConverter(AppView<?> appView, Mode mode) {
    this.appView = appView;
    this.factory = appView.dexItemFactory();
    this.mode = mode;
    this.wrapperSynthesizor = new DesugaredLibraryWrapperSynthesizer(appView, this);
    if (appView.options().testing.trackDesugaredAPIConversions) {
      trackedCallBackAPIs = Sets.newConcurrentHashSet();
      trackedAPIs = Sets.newConcurrentHashSet();
    } else {
      trackedCallBackAPIs = null;
      trackedAPIs = null;
    }
  }

  public static boolean isVivifiedType(DexType type) {
    return type.descriptor.toString().startsWith(DESCRIPTOR_VIVIFIED_PREFIX);
  }

  boolean canGenerateWrappersAndCallbacks() {
    return mode == Mode.GENERATE_CALLBACKS_AND_WRAPPERS;
  }

  public void desugar(IRCode code) {

    if (wrapperSynthesizor.hasSynthesized(code.method.method.holder)) {
      return;
    }

    if (!canGenerateWrappersAndCallbacks()) {
      assert validateCallbackWasGeneratedInEnqueuer(code.method);
    } else {
      registerCallbackIfRequired(code.method);
    }

    ListIterator<BasicBlock> blockIterator = code.listIterator();
    while (blockIterator.hasNext()) {
      BasicBlock block = blockIterator.next();
      InstructionListIterator iterator = block.listIterator(code);
      while (iterator.hasNext()) {
        Instruction instruction = iterator.next();
        if (!instruction.isInvokeMethod()) {
          continue;
        }
        InvokeMethod invokeMethod = instruction.asInvokeMethod();
        DexMethod invokedMethod = invokeMethod.getInvokedMethod();
        // Library methods do not understand desugared types, hence desugared types have to be
        // converted around non desugared library calls for the invoke to resolve.
        if (shouldRewriteInvoke(invokedMethod)) {
          rewriteLibraryInvoke(code, invokeMethod, iterator, blockIterator);
        }
      }
    }
  }

  private boolean validateCallbackWasGeneratedInEnqueuer(DexEncodedMethod encodedMethod) {
    if (!shouldRegisterCallback(encodedMethod)) {
      return true;
    }
    DexProgramClass holderClass = appView.definitionForProgramType(encodedMethod.method.holder);
    DexMethod installedCallback =
        methodWithVivifiedTypeInSignature(encodedMethod.method, holderClass.type, appView);
    assert holderClass.lookupMethod(installedCallback) != null;
    return true;
  }

  private boolean shouldRewriteInvoke(DexMethod invokedMethod) {
    if (appView.rewritePrefix.hasRewrittenType(invokedMethod.holder, appView)
        || invokedMethod.holder.isArrayType()) {
      return false;
    }
    DexClass dexClass = appView.definitionFor(invokedMethod.holder);
    if (dexClass == null || !dexClass.isLibraryClass()) {
      return false;
    }
    return appView.rewritePrefix.hasRewrittenTypeInSignature(invokedMethod.proto, appView);
  }

  public void registerCallbackIfRequired(DexEncodedMethod encodedMethod) {
    if (shouldRegisterCallback(encodedMethod)) {
      DexClass dexClass = appView.definitionFor(encodedMethod.method.holder);
      assert dexClass != null;
      registerCallback(dexClass, encodedMethod);
    }
  }

  private boolean shouldRegisterCallback(DexEncodedMethod encodedMethod) {
    // Any override of a library method can be called by the library.
    // We duplicate the method to have a vivified type version callable by the library and
    // a type version callable by the program. We need to add the vivified version to the rootset
    // as it is actually overriding a library method (after changing the vivified type to the core
    // library type), but the enqueuer cannot see that.
    // To avoid too much computation we first look if the method would need to be rewritten if
    // it would override a library method, then check if it overrides a library method.
    if (encodedMethod.isPrivateMethod() || encodedMethod.isStatic()) {
      return false;
    }
    DexMethod method = encodedMethod.method;
    if (method.holder.isArrayType()
        || !appView.rewritePrefix.hasRewrittenTypeInSignature(method.proto, appView)
        || appView
            .options()
            .desugaredLibraryConfiguration
            .getEmulateLibraryInterface()
            .containsKey(method.holder)) {
      return false;
    }
    DexClass dexClass = appView.definitionFor(method.holder);
    if (dexClass == null) {
      return false;
    }
    return overridesLibraryMethod(dexClass, method);
  }


  private boolean overridesLibraryMethod(DexClass theClass, DexMethod method) {
    // We look up everywhere to see if there is a supertype/interface implementing the method...
    LinkedList<DexType> workList = new LinkedList<>();
    Collections.addAll(workList, theClass.interfaces.values);
    boolean foundOverrideToRewrite = false;
    // There is no methods with desugared types on Object.
    if (theClass.superType != factory.objectType) {
      workList.add(theClass.superType);
    }
    while (!workList.isEmpty()) {
      DexType current = workList.removeFirst();
      DexClass dexClass = appView.definitionFor(current);
      if (dexClass == null) {
        continue;
      }
      workList.addAll(Arrays.asList(dexClass.interfaces.values));
      if (dexClass.superType != factory.objectType) {
        workList.add(dexClass.superType);
      }
      if (!dexClass.isLibraryClass() && !appView.options().isDesugaredLibraryCompilation()) {
        continue;
      }
      DexEncodedMethod dexEncodedMethod = dexClass.lookupVirtualMethod(method);
      if (dexEncodedMethod != null) {
        // In this case, the object will be wrapped.
        if (appView.rewritePrefix.hasRewrittenType(dexClass.type, appView)) {
          return false;
        }
        foundOverrideToRewrite = true;
      }
    }
    return foundOverrideToRewrite;
  }

  private synchronized void registerCallback(DexClass dexClass, DexEncodedMethod originalMethod) {
    // In R8 we should be in the enqueuer, therefore we can duplicate a default method and both
    // methods will be desugared.
    // In D8, this happens after interface method desugaring, we cannot introduce new default
    // methods, but we do not need to since this is a library override (invokes will resolve) and
    // all implementors have been enhanced with a forwarding method which will be duplicated.
    if (!appView.enableWholeProgramOptimizations()) {
      if (dexClass.isInterface()
          && originalMethod.isDefaultMethod()
          && (!appView.options().canUseDefaultAndStaticInterfaceMethods()
              || appView.options().isDesugaredLibraryCompilation())) {
        return;
      }
    }
    if (trackedCallBackAPIs != null) {
      trackedCallBackAPIs.add(originalMethod.method);
    }
    addCallBackSignature(dexClass, originalMethod);
  }

  private synchronized void addCallBackSignature(DexClass dexClass, DexEncodedMethod method) {
    assert dexClass.type == method.method.holder;
    callBackMethods.putIfAbsent(dexClass, new HashSet<>());
    callBackMethods.get(dexClass).add(method);
  }

  public static DexMethod methodWithVivifiedTypeInSignature(
      DexMethod originalMethod, DexType holder, AppView<?> appView) {
    DexType[] newParameters = originalMethod.proto.parameters.values.clone();
    int index = 0;
    for (DexType param : originalMethod.proto.parameters.values) {
      if (appView.rewritePrefix.hasRewrittenType(param, appView)) {
        newParameters[index] = vivifiedTypeFor(param, appView);
      }
      index++;
    }
    DexType returnType = originalMethod.proto.returnType;
    DexType newReturnType =
        appView.rewritePrefix.hasRewrittenType(returnType, appView)
            ? vivifiedTypeFor(returnType, appView)
            : returnType;
    DexProto newProto = appView.dexItemFactory().createProto(newReturnType, newParameters);
    return appView.dexItemFactory().createMethod(holder, newProto, originalMethod.name);
  }

  public void finalizeWrappers(
      DexApplication.Builder<?> builder, IRConverter irConverter, ExecutorService executorService)
      throws ExecutionException {
    // In D8, we generate the wrappers here. In R8, wrappers have already been generated in the
    // enqueuer, so nothing needs to be done.
    if (appView.enableWholeProgramOptimizations()) {
      return;
    }
    List<DexEncodedMethod> callbacks = generateCallbackMethods();
    irConverter.processMethodsConcurrently(callbacks, executorService);
    wrapperSynthesizor.finalizeWrappersForD8(builder, irConverter, executorService);
  }

  public List<DexEncodedMethod> generateCallbackMethods() {
    if (appView.options().testing.trackDesugaredAPIConversions) {
      generateTrackDesugaredAPIWarnings(trackedAPIs, "");
      generateTrackDesugaredAPIWarnings(trackedCallBackAPIs, "callback ");
    }
    List<DexEncodedMethod> result = new ArrayList<>();
    for (DexClass dexClass : callBackMethods.keySet()) {
      List<DexEncodedMethod> dexEncodedMethods =
          generateCallbackMethods(callBackMethods.get(dexClass), dexClass);
      dexClass.addVirtualMethods(dexEncodedMethods);
      result.addAll(dexEncodedMethods);
    }
    return result;
  }

  public Map<DexProgramClass, DexProgramClass> synthesizeWrappersAndMapToReverse() {
    return wrapperSynthesizor.synthesizeWrappersAndMapToReverse();
  }

  public DexClasspathClass synthesizeClasspathMock(
      DexClass classToMock, DexType mockType, boolean mockIsInterface) {
    return wrapperSynthesizor.synthesizeClasspathMock(classToMock, mockType, mockIsInterface);
  }

  private List<DexEncodedMethod> generateCallbackMethods(
      Set<DexEncodedMethod> originalMethods, DexClass dexClass) {
    List<DexEncodedMethod> newDexEncodedMethods = new ArrayList<>();
    for (DexEncodedMethod originalMethod : originalMethods) {
      DexMethod methodToInstall =
          methodWithVivifiedTypeInSignature(originalMethod.method, dexClass.type, appView);
      CfCode cfCode =
          new APIConverterWrapperCfCodeProvider(
                  appView, originalMethod.method, null, this, dexClass.isInterface())
              .generateCfCode();
      DexEncodedMethod newDexEncodedMethod =
          wrapperSynthesizor.newSynthesizedMethod(methodToInstall, originalMethod, cfCode);
      newDexEncodedMethod.setCode(cfCode, appView);
      newDexEncodedMethods.add(newDexEncodedMethod);
    }
    assert Sets.newHashSet(newDexEncodedMethods).size() == newDexEncodedMethods.size();
    return newDexEncodedMethods;
  }

  private void generateTrackDesugaredAPIWarnings(Set<DexMethod> tracked, String inner) {
    StringBuilder sb = new StringBuilder();
    sb.append("Tracked ").append(inner).append("desugared API conversions: ");
    for (DexMethod method : tracked) {
      sb.append("\n");
      sb.append(method);
    }
    appView.options().reporter.warning(new StringDiagnostic(sb.toString()));
  }

  private void warnInvalidInvoke(DexType type, DexMethod invokedMethod, String debugString) {
    DexType desugaredType = appView.rewritePrefix.rewrittenType(type, appView);
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

  public static DexType vivifiedTypeFor(DexType type, AppView<?> appView) {
    DexType vivifiedType =
        appView
            .dexItemFactory()
            .createType(DescriptorUtils.javaTypeToDescriptor(VIVIFIED_PREFIX + type.toString()));
    appView.rewritePrefix.rewriteType(vivifiedType, type);
    return vivifiedType;
  }

  public void registerWrappersForLibraryInvokeIfRequired(DexMethod invokedMethod) {
    if (!shouldRewriteInvoke(invokedMethod)) {
      return;
    }
    if (trackedAPIs != null) {
      trackedAPIs.add(invokedMethod);
    }
    DexType returnType = invokedMethod.proto.returnType;
    if (appView.rewritePrefix.hasRewrittenType(returnType, appView) && canConvert(returnType)) {
      registerConversionWrappers(returnType, vivifiedTypeFor(returnType, appView));
    }
    for (DexType argType : invokedMethod.proto.parameters.values) {
      if (appView.rewritePrefix.hasRewrittenType(argType, appView) && canConvert(argType)) {
        registerConversionWrappers(argType, argType);
      }
    }
  }

  private void rewriteLibraryInvoke(
      IRCode code,
      InvokeMethod invokeMethod,
      InstructionListIterator iterator,
      ListIterator<BasicBlock> blockIterator) {
    DexMethod invokedMethod = invokeMethod.getInvokedMethod();
    if (trackedAPIs != null) {
      trackedAPIs.add(invokedMethod);
    }

    // Create return conversion if required.
    Instruction returnConversion = null;
    DexType newReturnType;
    DexType returnType = invokedMethod.proto.returnType;
    if (appView.rewritePrefix.hasRewrittenType(returnType, appView)) {
      if (canConvert(returnType)) {
        newReturnType = vivifiedTypeFor(returnType, appView);
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
      assert !appView.rewritePrefix.hasRewrittenType(invokedMethod.holder, appView);
      newInValues.add(invokeMethod.asInvokeMethodWithReceiver().getReceiver());
    }
    int receiverShift = BooleanUtils.intValue(invokeMethod.isInvokeMethodWithReceiver());
    DexType[] parameters = invokedMethod.proto.parameters.values;
    DexType[] newParameters = parameters.clone();
    for (int i = 0; i < parameters.length; i++) {
      DexType argType = parameters[i];
      if (appView.rewritePrefix.hasRewrittenType(argType, appView)) {
        if (canConvert(argType)) {
          DexType argVivifiedType = vivifiedTypeFor(argType, appView);
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
    assert newDexMethod
        == methodWithVivifiedTypeInSignature(invokedMethod, invokedMethod.holder, appView);

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

    // If the invoke is in a try-catch, since all conversions can throw, the basic block needs
    // to be split in between each invoke...
    if (newInvokeMethod.getBlock().hasCatchHandlers()) {
      splitIfCatchHandlers(code, newInvokeMethod.getBlock(), blockIterator);
    }
  }

  private void splitIfCatchHandlers(
      IRCode code,
      BasicBlock blockWithIncorrectThrowingInstructions,
      ListIterator<BasicBlock> blockIterator) {
    InstructionListIterator instructionsIterator =
        blockWithIncorrectThrowingInstructions.listIterator(code);
    BasicBlock currentBlock = blockWithIncorrectThrowingInstructions;
    while (currentBlock != null && instructionsIterator.hasNext()) {
      Instruction throwingInstruction =
          instructionsIterator.nextUntil(Instruction::instructionTypeCanThrow);
      BasicBlock nextBlock;
      if (throwingInstruction != null) {
        nextBlock = instructionsIterator.split(code, blockIterator);
        // Back up to before the split before inserting catch handlers.
        blockIterator.previous();
        nextBlock.copyCatchHandlers(code, blockIterator, currentBlock, appView.options());
        BasicBlock b = blockIterator.next();
        assert b == nextBlock;
        // Switch iteration to the split block.
        instructionsIterator = nextBlock.listIterator(code);
        currentBlock = nextBlock;
      } else {
        assert !instructionsIterator.hasNext();
        instructionsIterator = null;
        currentBlock = null;
      }
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
    Value outValue = invokeMethod.outValue();
    outValue.replaceUsers(convertedValue);
    // The only user of out value is now the new invoke static, so no type propagation is required.
    outValue.setTypeLattice(
        TypeLatticeElement.fromDexType(
            returnVivifiedType, outValue.getTypeLattice().nullability(), appView));
    return new InvokeStatic(conversionMethod, convertedValue, Collections.singletonList(outValue));
  }

  private void registerConversionWrappers(DexType type, DexType srcType) {
    if (appView.options().desugaredLibraryConfiguration.getCustomConversions().get(type) == null) {
      if (type == srcType) {
        wrapperSynthesizor.getTypeWrapper(type);
      } else {
        wrapperSynthesizor.getVivifiedTypeWrapper(type);
      }
    }
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
