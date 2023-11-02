// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar.itf;

import static com.android.tools.r8.ir.desugar.itf.InterfaceDesugaringSyntheticHelper.InterfaceMethodDesugaringMode.EMULATED_INTERFACE_ONLY;
import static com.android.tools.r8.ir.desugar.itf.InterfaceDesugaringSyntheticHelper.InterfaceMethodDesugaringMode.NONE;

import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfNew;
import com.android.tools.r8.cf.code.CfStackInstruction;
import com.android.tools.r8.cf.code.CfStackInstruction.Opcode;
import com.android.tools.r8.cf.code.CfThrow;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndMember;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexEncodedMember;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexLibraryClass;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GenericSignature;
import com.android.tools.r8.graph.GenericSignature.ClassTypeSignature;
import com.android.tools.r8.graph.LookupMethodTarget;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.graph.MethodResolutionResult.FailedResolutionResult;
import com.android.tools.r8.graph.MethodResolutionResult.NoSuchMethodResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.DerivedMethod;
import com.android.tools.r8.ir.desugar.itf.InterfaceDesugaringSyntheticHelper.InterfaceMethodDesugaringMode;
import com.android.tools.r8.position.MethodPosition;
import com.android.tools.r8.utils.BooleanBox;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.ConsumerUtils;
import com.android.tools.r8.utils.IterableUtils;
import com.android.tools.r8.utils.MethodSignatureEquivalence;
import com.android.tools.r8.utils.OptionalBool;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.WorkList;
import com.google.common.base.Equivalence.Wrapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.objectweb.asm.Opcodes;

/**
 * Default and static method interface desugaring processor for classes.
 *
 * <p>The core algorithm of the class processing is to ensure that for any type, all of its super
 * and implements hierarchy is computed first, and based on the summaries of these types the summary
 * of the class can be computed and the required forwarding methods on that type can be generated.
 * In other words, the traversal is in top-down (edges from type to its subtypes) topological order.
 * The traversal is lazy, starting from the unordered set of program classes.
 */
final class ClassProcessor {

  private abstract static class SyntheticMethodInfo {

    private final ProgramMethod method;

    SyntheticMethodInfo(ProgramMethod method) {
      this.method = method;
    }

    ProgramMethod getMethod() {
      return method;
    }

    boolean isForwardingMethodInfo() {
      return false;
    }

    SyntheticForwardingMethodInfo asForwardingMethodInfo() {
      return null;
    }

    SyntheticThrowingMethodInfo asThrowingMethodInfo() {
      return null;
    }
  }

  private static class SyntheticForwardingMethodInfo extends SyntheticMethodInfo {

    private final DexClassAndMethod baseMethod;

    SyntheticForwardingMethodInfo(ProgramMethod method, DexClassAndMethod baseMethod) {
      super(method);
      this.baseMethod = baseMethod;
    }

    DexClassAndMethod getBaseMethod() {
      return baseMethod;
    }

    @Override
    boolean isForwardingMethodInfo() {
      return true;
    }

    @Override
    SyntheticForwardingMethodInfo asForwardingMethodInfo() {
      return this;
    }
  }

  private static class SyntheticThrowingMethodInfo extends SyntheticMethodInfo {

    private final DexType errorType;
    private final FailedResolutionResult resolutionResult;

    SyntheticThrowingMethodInfo(
        ProgramMethod method, DexType errorType, FailedResolutionResult resolutionResult) {
      super(method);
      this.errorType = errorType;
      this.resolutionResult = resolutionResult;
    }

    DexType getErrorType() {
      return errorType;
    }

    FailedResolutionResult getResolutionResult() {
      return resolutionResult;
    }

    @Override
    SyntheticThrowingMethodInfo asThrowingMethodInfo() {
      return this;
    }
  }

  // Collection for method signatures that may cause forwarding methods to be created.
  private static class MethodSignatures {

    static final MethodSignatures EMPTY = new MethodSignatures(Collections.emptySet());

    static MethodSignatures create(Set<Wrapper<DexMethod>> signatures) {
      return signatures.isEmpty() ? EMPTY : new MethodSignatures(signatures);
    }

    final Set<Wrapper<DexMethod>> signatures;

    MethodSignatures(Set<Wrapper<DexMethod>> signatures) {
      this.signatures = Collections.unmodifiableSet(signatures);
    }

    MethodSignatures merge(MethodSignatures other) {
      if (isEmpty()) {
        return other;
      }
      if (other.isEmpty()) {
        return this;
      }
      Set<Wrapper<DexMethod>> merged = new HashSet<>(signatures);
      merged.addAll(other.signatures);
      return signatures.size() == merged.size() ? this : new MethodSignatures(merged);
    }

    boolean isEmpty() {
      return signatures.isEmpty();
    }

    public MethodSignatures withoutAll(MethodSignatures other) {
      Set<Wrapper<DexMethod>> merged = new HashSet<>(signatures);
      merged.removeAll(other.signatures);
      return signatures.size() == merged.size() ? this : new MethodSignatures(merged);
    }
  }

  // Collection of information known at the point of a given (non-library) class.
  // This info is immutable and shared as it is often the same on a significant part of the
  // class hierarchy. Thus, in the case of additions the parent pointer will contain prior info.
  private static class ClassInfo {

    static final ClassInfo EMPTY =
        new ClassInfo(null, ImmutableList.of(), EmulatedInterfaceInfo.EMPTY);

    final ClassInfo parent;

    // List of methods that are known to be forwarded to by a forwarding method at this point in the
    // class hierarchy. This set consists of the default interface methods, i.e., the targets of the
    // forwarding methods, *not* the forwarding methods themselves.
    final ImmutableList<DexClassAndMethod> forwardedMethodTargets;
    // If the forwarding methods for the emulated interface methods have not been added yet,
    // this contains the information to add it in the subclasses.
    final EmulatedInterfaceInfo emulatedInterfaceInfo;

    ClassInfo(
        ClassInfo parent,
        ImmutableList<DexClassAndMethod> forwardedMethodTargets,
        EmulatedInterfaceInfo emulatedInterfaceInfo) {
      this.parent = parent;
      this.forwardedMethodTargets = forwardedMethodTargets;
      this.emulatedInterfaceInfo = emulatedInterfaceInfo;
    }

    static ClassInfo create(
        ClassInfo parent,
        ImmutableList<DexClassAndMethod> forwardedMethodTargets,
        EmulatedInterfaceInfo emulatedInterfaceInfo) {
      return forwardedMethodTargets.isEmpty() && emulatedInterfaceInfo.isEmpty()
          ? parent
          : new ClassInfo(parent, forwardedMethodTargets, emulatedInterfaceInfo);
    }

    public boolean isEmpty() {
      return this == EMPTY;
    }

    @SuppressWarnings("ReferenceEquality")
    boolean isTargetedByForwards(DexClassAndMethod method) {
      return IterableUtils.any(
              forwardedMethodTargets,
              DexClassAndMember::getDefinition,
              definition -> definition == method.getDefinition())
          || (parent != null && parent.isTargetedByForwards(method));
    }
  }

  // Collection of information on what signatures and what emulated interfaces require
  // forwarding methods for library classes and interfaces.
  private static class SignaturesInfo {

    static final SignaturesInfo EMPTY =
        new SignaturesInfo(MethodSignatures.EMPTY, EmulatedInterfaceInfo.EMPTY);

    final MethodSignatures signatures;
    final EmulatedInterfaceInfo emulatedInterfaceInfo;

    private SignaturesInfo(
        MethodSignatures methodsToForward, EmulatedInterfaceInfo emulatedInterfaceInfo) {
      this.signatures = methodsToForward;
      this.emulatedInterfaceInfo = emulatedInterfaceInfo;
    }

    public SignaturesInfo merge(SignaturesInfo other) {
      if (isEmpty()) {
        return other;
      }
      if (other.isEmpty()) {
        return this;
      }
      return new SignaturesInfo(
          signatures.merge(other.signatures),
          emulatedInterfaceInfo.merge(other.emulatedInterfaceInfo));
    }

    public MethodSignatures emulatedInterfaceSignaturesToForward() {
      return emulatedInterfaceInfo.signatures.withoutAll(signatures);
    }

    boolean isEmpty() {
      return signatures.isEmpty() && emulatedInterfaceInfo.isEmpty();
    }

    public SignaturesInfo withSignatures(MethodSignatures additions) {
      if (additions.isEmpty()) {
        return this;
      }
      MethodSignatures newSignatures = signatures.merge(additions);
      return new SignaturesInfo(newSignatures, emulatedInterfaceInfo);
    }

    public SignaturesInfo withEmulatedInterfaceInfo(
        EmulatedInterfaceInfo additionalEmulatedInterfaceInfo) {
      if (additionalEmulatedInterfaceInfo.isEmpty()) {
        return this;
      }
      return new SignaturesInfo(
          signatures, emulatedInterfaceInfo.merge(additionalEmulatedInterfaceInfo));
    }
  }

  // Emulated interfaces together with the generic signatures.
  static class EmulatedInterfaces {
    static EmulatedInterfaces EMPTY = new EmulatedInterfaces(ImmutableSet.of());

    final Set<DexType> emulatedInterfaces;

    EmulatedInterfaces(DexType emulatedInterface) {
      this.emulatedInterfaces = ImmutableSet.of(emulatedInterface);
    }

    private EmulatedInterfaces(Set<DexType> emulatedInterfaces) {
      this.emulatedInterfaces = emulatedInterfaces;
    }

    boolean isEmpty() {
      return emulatedInterfaces.isEmpty();
    }

    boolean contains(DexType type) {
      return emulatedInterfaces.contains(type);
    }

    Set<DexType> getEmulatedInterfaces() {
      return emulatedInterfaces;
    }

    EmulatedInterfaces merge(EmulatedInterfaces other) {
      ImmutableSet.Builder<DexType> newEmulatedInterfaces = ImmutableSet.builder();
      newEmulatedInterfaces.addAll(emulatedInterfaces);
      newEmulatedInterfaces.addAll(other.emulatedInterfaces);
      return new EmulatedInterfaces(newEmulatedInterfaces.build());
    }
  }

  // List of emulated interfaces and corresponding signatures which may require forwarding methods.
  // If one of the signatures has an override, then the class holding the override is required to
  // add the forwarding methods for all signatures, and introduce the corresponding emulated
  // interface in its interfaces attribute for correct emulated dispatch.
  // If no override is present, then no forwarding methods are required, the class relies on the
  // default behavior of the emulated dispatch.
  private static class EmulatedInterfaceInfo {

    static final EmulatedInterfaceInfo EMPTY =
        new EmulatedInterfaceInfo(MethodSignatures.EMPTY, EmulatedInterfaces.EMPTY);

    final MethodSignatures signatures;
    final EmulatedInterfaces emulatedInterfaces;

    private EmulatedInterfaceInfo(
        MethodSignatures methodsToForward, EmulatedInterfaces emulatedInterfaces) {
      this.signatures = methodsToForward;
      this.emulatedInterfaces = emulatedInterfaces;
    }

    public EmulatedInterfaceInfo merge(EmulatedInterfaceInfo other) {
      if (isEmpty()) {
        return other;
      }
      if (other.isEmpty()) {
        return this;
      }
      return new EmulatedInterfaceInfo(
          signatures.merge(other.signatures), emulatedInterfaces.merge(other.emulatedInterfaces));
    }

    public boolean isEmpty() {
      assert !emulatedInterfaces.isEmpty() || signatures.isEmpty();
      return emulatedInterfaces.isEmpty();
    }

    boolean contains(DexType type) {
      return emulatedInterfaces.contains(type);
    }
  }

  // Helper to keep track of the direct active subclass and nearest program subclass for reporting.
  private static class ReportingContext {

    final DexClass directSubClass;
    final DexProgramClass closestProgramSubClass;
    final BiConsumer<DexProgramClass, DexType> reportMissingTypeCallback;

    public ReportingContext(
        DexClass directSubClass,
        DexProgramClass closestProgramSubClass,
        BiConsumer<DexProgramClass, DexType> reportMissingTypeCallback) {
      this.directSubClass = directSubClass;
      this.closestProgramSubClass = closestProgramSubClass;
      this.reportMissingTypeCallback = reportMissingTypeCallback;
    }

    ReportingContext forClass(DexClass directSubClass) {
      return new ReportingContext(
          directSubClass,
          directSubClass.isProgramClass()
              ? directSubClass.asProgramClass()
              : closestProgramSubClass,
          reportMissingTypeCallback);
    }

    public DexClass definitionFor(DexType type, AppView<?> appView) {
      return appView.appInfo().definitionForDesugarDependency(directSubClass, type);
    }

    public void reportMissingType(DexType missingType) {
      reportMissingTypeCallback.accept(closestProgramSubClass, missingType);
    }
  }

  // Specialized context to disable reporting when traversing the library structure.
  private static class LibraryReportingContext extends ReportingContext {

    static final LibraryReportingContext LIBRARY_CONTEXT = new LibraryReportingContext();

    LibraryReportingContext() {
      super(null, null, null);
    }

    @Override
    ReportingContext forClass(DexClass directSubClass) {
      return this;
    }

    @Override
    public DexClass definitionFor(DexType type, AppView<?> appView) {
      return appView.definitionFor(type);
    }

    @Override
    public void reportMissingType(DexType missingType) {
      // Ignore missing types in the library.
    }
  }

  private final AppView<?> appView;
  private final DexItemFactory dexItemFactory;
  private final InterfaceDesugaringSyntheticHelper helper;
  private final MethodSignatureEquivalence equivalence = MethodSignatureEquivalence.get();
  private final boolean needsLibraryInfo;
  private final Predicate<ProgramMethod> isLiveMethod;

  // Mapping from program and classpath classes to their information summary.
  private final Map<DexClass, ClassInfo> classInfo = new ConcurrentHashMap<>();

  // Mapping from library classes to their information summary.
  private final Map<DexLibraryClass, SignaturesInfo> libraryClassInfo = new ConcurrentHashMap<>();

  // Mapping from arbitrary interfaces to an information summary.
  private final Map<DexClass, SignaturesInfo> interfaceInfo = new ConcurrentHashMap<>();

  // Mapping from actual program classes to the synthesized forwarding methods to be created.
  private final Map<DexProgramClass, Map<DexMethod, SyntheticMethodInfo>> newSyntheticMethods =
      new ConcurrentHashMap<>();

  // Mapping from actual program classes to the extra interfaces needed for emulated dispatch.
  private final Map<DexProgramClass, List<ClassTypeSignature>> newExtraInterfaceSignatures =
      new ConcurrentHashMap<>();

  private final InterfaceMethodDesugaringMode desugaringMode;

  ClassProcessor(
      AppView<?> appView,
      Predicate<ProgramMethod> isLiveMethod,
      InterfaceMethodDesugaringMode desugaringMode) {
    this.appView = appView;
    this.dexItemFactory = appView.dexItemFactory();
    this.helper = new InterfaceDesugaringSyntheticHelper(appView);
    assert desugaringMode != NONE;
    needsLibraryInfo = !appView.options().machineDesugaredLibrarySpecification.isEmpty();
    this.isLiveMethod = isLiveMethod;
    this.desugaringMode = desugaringMode;
  }

  private boolean isLiveMethod(DexClassAndMethod method) {
    if (method.isProgramMethod()) {
      return isLiveMethod.test(method.asProgramMethod());
    }
    return true;
  }

  private boolean needsLibraryInfo() {
    return needsLibraryInfo;
  }

  private boolean ignoreLibraryInfo() {
    return !needsLibraryInfo;
  }

  public void process(
      DexProgramClass clazz, InterfaceProcessingDesugaringEventConsumer eventConsumer) {
    if (!clazz.isInterface()) {
      visitClassInfo(
          clazz,
          new ReportingContext(
              clazz,
              clazz,
              (context, missing) -> eventConsumer.warnMissingInterface(context, missing, helper)),
          eventConsumer);
    }
  }

  // We introduce forwarding methods only once all desugaring has been performed to avoid
  // confusing the look-up with inserted forwarding methods.
  public void finalizeProcessing(
      InterfaceProcessingDesugaringEventConsumer eventConsumer, ExecutorService executorService)
      throws ExecutionException {
    ThreadUtils.processMap(
        newSyntheticMethods,
        (clazz, infos) -> {
          List<DexEncodedMethod> sortedDefinitions = new ArrayList<>(infos.size());
          for (SyntheticMethodInfo info : infos.values()) {
            sortedDefinitions.add(info.getMethod().getDefinition());
          }
          sortedDefinitions.sort(Comparator.comparing(DexEncodedMember::getReference));
          clazz.addVirtualMethods(sortedDefinitions);
          for (DexEncodedMethod definition : sortedDefinitions) {
            SyntheticMethodInfo info = infos.get(definition.getReference());
            if (info.isForwardingMethodInfo()) {
              eventConsumer.acceptInterfaceMethodDesugaringForwardingMethod(
                  info.getMethod(), info.asForwardingMethodInfo().getBaseMethod());
            } else {
              SyntheticThrowingMethodInfo throwingMethodInfo = info.asThrowingMethodInfo();
              eventConsumer.acceptThrowingMethod(
                  info.getMethod(),
                  throwingMethodInfo.getErrorType(),
                  throwingMethodInfo.getResolutionResult());
            }
          }
        },
        appView.options().getThreadingModule(),
        executorService);
    newExtraInterfaceSignatures.forEach(
        (clazz, extraInterfaceSignatures) -> {
          if (!extraInterfaceSignatures.isEmpty()) {
            for (ClassTypeSignature signature : extraInterfaceSignatures) {
              eventConsumer.acceptEmulatedInterfaceMarkerInterface(
                  clazz, helper.ensureEmulatedInterfaceMarkerInterface(signature.type()));
            }
            clazz.addExtraInterfaces(extraInterfaceSignatures, appView.dexItemFactory());
          }
        });
  }

  @SuppressWarnings("ReferenceEquality")
  // Computes the set of method signatures that may need forwarding methods on derived classes.
  private SignaturesInfo computeInterfaceInfo(DexClass iface, SignaturesInfo interfaceInfo) {
    assert iface.isInterface();
    assert iface.superType == dexItemFactory.objectType;
    assert !helper.isEmulatedInterface(iface.type);
    if (desugaringMode == EMULATED_INTERFACE_ONLY) {
      return SignaturesInfo.EMPTY;
    }
    // Add non-library default methods as well as those for desugared library classes.
    if (!iface.isLibraryClass() || (needsLibraryInfo() && helper.isInDesugaredLibrary(iface))) {
      MethodSignatures signatures = getDefaultMethodsMatching(iface, m -> true);
      interfaceInfo = interfaceInfo.withSignatures(signatures);
    }
    return interfaceInfo;
  }

  @SuppressWarnings("ReferenceEquality")
  private SignaturesInfo computeEmulatedInterfaceInfo(
      DexClass iface, SignaturesInfo interfaceInfo) {
    assert iface.isInterface();
    assert iface.superType == dexItemFactory.objectType;
    assert helper.isEmulatedInterface(iface.type);
    assert needsLibraryInfo();
    MethodSignatures signatures =
        getDefaultMethodsMatching(
            iface,
            m ->
                appView
                        .options()
                        .machineDesugaredLibrarySpecification
                        .getEmulatedInterfaceEmulatedDispatchMethodDescriptor(m)
                    != null);
    EmulatedInterfaceInfo emulatedInterfaceInfo =
        new EmulatedInterfaceInfo(signatures, new EmulatedInterfaces(iface.type));
    return interfaceInfo.withEmulatedInterfaceInfo(emulatedInterfaceInfo);
  }

  private MethodSignatures getDefaultMethodsMatching(
      DexClass iface, Predicate<DexMethod> predicate) {
    assert iface.isInterface();
    Set<Wrapper<DexMethod>> defaultMethods =
        new HashSet<>(iface.getMethodCollection().numberOfVirtualMethods());
    for (DexEncodedMethod method : iface.virtualMethods(DexEncodedMethod::isDefaultMethod)) {
      if (predicate.test(method.getReference())) {
        defaultMethods.add(equivalence.wrap(method.getReference()));
      }
    }
    return MethodSignatures.create(defaultMethods);
  }

  @SuppressWarnings("UnusedVariable")
  // Computes the set of signatures of that may need forwarding methods on classes that derive
  // from a library class.
  private SignaturesInfo computeLibraryClassInfo(DexLibraryClass clazz, SignaturesInfo signatures) {
    // The result is the identity as the library class does not itself contribute to the set.
    return signatures;
  }

  // The computation of a class information and the insertions of forwarding methods.
  private ClassInfo computeClassInfo(
      DexClass clazz,
      ClassInfo superInfo,
      SignaturesInfo signatureInfo,
      InterfaceProcessingDesugaringEventConsumer eventConsumer) {
    ImmutableList.Builder<DexClassAndMethod> additionalForwards = ImmutableList.builder();
    // First we deal with non-emulated interface desugaring.
    resolveForwardingMethods(
        clazz, superInfo, signatureInfo.signatures, additionalForwards, eventConsumer);
    // Second we deal with emulated interface, if one method has override in the current class,
    // we resolve them, else we propagate the emulated interface info down.
    if (shouldResolveForwardingMethodsForEmulatedInterfaces(
        clazz, signatureInfo.emulatedInterfaceInfo)) {
      resolveForwardingMethods(
          clazz,
          superInfo,
          signatureInfo.emulatedInterfaceSignaturesToForward(),
          additionalForwards,
          eventConsumer);
      duplicateEmulatedInterfaces(clazz, signatureInfo.emulatedInterfaceInfo.emulatedInterfaces);
      return ClassInfo.create(superInfo, additionalForwards.build(), EmulatedInterfaceInfo.EMPTY);
    }
    return ClassInfo.create(
        superInfo, additionalForwards.build(), signatureInfo.emulatedInterfaceInfo);
  }

  @SuppressWarnings("ReferenceEquality")
  // All classes implementing an emulated interface and overriding a default method should now
  // implement the interface and the emulated one for correct emulated dispatch.
  // The class signature won't include the correct type parameters for the duplicated interfaces,
  // i.e., there will be foo.A instead of foo.A<K,V>, but such parameters are unused.
  private void duplicateEmulatedInterfaces(DexClass clazz, EmulatedInterfaces emulatedInterfaces) {
    if (clazz.isNotProgramClass()) {
      return;
    }
    Set<DexType> filtered = new HashSet<>(emulatedInterfaces.getEmulatedInterfaces());
    WorkList<DexType> workList = WorkList.newIdentityWorkList();
    for (DexType emulatedInterface : emulatedInterfaces.getEmulatedInterfaces()) {
      DexClass iface = appView.definitionFor(emulatedInterface);
      if (iface != null) {
        assert iface.isLibraryClass()
            || appView.options().machineDesugaredLibrarySpecification.isLibraryCompilation();
        workList.addIfNotSeen(iface.getInterfaces());
      }
    }
    while (workList.hasNext()) {
      DexType type = workList.next();
      filtered.remove(type);
      DexClass iface = appView.definitionFor(type);
      if (iface == null) {
        continue;
      }
      workList.addIfNotSeen(iface.getInterfaces());
    }

    for (DexType emulatedInterface : emulatedInterfaces.getEmulatedInterfaces()) {
      DexClass s = appView.definitionFor(emulatedInterface);
      if (s != null) {
        s = appView.definitionFor(s.superType);
      }
      while (s != null && s.getType() != appView.dexItemFactory().objectType) {
        filtered.remove(s.getType());
        s = appView.definitionFor(s.getSuperType());
      }
    }

    // Collect the signatures for the emulated interfaces to add.
    Map<DexType, GenericSignature.ClassTypeSignature> signatures = new IdentityHashMap<>();
    collectEmulatedInterfaces(clazz, filtered, signatures);
    // We need to introduce them in deterministic order for deterministic compilation.
    ArrayList<DexType> sortedEmulatedInterfaces = new ArrayList<>(filtered);
    Collections.sort(sortedEmulatedInterfaces);
    List<GenericSignature.ClassTypeSignature> extraInterfaceSignatures = new ArrayList<>();
    for (DexType extraInterface : sortedEmulatedInterfaces) {
      GenericSignature.ClassTypeSignature signature = signatures.get(extraInterface);
      assert signature != null;
      extraInterfaceSignatures.add(signature);
    }
    // The emulated interface might already be implemented if the input class has gone through
    // library desugaring already.
    clazz
        .getInterfaces()
        .forEach(
            iface -> {
              for (int i = 0; i < extraInterfaceSignatures.size(); i++) {
                if (extraInterfaceSignatures.get(i).type() == iface) {
                  if (!appView.options().desugarSpecificOptions().allowAllDesugaredInput) {
                    throw new CompilationError(
                        "Code has already been library desugared. Interface "
                            + iface.getDescriptor()
                            + " is already implemented by "
                            + clazz.getType().getDescriptor());
                  }
                  extraInterfaceSignatures.remove(i);
                  break;
                }
              }
            });
    newExtraInterfaceSignatures.put(clazz.asProgramClass(), extraInterfaceSignatures);
  }

  private void collectEmulatedInterfaces(
      DexClass clazz,
      Set<DexType> emulatesInterfaces,
      Map<DexType, GenericSignature.ClassTypeSignature> extraInterfaceSignatures) {
    // TODO(b/182329331): Only handle type arguments for Cf to Cf desugar.
    if (appView.options().isCfDesugaring() && clazz.validInterfaceSignatures()) {
      clazz.forEachImmediateSupertypeWithSignature(
          appView.dexItemFactory(),
          (type, signature) -> {
            if (emulatesInterfaces.contains(type)) {
              extraInterfaceSignatures.put(
                  type,
                  new GenericSignature.ClassTypeSignature(
                      helper.getEmulatedInterface(type), signature.typeArguments()));
            }
            collectEmulatedInterfacesWithPropagatedTypeArguments(
                type, signature.typeArguments(), emulatesInterfaces, extraInterfaceSignatures);
          });
    } else {
      clazz.forEachImmediateSupertype(
          type -> {
            if (emulatesInterfaces.contains(type)) {
              extraInterfaceSignatures.put(
                  type, new GenericSignature.ClassTypeSignature(helper.getEmulatedInterface(type)));
            }
            collectEmulatedInterfacesWithPropagatedTypeArguments(
                type, null, emulatesInterfaces, extraInterfaceSignatures);
          });
    }
  }

  private void collectEmulatedInterfacesWithPropagatedTypeArguments(
      DexType type,
      List<GenericSignature.FieldTypeSignature> typeArguments,
      Set<DexType> emulatesInterfaces,
      Map<DexType, GenericSignature.ClassTypeSignature> extraInterfaceSignatures) {
    DexClass clazz = appView.definitionFor(type);
    if (clazz == null) {
      return;
    }
    // TODO(b/182329331): Only handle type arguments for Cf to Cf desugar.
    if (appView.options().isCfDesugaring() && clazz.validInterfaceSignatures()) {
      assert typeArguments != null;
      clazz.forEachImmediateSupertypeWithAppliedTypeArguments(
          typeArguments,
          (iface, signature) -> {
            if (emulatesInterfaces.contains(iface)) {
              extraInterfaceSignatures.put(
                  iface,
                  new GenericSignature.ClassTypeSignature(
                      helper.getEmulatedInterface(iface), signature));
            }
            collectEmulatedInterfacesWithPropagatedTypeArguments(
                iface, signature, emulatesInterfaces, extraInterfaceSignatures);
          });
    } else {
      assert typeArguments == null;
      clazz.forEachImmediateSupertype(
          iface -> {
            if (emulatesInterfaces.contains(iface)) {
              extraInterfaceSignatures.put(
                  iface,
                  new GenericSignature.ClassTypeSignature(helper.getEmulatedInterface(iface)));
            }
            collectEmulatedInterfacesWithPropagatedTypeArguments(
                iface, null, emulatesInterfaces, extraInterfaceSignatures);
          });
    }
  }

  // If any of the signature would lead to a different behavior than the default method on the
  // emulated interface, we need to resolve the forwarding methods.
  private boolean shouldResolveForwardingMethodsForEmulatedInterfaces(
      DexClass clazz, EmulatedInterfaceInfo emulatedInterfaceInfo) {
    AppInfoWithClassHierarchy appInfo = appView.appInfoForDesugaring();
    for (Wrapper<DexMethod> signature : emulatedInterfaceInfo.signatures.signatures) {
      MethodResolutionResult resolutionResult =
          appInfo.resolveMethodOnClassLegacy(clazz, signature.get());
      if (resolutionResult.isFailedResolution()) {
        return true;
      }
      DexClass resolvedHolder = resolutionResult.asSingleResolution().getResolvedHolder();
      if (!resolvedHolder.isLibraryClass()
          && !emulatedInterfaceInfo.contains(resolvedHolder.type)) {
        return true;
      }
      // If the method overrides an abstract method which does not match the criteria, then
      // forwarding methods are required so the invokes on the top level holder can resolve at
      // runtime. See b/202188674.
      if (overridesAbstractNonLibraryInterfaceMethod(
          clazz, signature.get(), emulatedInterfaceInfo)) {
        return true;
      }
    }
    return false;
  }

  @SuppressWarnings("BadImport")
  private boolean overridesAbstractNonLibraryInterfaceMethod(
      DexClass clazz, DexMethod dexMethod, EmulatedInterfaceInfo emulatedInterfaceInfo) {
    List<Entry<DexClass, DexEncodedMethod>> abstractInterfaceMethods =
        appView.appInfoForDesugaring().getAbstractInterfaceMethods(clazz, dexMethod);
    for (Entry<DexClass, DexEncodedMethod> classAndMethod : abstractInterfaceMethods) {
      DexClass foundMethodClass = classAndMethod.getKey();
      if (!foundMethodClass.isLibraryClass()
          && !emulatedInterfaceInfo.contains(foundMethodClass.type)) {
        return true;
      }
    }
    return false;
  }

  @SuppressWarnings("BadImport")
  private void resolveForwardingMethods(
      DexClass clazz,
      ClassInfo superInfo,
      MethodSignatures signatures,
      Builder<DexClassAndMethod> additionalForwards,
      InterfaceProcessingDesugaringEventConsumer eventConsumer) {
    if (clazz.isProgramClass() && appView.isAlreadyLibraryDesugared(clazz.asProgramClass())) {
      return;
    }
    for (Wrapper<DexMethod> wrapper : signatures.signatures) {
      resolveForwardForSignature(
          clazz, superInfo, additionalForwards, wrapper.get(), eventConsumer);
    }
  }

  // Looks up a method signature from the point of 'clazz', if it can dispatch to a default method
  // the 'addForward' call-back is called with the target of the forward.
  @SuppressWarnings("BadImport")
  private void resolveForwardForSignature(
      DexClass clazz,
      ClassInfo superInfo,
      Builder<DexClassAndMethod> additionalForwards,
      DexMethod method,
      InterfaceProcessingDesugaringEventConsumer eventConsumer) {
    AppInfoWithClassHierarchy appInfo = appView.appInfoForDesugaring();
    MethodResolutionResult resolutionResult = appInfo.resolveMethodOnLegacy(clazz, method);
    if (resolutionResult.isFailedResolution()
        || resolutionResult.asSuccessfulMemberResolutionResult().getResolvedMember().isStatic()) {
      // When doing resolution we may find a static or private targets and bubble up the failed
      // resolution to preserve ICCE even though the resolution actually succeeded, ie. finding a
      // method with the same name and descriptor. For invoke-virtual and invoke-interface, the
      // selected method will only consider instance methods.
      BooleanBox staticTarget = new BooleanBox(true);
      if (resolutionResult.isFailedResolution()) {
        resolutionResult
            .asFailedResolution()
            .forEachFailureDependency(
                ConsumerUtils.emptyConsumer(), target -> staticTarget.and(target.isStatic()));
      } else if (resolutionResult.isSuccessfulMemberResolutionResult()) {
        staticTarget.set(
            resolutionResult.asSuccessfulMemberResolutionResult().getResolvedMember().isStatic());
      }
      if (staticTarget.isAssigned() && staticTarget.isTrue()) {
        resolutionResult = appInfo.resolveMethodOnInterfaceLegacy(method.holder, method);
      }
      if (resolutionResult.isFailedResolution()) {
        if (resolutionResult.isIncompatibleClassChangeErrorResult()) {
          addICCEThrowingMethod(method, clazz, resolutionResult.asFailedResolution());
          return;
        }
        if (resolutionResult.isNoSuchMethodErrorResult(clazz, appView, appInfo)) {
          addNoSuchMethodErrorThrowingMethod(method, clazz, resolutionResult.asFailedResolution());
          return;
        }
        assert resolutionResult.isIllegalAccessErrorResult(clazz, appView, appInfo);
        addIllegalAccessErrorThrowingMethod(method, clazz, resolutionResult.asFailedResolution());
        return;
      }
    }
    assert resolutionResult.isSuccessfulMemberResolutionResult();
    LookupMethodTarget lookupMethodTarget =
        resolutionResult.lookupVirtualDispatchTarget(clazz, appInfo);
    if (lookupMethodTarget == null) {
      // This should not happen, but is does in b/237507594.
      // We have no reproduction for the issue.
      // We try to raise a warning here to help investigating:
      // It could be an issue related to array#clone, or this can be due to a missing/invalid class.
      assert false;
      appView
          .options()
          .reporter
          .warning(
              "The class processor was not able to look-up the default method "
                  + method
                  + " in the class "
                  + clazz
                  + " (Single resolution: "
                  + resolutionResult.isSingleResolution()
                  + "; resolution pair: "
                  + resolutionResult.getResolutionPair()
                  + "). Please report this issue in the D8/R8 bug tracker at"
                  + " https://issuetracker.google.com/issues/237507594.");
      // To be able to resume compilation we add a NoSuchMethodErrorThrowingMethod.
      addNoSuchMethodErrorThrowingMethod(
          method, clazz, NoSuchMethodResult.getEmptyNoSuchMethodResult());
      return;
    }
    DexClassAndMethod virtualDispatchTarget = lookupMethodTarget.getTarget();
    assert virtualDispatchTarget != null;

    if (!isLiveMethod(virtualDispatchTarget)
        || superInfo.isTargetedByForwards(virtualDispatchTarget)) {
      return;
    }

    // If resolution targets a default interface method, forward it.
    DexMethod forward = null;
    if (virtualDispatchTarget.isDefaultMethod()) {
      forward =
          helper
              .ensureDefaultAsMethodOfCompanionClassStub(virtualDispatchTarget, eventConsumer)
              .getReference();
    } else {
      DerivedMethod forwardingMethod =
          helper.computeEmulatedInterfaceForwardingMethod(
              virtualDispatchTarget.getHolder(), virtualDispatchTarget);
      if (forwardingMethod != null) {
        forward = helper.ensureEmulatedInterfaceForwardingMethod(forwardingMethod, eventConsumer);
      }
    }

    if (forward != null) {
      additionalForwards.add(virtualDispatchTarget);
      addForwardingMethod(virtualDispatchTarget, forward, clazz);
    }
  }

  private void addSyntheticForwardingMethod(ProgramMethod method, DexClassAndMethod baseMethod) {
    SyntheticMethodInfo existingMethodInfo =
        newSyntheticMethods
            .computeIfAbsent(method.getHolder(), key -> new ConcurrentHashMap<>())
            .put(method.getReference(), new SyntheticForwardingMethodInfo(method, baseMethod));
    assert existingMethodInfo == null;
  }

  private void addSyntheticThrowingMethod(
      ProgramMethod method, DexType errorType, FailedResolutionResult resolutionResult) {
    SyntheticMethodInfo existingMethodInfo =
        newSyntheticMethods
            .computeIfAbsent(method.getHolder(), key -> new ConcurrentHashMap<>())
            .put(
                method.getReference(),
                new SyntheticThrowingMethodInfo(method, errorType, resolutionResult));
    assert existingMethodInfo == null;
  }

  private void addICCEThrowingMethod(
      DexMethod method, DexClass clazz, FailedResolutionResult resolutionResult) {
    addThrowingMethod(method, clazz, dexItemFactory.icceType, resolutionResult);
  }

  private void addIllegalAccessErrorThrowingMethod(
      DexMethod method, DexClass clazz, FailedResolutionResult resolutionResult) {
    addThrowingMethod(method, clazz, dexItemFactory.illegalAccessErrorType, resolutionResult);
  }

  private void addNoSuchMethodErrorThrowingMethod(
      DexMethod method, DexClass clazz, FailedResolutionResult resolutionResult) {
    addThrowingMethod(method, clazz, dexItemFactory.noSuchMethodErrorType, resolutionResult);
  }

  private void addThrowingMethod(
      DexMethod method,
      DexClass clazz,
      DexType errorType,
      FailedResolutionResult resolutionResult) {
    if (!clazz.isProgramClass()) {
      return;
    }
    MethodAccessFlags accessFlags = MethodAccessFlags.builder().setPublic().build();
    DexMethod newMethod = method.withHolder(clazz.getType(), dexItemFactory);
    DexEncodedMethod newEncodedMethod =
        DexEncodedMethod.syntheticBuilder()
            .setMethod(newMethod)
            .setAccessFlags(accessFlags)
            .setCode(
                createExceptionThrowingCfCode(newMethod, accessFlags, errorType, dexItemFactory))
            .disableAndroidApiLevelCheck()
            .build();
    addSyntheticThrowingMethod(
        newEncodedMethod.asProgramMethod(clazz.asProgramClass()), errorType, resolutionResult);
  }

  private static CfCode createExceptionThrowingCfCode(
      DexMethod method,
      MethodAccessFlags accessFlags,
      DexType exceptionType,
      DexItemFactory dexItemFactory) {
    DexMethod instanceInitializer =
        dexItemFactory.createMethod(
            exceptionType,
            dexItemFactory.createProto(dexItemFactory.voidType),
            dexItemFactory.constructorMethodName);
    int maxStack = 2;
    int maxLocals = method.getParameters().size() + BooleanUtils.intValue(!accessFlags.isStatic());
    return new CfCode(
        method.getHolderType(),
        maxStack,
        maxLocals,
        ImmutableList.of(
            new CfNew(exceptionType),
            new CfStackInstruction(Opcode.Dup),
            new CfInvoke(Opcodes.INVOKESPECIAL, instanceInitializer, false),
            new CfThrow()));
  }

  // Note: The parameter 'target' may be a public method on a class in case of desugared
  // library retargeting (See below target.isInterface check).
  private void addForwardingMethod(
      DexClassAndMethod target, DexMethod forwardMethod, DexClass clazz) {
    if (!clazz.isProgramClass()) {
      return;
    }

    DexEncodedMethod methodOnSelf = clazz.lookupMethod(target.getReference());
    if (methodOnSelf != null) {
      throw new CompilationError(
          "Attempt to add forwarding method that conflicts with existing method.",
          null,
          clazz.getOrigin(),
          new MethodPosition(methodOnSelf.getReference().asMethodReference()));
    }

    // NOTE: Never add a forwarding method to methods of classes unknown or coming from android.jar
    // even if this results in invalid code, these classes are never desugared.
    // In desugared library, emulated interface methods can be overridden by retarget lib members.
    DexEncodedMethod desugaringForwardingMethod =
        DexEncodedMethod.createDesugaringForwardingMethod(
            target, clazz, forwardMethod, dexItemFactory);
    if (!target.isProgramMethod() || target.getDefinition().isLibraryMethodOverride().isTrue()) {
      desugaringForwardingMethod.setLibraryMethodOverride(OptionalBool.TRUE);
    }
    addSyntheticForwardingMethod(
        desugaringForwardingMethod.asProgramMethod(clazz.asProgramClass()), target);
  }

  @SuppressWarnings("ReferenceEquality")
  // Topological order traversal and its helpers.
  private DexClass definitionOrNull(DexType type, ReportingContext context) {
    // No forwards at the top of the class hierarchy (assuming java.lang.Object is never amended).
    if (type == null || type == dexItemFactory.objectType) {
      return null;
    }
    DexClass clazz = context.definitionFor(type, appView);
    if (clazz == null) {
      context.reportMissingType(type);
      return null;
    }
    return clazz;
  }

  // We cannot use ConcurrentHashMap computeIfAbsent because the calls are recursive ending in
  // concurrent modification of the ConcurrentHashMap while performing computeIfAbsent.
  private <C extends DexClass, I> I reentrantComputeIfAbsent(
      Map<C, I> infoMap, C clazz, Function<C, I> supplier) {
    // Try to avoid the synchronization when possible.
    I computedInfo = infoMap.get(clazz);
    if (computedInfo != null) {
      return computedInfo;
    }
    synchronized (clazz) {
      computedInfo = infoMap.get(clazz);
      if (computedInfo != null) {
        return computedInfo;
      }
      computedInfo = supplier.apply(clazz);
      infoMap.put(clazz, computedInfo);
      return computedInfo;
    }
  }

  private ClassInfo visitClassInfo(
      DexType type,
      ReportingContext context,
      InterfaceProcessingDesugaringEventConsumer eventConsumer) {
    DexClass clazz = definitionOrNull(type, context);
    return clazz == null ? ClassInfo.EMPTY : visitClassInfo(clazz, context, eventConsumer);
  }

  private ClassInfo visitClassInfo(
      DexClass clazz,
      ReportingContext context,
      InterfaceProcessingDesugaringEventConsumer eventConsumer) {
    assert !clazz.isInterface();
    if (clazz.isLibraryClass()) {
      return ClassInfo.EMPTY;
    }
    return reentrantComputeIfAbsent(
        classInfo, clazz, key -> visitClassInfoRaw(key, context, eventConsumer));
  }

  private ClassInfo visitClassInfoRaw(
      DexClass clazz,
      ReportingContext context,
      InterfaceProcessingDesugaringEventConsumer eventConsumer) {
    // We compute both library and class information, but one of them is empty, since a class is
    // a library class or is not, but cannot be both.
    ReportingContext thisContext = context.forClass(clazz);
    ClassInfo superInfo = visitClassInfo(clazz.superType, thisContext, eventConsumer);
    SignaturesInfo signatures = visitLibraryClassInfo(clazz.superType);
    // The class may inherit emulated interface info from its program superclass if the latter
    // did not require to resolve the forwarding methods for emualted interfaces.
    assert superInfo.isEmpty() || signatures.isEmpty();
    signatures = signatures.withEmulatedInterfaceInfo(superInfo.emulatedInterfaceInfo);
    for (DexType iface : clazz.interfaces.values) {
      signatures = signatures.merge(visitInterfaceInfo(iface, thisContext));
    }
    return computeClassInfo(clazz, superInfo, signatures, eventConsumer);
  }

  private SignaturesInfo visitLibraryClassInfo(DexType type) {
    // No desugaring required, no library class analysis.
    if (ignoreLibraryInfo()) {
      return SignaturesInfo.EMPTY;
    }
    DexClass clazz = definitionOrNull(type, LibraryReportingContext.LIBRARY_CONTEXT);
    return clazz == null ? SignaturesInfo.EMPTY : visitLibraryClassInfo(clazz);
  }

  private SignaturesInfo visitLibraryClassInfo(DexClass clazz) {
    assert !clazz.isInterface();
    return clazz.isLibraryClass()
        ? reentrantComputeIfAbsent(
            libraryClassInfo, clazz.asLibraryClass(), this::visitLibraryClassInfoRaw)
        : SignaturesInfo.EMPTY;
  }

  private SignaturesInfo visitLibraryClassInfoRaw(DexLibraryClass clazz) {
    SignaturesInfo signatures = visitLibraryClassInfo(clazz.superType);
    for (DexType iface : clazz.interfaces.values) {
      signatures =
          signatures.merge(visitInterfaceInfo(iface, LibraryReportingContext.LIBRARY_CONTEXT));
    }
    return computeLibraryClassInfo(clazz, signatures);
  }

  private SignaturesInfo visitInterfaceInfo(DexType iface, ReportingContext context) {
    DexClass definition = definitionOrNull(iface, context);
    return definition == null ? SignaturesInfo.EMPTY : visitInterfaceInfo(definition, context);
  }

  private SignaturesInfo visitInterfaceInfo(DexClass iface, ReportingContext context) {
    if (iface.isLibraryClass() && ignoreLibraryInfo()) {
      return SignaturesInfo.EMPTY;
    }
    return reentrantComputeIfAbsent(
        interfaceInfo, iface, key -> visitInterfaceInfoRaw(key, context));
  }

  private SignaturesInfo visitInterfaceInfoRaw(DexClass iface, ReportingContext context) {
    ReportingContext thisContext = context.forClass(iface);
    SignaturesInfo interfaceInfo = SignaturesInfo.EMPTY;
    for (DexType superiface : iface.interfaces.values) {
      interfaceInfo = interfaceInfo.merge(visitInterfaceInfo(superiface, thisContext));
    }
    return helper.isEmulatedInterface(iface.type)
        ? computeEmulatedInterfaceInfo(iface, interfaceInfo)
        : computeInterfaceInfo(iface, interfaceInfo);
  }
}
