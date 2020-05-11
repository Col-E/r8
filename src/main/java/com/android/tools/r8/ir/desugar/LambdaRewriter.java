// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexApplication.Builder;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.TypeAnalysis;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeCustom;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.NewInstance;
import com.android.tools.r8.ir.code.StaticGet;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.collections.SortedProgramMethodSet;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
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

  final BiMap<DexMethod, DexMethod> originalMethodSignatures = HashBiMap.create();

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

  private void synthesizeAccessibilityBridgesForLambdaClassesD8(
      Collection<LambdaClass> lambdaClasses, IRConverter converter, ExecutorService executorService)
      throws ExecutionException {
    SortedProgramMethodSet nonDexAccessibilityBridges = SortedProgramMethodSet.create();
    for (LambdaClass lambdaClass : lambdaClasses) {
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
  public void desugarLambdas(IRCode code) {
    Set<Value> affectedValues = Sets.newIdentityHashSet();
    ProgramMethod context = code.context();
    ListIterator<BasicBlock> blocks = code.listIterator();
    while (blocks.hasNext()) {
      BasicBlock block = blocks.next();
      InstructionListIterator instructions = block.listIterator(code);
      while (instructions.hasNext()) {
        Instruction instruction = instructions.next();
        if (instruction.isInvokeCustom()) {
          InvokeCustom invoke = instruction.asInvokeCustom();
          LambdaDescriptor descriptor = inferLambdaDescriptor(invoke.getCallSite(), context);
          if (descriptor == LambdaDescriptor.MATCH_FAILED) {
            continue;
          }

          // We have a descriptor, get the lambda class. In D8, we synthesize the lambda classes
          // during IR processing, and therefore we may need to create it now.
          LambdaClass lambdaClass =
              appView.enableWholeProgramOptimizations()
                  ? getKnownLambdaClass(descriptor, context)
                  : getOrCreateLambdaClass(descriptor, context);
          assert lambdaClass != null;

          // We rely on patch performing its work in a way which
          // keeps both `instructions` and `blocks` iterators in
          // valid state so that we can continue iteration.
          patchInstruction(invoke, lambdaClass, code, blocks, instructions, affectedValues);
        }
      }
    }
    if (!affectedValues.isEmpty()) {
      new TypeAnalysis(appView).narrowing(affectedValues);
    }
    assert code.isConsistentSSA();
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
      appView.appInfo().addSynthesizedClass(synthesizedClass);
      builder.addSynthesizedClass(synthesizedClass, lambdaClass.addToMainDexList.get());
    }
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

  private boolean isInMainDexList(DexType type) {
    return appView.appInfo().isInMainDexList(type);
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
    if (isInMainDexList(accessedFrom.getHolderType())) {
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
}
