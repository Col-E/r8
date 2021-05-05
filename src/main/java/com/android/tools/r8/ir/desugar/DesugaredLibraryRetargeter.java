// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
//  for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.ProgramResource.Kind;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.ClassAccessFlags;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexApplication.Builder;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexLibraryClass;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProgramClass.ChecksumSupplier;
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
import com.android.tools.r8.graph.ResolutionResult;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.ir.synthetic.EmulateInterfaceSyntheticCfCodeProvider;
import com.android.tools.r8.origin.SynthesizedOrigin;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.WorkList;
import com.android.tools.r8.utils.collections.DexClassAndMethodSet;
import com.android.tools.r8.utils.collections.SortedProgramMethodSet;
import com.google.common.collect.Maps;
import java.util.ArrayList;
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

public class DesugaredLibraryRetargeter {

  private static final String RETARGET_PACKAGE = "retarget/";
  public static final String DESUGAR_LIB_RETARGET_CLASS_NAME_PREFIX =
      "$r8$retargetLibraryMember$virtualDispatch";

  private final AppView<?> appView;
  private final Map<DexMethod, DexMethod> retargetLibraryMember = new IdentityHashMap<>();
  // Map nonFinalRewrite hold a methodName -> method mapping for methods which are rewritten while
  // the holder is non final. In this case d8 needs to force resolution of given methods to see if
  // the invoke needs to be rewritten.
  private final Map<DexString, List<DexMethod>> nonFinalHolderRewrites = new IdentityHashMap<>();
  // Non final virtual library methods requiring generation of emulated dispatch.
  private final DexClassAndMethodSet emulatedDispatchMethods = DexClassAndMethodSet.create();

  private final String packageAndClassDescriptorPrefix;

  public DesugaredLibraryRetargeter(AppView<?> appView) {
    this.appView = appView;
    packageAndClassDescriptorPrefix =
        getRetargetPackageAndClassPrefixDescriptor(appView.options().desugaredLibraryConfiguration);
    if (appView.options().desugaredLibraryConfiguration.getRetargetCoreLibMember().isEmpty()) {
      return;
    }
    new RetargetingSetup().setUpRetargeting();
  }

  public static boolean isRetargetType(DexType type, InternalOptions options) {
    if (options.desugaredLibraryConfiguration == null) {
      return false;
    }
    return type.toDescriptorString()
        .startsWith(
            getRetargetPackageAndClassPrefixDescriptor(options.desugaredLibraryConfiguration));
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

  private static void synthesizeClassWithUniqueMethod(
      Builder<?> builder,
      ClassAccessFlags accessFlags,
      DexType type,
      DexEncodedMethod uniqueMethod,
      String origin,
      AppView<?> appView) {
    DexItemFactory factory = appView.dexItemFactory();
    DexProgramClass newClass =
        new DexProgramClass(
            type,
            null,
            new SynthesizedOrigin(origin, BackportedMethodRewriter.class),
            accessFlags,
            factory.objectType,
            DexTypeList.empty(),
            null,
            null,
            Collections.emptyList(),
            null,
            Collections.emptyList(),
            ClassSignature.noSignature(),
            DexAnnotationSet.empty(),
            DexEncodedField.EMPTY_ARRAY,
            DexEncodedField.EMPTY_ARRAY,
            uniqueMethod.isStatic()
                ? new DexEncodedMethod[] {uniqueMethod}
                : DexEncodedMethod.EMPTY_ARRAY,
            uniqueMethod.isStatic()
                ? DexEncodedMethod.EMPTY_ARRAY
                : new DexEncodedMethod[] {uniqueMethod},
            factory.getSkipNameValidationForTesting(),
            getChecksumSupplier(uniqueMethod, appView));
    appView.appInfo().addSynthesizedClassForLibraryDesugaring(newClass);
    builder.addSynthesizedClass(newClass);
  }

  private static ChecksumSupplier getChecksumSupplier(DexEncodedMethod method, AppView<?> appView) {
    if (!appView.options().encodeChecksums) {
      return DexProgramClass::invalidChecksumRequest;
    }
    return c -> method.getReference().hashCode();
  }

  // Used by the ListOfBackportedMethods utility.
  void visit(Consumer<DexMethod> consumer) {
    retargetLibraryMember.keySet().forEach(consumer);
  }

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

      DexMethod retarget = getRetargetedMethod(invokedMethod, isInterface);
      if (retarget == null) {
        continue;
      }

      // Due to emulated dispatch, we have to rewrite invoke-super differently or we end up in
      // infinite loops. We do direct resolution. This is a very uncommon case.
      if (invoke.isInvokeSuper() && matchesNonFinalHolderRewrite(invoke.getInvokedMethod())) {
        DexClassAndMethod superTarget =
            appView
                .appInfoForDesugaring()
                .lookupSuperTarget(invoke.getInvokedMethod(), code.context());
        // Final methods can be rewritten as a normal invoke.
        if (superTarget != null && !superTarget.getAccessFlags().isFinal()) {
          DexMethod retargetMethod =
              appView.options().desugaredLibraryConfiguration.retargetMethod(superTarget, appView);
          if (retargetMethod != null) {
            iterator.replaceCurrentInstruction(
                new InvokeStatic(retargetMethod, invoke.outValue(), invoke.arguments()));
          }
          continue;
        }
      }

      iterator.replaceCurrentInstruction(
          new InvokeStatic(retarget, invoke.outValue(), invoke.inValues()));
    }
  }

  public DexMethod getRetargetedMethod(DexMethod invokedMethod, boolean isInterface) {
    DexMethod retarget = getRetargetLibraryMember(invokedMethod);
    if (retarget == null) {
      if (!matchesNonFinalHolderRewrite(invokedMethod)) {
        return null;
      }
      // We need to force resolution, even on d8, to know if the invoke has to be rewritten.
      ResolutionResult resolutionResult =
          appView.appInfoForDesugaring().resolveMethod(invokedMethod, isInterface);
      if (resolutionResult.isFailedResolution()) {
        return null;
      }
      DexEncodedMethod singleTarget = resolutionResult.getSingleTarget();
      assert singleTarget != null;
      retarget = getRetargetLibraryMember(singleTarget.getReference());
    }
    return retarget;
  }

  private DexMethod getRetargetLibraryMember(DexMethod method) {
    Map<DexType, DexType> backportCoreLibraryMembers =
        appView.options().desugaredLibraryConfiguration.getBackportCoreLibraryMember();
    if (backportCoreLibraryMembers.containsKey(method.holder)) {
      DexType newHolder = backportCoreLibraryMembers.get(method.holder);
      return appView.dexItemFactory().createMethod(newHolder, method.proto, method.name);
    }
    return retargetLibraryMember.get(method);
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
                    handleEmulateDispatch(appView, method);
                    newHolder = dispatchHolderTypeFor(method);
                  }
                }
              }
              retargetLibraryMember.put(methodReference, computeRetargetMethod(method, newHolder));
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
            itemFactory.createMethod(
                itemFactory.createType("Ljava/util/DesugarArrays;"), proto, name);
        retargetLibraryMember.put(source, target);

        // TODO(b/181629049): This is only a workaround rewriting invokes of
        //  j.u.TimeZone.getTimeZone taking a java.time.ZoneId.
        // to j.u.DesugarArrays.deepEquals0.
        name = itemFactory.createString("getTimeZone");
        proto =
            itemFactory.createProto(
                itemFactory.createType("Ljava/util/TimeZone;"),
                itemFactory.createType("Ljava/time/ZoneId;"));
        source =
            itemFactory.createMethod(itemFactory.createType("Ljava/util/TimeZone;"), proto, name);
        target =
            itemFactory.createMethod(
                itemFactory.createType("Ljava/util/DesugarTimeZone;"), proto, name);
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

    private DexMethod computeRetargetMethod(DexClassAndMethod method, DexType newHolder) {
      DexItemFactory factory = appView.dexItemFactory();
      DexProto newProto =
          method.getAccessFlags().isStatic()
              ? method.getProto()
              : factory.prependHolderToProto(method.getReference());
      return factory.createMethod(newHolder, newProto, method.getName());
    }

    private List<DexClassAndMethod> findMethodsWithName(DexString methodName, DexClass clazz) {
      List<DexClassAndMethod> found = new ArrayList<>();
      clazz.forEachClassMethodMatching(
          definition -> definition.getName() == methodName, found::add);
      assert !found.isEmpty() : "Should have found a method (library specifications).";
      return found;
    }

    private void handleEmulateDispatch(AppView<?> appView, DexClassAndMethod method) {
      emulatedDispatchMethods.add(method);
      if (!appView.options().isDesugaredLibraryCompilation()) {
        // Add rewrite rules so keeps rules are correctly generated in the program.
        DexType dispatchInterfaceType = dispatchInterfaceTypeFor(method);
        appView.rewritePrefix.rewriteType(dispatchInterfaceType, dispatchInterfaceType);
        DexType dispatchHolderType = dispatchHolderTypeFor(method);
        appView.rewritePrefix.rewriteType(dispatchHolderType, dispatchHolderType);
      }
    }
  }

  public void synthesizeRetargetClasses(
      DexApplication.Builder<?> builder, ExecutorService executorService, IRConverter converter)
      throws ExecutionException {
    new EmulatedDispatchTreeFixer().fixApp(builder, executorService, converter);
  }

  // The rewrite of virtual calls requires to go through emulate dispatch. This class is responsible
  // for inserting interfaces on library boundaries and forwarding methods in the program, and to
  // synthesize the interfaces and emulated dispatch classes in the desugared library.
  class EmulatedDispatchTreeFixer {

    void fixApp(
        DexApplication.Builder<?> builder, ExecutorService executorService, IRConverter converter)
        throws ExecutionException {
      if (appView.options().isDesugaredLibraryCompilation()) {
        synthesizeEmulatedDispatchMethods(builder);
      } else {
        addInterfacesAndForwardingMethods(executorService, converter);
      }
    }

    private void addInterfacesAndForwardingMethods(
        ExecutorService executorService, IRConverter converter) throws ExecutionException {
      assert !appView.options().isDesugaredLibraryCompilation();
      Map<DexType, List<DexClassAndMethod>> map = Maps.newIdentityHashMap();
      for (DexClassAndMethod emulatedDispatchMethod : emulatedDispatchMethods) {
        map.putIfAbsent(emulatedDispatchMethod.getHolderType(), new ArrayList<>(1));
        map.get(emulatedDispatchMethod.getHolderType()).add(emulatedDispatchMethod);
      }
      SortedProgramMethodSet addedMethods = SortedProgramMethodSet.create();
      for (DexProgramClass clazz : appView.appInfo().classes()) {
        if (appView.isAlreadyLibraryDesugared(clazz)) {
          continue;
        }
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
                  addInterfacesAndForwardingMethods(
                      clazz, methods, method -> addedMethods.createAndAdd(clazz, method));
                }
              });
        }
      }
      converter.processMethodsConcurrently(addedMethods, executorService);
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
        DexProgramClass clazz,
        List<DexClassAndMethod> methods,
        Consumer<DexEncodedMethod> newForwardingMethodsConsumer) {
      // DesugaredLibraryRetargeter emulate dispatch: insertion of a marker interface & forwarding
      // methods.
      // We cannot use the ClassProcessor since this applies up to 26, while the ClassProcessor
      // applies up to 24.
      for (DexClassAndMethod method : methods) {
        clazz.addExtraInterfaces(
            Collections.singletonList(new ClassTypeSignature(dispatchInterfaceTypeFor(method))));
        if (clazz.lookupVirtualMethod(method.getReference()) == null) {
          DexEncodedMethod newMethod = createForwardingMethod(method, clazz);
          clazz.addVirtualMethod(newMethod);
          newForwardingMethodsConsumer.accept(newMethod);
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

    private void synthesizeEmulatedDispatchMethods(DexApplication.Builder<?> builder) {
      assert appView.options().isDesugaredLibraryCompilation();
      if (emulatedDispatchMethods.isEmpty()) {
        return;
      }
      ClassAccessFlags itfAccessFlags =
          ClassAccessFlags.fromSharedAccessFlags(
              Constants.ACC_PUBLIC
                  | Constants.ACC_SYNTHETIC
                  | Constants.ACC_ABSTRACT
                  | Constants.ACC_INTERFACE);
      ClassAccessFlags holderAccessFlags =
          ClassAccessFlags.fromSharedAccessFlags(Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC);
      for (DexClassAndMethod emulatedDispatchMethod : emulatedDispatchMethods) {
        // Dispatch interface.
        DexType interfaceType = dispatchInterfaceTypeFor(emulatedDispatchMethod);
        DexEncodedMethod itfMethod =
            generateInterfaceDispatchMethod(emulatedDispatchMethod, interfaceType);
        synthesizeClassWithUniqueMethod(
            builder,
            itfAccessFlags,
            interfaceType,
            itfMethod,
            "desugared library dispatch interface",
            appView);
        // Dispatch holder.
        DexType holderType = dispatchHolderTypeFor(emulatedDispatchMethod);
        DexEncodedMethod dispatchMethod =
            generateHolderDispatchMethod(
                emulatedDispatchMethod, holderType, itfMethod.getReference());
        synthesizeClassWithUniqueMethod(
            builder,
            holderAccessFlags,
            holderType,
            dispatchMethod,
            "desugared library dispatch class",
            appView);
      }
    }

    private DexEncodedMethod generateInterfaceDispatchMethod(
        DexClassAndMethod emulatedDispatchMethod, DexType interfaceType) {
      MethodAccessFlags flags =
          MethodAccessFlags.fromSharedAccessFlags(
              Constants.ACC_PUBLIC | Constants.ACC_ABSTRACT | Constants.ACC_SYNTHETIC, false);
      DexMethod newMethod =
          appView
              .dexItemFactory()
              .createMethod(
                  interfaceType,
                  emulatedDispatchMethod.getProto(),
                  emulatedDispatchMethod.getName());
      return new DexEncodedMethod(
          newMethod,
          flags,
          MethodTypeSignature.noSignature(),
          DexAnnotationSet.empty(),
          ParameterAnnotationsList.empty(),
          null,
          true);
    }

    private DexEncodedMethod generateHolderDispatchMethod(
        DexClassAndMethod emulatedDispatchMethod, DexType dispatchHolder, DexMethod itfMethod) {
      // The method should look like:
      // static foo(rcvr, arg0, arg1) {
      //    if (rcvr instanceof interfaceType) {
      //      return invoke-interface receiver.foo(arg0, arg1);
      //    } else {
      //      return DesugarX.foo(rcvr, arg0, arg1)
      //    }
      // We do not deal with complex cases (multiple retargeting of the same signature in the
      // same inheritance tree, etc., since they do not happen in the most common desugared library.
      DexMethod desugarMethod =
          appView
              .options()
              .desugaredLibraryConfiguration
              .retargetMethod(emulatedDispatchMethod, appView);
      assert desugarMethod != null; // This method is reached only for retarget core lib members.
      DexMethod newMethod =
          appView
              .dexItemFactory()
              .createMethod(dispatchHolder, desugarMethod.proto, emulatedDispatchMethod.getName());
      CfCode code =
          new EmulateInterfaceSyntheticCfCodeProvider(
                  emulatedDispatchMethod.getHolderType(),
                  desugarMethod,
                  itfMethod,
                  Collections.emptyList(),
                  appView)
              .generateCfCode();
      return new DexEncodedMethod(
          newMethod,
          MethodAccessFlags.createPublicStaticSynthetic(),
          MethodTypeSignature.noSignature(),
          DexAnnotationSet.empty(),
          ParameterAnnotationsList.empty(),
          code,
          true);
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

  private DexType dispatchInterfaceTypeFor(DexClassAndMethod method) {
    return dispatchTypeFor(method, "dispatchInterface");
  }

  private DexType dispatchHolderTypeFor(DexClassAndMethod method) {
    return dispatchTypeFor(method, "dispatchHolder");
  }

  public static String getRetargetPackageAndClassPrefixDescriptor(
      DesugaredLibraryConfiguration config) {
    return "L"
        + config.getSynthesizedLibraryClassesPackagePrefix()
        + RETARGET_PACKAGE
        + DESUGAR_LIB_RETARGET_CLASS_NAME_PREFIX;
  }

  private DexType dispatchTypeFor(DexClassAndMethod method, String suffix) {
    String descriptor =
        packageAndClassDescriptorPrefix
            + '$'
            + method.getHolderType().getName()
            + '$'
            + method.getName()
            + '$'
            + suffix
            + ';';
    return appView.dexItemFactory().createSynthesizedType(descriptor);
  }
}
