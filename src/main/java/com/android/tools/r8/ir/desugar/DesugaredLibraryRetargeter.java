// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
//  for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import static com.android.tools.r8.ir.desugar.DesugaredLibraryRetargeter.InvokeRetargetingResult.NO_REWRITING;

import com.android.tools.r8.ProgramResource.Kind;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ClassAccessFlags;
import com.android.tools.r8.graph.ClasspathOrLibraryClass;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexLibraryClass;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.DirectMappedDexApplication;
import com.android.tools.r8.graph.EnclosingMethodAttribute;
import com.android.tools.r8.graph.GenericSignature.ClassSignature;
import com.android.tools.r8.graph.GenericSignature.ClassTypeSignature;
import com.android.tools.r8.graph.GenericSignature.MethodTypeSignature;
import com.android.tools.r8.graph.InnerClassAttribute;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.NestHostClassAttribute;
import com.android.tools.r8.graph.NestMemberClassAttribute;
import com.android.tools.r8.graph.ParameterAnnotationsList;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.ResolutionResult;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.ir.synthetic.EmulateInterfaceSyntheticCfCodeProvider;
import com.android.tools.r8.origin.SynthesizedOrigin;
import com.android.tools.r8.synthesis.SyntheticClassBuilder;
import com.android.tools.r8.synthesis.SyntheticNaming.SyntheticKind;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.WorkList;
import com.android.tools.r8.utils.collections.DexClassAndMethodSet;
import com.android.tools.r8.utils.collections.SortedProgramMethodSet;
import com.google.common.collect.Maps;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Function;
import org.objectweb.asm.Opcodes;

public class DesugaredLibraryRetargeter implements CfInstructionDesugaring {

  private final AppView<?> appView;
  private final Map<DexMethod, DexMethod> retargetLibraryMember = new IdentityHashMap<>();
  // Map nonFinalRewrite hold a methodName -> method mapping for methods which are rewritten while
  // the holder is non final. In this case d8 needs to force resolution of given methods to see if
  // the invoke needs to be rewritten.
  private final Map<DexString, List<DexMethod>> nonFinalHolderRewrites = new IdentityHashMap<>();
  // Non final virtual library methods requiring generation of emulated dispatch.
  private final DexClassAndMethodSet emulatedDispatchMethods = DexClassAndMethodSet.create();

  private final SortedProgramMethodSet forwardingMethods = SortedProgramMethodSet.create();

  public DesugaredLibraryRetargeter(AppView<?> appView) {
    this.appView = appView;
    if (appView.options().desugaredLibraryConfiguration.getRetargetCoreLibMember().isEmpty()) {
      return;
    }
    new RetargetingSetup().setUpRetargeting();
  }

  public static void checkForAssumedLibraryTypes(AppView<?> appView) {
    Map<DexString, Map<DexType, DexType>> retargetCoreLibMember =
        appView.options().desugaredLibraryConfiguration.getRetargetCoreLibMember();
    for (DexString methodName : retargetCoreLibMember.keySet()) {
      for (DexType inType : retargetCoreLibMember.get(methodName).keySet()) {
        DexClass typeClass = appView.definitionFor(inType);
        if (typeClass == null) {
          warnMissingRetargetCoreLibraryMember(inType, appView);
        }
      }
    }
  }

  public static void amendLibraryWithRetargetedMembers(AppView<AppInfoWithClassHierarchy> appView) {
    Map<DexString, Map<DexType, DexType>> retargetCoreLibMember =
        appView.options().desugaredLibraryConfiguration.getRetargetCoreLibMember();
    Map<DexType, DexLibraryClass> synthesizedLibraryClasses =
        synthesizeLibraryClassesForRetargetedMembers(appView, retargetCoreLibMember);
    Map<DexLibraryClass, Set<DexEncodedMethod>> synthesizedLibraryMethods =
        synthesizedMembersForRetargetClasses(
            appView, retargetCoreLibMember, synthesizedLibraryClasses);
    synthesizedLibraryMethods.forEach(DexLibraryClass::addDirectMethods);
    DirectMappedDexApplication newApplication =
        appView
            .appInfo()
            .app()
            .asDirect()
            .builder()
            .addLibraryClasses(synthesizedLibraryClasses.values())
            .build();
    appView.setAppInfo(appView.appInfo().rebuildWithClassHierarchy(app -> newApplication));
  }

  private static Map<DexType, DexLibraryClass> synthesizeLibraryClassesForRetargetedMembers(
      AppView<AppInfoWithClassHierarchy> appView,
      Map<DexString, Map<DexType, DexType>> retargetCoreLibMember) {
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    Map<DexType, DexLibraryClass> synthesizedLibraryClasses = new LinkedHashMap<>();
    for (Map<DexType, DexType> oldToNewTypeMap : retargetCoreLibMember.values()) {
      for (DexType newType : oldToNewTypeMap.values()) {
        if (appView.definitionFor(newType) == null) {
          synthesizedLibraryClasses.computeIfAbsent(
              newType,
              type ->
                  // Synthesize a library class with the given name. Note that this is assuming that
                  // the library class inherits directly from java.lang.Object, does not implement
                  // any interfaces, etc.
                  new DexLibraryClass(
                      type,
                      Kind.CF,
                      new SynthesizedOrigin(
                          "Desugared library retargeter", DesugaredLibraryRetargeter.class),
                      ClassAccessFlags.fromCfAccessFlags(Constants.ACC_PUBLIC),
                      dexItemFactory.objectType,
                      DexTypeList.empty(),
                      dexItemFactory.createString("DesugaredLibraryRetargeter"),
                      NestHostClassAttribute.none(),
                      NestMemberClassAttribute.emptyList(),
                      EnclosingMethodAttribute.none(),
                      InnerClassAttribute.emptyList(),
                      ClassSignature.noSignature(),
                      DexAnnotationSet.empty(),
                      DexEncodedField.EMPTY_ARRAY,
                      DexEncodedField.EMPTY_ARRAY,
                      DexEncodedMethod.EMPTY_ARRAY,
                      DexEncodedMethod.EMPTY_ARRAY,
                      dexItemFactory.getSkipNameValidationForTesting()));
        }
      }
    }
    return synthesizedLibraryClasses;
  }

  private static Map<DexLibraryClass, Set<DexEncodedMethod>> synthesizedMembersForRetargetClasses(
      AppView<AppInfoWithClassHierarchy> appView,
      Map<DexString, Map<DexType, DexType>> retargetCoreLibMember,
      Map<DexType, DexLibraryClass> synthesizedLibraryClasses) {
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    Map<DexLibraryClass, Set<DexEncodedMethod>> synthesizedMembers = new IdentityHashMap<>();
    for (Entry<DexString, Map<DexType, DexType>> entry : retargetCoreLibMember.entrySet()) {
      DexString methodName = entry.getKey();
      Map<DexType, DexType> types = entry.getValue();
      types.forEach(
          (oldType, newType) -> {
            DexClass oldClass = appView.definitionFor(oldType);
            DexLibraryClass newClass = synthesizedLibraryClasses.get(newType);
            if (oldClass == null || newClass == null) {
              return;
            }
            for (DexEncodedMethod method :
                oldClass.methods(method -> method.getName() == methodName)) {
              DexMethod retargetMethod = method.getReference().withHolder(newType, dexItemFactory);
              if (!method.isStatic()) {
                retargetMethod = retargetMethod.withExtraArgumentPrepended(oldType, dexItemFactory);
              }
              synthesizedMembers
                  .computeIfAbsent(
                      newClass,
                      ignore -> new TreeSet<>(Comparator.comparing(DexEncodedMethod::getReference)))
                  .add(
                      new DexEncodedMethod(
                          retargetMethod,
                          MethodAccessFlags.fromCfAccessFlags(
                              Constants.ACC_PUBLIC | Constants.ACC_STATIC, false),
                          MethodTypeSignature.noSignature(),
                          DexAnnotationSet.empty(),
                          ParameterAnnotationsList.empty(),
                          null,
                          true));
            }
          });
    }
    return synthesizedMembers;
  }

  private static void warnMissingRetargetCoreLibraryMember(DexType type, AppView<?> appView) {
    StringDiagnostic warning =
        new StringDiagnostic(
            "Cannot retarget core library member "
                + type.getName()
                + " because the class is missing.");
    appView.options().reporter.warning(warning);
  }

  // Used by the ListOfBackportedMethods utility.
  void visit(Consumer<DexMethod> consumer) {
    retargetLibraryMember.keySet().forEach(consumer);
  }

  @Override
  public Collection<CfInstruction> desugarInstruction(
      CfInstruction instruction,
      FreshLocalProvider freshLocalProvider,
      LocalStackAllocator localStackAllocator,
      CfInstructionDesugaringEventConsumer eventConsumer,
      ProgramMethod context,
      MethodProcessingContext methodProcessingContext,
      DexItemFactory dexItemFactory) {
    InvokeRetargetingResult invokeRetargetingResult = computeNewInvokeTarget(instruction, context);

    if (!invokeRetargetingResult.hasNewInvokeTarget()) {
      return null;
    }

    DexMethod newInvokeTarget = invokeRetargetingResult.getNewInvokeTarget(eventConsumer);
    return Collections.singletonList(
        new CfInvoke(Opcodes.INVOKESTATIC, newInvokeTarget, instruction.asInvoke().isInterface()));
  }

  @Override
  public boolean needsDesugaring(CfInstruction instruction, ProgramMethod context) {
    return computeNewInvokeTarget(instruction, context).hasNewInvokeTarget();
  }

  @Deprecated // Use Cf to Cf desugaring instead.
  public void desugar(IRCode code) {
    if (retargetLibraryMember.isEmpty()) {
      return;
    }

    InstructionListIterator iterator = code.instructionListIterator();
    while (iterator.hasNext()) {
      Instruction instruction = iterator.next();
      if (!instruction.isInvokeMethod()) {
        continue;
      }

      InvokeMethod invoke = instruction.asInvokeMethod();
      DexMethod invokedMethod = invoke.getInvokedMethod();
      boolean isInterface = invoke.getInterfaceBit();

      InvokeRetargetingResult invokeRetargetingResult =
          computeNewInvokeTarget(
              invokedMethod, isInterface, invoke.isInvokeSuper(), code.context());
      if (invokeRetargetingResult.hasNewInvokeTarget()) {
        DexMethod newInvokeTarget = invokeRetargetingResult.getNewInvokeTarget(null);
        iterator.replaceCurrentInstruction(
            new InvokeStatic(newInvokeTarget, invoke.outValue(), invoke.inValues()));
      }
    }
  }

  static class InvokeRetargetingResult {

    static InvokeRetargetingResult NO_REWRITING =
        new InvokeRetargetingResult(false, ignored -> null);

    private final boolean hasNewInvokeTarget;
    private final Function<DesugaredLibraryRetargeterEventConsumer, DexMethod>
        newInvokeTargetSupplier;

    static InvokeRetargetingResult createInvokeRetargetingResult(DexMethod retarget) {
      if (retarget == null) {
        return NO_REWRITING;
      }
      return new InvokeRetargetingResult(true, ignored -> retarget);
    }

    private InvokeRetargetingResult(
        boolean hasNewInvokeTarget,
        Function<DesugaredLibraryRetargeterEventConsumer, DexMethod> newInvokeTargetSupplier) {
      this.hasNewInvokeTarget = hasNewInvokeTarget;
      this.newInvokeTargetSupplier = newInvokeTargetSupplier;
    }

    public boolean hasNewInvokeTarget() {
      return hasNewInvokeTarget;
    }

    public DexMethod getNewInvokeTarget(DesugaredLibraryRetargeterEventConsumer eventConsumer) {
      assert hasNewInvokeTarget();
      return newInvokeTargetSupplier.apply(eventConsumer);
    }
  }

  public boolean hasNewInvokeTarget(
      DexMethod invokedMethod, boolean isInterface, boolean isInvokeSuper, ProgramMethod context) {
    return computeNewInvokeTarget(invokedMethod, isInterface, isInvokeSuper, context)
        .hasNewInvokeTarget();
  }

  private InvokeRetargetingResult computeNewInvokeTarget(
      CfInstruction instruction, ProgramMethod context) {
    if (retargetLibraryMember.isEmpty() || !instruction.isInvoke()) {
      return NO_REWRITING;
    }
    CfInvoke cfInvoke = instruction.asInvoke();
    return computeNewInvokeTarget(
        cfInvoke.getMethod(),
        cfInvoke.isInterface(),
        cfInvoke.isInvokeSuper(context.getHolderType()),
        context);
  }

  private InvokeRetargetingResult computeNewInvokeTarget(
      DexMethod invokedMethod, boolean isInterface, boolean isInvokeSuper, ProgramMethod context) {
    InvokeRetargetingResult retarget = computeRetargetedMethod(invokedMethod, isInterface);
    if (!retarget.hasNewInvokeTarget()) {
      return NO_REWRITING;
    }
    if (isInvokeSuper && matchesNonFinalHolderRewrite(invokedMethod)) {
      DexClassAndMethod superTarget =
          appView.appInfoForDesugaring().lookupSuperTarget(invokedMethod, context);
      // Final methods can be rewritten as a normal invoke.
      if (superTarget != null && !superTarget.getAccessFlags().isFinal()) {
        return InvokeRetargetingResult.createInvokeRetargetingResult(
            appView.options().desugaredLibraryConfiguration.retargetMethod(superTarget, appView));
      }
    }
    return retarget;
  }

  private InvokeRetargetingResult computeRetargetedMethod(
      DexMethod invokedMethod, boolean isInterface) {
    InvokeRetargetingResult invokeRetargetingResult = computeRetargetLibraryMember(invokedMethod);
    if (!invokeRetargetingResult.hasNewInvokeTarget()) {
      if (!matchesNonFinalHolderRewrite(invokedMethod)) {
        return NO_REWRITING;
      }
      // We need to force resolution, even on d8, to know if the invoke has to be rewritten.
      ResolutionResult resolutionResult =
          appView.appInfoForDesugaring().resolveMethod(invokedMethod, isInterface);
      if (resolutionResult.isFailedResolution()) {
        return NO_REWRITING;
      }
      DexEncodedMethod singleTarget = resolutionResult.getSingleTarget();
      assert singleTarget != null;
      invokeRetargetingResult = computeRetargetLibraryMember(singleTarget.getReference());
    }
    return invokeRetargetingResult;
  }

  private InvokeRetargetingResult computeRetargetLibraryMember(DexMethod method) {
    Map<DexType, DexType> backportCoreLibraryMembers =
        appView.options().desugaredLibraryConfiguration.getBackportCoreLibraryMember();
    if (backportCoreLibraryMembers.containsKey(method.holder)) {
      DexType newHolder = backportCoreLibraryMembers.get(method.holder);
      DexMethod newMethod =
          appView.dexItemFactory().createMethod(newHolder, method.proto, method.name);
      return InvokeRetargetingResult.createInvokeRetargetingResult(newMethod);
    }
    DexClassAndMethod emulatedMethod = emulatedDispatchMethods.get(method);
    if (emulatedMethod != null) {
      assert !emulatedMethod.getAccessFlags().isStatic();
      return new InvokeRetargetingResult(
          true,
          eventConsumer -> {
            DexType newHolder =
                ensureEmulatedHolderDispatchMethod(emulatedMethod, eventConsumer).type;
            return computeRetargetMethod(
                method, emulatedMethod.getAccessFlags().isStatic(), newHolder);
          });
    }
    return InvokeRetargetingResult.createInvokeRetargetingResult(retargetLibraryMember.get(method));
  }

  private boolean matchesNonFinalHolderRewrite(DexMethod method) {
    List<DexMethod> dexMethods = nonFinalHolderRewrites.get(method.name);
    if (dexMethods == null) {
      return false;
    }
    for (DexMethod dexMethod : dexMethods) {
      if (method.match(dexMethod)) {
        return true;
      }
    }
    return false;
  }

  DexMethod computeRetargetMethod(DexMethod method, boolean isStatic, DexType newHolder) {
    DexItemFactory factory = appView.dexItemFactory();
    DexProto newProto = isStatic ? method.getProto() : factory.prependHolderToProto(method);
    return factory.createMethod(newHolder, newProto, method.getName());
  }

  private class RetargetingSetup {

    private void setUpRetargeting() {
      DesugaredLibraryConfiguration desugaredLibraryConfiguration =
          appView.options().desugaredLibraryConfiguration;
      Map<DexString, Map<DexType, DexType>> retargetCoreLibMember =
          desugaredLibraryConfiguration.getRetargetCoreLibMember();
      for (DexString methodName : retargetCoreLibMember.keySet()) {
        for (DexType inType : retargetCoreLibMember.get(methodName).keySet()) {
          DexClass typeClass = appView.definitionFor(inType);
          if (typeClass != null) {
            DexType newHolder = retargetCoreLibMember.get(methodName).get(inType);
            List<DexClassAndMethod> found = findMethodsWithName(methodName, typeClass);
            for (DexClassAndMethod method : found) {
              boolean emulatedDispatch = false;
              DexMethod methodReference = method.getReference();
              if (!typeClass.isFinal()) {
                nonFinalHolderRewrites.putIfAbsent(method.getName(), new ArrayList<>());
                nonFinalHolderRewrites.get(method.getName()).add(methodReference);
                if (!method.getAccessFlags().isStatic()) {
                  if (isEmulatedInterfaceDispatch(method)) {
                    // In this case interface method rewriter takes care of it.
                    continue;
                  } else if (!method.getAccessFlags().isFinal()) {
                    // Virtual rewrites require emulated dispatch for inheritance.
                    // The call is rewritten to the dispatch holder class instead.
                    emulatedDispatchMethods.add(method);
                    emulatedDispatch = true;
                  }
                }
              }
              if (!emulatedDispatch) {
                retargetLibraryMember.put(
                    methodReference,
                    computeRetargetMethod(
                        methodReference, method.getAccessFlags().isStatic(), newHolder));
              }
            }
          }
        }
      }
      if (desugaredLibraryConfiguration.isLibraryCompilation()) {
        // TODO(b/177977763): This is only a workaround rewriting invokes of j.u.Arrays.deepEquals0
        // to j.u.DesugarArrays.deepEquals0.
        DexItemFactory itemFactory = appView.options().dexItemFactory();
        DexString name = itemFactory.createString("deepEquals0");
        DexProto proto =
            itemFactory.createProto(
                itemFactory.booleanType, itemFactory.objectType, itemFactory.objectType);
        DexMethod source =
            itemFactory.createMethod(
                itemFactory.createType(itemFactory.arraysDescriptor), proto, name);
        DexMethod target =
            computeRetargetMethod(
                source, true, itemFactory.createType("Ljava/util/DesugarArrays;"));
        retargetLibraryMember.put(source, target);

        // TODO(b/181629049): This is only a workaround rewriting invokes of
        //  j.u.TimeZone.getTimeZone taking a java.time.ZoneId.
        name = itemFactory.createString("getTimeZone");
        proto =
            itemFactory.createProto(
                itemFactory.createType("Ljava/util/TimeZone;"),
                itemFactory.createType("Ljava/time/ZoneId;"));
        source =
            itemFactory.createMethod(itemFactory.createType("Ljava/util/TimeZone;"), proto, name);
        target =
            computeRetargetMethod(
                source, true, itemFactory.createType("Ljava/util/DesugarTimeZone;"));
        retargetLibraryMember.put(source, target);
      }
    }

    private boolean isEmulatedInterfaceDispatch(DexClassAndMethod method) {
      // Answers true if this method is already managed through emulated interface dispatch.
      Map<DexType, DexType> emulateLibraryInterface =
          appView.options().desugaredLibraryConfiguration.getEmulateLibraryInterface();
      if (emulateLibraryInterface.isEmpty()) {
        return false;
      }
      DexMethod methodToFind = method.getReference();

      // Look-up all superclass and interfaces, if an emulated interface is found, and it implements
      // the method, answers true.
      WorkList<DexClass> worklist = WorkList.newIdentityWorkList(method.getHolder());
      while (worklist.hasNext()) {
        DexClass clazz = worklist.next();
        if (clazz.isInterface()
            && emulateLibraryInterface.containsKey(clazz.getType())
            && clazz.lookupMethod(methodToFind) != null) {
          return true;
        }
        // All super types are library class, or we are doing L8 compilation.
        clazz.forEachImmediateSupertype(
            superType -> {
              DexClass superClass = appView.definitionFor(superType);
              if (superClass != null) {
                worklist.addIfNotSeen(superClass);
              }
            });
      }
      return false;
    }

    private List<DexClassAndMethod> findMethodsWithName(DexString methodName, DexClass clazz) {
      List<DexClassAndMethod> found = new ArrayList<>();
      clazz.forEachClassMethodMatching(
          definition -> definition.getName() == methodName, found::add);
      assert !found.isEmpty() : "Should have found a method (library specifications).";
      return found;
    }
  }

  public void finalizeDesugaring(DesugaredLibraryRetargeterEventConsumer eventConsumer) {
    new EmulatedDispatchTreeFixer().fixApp(eventConsumer);
  }

  private void rewriteType(DexType type) {
    String newName =
        appView.options().desugaredLibraryConfiguration.convertJavaNameToDesugaredLibrary(type);
    DexType newType =
        appView.dexItemFactory().createType(DescriptorUtils.javaTypeToDescriptor(newName));
    appView.rewritePrefix.rewriteType(type, newType);
  }

  public DexClass ensureEmulatedHolderDispatchMethod(
      DexClassAndMethod emulatedDispatchMethod,
      DesugaredLibraryRetargeterEventConsumer eventConsumer) {
    assert eventConsumer != null || appView.enableWholeProgramOptimizations();
    DexClass interfaceClass =
        ensureEmulatedInterfaceDispatchMethod(emulatedDispatchMethod, eventConsumer);
    DexMethod itfMethod =
        interfaceClass.lookupMethod(emulatedDispatchMethod.getReference()).getReference();
    DexClass holderDispatch;
    if (appView.options().isDesugaredLibraryCompilation()) {
      holderDispatch =
          appView
              .getSyntheticItems()
              .ensureFixedClass(
                  SyntheticKind.RETARGET_CLASS,
                  emulatedDispatchMethod.getHolder(),
                  appView,
                  classBuilder ->
                      buildHolderDispatchMethod(classBuilder, emulatedDispatchMethod, itfMethod),
                  clazz -> {
                    if (eventConsumer != null) {
                      eventConsumer.acceptDesugaredLibraryRetargeterDispatchProgramClass(clazz);
                    }
                  });
    } else {
      ClasspathOrLibraryClass context =
          emulatedDispatchMethod.getHolder().asClasspathOrLibraryClass();
      assert context != null;
      holderDispatch =
          appView
              .getSyntheticItems()
              .ensureFixedClasspathClass(
                  SyntheticKind.RETARGET_CLASS,
                  context,
                  appView,
                  classBuilder ->
                      buildHolderDispatchMethod(classBuilder, emulatedDispatchMethod, itfMethod),
                  clazz -> {
                    if (eventConsumer != null) {
                      eventConsumer.acceptDesugaredLibraryRetargeterDispatchClasspathClass(clazz);
                    }
                  });
    }
    rewriteType(holderDispatch.type);
    return holderDispatch;
  }

  public DexClass ensureEmulatedInterfaceDispatchMethod(
      DexClassAndMethod emulatedDispatchMethod,
      DesugaredLibraryRetargeterEventConsumer eventConsumer) {
    assert eventConsumer != null || appView.enableWholeProgramOptimizations();
    DexClass interfaceDispatch;
    if (appView.options().isDesugaredLibraryCompilation()) {
      interfaceDispatch =
          appView
              .getSyntheticItems()
              .ensureFixedClass(
                  SyntheticKind.RETARGET_INTERFACE,
                  emulatedDispatchMethod.getHolder(),
                  appView,
                  classBuilder ->
                      buildInterfaceDispatchMethod(classBuilder, emulatedDispatchMethod),
                  clazz -> {
                    if (eventConsumer != null) {
                      eventConsumer.acceptDesugaredLibraryRetargeterDispatchProgramClass(clazz);
                    }
                  });
    } else {
      ClasspathOrLibraryClass context =
          emulatedDispatchMethod.getHolder().asClasspathOrLibraryClass();
      assert context != null;
      interfaceDispatch =
          appView
              .getSyntheticItems()
              .ensureFixedClasspathClass(
                  SyntheticKind.RETARGET_INTERFACE,
                  context,
                  appView,
                  classBuilder ->
                      buildInterfaceDispatchMethod(classBuilder, emulatedDispatchMethod),
                  clazz -> {
                    if (eventConsumer != null) {
                      eventConsumer.acceptDesugaredLibraryRetargeterDispatchClasspathClass(clazz);
                    }
                  });
    }
    rewriteType(interfaceDispatch.type);
    return interfaceDispatch;
  }

  private void buildInterfaceDispatchMethod(
      SyntheticClassBuilder<?, ?> classBuilder, DexClassAndMethod emulatedDispatchMethod) {
    classBuilder
        .setInterface()
        .addMethod(
            methodBuilder -> {
              MethodAccessFlags flags =
                  MethodAccessFlags.fromSharedAccessFlags(
                      Constants.ACC_PUBLIC | Constants.ACC_ABSTRACT | Constants.ACC_SYNTHETIC,
                      false);
              methodBuilder
                  .setName(emulatedDispatchMethod.getName())
                  .setProto(emulatedDispatchMethod.getProto())
                  .setAccessFlags(flags);
            });
  }

  private <SCB extends SyntheticClassBuilder<?, ?>> void buildHolderDispatchMethod(
      SCB classBuilder, DexClassAndMethod emulatedDispatchMethod, DexMethod itfMethod) {
    classBuilder.addMethod(
        methodBuilder -> {
          DexMethod desugarMethod =
              appView
                  .options()
                  .desugaredLibraryConfiguration
                  .retargetMethod(emulatedDispatchMethod, appView);
          assert desugarMethod
              != null; // This method is reached only for retarget core lib members.
          methodBuilder
              .setName(emulatedDispatchMethod.getName())
              .setProto(desugarMethod.proto)
              .setAccessFlags(MethodAccessFlags.createPublicStaticSynthetic())
              .setCode(
                  methodSig ->
                      new EmulateInterfaceSyntheticCfCodeProvider(
                              emulatedDispatchMethod.getHolderType(),
                              desugarMethod,
                              itfMethod,
                              Collections.emptyList(),
                              appView)
                          .generateCfCode());
        });
  }

  @Deprecated // Use Cf to Cf desugaring.
  public void synthesizeRetargetClasses(IRConverter converter, ExecutorService executorService)
      throws ExecutionException {
    assert appView.enableWholeProgramOptimizations();
    new EmulatedDispatchTreeFixer().fixApp(null);
    converter.processMethodsConcurrently(forwardingMethods, executorService);
  }

  // The rewrite of virtual calls requires to go through emulate dispatch. This class is responsible
  // for inserting interfaces on library boundaries and forwarding methods in the program, and to
  // synthesize the interfaces and emulated dispatch classes in the desugared library.
  class EmulatedDispatchTreeFixer {

    void fixApp(DesugaredLibraryRetargeterEventConsumer eventConsumer) {
      if (appView.options().isDesugaredLibraryCompilation()) {
        synthesizeEmulatedDispatchMethods(eventConsumer);
      } else {
        addInterfacesAndForwardingMethods(eventConsumer);
      }
    }

    private void addInterfacesAndForwardingMethods(
        DesugaredLibraryRetargeterEventConsumer eventConsumer) {
      assert !appView.options().isDesugaredLibraryCompilation();
      Map<DexType, List<DexClassAndMethod>> map = Maps.newIdentityHashMap();
      for (DexClassAndMethod emulatedDispatchMethod : emulatedDispatchMethods) {
        map.putIfAbsent(emulatedDispatchMethod.getHolderType(), new ArrayList<>(1));
        map.get(emulatedDispatchMethod.getHolderType()).add(emulatedDispatchMethod);
      }
      for (DexProgramClass clazz : appView.appInfo().classes()) {
        if (clazz.superType == null) {
          assert clazz.type == appView.dexItemFactory().objectType : clazz.type.toSourceString();
          continue;
        }
        DexClass superclass = appView.definitionFor(clazz.superType);
        // Only performs computation if superclass is a library class, but not object to filter out
        // the most common case.
        if (superclass != null
            && superclass.isLibraryClass()
            && superclass.type != appView.dexItemFactory().objectType) {
          map.forEach(
              (type, methods) -> {
                if (inherit(superclass.asLibraryClass(), type, emulatedDispatchMethods)) {
                  addInterfacesAndForwardingMethods(eventConsumer, clazz, methods);
                }
              });
        }
      }
    }

    private boolean inherit(
        DexLibraryClass clazz, DexType typeToInherit, DexClassAndMethodSet retarget) {
      DexLibraryClass current = clazz;
      while (current.type != appView.dexItemFactory().objectType) {
        if (current.type == typeToInherit) {
          return true;
        }
        DexClass dexClass = appView.definitionFor(current.superType);
        if (dexClass == null || dexClass.isClasspathClass()) {
          reportInvalidLibrarySupertype(current, retarget);
          return false;
        } else if (dexClass.isProgramClass()) {
          // If dexClass is a program class, then it is already correctly desugared.
          return false;
        }
        current = dexClass.asLibraryClass();
      }
      return false;
    }

    private void addInterfacesAndForwardingMethods(
        DesugaredLibraryRetargeterEventConsumer eventConsumer,
        DexProgramClass clazz,
        List<DexClassAndMethod> methods) {
      // DesugaredLibraryRetargeter emulate dispatch: insertion of a marker interface & forwarding
      // methods.
      // We cannot use the ClassProcessor since this applies up to 26, while the ClassProcessor
      // applies up to 24.
      for (DexClassAndMethod method : methods) {
        DexClass dexClass = ensureEmulatedInterfaceDispatchMethod(method, eventConsumer);
        if (clazz.interfaces.contains(dexClass.type)) {
          // The class has already been desugared.
          continue;
        }
        clazz.addExtraInterfaces(Collections.singletonList(new ClassTypeSignature(dexClass.type)));
        if (clazz.lookupVirtualMethod(method.getReference()) == null) {
          DexEncodedMethod newMethod = createForwardingMethod(method, clazz);
          clazz.addVirtualMethod(newMethod);
          if (eventConsumer != null) {
            eventConsumer.acceptForwardingMethod(new ProgramMethod(clazz, newMethod));
          } else {
            assert appView.enableWholeProgramOptimizations();
            forwardingMethods.add(new ProgramMethod(clazz, newMethod));
          }
        }
      }
    }

    private DexEncodedMethod createForwardingMethod(DexClassAndMethod target, DexClass clazz) {
      // NOTE: Never add a forwarding method to methods of classes unknown or coming from
      // android.jar
      // even if this results in invalid code, these classes are never desugared.
      // In desugared library, emulated interface methods can be overridden by retarget lib members.
      DexMethod forwardMethod =
          appView.options().desugaredLibraryConfiguration.retargetMethod(target, appView);
      assert forwardMethod != null && forwardMethod != target.getReference();
      return DexEncodedMethod.createDesugaringForwardingMethod(
          target, clazz, forwardMethod, appView.dexItemFactory());
    }

    private void synthesizeEmulatedDispatchMethods(
        DesugaredLibraryRetargeterEventConsumer eventConsumer) {
      assert appView.options().isDesugaredLibraryCompilation();
      if (emulatedDispatchMethods.isEmpty()) {
        return;
      }
      for (DexClassAndMethod emulatedDispatchMethod : emulatedDispatchMethods) {
        ensureEmulatedHolderDispatchMethod(emulatedDispatchMethod, eventConsumer);
      }
    }

    private void reportInvalidLibrarySupertype(
        DexLibraryClass libraryClass, DexClassAndMethodSet retarget) {
      DexClass dexClass = appView.definitionFor(libraryClass.superType);
      String message;
      if (dexClass == null) {
        message = "missing";
      } else if (dexClass.isClasspathClass()) {
        message = "a classpath class";
      } else {
        message = "INVALID";
        assert false;
      }
      appView
          .options()
          .warningInvalidLibrarySuperclassForDesugar(
              dexClass == null ? libraryClass.getOrigin() : dexClass.getOrigin(),
              libraryClass.type,
              libraryClass.superType,
              message,
              retarget);
    }

  }
}
