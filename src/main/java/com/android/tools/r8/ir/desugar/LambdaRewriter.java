// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DefaultUseRegistry;
import com.android.tools.r8.graph.DexApplication.Builder;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.TypeAnalysis;
import com.android.tools.r8.ir.analysis.type.TypeLatticeElement;
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
import com.android.tools.r8.ir.conversion.LensCodeRewriter;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackDelayed;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.DescriptorUtils;
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
import java.util.function.Consumer;

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

  private final LambdaRewriterGraphLense graphLens;
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

  // Checks if the type starts with lambda-class prefix.
  public static boolean hasLambdaClassPrefix(DexType clazz) {
    return clazz.getName().startsWith(LAMBDA_CLASS_NAME_PREFIX);
  }

  public LambdaRewriter(AppView<?> appView) {
    this.appView = appView;
    this.graphLens = new LambdaRewriterGraphLense(appView);
    this.instanceFieldName = getFactory().createString(LAMBDA_INSTANCE_FIELD_NAME);
  }

  public AppView<?> getAppView() {
    return appView;
  }

  public AppInfo getAppInfo() {
    return getAppView().appInfo();
  }

  public DexItemFactory getFactory() {
    return getAppView().dexItemFactory();
  }

  public void installGraphLens() {
    appView.setGraphLense(graphLens);
  }

  public void synthesizeLambdaClassesForWave(
      Collection<DexEncodedMethod> wave,
      IRConverter converter,
      ExecutorService executorService,
      OptimizationFeedbackDelayed feedback,
      LensCodeRewriter lensCodeRewriter)
      throws ExecutionException {
    Set<LambdaClass> synthesizedLambdaClasses = Sets.newIdentityHashSet();
    Set<DexProgramClass> synthesizedLambdaProgramClasses = Sets.newIdentityHashSet();
    for (DexEncodedMethod method : wave) {
      synthesizeLambdaClassesForMethod(
          method,
          lambdaClass -> {
            synthesizedLambdaClasses.add(lambdaClass);
            synthesizedLambdaProgramClasses.add(lambdaClass.getOrCreateLambdaClass());
          },
          lensCodeRewriter);
    }

    if (synthesizedLambdaClasses.isEmpty()) {
      return;
    }

    synthesizeAccessibilityBridgesForLambdaClasses(
        synthesizedLambdaClasses, converter, executorService, feedback, lensCodeRewriter);

    // Record that the static fields on each lambda class are only written inside the static
    // initializer of the lambdas.
    Map<DexEncodedField, Set<DexEncodedMethod>> writesWithContexts = new IdentityHashMap<>();
    for (DexProgramClass synthesizedLambdaClass : synthesizedLambdaProgramClasses) {
      DexEncodedMethod clinit = synthesizedLambdaClass.getClassInitializer();
      if (clinit != null) {
        for (DexEncodedField field : synthesizedLambdaClass.staticFields()) {
          writesWithContexts.put(field, ImmutableSet.of(clinit));
        }
      }
    }

    AppView<AppInfoWithLiveness> appViewWithLiveness = getAppView().withLiveness();
    appViewWithLiveness.setAppInfo(
        appViewWithLiveness.appInfo().withStaticFieldWrites(writesWithContexts));

    converter.optimizeSynthesizedLambdaClasses(synthesizedLambdaProgramClasses, executorService);
    feedback.updateVisibleOptimizationInfo();
  }

  private void synthesizeAccessibilityBridgesForLambdaClasses(
      Collection<LambdaClass> lambdaClasses, IRConverter converter, ExecutorService executorService)
      throws ExecutionException {
    synthesizeAccessibilityBridgesForLambdaClasses(
        lambdaClasses, converter, executorService, null, null);
  }

  private void synthesizeAccessibilityBridgesForLambdaClasses(
      Collection<LambdaClass> lambdaClasses,
      IRConverter converter,
      ExecutorService executorService,
      OptimizationFeedbackDelayed feedback,
      LensCodeRewriter lensCodeRewriter)
      throws ExecutionException {
    Set<DexEncodedMethod> nonDexAccessibilityBridges = Sets.newIdentityHashSet();
    for (LambdaClass lambdaClass : lambdaClasses) {
      // This call may cause originalMethodSignatures to be updated.
      DexEncodedMethod accessibilityBridge = lambdaClass.target.ensureAccessibilityIfNeeded();
      if (accessibilityBridge != null && !accessibilityBridge.getCode().isDexCode()) {
        nonDexAccessibilityBridges.add(accessibilityBridge);
      }
    }
    if (appView.enableWholeProgramOptimizations()) {
      if (!originalMethodSignatures.isEmpty()) {
        graphLens.addOriginalMethodSignatures(originalMethodSignatures);
        originalMethodSignatures.clear();
      }

      // Ensure that all lambda classes referenced from accessibility bridges are synthesized
      // prior to the IR processing of these accessibility bridges.
      synthesizeLambdaClassesForWave(
          nonDexAccessibilityBridges, converter, executorService, feedback, lensCodeRewriter);
    }
    if (!nonDexAccessibilityBridges.isEmpty()) {
      converter.processMethodsConcurrently(nonDexAccessibilityBridges, executorService);
    }
  }

  public void synthesizeLambdaClassesForMethod(
      DexEncodedMethod method, Consumer<LambdaClass> consumer, LensCodeRewriter lensCodeRewriter) {
    if (!method.hasCode() || method.isProcessed()) {
      // Nothing to desugar.
      return;
    }

    Code code = method.getCode();
    if (!code.isCfCode()) {
      // Nothing to desugar.
      return;
    }

    // Introduce a lambda class in AppInfo for each call site such that we do not modify the
    // application (and, in particular, the class hierarchy) during wave processing.
    code.registerCodeReferences(
        method,
        new DefaultUseRegistry(getFactory()) {

          @Override
          public void registerCallSite(DexCallSite callSite) {
            LambdaDescriptor descriptor =
                inferLambdaDescriptor(lensCodeRewriter.rewriteCallSite(callSite, method));
            if (descriptor != LambdaDescriptor.MATCH_FAILED) {
              consumer.accept(getOrCreateLambdaClass(descriptor, method.method.holder));
            }
          }
        });
  }

  /**
   * Detect and desugar lambdas and method references found in the code.
   *
   * <p>NOTE: this method can be called concurrently for several different methods.
   */
  public void desugarLambdas(DexEncodedMethod encodedMethod, IRCode code) {
    Set<Value> affectedValues = Sets.newIdentityHashSet();
    DexType currentType = encodedMethod.method.holder;
    ListIterator<BasicBlock> blocks = code.listIterator();
    while (blocks.hasNext()) {
      BasicBlock block = blocks.next();
      InstructionListIterator instructions = block.listIterator(code);
      while (instructions.hasNext()) {
        Instruction instruction = instructions.next();
        if (instruction.isInvokeCustom()) {
          InvokeCustom invoke = instruction.asInvokeCustom();
          LambdaDescriptor descriptor = inferLambdaDescriptor(invoke.getCallSite());
          if (descriptor == LambdaDescriptor.MATCH_FAILED) {
            continue;
          }

          // We have a descriptor, get the lambda class. In D8, we synthesize the lambda classes
          // during IR processing, and therefore we may need to create it now.
          LambdaClass lambdaClass =
              getAppView().enableWholeProgramOptimizations()
                  ? getKnownLambdaClass(descriptor, currentType)
                  : getOrCreateLambdaClass(descriptor, currentType);
          assert lambdaClass != null;

          // We rely on patch performing its work in a way which
          // keeps both `instructions` and `blocks` iterators in
          // valid state so that we can continue iteration.
          patchInstruction(invoke, lambdaClass, code, blocks, instructions, affectedValues);
        }
      }
    }
    if (!affectedValues.isEmpty()) {
      new TypeAnalysis(getAppView()).narrowing(affectedValues);
    }
    assert code.isConsistentSSA();
  }

  /** Remove lambda deserialization methods. */
  public boolean removeLambdaDeserializationMethods(Iterable<DexProgramClass> classes) {
    for (DexProgramClass clazz : classes) {
      // Search for a lambda deserialization method and remove it if found.
      List<DexEncodedMethod> directMethods = clazz.directMethods();
      if (directMethods != null) {
        int methodCount = directMethods.size();
        for (int i = 0; i < methodCount; i++) {
          DexEncodedMethod encoded = directMethods.get(i);
          DexMethod method = encoded.method;
          if (method.isLambdaDeserializeMethod(getFactory())) {
            assert encoded.accessFlags.isStatic();
            assert encoded.accessFlags.isSynthetic();
            clazz.removeDirectMethod(i);

            // We assume there is only one such method in the class.
            return true;
          }
        }
      }
    }
    return false;
  }

  /** Generates lambda classes and adds them to the builder. */
  public void finalizeLambdaDesugaringForD8(
      Builder<?> builder, IRConverter converter, ExecutorService executorService)
      throws ExecutionException {
    synthesizeAccessibilityBridgesForLambdaClasses(
        knownLambdaClasses.values(), converter, executorService);
    for (LambdaClass lambdaClass : knownLambdaClasses.values()) {
      DexProgramClass synthesizedClass = lambdaClass.getOrCreateLambdaClass();
      getAppInfo().addSynthesizedClass(synthesizedClass);
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

  /** Generates lambda classes and adds them to the builder. */
  public void finalizeLambdaDesugaringForR8(Builder<?> builder) {
    // Verify that all mappings have been published to the LambdaRewriterGraphLense.
    assert originalMethodSignatures.isEmpty();
    graphLens.markShouldMapInvocationType();
    for (LambdaClass lambdaClass : knownLambdaClasses.values()) {
      DexProgramClass synthesizedClass = lambdaClass.getOrCreateLambdaClass();
      getAppInfo().addSynthesizedClass(synthesizedClass);
      builder.addSynthesizedClass(synthesizedClass, lambdaClass.addToMainDexList.get());
    }
  }

  public Set<DexCallSite> getDesugaredCallSites() {
    synchronized (knownCallSites) {
      return knownCallSites.keySet();
    }
  }

  // Matches invoke-custom instruction operands to infer lambda descriptor
  // corresponding to this lambda invocation point.
  //
  // Returns the lambda descriptor or `MATCH_FAILED`.
  private LambdaDescriptor inferLambdaDescriptor(DexCallSite callSite) {
    // We check the map before and after inferring lambda descriptor to minimize time
    // spent in synchronized block. As a result we may throw away calculated descriptor
    // in rare case when another thread has same call site processed concurrently,
    // but this is a low price to pay comparing to making whole method synchronous.
    LambdaDescriptor descriptor = getKnown(knownCallSites, callSite);
    return descriptor != null
        ? descriptor
        : putIfAbsent(knownCallSites, callSite, LambdaDescriptor.infer(callSite, getAppInfo()));
  }

  private boolean isInMainDexList(DexType type) {
    return getAppInfo().isInMainDexList(type);
  }

  // Returns a lambda class corresponding to the lambda descriptor and context,
  // creates the class if it does not yet exist.
  private LambdaClass getOrCreateLambdaClass(LambdaDescriptor descriptor, DexType accessedFrom) {
    DexType lambdaClassType = LambdaClass.createLambdaClassType(this, accessedFrom, descriptor);
    // We check the map twice to to minimize time spent in synchronized block.
    LambdaClass lambdaClass = getKnown(knownLambdaClasses, lambdaClassType);
    if (lambdaClass == null) {
      lambdaClass =
          putIfAbsent(
              knownLambdaClasses,
              lambdaClassType,
              new LambdaClass(this, accessedFrom, lambdaClassType, descriptor));
      if (getAppView().options().isDesugaredLibraryCompilation()) {
        DexType rewrittenType = getAppView().rewritePrefix.rewrittenType(accessedFrom);
        if (rewrittenType == null) {
          rewrittenType =
              getAppView()
                  .options()
                  .desugaredLibraryConfiguration
                  .getEmulateLibraryInterface()
                  .get(accessedFrom);
        }
        if (rewrittenType != null) {
          addRewritingPrefix(accessedFrom, rewrittenType, lambdaClassType);
        }
      }
    }
    lambdaClass.addSynthesizedFrom(getAppView().definitionFor(accessedFrom).asProgramClass());
    if (isInMainDexList(accessedFrom)) {
      lambdaClass.addToMainDexList.set(true);
    }
    return lambdaClass;
  }

  private LambdaClass getKnownLambdaClass(LambdaDescriptor descriptor, DexType accessedFrom) {
    DexType lambdaClassType = LambdaClass.createLambdaClassType(this, accessedFrom, descriptor);
    return getKnown(knownLambdaClasses, lambdaClassType);
  }

  private void addRewritingPrefix(DexType type, DexType rewritten, DexType lambdaClassType) {
    String javaName = lambdaClassType.toString();
    String typeString = type.toString();
    String actualPrefix = typeString.substring(0, typeString.lastIndexOf('.'));
    String rewrittenString = rewritten.toString();
    String actualRewrittenPrefix = rewrittenString.substring(0, rewrittenString.lastIndexOf('.'));
    assert javaName.startsWith(actualPrefix);
    getAppView()
        .rewritePrefix
        .rewriteType(
            lambdaClassType,
            getFactory()
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
              TypeLatticeElement.fromDexType(
                  lambdaClass.type, Nullability.maybeNull(), getAppView()));
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
    lambdaInstanceValue.setTypeLattice(
        lambdaInstanceValue.getTypeLattice().asReferenceTypeLatticeElement().asDefinitelyNotNull());
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
    nextBlock.copyCatchHandlers(code, blocks, currentBlock, getAppView().options());
  }
}
