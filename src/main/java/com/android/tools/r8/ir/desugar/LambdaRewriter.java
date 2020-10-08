// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.cf.code.CfFieldInstruction;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfInvokeDynamic;
import com.android.tools.r8.cf.code.CfLoad;
import com.android.tools.r8.cf.code.CfNew;
import com.android.tools.r8.cf.code.CfStackInstruction;
import com.android.tools.r8.cf.code.CfStackInstruction.Opcode;
import com.android.tools.r8.cf.code.CfStore;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexApplication.Builder;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.EnclosingMethodAttribute;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.GraphLens.NestedGraphLens;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeCustom;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.NewInstance;
import com.android.tools.r8.ir.code.StaticGet;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.collections.SortedProgramMethodSet;
import com.google.common.base.Suppliers;
import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.function.Supplier;
import org.objectweb.asm.Opcodes;

/**
 * Lambda desugaring rewriter.
 *
 * <p>Performs lambda instantiation point matching, lambda class generation, and instruction
 * patching.
 */
public class LambdaRewriter {

  // Public for testing.
  public static final String LAMBDA_CLASS_NAME_PREFIX = "-$$Lambda$";
  public static final String LAMBDA_GROUP_CLASS_NAME_PREFIX = "-$$LambdaGroup$";
  static final String EXPECTED_LAMBDA_METHOD_PREFIX = "lambda$";
  private static final String LAMBDA_INSTANCE_FIELD_NAME = "INSTANCE";

  private final AppView<?> appView;

  final DexString instanceFieldName;

  private final LambdaRewriterLens.Builder lensBuilder = LambdaRewriterLens.builder();
  private final Set<DexMethod> forcefullyMovedMethods = Sets.newIdentityHashSet();

  // Maps call sites seen so far to inferred lambda descriptor. It is intended
  // to help avoid re-matching call sites we already seen. Note that same call
  // site may match one or several lambda classes.
  //
  // NOTE: synchronize concurrent access on `knownCallSites`.
  private final Map<DexCallSite, LambdaDescriptor> knownCallSites = new IdentityHashMap<>();
  // Maps lambda class type into lambda class representation. Since lambda class
  // type uniquely defines lambda class, effectively canonicalizes lambda classes.
  // NOTE: synchronize concurrent access on `knownLambdaClasses`.
  private final Map<DexType, LambdaClass> knownLambdaClasses = new IdentityHashMap<>();

  public LambdaRewriter(AppView<?> appView) {
    this.appView = appView;
    this.instanceFieldName = appView.dexItemFactory().createString(LAMBDA_INSTANCE_FIELD_NAME);
  }

  void forcefullyMoveMethod(DexMethod from, DexMethod to) {
    lensBuilder.move(from, to);
    forcefullyMovedMethods.add(from);
  }

  public Set<DexMethod> getForcefullyMovedMethods() {
    return forcefullyMovedMethods;
  }

  private void synthesizeAccessibilityBridgesForLambdaClassesD8(
      Collection<LambdaClass> lambdaClasses, IRConverter converter, ExecutorService executorService)
      throws ExecutionException {
    SortedProgramMethodSet nonDexAccessibilityBridges = SortedProgramMethodSet.create();
    List<LambdaClass> sortedLambdaClasses = new ArrayList<>(lambdaClasses);
    sortedLambdaClasses.sort((x, y) -> x.type.slowCompareTo(y.type));
    for (LambdaClass lambdaClass : sortedLambdaClasses) {
      // This call may cause originalMethodSignatures to be updated.
      ProgramMethod accessibilityBridge = lambdaClass.target.ensureAccessibilityIfNeeded(true);
      if (accessibilityBridge != null
          && !accessibilityBridge.getDefinition().getCode().isDexCode()) {
        nonDexAccessibilityBridges.add(accessibilityBridge);
      }
    }
    if (!nonDexAccessibilityBridges.isEmpty()) {
      converter.processMethodsConcurrently(nonDexAccessibilityBridges, executorService);
    }
  }

  /**
   * Detect and desugar lambdas and method references found in the code.
   *
   * <p>NOTE: this method can be called concurrently for several different methods.
   */
  public int desugarLambdas(ProgramMethod method, AppInfoWithClassHierarchy appInfo) {
    return desugarLambdas(
        method.getDefinition(),
        callsite -> {
          LambdaDescriptor descriptor = LambdaDescriptor.tryInfer(callsite, appInfo, method);
          if (descriptor == null) {
            return null;
          }
          return getOrCreateLambdaClass(descriptor, method);
        });
  }

  // Same as above, but where lambdas are always known to exist for the call sites.
  public static int desugarLambdas(
      DexEncodedMethod method, Function<DexCallSite, LambdaClass> callSites) {
    CfCode code = method.getCode().asCfCode();
    List<CfInstruction> instructions = code.getInstructions();
    Supplier<List<CfInstruction>> lazyNewInstructions =
        Suppliers.memoize(() -> new ArrayList<>(instructions));
    int replaced = 0;
    int maxTemp = 0;
    int newInstructionsDelta = 0;
    for (int i = 0; i < instructions.size(); i++) {
      CfInstruction instruction = instructions.get(i);
      if (instruction instanceof CfInvokeDynamic) {
        LambdaClass lambdaClass = callSites.apply(((CfInvokeDynamic) instruction).getCallSite());
        if (lambdaClass == null) {
          continue;
        }
        int newInstructionsIndex = i + newInstructionsDelta;
        if (lambdaClass.isStateless()) {
          CfFieldInstruction getStaticLambdaInstance =
              new CfFieldInstruction(
                  Opcodes.GETSTATIC, lambdaClass.lambdaField, lambdaClass.lambdaField);
          lazyNewInstructions.get().set(newInstructionsIndex, getStaticLambdaInstance);
        } else {
          List<CfInstruction> replacement = new ArrayList<>();
          int arguments = lambdaClass.descriptor.captures.size();
          int temp = code.getMaxLocals();
          for (int j = arguments - 1; j >= 0; j--) {
            ValueType type = ValueType.fromDexType(lambdaClass.descriptor.captures.values[j]);
            replacement.add(new CfStore(type, temp));
            temp += type.requiredRegisters();
          }
          maxTemp = Math.max(temp, maxTemp);
          replacement.add(new CfNew(lambdaClass.type));
          replacement.add(new CfStackInstruction(Opcode.Dup));
          for (int j = 0; j < arguments; j++) {
            ValueType type = ValueType.fromDexType(lambdaClass.descriptor.captures.values[j]);
            temp -= type.requiredRegisters();
            replacement.add(new CfLoad(type, temp));
          }
          replacement.add(new CfInvoke(Opcodes.INVOKESPECIAL, lambdaClass.constructor, false));
          List<CfInstruction> newInstructions = lazyNewInstructions.get();
          newInstructions.remove(newInstructionsIndex);
          newInstructions.addAll(newInstructionsIndex, replacement);
          newInstructionsDelta += replacement.size() - 1;
        }
        ++replaced;
      }
    }
    if (maxTemp > 0) {
      assert maxTemp > code.getMaxLocals();
      code.setMaxLocals(maxTemp);
    }
    if (replaced > 0) {
      code.setInstructions(lazyNewInstructions.get());
    }
    return replaced;
  }

  /** Remove lambda deserialization methods. */
  public void removeLambdaDeserializationMethods(Iterable<DexProgramClass> classes) {
    for (DexProgramClass clazz : classes) {
      clazz.removeMethod(appView.dexItemFactory().deserializeLambdaMethod);
    }
  }

  /** Generates lambda classes and adds them to the builder. */
  public void finalizeLambdaDesugaringForD8(
      Builder<?> builder, IRConverter converter, ExecutorService executorService)
      throws ExecutionException {
    synthesizeAccessibilityBridgesForLambdaClassesD8(
        knownLambdaClasses.values(), converter, executorService);
    for (LambdaClass lambdaClass : knownLambdaClasses.values()) {
      DexProgramClass synthesizedClass = lambdaClass.getOrCreateLambdaClass();
      appView.appInfo().addSynthesizedClass(synthesizedClass, lambdaClass.addToMainDexList.get());
      builder.addSynthesizedClass(synthesizedClass);
    }
    fixup();
    optimizeSynthesizedClasses(converter, executorService);
  }

  private void optimizeSynthesizedClasses(IRConverter converter, ExecutorService executorService)
      throws ExecutionException {
    converter.optimizeSynthesizedClasses(
        knownLambdaClasses.values().stream()
            .map(LambdaClass::getOrCreateLambdaClass)
            .collect(ImmutableSet.toImmutableSet()),
        executorService);
  }

  // Matches invoke-custom instruction operands to infer lambda descriptor
  // corresponding to this lambda invocation point.
  //
  // Returns the lambda descriptor or `MATCH_FAILED`.
  private LambdaDescriptor inferLambdaDescriptor(DexCallSite callSite, ProgramMethod context) {
    // We check the map before and after inferring lambda descriptor to minimize time
    // spent in synchronized block. As a result we may throw away calculated descriptor
    // in rare case when another thread has same call site processed concurrently,
    // but this is a low price to pay comparing to making whole method synchronous.
    LambdaDescriptor descriptor = getKnown(knownCallSites, callSite);
    return descriptor != null
        ? descriptor
        : putIfAbsent(
            knownCallSites,
            callSite,
            LambdaDescriptor.infer(callSite, appView.appInfoForDesugaring(), context));
  }

  // Returns a lambda class corresponding to the lambda descriptor and context,
  // creates the class if it does not yet exist.
  public LambdaClass getOrCreateLambdaClass(
      LambdaDescriptor descriptor, ProgramMethod accessedFrom) {
    DexType lambdaClassType = LambdaClass.createLambdaClassType(appView, accessedFrom, descriptor);
    // We check the map twice to to minimize time spent in synchronized block.
    LambdaClass lambdaClass = getKnown(knownLambdaClasses, lambdaClassType);
    if (lambdaClass == null) {
      lambdaClass =
          putIfAbsent(
              knownLambdaClasses,
              lambdaClassType,
              new LambdaClass(appView, this, accessedFrom, lambdaClassType, descriptor));
      if (appView.options().isDesugaredLibraryCompilation()) {
        DexType rewrittenType =
            appView.rewritePrefix.rewrittenType(accessedFrom.getHolderType(), appView);
        if (rewrittenType == null) {
          rewrittenType =
              appView
                  .options()
                  .desugaredLibraryConfiguration
                  .getEmulateLibraryInterface()
                  .get(accessedFrom.getHolderType());
        }
        if (rewrittenType != null) {
          addRewritingPrefix(accessedFrom, rewrittenType, lambdaClassType);
        }
      }
    }
    lambdaClass.addSynthesizedFrom(accessedFrom.getHolder());
    if (appView.appInfo().getMainDexClasses().contains(accessedFrom.getHolder())) {
      lambdaClass.addToMainDexList.set(true);
    }
    return lambdaClass;
  }

  private LambdaClass getKnownLambdaClass(LambdaDescriptor descriptor, ProgramMethod accessedFrom) {
    DexType lambdaClassType = LambdaClass.createLambdaClassType(appView, accessedFrom, descriptor);
    return getKnown(knownLambdaClasses, lambdaClassType);
  }

  private void addRewritingPrefix(
      ProgramMethod context, DexType rewritten, DexType lambdaClassType) {
    String javaName = lambdaClassType.toString();
    String typeString = context.getHolderType().toString();
    String actualPrefix = typeString.substring(0, typeString.lastIndexOf('.'));
    String rewrittenString = rewritten.toString();
    String actualRewrittenPrefix = rewrittenString.substring(0, rewrittenString.lastIndexOf('.'));
    assert javaName.startsWith(actualPrefix);
    appView.rewritePrefix.rewriteType(
        lambdaClassType,
        appView
            .dexItemFactory()
            .createType(
                DescriptorUtils.javaTypeToDescriptor(
                    actualRewrittenPrefix + javaName.substring(actualPrefix.length()))));
  }

  private static <K, V> V getKnown(Map<K, V> map, K key) {
    synchronized (map) {
      return map.get(key);
    }
  }

  private static <K, V> V putIfAbsent(Map<K, V> map, K key, V value) {
    synchronized (map) {
      V known = map.get(key);
      if (known != null) {
        return known;
      }
      map.put(key, value);
      return value;
    }
  }

  // Patches invoke-custom instruction to create or get an instance
  // of the generated lambda class.
  private void patchInstruction(
      InvokeCustom invoke,
      LambdaClass lambdaClass,
      IRCode code,
      ListIterator<BasicBlock> blocks,
      InstructionListIterator instructions,
      Set<Value> affectedValues) {
    assert lambdaClass != null;
    assert instructions != null;

    // The value representing new lambda instance: we reuse the
    // value from the original invoke-custom instruction, and thus
    // all its usages.
    Value lambdaInstanceValue = invoke.outValue();
    if (lambdaInstanceValue == null) {
      // The out value might be empty in case it was optimized out.
      lambdaInstanceValue =
          code.createValue(
              TypeElement.fromDexType(lambdaClass.type, Nullability.maybeNull(), appView));
    } else {
      affectedValues.add(lambdaInstanceValue);
    }

    // For stateless lambdas we replace InvokeCustom instruction with StaticGet
    // reading the value of INSTANCE field created for singleton lambda class.
    if (lambdaClass.isStateless()) {
      instructions.replaceCurrentInstruction(
          new StaticGet(lambdaInstanceValue, lambdaClass.lambdaField));
      // Note that since we replace one throwing operation with another we don't need
      // to have any special handling for catch handlers.
      return;
    }

    // For stateful lambdas we always create a new instance since we need to pass
    // captured values to the constructor.
    //
    // We replace InvokeCustom instruction with a new NewInstance instruction
    // instantiating lambda followed by InvokeDirect instruction calling a
    // constructor on it.
    //
    //    original:
    //      Invoke-Custom rResult <- { rArg0, rArg1, ... }; call site: ...
    //
    //    result:
    //      NewInstance   rResult <-  LambdaClass
    //      Invoke-Direct { rResult, rArg0, rArg1, ... }; method: void LambdaClass.<init>(...)
    lambdaInstanceValue.setType(
        lambdaInstanceValue.getType().asReferenceType().asDefinitelyNotNull());
    NewInstance newInstance = new NewInstance(lambdaClass.type, lambdaInstanceValue);
    instructions.replaceCurrentInstruction(newInstance);

    List<Value> arguments = new ArrayList<>();
    arguments.add(lambdaInstanceValue);
    arguments.addAll(invoke.arguments()); // Optional captures.
    InvokeDirect constructorCall =
        new InvokeDirect(lambdaClass.constructor, null /* no return value */, arguments);
    instructions.add(constructorCall);
    constructorCall.setPosition(newInstance.getPosition());

    // If we don't have catch handlers we are done.
    if (!constructorCall.getBlock().hasCatchHandlers()) {
      return;
    }

    // Move the iterator back to position it between the two instructions, split
    // the block between the two instructions, and copy the catch handlers.
    instructions.previous();
    assert instructions.peekNext().isInvokeDirect();
    BasicBlock currentBlock = newInstance.getBlock();
    BasicBlock nextBlock = instructions.split(code, blocks);
    assert !instructions.hasNext();
    nextBlock.copyCatchHandlers(code, blocks, currentBlock, appView.options());
  }

  public Map<DexType, LambdaClass> getKnownLambdaClasses() {
    return knownLambdaClasses;
  }

  public NestedGraphLens fixup() {
    LambdaRewriterLens lens = lensBuilder.build(appView.graphLens(), appView.dexItemFactory());
    if (lens == null) {
      return null;
    }
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      EnclosingMethodAttribute enclosingMethod = clazz.getEnclosingMethodAttribute();
      if (enclosingMethod != null) {
        if (enclosingMethod.getEnclosingMethod() != null) {
          DexMethod mappedEnclosingMethod = lens.lookupMethod(enclosingMethod.getEnclosingMethod());
          if (mappedEnclosingMethod != enclosingMethod.getEnclosingMethod()) {
            clazz.setEnclosingMethodAttribute(new EnclosingMethodAttribute(mappedEnclosingMethod));
          }
        } else {
          assert enclosingMethod.getEnclosingClass() != null;
          DexType mappedEnclosingClass = lens.lookupType(enclosingMethod.getEnclosingClass());
          if (mappedEnclosingClass != enclosingMethod.getEnclosingClass()) {
            clazz.setEnclosingMethodAttribute(new EnclosingMethodAttribute(mappedEnclosingClass));
          }
        }
      }
    }
    // Return lens without method map (but still retaining originalMethodSignatures), as the
    // generated lambdas classes are generated with the an invoke to the new method, so no
    // code rewriting is required.
    return lens.withoutMethodMap();
  }

  static class LambdaRewriterLens extends NestedGraphLens {

    LambdaRewriterLens(
        Map<DexType, DexType> typeMap,
        Map<DexMethod, DexMethod> methodMap,
        Map<DexField, DexField> fieldMap,
        BiMap<DexField, DexField> originalFieldSignatures,
        BiMap<DexMethod, DexMethod> originalMethodSignatures,
        GraphLens previousLens,
        DexItemFactory dexItemFactory) {
      super(
          typeMap,
          methodMap,
          fieldMap,
          originalFieldSignatures,
          originalMethodSignatures,
          previousLens,
          dexItemFactory);
    }

    @Override
    protected boolean isLegitimateToHaveEmptyMappings() {
      return true;
    }

    private LambdaRewriterLens withoutMethodMap() {
      methodMap.clear();
      return this;
    }

    public static LambdaRewriterLens.Builder builder() {
      return new LambdaRewriterLens.Builder();
    }

    public static class Builder extends NestedGraphLens.Builder {
      public LambdaRewriterLens build(GraphLens previousLens, DexItemFactory dexItemFactory) {
        if (typeMap.isEmpty() && methodMap.isEmpty() && fieldMap.isEmpty()) {
          return null;
        }
        assert typeMap.isEmpty();
        assert fieldMap.isEmpty();
        return new LambdaRewriterLens(
            typeMap,
            methodMap,
            fieldMap,
            originalFieldSignatures,
            originalMethodSignatures,
            previousLens,
            dexItemFactory);
      }
    }
  }
}
