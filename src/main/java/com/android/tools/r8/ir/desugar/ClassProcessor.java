// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexLibraryClass;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ParameterAnnotationsList;
import com.android.tools.r8.graph.ResolutionResult;
import com.android.tools.r8.graph.ResolutionResult.IncompatibleClassResult;
import com.android.tools.r8.ir.code.Invoke;
import com.android.tools.r8.ir.synthetic.ExceptionThrowingSourceCode;
import com.android.tools.r8.ir.synthetic.ForwardMethodSourceCode;
import com.android.tools.r8.ir.synthetic.SynthesizedCode;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.MethodSignatureEquivalence;
import com.android.tools.r8.utils.Pair;
import com.google.common.base.Equivalence.Wrapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.objectweb.asm.Opcodes;

// Default and static method interface desugaring processor for classes.
// Adds default interface methods into the class when needed.
final class ClassProcessor {

  private final AppView<?> appView;
  private final DexItemFactory dexItemFactory;
  private final InterfaceMethodRewriter rewriter;
  private final Consumer<DexEncodedMethod> newSynthesizedMethodConsumer;

  // Mapping from program and classpath classes to an information summary of forwarding methods.
  private final Map<DexClass, ClassInfo> classInfo = new IdentityHashMap<>();
  private final Map<DexLibraryClass, LibraryClassInfo> libraryClassInfo = new IdentityHashMap<>();

  // Mapping from program and classpath interfaces to an information summary of default methods.
  private final Map<DexClass, InterfaceInfo> interfaceInfo = new IdentityHashMap<>();

  // Mapping from actual program classes to the synthesized forwarding methods to be created.
  private final Map<DexProgramClass, List<DexEncodedMethod>> newSyntheticMethods =
      new IdentityHashMap<>();

  // Collection of information known at the point of a given class.
  // This info is immutable and shared as it is often the same on a significant part of the
  // class hierarchy. Thus, in the case of additions the parent pointer will contain prior info.
  private static class ClassInfo {

    static final ClassInfo EMPTY = new ClassInfo(null, ImmutableList.of(), ImmutableSet.of());

    final ClassInfo parent;
    final ImmutableList<DexEncodedMethod> forwardingMethods;
    // The value DexType is null if no retargeting is required, and contains the type to retarget
    // to if retargeting is required.
    final ImmutableSet<DexEncodedMethod> desugaredLibraryForwardingMethods;

    ClassInfo(
        ClassInfo parent,
        ImmutableList<DexEncodedMethod> forwardingMethods,
        ImmutableSet<DexEncodedMethod> desugaredLibraryForwardingMethods) {
      this.parent = parent;
      this.forwardingMethods = forwardingMethods;
      this.desugaredLibraryForwardingMethods = desugaredLibraryForwardingMethods;
    }

    boolean containsForwardingMethod(DexEncodedMethod method) {
      return forwardingMethods.contains(method)
          || (parent != null && parent.containsForwardingMethod(method));
    }

    boolean containsDesugaredLibraryForwardingMethod(DexEncodedMethod method) {
      return desugaredLibraryForwardingMethods.contains(method)
          || (parent != null && parent.containsDesugaredLibraryForwardingMethod(method))
          || containsMethods(forwardingMethods, method.method);
    }
  }

  // Collection of information known at the point of a given library class.
  // This information is immutable and shared in an inheritance tree. In practice most library
  // classes use the empty library class info.
  private static class LibraryClassInfo {

    static final LibraryClassInfo EMPTY = new LibraryClassInfo(ImmutableSet.of());

    final ImmutableSet<DexEncodedMethod> desugaredLibraryMethods;

    LibraryClassInfo(ImmutableSet<DexEncodedMethod> desugaredLibraryMethodsToImplement) {
      this.desugaredLibraryMethods = desugaredLibraryMethodsToImplement;
    }

    static LibraryClassInfo create(ImmutableSet<DexEncodedMethod> desugaredLibraryMethods) {
      if (desugaredLibraryMethods.isEmpty()) {
        return EMPTY;
      }
      return new LibraryClassInfo(desugaredLibraryMethods);
    }
  }

  // Collection of information known at the point of a given interface.
  // This information is mutable and a copy exist for each interface point except for the trivial
  // empty case which is shared.
  private static class InterfaceInfo {

    static final InterfaceInfo EMPTY =
        new InterfaceInfo(Collections.emptySet(), Collections.emptySet());

    final Set<Wrapper<DexMethod>> defaultMethods;
    final Set<DexEncodedMethod> desugaredLibraryMethods;

    InterfaceInfo(
        Set<Wrapper<DexMethod>> defaultMethods, Set<DexEncodedMethod> desugaredLibraryMethods) {
      this.defaultMethods = defaultMethods;
      this.desugaredLibraryMethods = desugaredLibraryMethods;
    }

    static InterfaceInfo create(
        Set<Wrapper<DexMethod>> defaultMethods,
        Set<DexEncodedMethod> desugaredLibraryMethodsToImplement) {
      return defaultMethods.isEmpty() && desugaredLibraryMethodsToImplement.isEmpty()
          ? EMPTY
          : new InterfaceInfo(defaultMethods, desugaredLibraryMethodsToImplement);
    }
  }

  private static boolean containsMethods(Collection<DexEncodedMethod> methods, DexMethod method) {
    for (DexEncodedMethod theMethod : methods) {
      if (theMethod.method.match(method)) {
        return true;
      }
    }
    return false;
  }

  ClassProcessor(
      AppView<?> appView,
      InterfaceMethodRewriter rewriter,
      Consumer<DexEncodedMethod> newSynthesizedMethodConsumer) {
    this.appView = appView;
    this.dexItemFactory = appView.dexItemFactory();
    this.rewriter = rewriter;
    this.newSynthesizedMethodConsumer = newSynthesizedMethodConsumer;
  }

  final void addSyntheticMethods() {
    for (DexProgramClass clazz : newSyntheticMethods.keySet()) {
      List<DexEncodedMethod> newForwardingMethods = newSyntheticMethods.get(clazz);
      if (newForwardingMethods != null) {
        clazz.appendVirtualMethods(newForwardingMethods);
      }
    }
  }

  private void addSyntheticMethod(DexProgramClass clazz, DexEncodedMethod newMethod) {
    newSyntheticMethods.computeIfAbsent(clazz, key -> new ArrayList<>()).add(newMethod);
    newSynthesizedMethodConsumer.accept(newMethod);
  }

  private ClassInfo computeClassInfo(DexType type) {
    // No forwards at the top of the class hierarchy (assuming java.lang.Object is never amended).
    if (type == null || type == dexItemFactory.objectType) {
      return ClassInfo.EMPTY;
    }
    // If the type does not exist there is little we can do but assume no default methods.
    DexClass clazz = appView.definitionFor(type);
    if (clazz == null) {
      return ClassInfo.EMPTY;
    }
    return computeClassInfo(clazz);
  }

  ClassInfo computeClassInfo(DexClass clazz) {
    assert !clazz.isInterface();
    return clazz.isLibraryClass()
        ? ClassInfo.EMPTY
        : classInfo.computeIfAbsent(clazz, this::computeClassInfoRaw);
  }

  private ClassInfo computeClassInfoRaw(DexClass clazz) {
    // We compute both library and class information, but one of them is empty, since a class is
    // a library class or is not, but cannot be both.
    ClassInfo superInfo = computeClassInfo(clazz.superType);
    List<DexMethod> defaultMethods = computeInterfaceInfo(clazz.interfaces.values);
    LibraryClassInfo superLibraryClassInfo = computeLibraryClassInfo(clazz.superType);
    assert superLibraryClassInfo.desugaredLibraryMethods.isEmpty()
        || superInfo.desugaredLibraryForwardingMethods.isEmpty();
    ImmutableSet<DexEncodedMethod> desugaredLibraryMethods =
        computeDesugaredLibraryMethods(
            clazz.interfaces.values,
            superLibraryClassInfo.desugaredLibraryMethods.isEmpty()
                ? superInfo.desugaredLibraryForwardingMethods
                : superLibraryClassInfo.desugaredLibraryMethods);
    if (defaultMethods.isEmpty() && desugaredLibraryMethods.isEmpty()) {
      return superInfo;
    }
    ImmutableList<DexEncodedMethod> forwards =
        computeDefaultMethods(clazz, superInfo, defaultMethods);
    ImmutableSet<DexEncodedMethod> desugaredLibraryForwardingMethods =
        computeDesugaredLibraryMethods(clazz, superInfo, desugaredLibraryMethods, forwards);
    if (forwards.isEmpty() && desugaredLibraryForwardingMethods.isEmpty()) {
      return superInfo;
    }
    return new ClassInfo(superInfo, forwards, desugaredLibraryForwardingMethods);
  }

  private ImmutableSet<DexEncodedMethod> computeDesugaredLibraryMethods(
      DexClass clazz,
      ClassInfo superInfo,
      ImmutableSet<DexEncodedMethod> desugaredLibraryMethods,
      ImmutableList<DexEncodedMethod> forwards) {
    ImmutableSet.Builder<DexEncodedMethod> additionalDesugaredLibraryForwardingMethods =
        ImmutableSet.builder();
    for (DexEncodedMethod dexMethod : desugaredLibraryMethods) {
      if (!superInfo.containsDesugaredLibraryForwardingMethod(dexMethod)
          && !containsMethods(forwards, dexMethod.method)) {
        additionalDesugaredLibraryForwardingMethods.add(dexMethod);
        // We still add the methods to the list, even if override, to avoid generating it again.
        if (clazz.lookupVirtualMethod(dexMethod.method) == null) {
          addForwardingMethod(dexMethod, clazz);
        }
      }
    }
    return additionalDesugaredLibraryForwardingMethods.build();
  }

  private ImmutableList<DexEncodedMethod> computeDefaultMethods(
      DexClass clazz, ClassInfo superInfo, List<DexMethod> defaultMethods) {
    Builder<DexEncodedMethod> additionalForwards = ImmutableList.builder();
    for (DexMethod defaultMethod : defaultMethods) {
      ResolutionResult resolution = appView.appInfo().resolveMethod(clazz, defaultMethod);
      if (resolution.isFailedResolution()) {
        assert resolution instanceof IncompatibleClassResult;
        addICCEThrowingMethod(defaultMethod, clazz);
      } else {
        DexEncodedMethod target = resolution.getSingleTarget();
        DexClass dexClass = appView.definitionFor(target.method.holder);
        if (target.isDefaultMethod()
            && dexClass != null
            && dexClass.isInterface()
            && !superInfo.containsForwardingMethod(target)) {
          additionalForwards.add(target);
          addForwardingMethod(target, clazz);
        }
      }
    }
    return additionalForwards.build();
  }

  private LibraryClassInfo computeLibraryClassInfo(DexType type) {
    // No desugaring required, no library class analysis.
    if (appView.options().desugaredLibraryConfiguration.getEmulateLibraryInterface().isEmpty()
        && appView.options().desugaredLibraryConfiguration.getRetargetCoreLibMember().isEmpty()) {
      return LibraryClassInfo.EMPTY;
    }
    // No forwards at the top of the class hierarchy (assuming java.lang.Object is never amended).
    if (type == null || type == dexItemFactory.objectType) {
      return LibraryClassInfo.EMPTY;
    }
    // If the type does not exist there is little we can do but assume no default methods.
    DexClass clazz = appView.definitionFor(type);
    if (clazz == null) {
      return LibraryClassInfo.EMPTY;
    }
    return computeLibraryClassInfo(clazz);
  }

  private LibraryClassInfo computeLibraryClassInfo(DexClass clazz) {
    assert !clazz.isInterface();
    return clazz.isLibraryClass()
        ? libraryClassInfo.computeIfAbsent(clazz.asLibraryClass(), this::computeLibraryClassInfoRaw)
        : LibraryClassInfo.EMPTY;
  }

  private LibraryClassInfo computeLibraryClassInfoRaw(DexLibraryClass clazz) {
    LibraryClassInfo superInfo = computeLibraryClassInfo(clazz.superType);
    ImmutableSet<DexEncodedMethod> desugaredLibraryMethods =
        computeDesugaredLibraryMethods(clazz.interfaces.values, superInfo.desugaredLibraryMethods);
    // Retarget method management.
    for (DexEncodedMethod method : clazz.virtualMethods()) {
      if (!method.isFinal()) {
        Map<DexType, DexType> typeMap =
            appView
                .options()
                .desugaredLibraryConfiguration
                .getRetargetCoreLibMember()
                .get(method.method.name);
        if (typeMap != null) {
          DexType dexType = typeMap.get(clazz.type);
          if (dexType != null) {
            ImmutableSet.Builder<DexEncodedMethod> newDesugaredLibraryMethods =
                ImmutableSet.builder();
            for (DexEncodedMethod desugaredLibraryForwardingMethod : desugaredLibraryMethods) {
              if (!desugaredLibraryForwardingMethod.method.match(method.method)) {
                newDesugaredLibraryMethods.add(desugaredLibraryForwardingMethod);
              }
            }
            newDesugaredLibraryMethods.add(method);
            desugaredLibraryMethods = newDesugaredLibraryMethods.build();
          }
        }
      }
    }
    if (desugaredLibraryMethods == superInfo.desugaredLibraryMethods) {
      return superInfo;
    }
    return LibraryClassInfo.create(desugaredLibraryMethods);
  }

  private List<DexMethod> computeInterfaceInfo(DexType[] interfaces) {
    if (interfaces.length == 0) {
      return Collections.emptyList();
    }
    Set<Wrapper<DexMethod>> defaultMethods = new HashSet<>();
    for (DexType iface : interfaces) {
      InterfaceInfo itfInfo = computeInterfaceInfo(iface);
      defaultMethods.addAll(itfInfo.defaultMethods);
    }
    return defaultMethods.isEmpty()
        ? Collections.emptyList()
        : ListUtils.map(defaultMethods, Wrapper::get);
  }

  private void mergeDesugaredLibraryMethods(
      Map<Wrapper<DexMethod>, DexEncodedMethod> desugaredLibraryMethods,
      Set<DexEncodedMethod> methods) {
    for (DexEncodedMethod method : methods) {
      Wrapper<DexMethod> wrap = MethodSignatureEquivalence.get().wrap(method.method);
      DexEncodedMethod initialMethod = desugaredLibraryMethods.get(wrap);
      if (initialMethod == null || initialMethod.method == method.method) {
        desugaredLibraryMethods.put(wrap, method);
      } else {
        // We need to do resolution, different desugared library methods are incoming from
        // interfaces/superclass without priorities.
        DexClass initialHolder = appView.definitionFor(initialMethod.method.holder);
        DexClass holder = appView.definitionFor(method.method.holder);
        assert holder != null && initialHolder != null;
        assert holder.isInterface() || initialHolder.isInterface();
        if (!holder.isInterface()) {
          desugaredLibraryMethods.put(wrap, method);
        } else if (!initialHolder.isInterface()) {
          desugaredLibraryMethods.put(wrap, initialMethod);
        } else if (implementsInterface(initialHolder, holder)) {
          desugaredLibraryMethods.put(wrap, initialMethod);
        } else {
          desugaredLibraryMethods.put(wrap, method);
        }
      }
    }
  }

  private boolean implementsInterface(DexClass initialHolder, DexClass holder) {
    LinkedList<DexType> workList = new LinkedList<>();
    Collections.addAll(workList, initialHolder.interfaces.values);
    while (!workList.isEmpty()) {
      DexType dexType = workList.removeFirst();
      if (dexType == holder.type) {
        return true;
      }
      DexClass dexClass = appView.definitionFor(dexType);
      assert dexClass != null;
      Collections.addAll(workList, dexClass.interfaces.values);
    }
    return false;
  }

  private ImmutableSet<DexEncodedMethod> computeDesugaredLibraryMethods(
      DexType[] interfaces, ImmutableSet<DexEncodedMethod> incomingDesugaredLibraryMethods) {
    Map<Wrapper<DexMethod>, DexEncodedMethod> desugaredLibraryMethods = new HashMap<>();
    for (DexType iface : interfaces) {
      mergeDesugaredLibraryMethods(
          desugaredLibraryMethods, computeInterfaceInfo(iface).desugaredLibraryMethods);
    }
    if (desugaredLibraryMethods.isEmpty()) {
      return incomingDesugaredLibraryMethods;
    }
    mergeDesugaredLibraryMethods(desugaredLibraryMethods, incomingDesugaredLibraryMethods);
    return desugaredLibraryMethods.isEmpty()
        ? ImmutableSet.of()
        : ImmutableSet.copyOf(desugaredLibraryMethods.values());
  }

  private InterfaceInfo computeInterfaceInfo(DexType iface) {
    if (iface == null || iface == dexItemFactory.objectType) {
      return InterfaceInfo.EMPTY;
    }
    DexClass definition = appView.definitionFor(iface);
    if (definition == null) {
      return InterfaceInfo.EMPTY;
    }
    return computeInterfaceInfo(definition);
  }

  private InterfaceInfo computeInterfaceInfo(DexClass iface) {
    return interfaceInfo.computeIfAbsent(iface, this::computeInterfaceInfoRaw);
  }

  private InterfaceInfo computeInterfaceInfoRaw(DexClass iface) {
    assert iface.isInterface();
    assert iface.superType == dexItemFactory.objectType;
    Set<Wrapper<DexMethod>> defaultMethods = new HashSet<>();
    Set<DexEncodedMethod> desugaredLibraryMethods = new HashSet<>();
    for (DexType superiface : iface.interfaces.values) {
      defaultMethods.addAll(computeInterfaceInfo(superiface).defaultMethods);
      desugaredLibraryMethods.addAll(computeInterfaceInfo(superiface).desugaredLibraryMethods);
    }
    // NOTE: we intentionally don't desugar default methods into interface methods
    // coming from android.jar since it is only possible in case v24+ version
    // of android.jar is provided. The only exception is desugared library classes.
    if (!iface.isLibraryClass() || appView.rewritePrefix.hasRewrittenType(iface.type)) {
      for (DexEncodedMethod method : iface.methods()) {
        if (method.isDefaultMethod()) {
          defaultMethods.add(MethodSignatureEquivalence.get().wrap(method.method));
        }
      }
    }
    if (appView
        .options()
        .desugaredLibraryConfiguration
        .getEmulateLibraryInterface()
        .containsKey(iface.type)) {
      for (DexEncodedMethod method : iface.methods()) {
        if (method.isDefaultMethod() && shouldEmulate(method.method)) {
          desugaredLibraryMethods.add(method);
        }
      }
    }
    return InterfaceInfo.create(defaultMethods, desugaredLibraryMethods);
  }

  private boolean shouldEmulate(DexMethod method) {
    List<Pair<DexType, DexString>> dontRewriteInvocation =
        appView.options().desugaredLibraryConfiguration.getDontRewriteInvocation();
    for (Pair<DexType, DexString> pair : dontRewriteInvocation) {
      if (pair.getFirst() == method.holder && pair.getSecond() == method.name) {
        return false;
      }
    }
    return true;
  }

  private void addICCEThrowingMethod(DexMethod method, DexClass clazz) {
    if (!clazz.isProgramClass()) {
      return;
    }
    DexMethod newMethod = dexItemFactory.createMethod(clazz.type, method.proto, method.name);
    DexEncodedMethod newEncodedMethod =
        new DexEncodedMethod(
            newMethod,
            MethodAccessFlags.fromCfAccessFlags(Opcodes.ACC_PUBLIC, false),
            DexAnnotationSet.empty(),
            ParameterAnnotationsList.empty(),
            new SynthesizedCode(
                callerPosition ->
                    new ExceptionThrowingSourceCode(
                        clazz.type, method, callerPosition, dexItemFactory.icceType)));
    addSyntheticMethod(clazz.asProgramClass(), newEncodedMethod);
  }

  // Note: The parameter defaultMethod may be a public method on a class in case of desugared
  // library retargeting (See below target.isInterface check).
  private void addForwardingMethod(DexEncodedMethod defaultMethod, DexClass clazz) {
    if (!clazz.isProgramClass()) {
      return;
    }
    DexMethod method = defaultMethod.method;
    DexClass target = appView.definitionFor(method.holder);
    // NOTE: Never add a forwarding method to methods of classes unknown or coming from android.jar
    // even if this results in invalid code, these classes are never desugared.
    assert target != null;
    // In desugared library, emulated interface methods can be overridden by retarget lib members.
    DexMethod forwardMethod =
        target.isInterface()
            ? rewriter.defaultAsMethodOfCompanionClass(method)
            : retargetMethod(method);
    // New method will have the same name, proto, and also all the flags of the
    // default method, including bridge flag.
    DexMethod newMethod = dexItemFactory.createMethod(clazz.type, method.proto, method.name);
    MethodAccessFlags newFlags = defaultMethod.accessFlags.copy();
    // Some debuggers (like IntelliJ) automatically skip synthetic methods on single step.
    newFlags.setSynthetic();
    ForwardMethodSourceCode.Builder forwardSourceCodeBuilder =
        ForwardMethodSourceCode.builder(newMethod);
    forwardSourceCodeBuilder
        .setReceiver(clazz.type)
        .setTarget(forwardMethod)
        .setInvokeType(Invoke.Type.STATIC)
        .setIsInterface(false); // Holder is companion class, not an interface.
    DexEncodedMethod newEncodedMethod =
        new DexEncodedMethod(
            newMethod,
            newFlags,
            defaultMethod.annotations,
            defaultMethod.parameterAnnotationsList,
            new SynthesizedCode(forwardSourceCodeBuilder::build));
    addSyntheticMethod(clazz.asProgramClass(), newEncodedMethod);
  }

  private DexMethod retargetMethod(DexMethod method) {
    Map<DexString, Map<DexType, DexType>> retargetCoreLibMember =
        appView.options().desugaredLibraryConfiguration.getRetargetCoreLibMember();
    Map<DexType, DexType> typeMap = retargetCoreLibMember.get(method.name);
    assert typeMap != null;
    assert typeMap.get(method.holder) != null;
    return dexItemFactory.createMethod(
        typeMap.get(method.holder),
        dexItemFactory.prependTypeToProto(method.holder, method.proto),
        method.name);
  }
}
