// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
//  for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ClassAccessFlags;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexLibraryClass;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ParameterAnnotationsList;
import com.android.tools.r8.graph.ResolutionResult;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.collections.SortedProgramMethodSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

public class DesugaredLibraryRetargeter {

  public static final String DESUGAR_LIB_RETARGET_CLASS_NAME_PREFIX =
      "$r8$retargetLibraryMember$virtualDispatch";

  private final AppView<?> appView;
  private final Map<DexMethod, DexMethod> retargetLibraryMember = new IdentityHashMap<>();
  // Map nonFinalRewrite hold a methodName -> method mapping for methods which are rewritten while
  // the holder is non final. In this case d8 needs to force resolution of given methods to see if
  // the invoke needs to be rewritten.
  private final Map<DexString, List<DexMethod>> nonFinalHolderRewrites = new IdentityHashMap<>();
  // Non final virtual library methods requiring generation of emulated dispatch.
  private final Set<DexEncodedMethod> emulatedDispatchMethods = Sets.newIdentityHashSet();

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
      DexMethod retarget = getRetargetLibraryMember(invoke.getInvokedMethod());
      if (retarget == null) {
        if (!matchesNonFinalHolderRewrite(invoke.getInvokedMethod())) {
          continue;
        }
        // We need to force resolution, even on d8, to know if the invoke has to be rewritten.
        ResolutionResult resolutionResult =
            appView
                .appInfoForDesugaring()
                .resolveMethod(invoke.getInvokedMethod(), invoke.getInterfaceBit());
        if (resolutionResult.isFailedResolution()) {
          continue;
        }
        DexEncodedMethod singleTarget = resolutionResult.getSingleTarget();
        assert singleTarget != null;
        retarget = getRetargetLibraryMember(singleTarget.method);
        if (retarget == null) {
          continue;
        }
      }

      // Due to emulated dispatch, we have to rewrite invoke-super differently or we end up in
      // infinite loops. We do direct resolution. This is a very uncommon case.
      if (invoke.isInvokeSuper() && matchesNonFinalHolderRewrite(invoke.getInvokedMethod())) {
        DexEncodedMethod dexEncodedMethod =
            appView
                .appInfoForDesugaring()
                .lookupSuperTarget(invoke.getInvokedMethod(), code.context());
        // Final methods can be rewritten as a normal invoke.
        if (dexEncodedMethod != null && !dexEncodedMethod.isFinal()) {
          DexMethod retargetMethod =
              appView
                  .options()
                  .desugaredLibraryConfiguration
                  .retargetMethod(dexEncodedMethod, appView);
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
      Map<DexString, Map<DexType, DexType>> retargetCoreLibMember =
          appView.options().desugaredLibraryConfiguration.getRetargetCoreLibMember();
      for (DexString methodName : retargetCoreLibMember.keySet()) {
        for (DexType inType : retargetCoreLibMember.get(methodName).keySet()) {
          DexClass typeClass = appView.definitionFor(inType);
          if (typeClass != null) {
            DexType newHolder = retargetCoreLibMember.get(methodName).get(inType);
            List<DexEncodedMethod> found = findDexEncodedMethodsWithName(methodName, typeClass);
            for (DexEncodedMethod encodedMethod : found) {
              if (!typeClass.isFinal()) {
                nonFinalHolderRewrites.putIfAbsent(encodedMethod.method.name, new ArrayList<>());
                nonFinalHolderRewrites.get(encodedMethod.method.name).add(encodedMethod.method);
                if (!encodedMethod.isStatic()) {
                  if (InterfaceMethodRewriter.isEmulatedInterfaceDispatch(appView, encodedMethod)) {
                    // In this case interface method rewriter takes care of it.
                    continue;
                  } else if (!encodedMethod.isFinal()) {
                    // Virtual rewrites require emulated dispatch for inheritance.
                    // The call is rewritten to the dispatch holder class instead.
                    handleEmulateDispatch(appView, encodedMethod);
                    newHolder = dispatchHolderTypeFor(encodedMethod);
                  }
                }
              }
              DexProto proto = encodedMethod.method.proto;
              DexMethod method = appView.dexItemFactory().createMethod(inType, proto, methodName);
              retargetLibraryMember.put(
                  method, computeRetargetMethod(method, encodedMethod.isStatic(), newHolder));
            }
          }
        }
      }
    }

    private DexMethod computeRetargetMethod(DexMethod method, boolean isStatic, DexType newHolder) {
      DexItemFactory factory = appView.dexItemFactory();
      DexProto newProto = isStatic ? method.proto : factory.prependHolderToProto(method);
      return factory.createMethod(newHolder, newProto, method.name);
    }

    private List<DexEncodedMethod> findDexEncodedMethodsWithName(
        DexString methodName, DexClass clazz) {
      List<DexEncodedMethod> found = new ArrayList<>();
      for (DexEncodedMethod encodedMethod : clazz.methods()) {
        if (encodedMethod.method.name == methodName) {
          found.add(encodedMethod);
        }
      }
      assert found.size() > 0 : "Should have found a method (library specifications).";
      return found;
    }

    private void handleEmulateDispatch(AppView<?> appView, DexEncodedMethod method) {
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
      Map<DexType, List<DexEncodedMethod>> map = Maps.newIdentityHashMap();
      for (DexEncodedMethod emulatedDispatchMethod : emulatedDispatchMethods) {
        map.putIfAbsent(emulatedDispatchMethod.getHolderType(), new ArrayList<>(1));
        map.get(emulatedDispatchMethod.getHolderType()).add(emulatedDispatchMethod);
      }
      SortedProgramMethodSet addedMethods = SortedProgramMethodSet.create();
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
                  addInterfacesAndForwardingMethods(
                      clazz, methods, method -> addedMethods.createAndAdd(clazz, method));
                }
              });
        }
      }
      converter.processMethodsConcurrently(addedMethods, executorService);
    }

    private boolean inherit(
        DexLibraryClass clazz, DexType typeToInherit, Set<DexEncodedMethod> retarget) {
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
        List<DexEncodedMethod> methods,
        Consumer<DexEncodedMethod> newForwardingMethodsConsumer) {
      // DesugaredLibraryRetargeter emulate dispatch: insertion of a marker interface & forwarding
      // methods.
      // We cannot use the ClassProcessor since this applies up to 26, while the ClassProcessor
      // applies up to 24.
      for (DexEncodedMethod method : methods) {
        clazz.addExtraInterfaces(
            Collections.singletonList(dispatchInterfaceTypeFor(method)), appView.dexItemFactory());
        if (clazz.lookupVirtualMethod(method.getReference()) == null) {
          DexEncodedMethod newMethod = createForwardingMethod(method, clazz);
          clazz.addVirtualMethod(newMethod);
          newForwardingMethodsConsumer.accept(newMethod);
        }
      }
    }

    private DexEncodedMethod createForwardingMethod(DexEncodedMethod target, DexClass clazz) {
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
      for (DexEncodedMethod emulatedDispatchMethod : emulatedDispatchMethods) {
        // Dispatch interface.
        DexType interfaceType = dispatchInterfaceTypeFor(emulatedDispatchMethod);
        DexEncodedMethod itfMethod =
            generateInterfaceDispatchMethod(emulatedDispatchMethod, interfaceType);
        BackportedMethodRewriter.synthesizeClassWithUniqueMethod(
            builder,
            itfAccessFlags,
            interfaceType,
            itfMethod,
            "desugared library dispatch interface",
            false,
            appView);
        // Dispatch holder.
        DexType holderType = dispatchHolderTypeFor(emulatedDispatchMethod);
        DexEncodedMethod dispatchMethod =
            generateHolderDispatchMethod(emulatedDispatchMethod, holderType, itfMethod.method);
        BackportedMethodRewriter.synthesizeClassWithUniqueMethod(
            builder,
            holderAccessFlags,
            holderType,
            dispatchMethod,
            "desugared library dispatch class",
            false,
            appView);
      }
    }

    private DexEncodedMethod generateInterfaceDispatchMethod(
        DexEncodedMethod emulatedDispatchMethod, DexType interfaceType) {
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
          newMethod, flags, DexAnnotationSet.empty(), ParameterAnnotationsList.empty(), null, true);
    }

    private DexEncodedMethod generateHolderDispatchMethod(
        DexEncodedMethod emulatedDispatchMethod, DexType dispatchHolder, DexMethod itfMethod) {
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
      return DexEncodedMethod.toEmulateDispatchLibraryMethod(
          emulatedDispatchMethod.getHolderType(),
          newMethod,
          desugarMethod,
          itfMethod,
          Collections.emptyList(),
          appView);
    }
  }

  private void reportInvalidLibrarySupertype(
      DexLibraryClass libraryClass, Set<DexEncodedMethod> retarget) {
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

  private DexType dispatchInterfaceTypeFor(DexEncodedMethod method) {
    return dispatchTypeFor(method, "dispatchInterface");
  }

  private DexType dispatchHolderTypeFor(DexEncodedMethod method) {
    return dispatchTypeFor(method, "dispatchHolder");
  }

  private DexType dispatchTypeFor(DexEncodedMethod method, String suffix) {
    String descriptor =
        "L"
            + appView
                .options()
                .desugaredLibraryConfiguration
                .getSynthesizedLibraryClassesPackagePrefix()
            + DESUGAR_LIB_RETARGET_CLASS_NAME_PREFIX
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
