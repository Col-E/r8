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
import com.android.tools.r8.graph.DexCallSite;
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
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.synthesis.SyntheticNaming;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.collections.BidirectionalManyToOneRepresentativeMap;
import com.android.tools.r8.utils.collections.BidirectionalOneToOneMap;
import com.android.tools.r8.utils.collections.SortedProgramMethodSet;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
  public static final String LAMBDA_GROUP_CLASS_NAME_PREFIX = "-$$LambdaGroup$";
  static final String EXPECTED_LAMBDA_METHOD_PREFIX = "lambda$";
  public static final String LAMBDA_INSTANCE_FIELD_NAME = "INSTANCE";

  private final AppView<?> appView;

  final DexString instanceFieldName;

  private final LambdaRewriterLens.Builder lensBuilder = LambdaRewriterLens.builder();
  private final Set<DexMethod> forcefullyMovedMethods = Sets.newIdentityHashSet();

  // Maps lambda class type into lambda class representation.
  // NOTE: synchronize concurrent access on `knownLambdaClasses`.
  private final List<LambdaClass> knownLambdaClasses = new ArrayList<>();

  private final Map<DexMethod, Integer> methodIds = new ConcurrentHashMap<>();

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
    sortedLambdaClasses.sort((x, y) -> x.type.compareTo(y.type));
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
        method,
        callsite -> {
          LambdaDescriptor descriptor = LambdaDescriptor.tryInfer(callsite, appInfo, method);
          if (descriptor == null) {
            return null;
          }
          return createLambdaClass(descriptor, method);
        });
  }

  // Same as above, but where lambdas are always known to exist for the call sites.
  public static int desugarLambdas(
      ProgramMethod method, Function<DexCallSite, LambdaClass> callSites) {
    CfCode code = method.getDefinition().getCode().asCfCode();
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
  public void finalizeLambdaDesugaringForD8(IRConverter converter, ExecutorService executorService)
      throws ExecutionException {
    synthesizeAccessibilityBridgesForLambdaClassesD8(
        knownLambdaClasses, converter, executorService);
    fixup();
    optimizeSynthesizedClasses(converter, executorService);
  }

  private void optimizeSynthesizedClasses(IRConverter converter, ExecutorService executorService)
      throws ExecutionException {
    converter.optimizeSynthesizedClasses(
        knownLambdaClasses.stream()
            .map(LambdaClass::getLambdaProgramClass)
            .collect(ImmutableSet.toImmutableSet()),
        executorService);
  }

  // Creates a lambda class corresponding to the lambda descriptor and context.
  public LambdaClass createLambdaClass(LambdaDescriptor descriptor, ProgramMethod accessedFrom) {
    int nextId =
        methodIds.compute(
            accessedFrom.getReference(), (method, value) -> value == null ? 0 : value + 1);
    Box<LambdaClass> box = new Box<>();
    DexProgramClass clazz =
        appView
            .getSyntheticItems()
            .createClass(
                SyntheticNaming.SyntheticKind.LAMBDA,
                accessedFrom.getHolder(),
                appView.dexItemFactory(),
                // TODO(b/172194101): Make this part of a unique context construction.
                () -> {
                  Hasher hasher = Hashing.sha256().newHasher();
                  accessedFrom.getReference().hash(hasher);
                  return "$" + hasher.hash().toString() + "$" + nextId;
                },
                builder ->
                    box.set(new LambdaClass(builder, appView, this, accessedFrom, descriptor)));
    // Immediately set the actual program class on the lambda.
    LambdaClass lambdaClass = box.get();
    lambdaClass.setClass(clazz);
    synchronized (knownLambdaClasses) {
      knownLambdaClasses.add(lambdaClass);
    }
    return lambdaClass;
  }

  public Collection<LambdaClass> getKnownLambdaClasses() {
    return Collections.unmodifiableList(knownLambdaClasses);
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
        BidirectionalManyToOneRepresentativeMap<DexField, DexField> fieldMap,
        BidirectionalOneToOneMap<DexMethod, DexMethod> originalMethodSignatures,
        GraphLens previousLens,
        DexItemFactory dexItemFactory) {
      super(
          typeMap,
          methodMap,
          fieldMap,
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
            originalMethodSignatures,
            previousLens,
            dexItemFactory);
      }
    }
  }
}
