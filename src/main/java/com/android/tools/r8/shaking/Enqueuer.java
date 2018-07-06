// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import static com.android.tools.r8.naming.IdentifierNameStringUtils.identifyIdentiferNameString;
import static com.android.tools.r8.naming.IdentifierNameStringUtils.isReflectionMethod;
import static com.android.tools.r8.naming.IdentifierNameStringUtils.warnUndeterminedIdentifier;
import static com.android.tools.r8.shaking.ProguardConfigurationUtils.buildIdentifierNameStringRule;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppInfo.ResolutionResult;
import com.android.tools.r8.graph.AppInfoWithSubtyping;
import com.android.tools.r8.graph.Descriptor;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItem;
import com.android.tools.r8.graph.DexItemBasedString;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.DirectMappedDexApplication;
import com.android.tools.r8.graph.GraphLense;
import com.android.tools.r8.graph.KeyedDexItem;
import com.android.tools.r8.graph.PresortedComparable;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.Invoke.Type;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.logging.Log;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.shaking.RootSetBuilder.ConsequentRootSet;
import com.android.tools.r8.shaking.RootSetBuilder.RootSet;
import com.android.tools.r8.shaking.protolite.ProtoLiteExtension;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Approximates the runtime dependencies for the given set of roots.
 * <p>
 * <p>The implementation filters the static call-graph with liveness information on classes to
 * remove virtual methods that are reachable by their static type but are unreachable at runtime as
 * they are not visible from any instance.
 * <p>
 * <p>As result of the analysis, an instance of {@link AppInfoWithLiveness} is returned. See the
 * field descriptions for details.
 */
public class Enqueuer {

  private final boolean forceProguardCompatibility;
  private boolean tracingMainDex = false;

  private final AppInfoWithSubtyping appInfo;
  private final InternalOptions options;
  private RootSet rootSet;

  private final Map<DexType, Set<DexMethod>> virtualInvokes = Maps.newIdentityHashMap();
  private final Map<DexType, Set<DexMethod>> interfaceInvokes = Maps.newIdentityHashMap();
  private final Map<DexType, Set<TargetWithContext<DexMethod>>> superInvokes =
      Maps.newIdentityHashMap();
  private final Map<DexType, Set<DexMethod>> directInvokes = Maps.newIdentityHashMap();
  private final Map<DexType, Set<DexMethod>> staticInvokes = Maps.newIdentityHashMap();
  private final Map<DexType, Set<TargetWithContext<DexField>>> instanceFieldsWritten =
      Maps.newIdentityHashMap();
  private final Map<DexType, Set<TargetWithContext<DexField>>> instanceFieldsRead =
      Maps.newIdentityHashMap();
  private final Map<DexType, Set<TargetWithContext<DexField>>> staticFieldsRead =
      Maps.newIdentityHashMap();
  private final Map<DexType, Set<TargetWithContext<DexField>>> staticFieldsWritten =
      Maps.newIdentityHashMap();

  private final ProtoLiteExtension protoLiteExtension;
  private final Set<DexField> protoLiteFields = Sets.newIdentityHashSet();
  private final Set<DexItem> identifierNameStrings = Sets.newIdentityHashSet();

  /**
   * Set of method signatures used in invoke-super instructions that either cannot be resolved or
   * resolve to a private method (leading to an IllegalAccessError).
   */
  private final Set<DexMethod> brokenSuperInvokes = Sets.newIdentityHashSet();
  /**
   * This map keeps a view of all virtual methods that are reachable from virtual invokes. A method
   * is reachable even if no live subtypes exist, so this is not sufficient for inclusion in the
   * live set.
   */
  private final Map<DexType, SetWithReason<DexEncodedMethod>> reachableVirtualMethods = Maps
      .newIdentityHashMap();
  /**
   * Tracks the dependency between a method and the super-method it calls, if any. Used to make
   * super methods become live when they become reachable from a live sub-method.
   */
  private final Map<DexEncodedMethod, Set<DexEncodedMethod>> superInvokeDependencies = Maps
      .newIdentityHashMap();
  /**
   * Set of instance fields that can be reached by read/write operations.
   */
  private final Map<DexType, SetWithReason<DexEncodedField>> reachableInstanceFields = Maps
      .newIdentityHashMap();

  /**
   * Set of types that are mentioned in the program. We at least need an empty abstract class item
   * for these.
   */
  private final Set<DexType> liveTypes = Sets.newIdentityHashSet();
  /**
   * Set of types that are actually instantiated. These cannot be abstract.
   */
  private final SetWithReason<DexType> instantiatedTypes = new SetWithReason<>();
  /**
   * Set of methods that are the immediate target of an invoke. They might not actually be live but
   * are required so that invokes can find the method. If a method is only a target but not live,
   * its implementation may be removed and it may be marked abstract.
   */
  private final SetWithReason<DexEncodedMethod> targetedMethods = new SetWithReason<>();
  /**
   * Set of methods that belong to live classes and can be reached by invokes. These need to be
   * kept.
   */
  private final SetWithReason<DexEncodedMethod> liveMethods = new SetWithReason<>();

  /**
   * Set of fields that belong to live classes and can be reached by invokes. These need to be
   * kept.
   */
  private final SetWithReason<DexEncodedField> liveFields = new SetWithReason<>();

  /**
   * Set of interface types for which a lambda expression can be reached. These never have a single
   * interface implementation.
   */
  private final SetWithReason<DexType> instantiatedLambdas = new SetWithReason<>();

  /**
   * A queue of items that need processing. Different items trigger different actions:
   */
  private final Queue<Action> workList = Queues.newArrayDeque();

  /**
   * A queue of items that have been added to try to keep Proguard compatibility.
   */
  private final Queue<Action> proguardCompatibilityWorkList = Queues.newArrayDeque();

  /**
   * A set of methods that need code inspection for Proguard compatibility rules.
   */
  private final Set<DexEncodedMethod> pendingProguardReflectiveCompatibility =
      Sets.newLinkedHashSet();

  /**
   * A cache for DexMethod that have been marked reachable.
   */
  private final Set<DexMethod> virtualTargetsMarkedAsReachable = Sets.newIdentityHashSet();

  /**
   * A set of dexitems we have reported missing to dedupe warnings.
   */
  private final Set<DexItem> reportedMissing = Sets.newIdentityHashSet();

  /**
   * A set of items that we are keeping due to keep rules. This may differ from the rootSet due to
   * dependent keep rules.
   */
  private final Set<DexItem> pinnedItems = Sets.newIdentityHashSet();

  /**
   * A map from classes to annotations that need to be processed should the classes ever become
   * live.
   */
  private final Map<DexType, Set<DexAnnotation>> deferredAnnotations = new IdentityHashMap<>();

  /**
   * Set of keep rules generated for Proguard compatibility in Proguard compatibility mode.
   */
  private final ProguardConfiguration.Builder compatibility;

  public Enqueuer(AppInfoWithSubtyping appInfo, InternalOptions options,
      boolean forceProguardCompatibility) {
    this(appInfo, options, forceProguardCompatibility, null, null);
  }

  public Enqueuer(AppInfoWithSubtyping appInfo, InternalOptions options,
      boolean forceProguardCompatibility,
      ProguardConfiguration.Builder compatibility, ProtoLiteExtension protoLiteExtension) {
    this.appInfo = appInfo;
    this.compatibility = compatibility;
    this.options = options;
    this.protoLiteExtension = protoLiteExtension;
    this.forceProguardCompatibility = forceProguardCompatibility;
  }

  private void enqueueRootItems(Map<DexItem, ProguardKeepRule> items) {
    items.entrySet().forEach(this::enqueueRootItem);
    pinnedItems.addAll(items.keySet());
  }

  private void enqueueRootItem(Map.Entry<DexItem, ProguardKeepRule> root) {
    DexItem item = root.getKey();
    KeepReason reason = KeepReason.dueToKeepRule(root.getValue());
    if (item instanceof DexClass) {
      DexClass clazz = (DexClass) item;
      workList.add(Action.markInstantiated(clazz, reason));
      if (forceProguardCompatibility && clazz.hasDefaultInitializer()) {
        ProguardKeepRule rule = ProguardConfigurationUtils.buildDefaultInitializerKeepRule(clazz);
        proguardCompatibilityWorkList.add(Action.markMethodLive(
            clazz.getDefaultInitializer(), KeepReason.dueToProguardCompatibilityKeepRule(rule)));
      }
    } else if (item instanceof DexEncodedField) {
      workList.add(Action.markFieldKept((DexEncodedField) item, reason));
    } else if (item instanceof DexEncodedMethod) {
      workList.add(Action.markMethodKept((DexEncodedMethod) item, reason));
    } else {
      throw new IllegalArgumentException(item.toString());
    }
  }

  //
  // Things to do with registering events. This is essentially the interface for byte-code
  // traversals.
  //

  private <S extends DexItem, T extends Descriptor<S, T>> boolean registerItemWithTarget(
      Map<DexType, Set<T>> seen, T item) {
    DexType holder = item.getHolder();
    if (holder.isArrayType()) {
      holder = holder.toBaseType(appInfo.dexItemFactory);
    }
    if (!holder.isClassType()) {
      return false;
    }
    markTypeAsLive(holder);
    return seen.computeIfAbsent(item.getHolder(), (ignore) -> Sets.newIdentityHashSet()).add(item);
  }

  private <S extends DexItem, T extends Descriptor<S, T>> boolean registerItemWithTargetAndContext(
      Map<DexType, Set<TargetWithContext<T>>> seen, T item, DexEncodedMethod context) {
    DexType holder = item.getHolder();
    if (holder.isArrayType()) {
      holder = holder.toBaseType(appInfo.dexItemFactory);
    }
    if (!holder.isClassType()) {
      return false;
    }
    markTypeAsLive(holder);
    return seen.computeIfAbsent(item.getHolder(), (ignore) -> new HashSet<>())
        .add(new TargetWithContext<>(item, context));
  }

  private class UseRegistry extends com.android.tools.r8.graph.UseRegistry {

    private final DexEncodedMethod currentMethod;

    private UseRegistry(DexEncodedMethod currentMethod) {
      this.currentMethod = currentMethod;
    }

    @Override
    public boolean registerInvokeVirtual(DexMethod method) {
      if (appInfo.dexItemFactory.classMethods.isReflectiveMemberLookup(method)) {
        if (forceProguardCompatibility) {
          // TODO(b/76181966): whether or not add this rule in normal mode.
          if (identifierNameStrings.add(method) && compatibility != null) {
            compatibility.addRule(buildIdentifierNameStringRule(method));
          }
          pendingProguardReflectiveCompatibility.add(currentMethod);
        }
      }
      if (!registerItemWithTarget(virtualInvokes, method)) {
        return false;
      }
      if (Log.ENABLED) {
        Log.verbose(getClass(), "Register invokeVirtual `%s`.", method);
      }
      workList.add(Action.markReachableVirtual(method, KeepReason.invokedFrom(currentMethod)));
      return true;
    }

    @Override
    public boolean registerInvokeDirect(DexMethod method) {
      if (!registerItemWithTarget(directInvokes, method)) {
        return false;
      }
      if (Log.ENABLED) {
        Log.verbose(getClass(), "Register invokeDirect `%s`.", method);
      }
      handleInvokeOfDirectTarget(method, KeepReason.invokedFrom(currentMethod));
      return true;
    }

    @Override
    public boolean registerInvokeStatic(DexMethod method) {
      if (method == appInfo.dexItemFactory.classMethods.forName
          || appInfo.dexItemFactory.atomicFieldUpdaterMethods.isFieldUpdater(method)) {
        if (forceProguardCompatibility) {
          // TODO(b/76181966): whether or not add this rule in normal mode.
          if (identifierNameStrings.add(method) && compatibility != null) {
            compatibility.addRule(buildIdentifierNameStringRule(method));
          }
          pendingProguardReflectiveCompatibility.add(currentMethod);
        }
      }
      if (!registerItemWithTarget(staticInvokes, method)) {
        return false;
      }
      if (Log.ENABLED) {
        Log.verbose(getClass(), "Register invokeStatic `%s`.", method);
      }
      handleInvokeOfStaticTarget(method, KeepReason.invokedFrom(currentMethod));
      return true;
    }

    @Override
    public boolean registerInvokeInterface(DexMethod method) {
      if (!registerItemWithTarget(interfaceInvokes, method)) {
        return false;
      }
      if (Log.ENABLED) {
        Log.verbose(getClass(), "Register invokeInterface `%s`.", method);
      }
      workList.add(Action.markReachableInterface(method, KeepReason.invokedFrom(currentMethod)));
      return true;
    }

    @Override
    public boolean registerInvokeSuper(DexMethod method) {
      // We have to revisit super invokes based on the context they are found in. The same
      // method descriptor will hit different targets, depending on the context it is used in.
      DexMethod actualTarget = getInvokeSuperTarget(method, currentMethod);
      if (!registerItemWithTargetAndContext(superInvokes, method, currentMethod)) {
        return false;
      }
      if (Log.ENABLED) {
        Log.verbose(getClass(), "Register invokeSuper `%s`.", actualTarget);
      }
      workList.add(Action.markReachableSuper(method, currentMethod));
      return true;
    }

    @Override
    public boolean registerInstanceFieldWrite(DexField field) {
      if (!registerItemWithTargetAndContext(instanceFieldsWritten, field, currentMethod)) {
        return false;
      }
      if (Log.ENABLED) {
        Log.verbose(getClass(), "Register Iput `%s`.", field);
      }
      // TODO(herhut): We have to add this, but DCR should eliminate dead writes.
      workList.add(Action.markReachableField(field, KeepReason.fieldReferencedIn(currentMethod)));
      return true;
    }

    @Override
    public boolean registerInstanceFieldRead(DexField field) {
      if (!registerItemWithTargetAndContext(instanceFieldsRead, field, currentMethod)) {
        return false;
      }
      if (Log.ENABLED) {
        Log.verbose(getClass(), "Register Iget `%s`.", field);
      }
      workList.add(Action.markReachableField(field, KeepReason.fieldReferencedIn(currentMethod)));
      return true;
    }

    @Override
    public boolean registerNewInstance(DexType type) {
      markInstantiated(type, currentMethod);
      return true;
    }

    @Override
    public boolean registerStaticFieldRead(DexField field) {
      if (!registerItemWithTargetAndContext(staticFieldsRead, field, currentMethod)) {
        return false;
      }
      if (Log.ENABLED) {
        Log.verbose(getClass(), "Register Sget `%s`.", field);
      }
      markStaticFieldAsLive(field, KeepReason.fieldReferencedIn(currentMethod));
      return true;
    }

    @Override
    public boolean registerStaticFieldWrite(DexField field) {
      if (!registerItemWithTargetAndContext(staticFieldsWritten, field, currentMethod)) {
        return false;
      }
      if (Log.ENABLED) {
        Log.verbose(getClass(), "Register Sput `%s`.", field);
      }
      // TODO(herhut): We have to add this, but DCR should eliminate dead writes.
      markStaticFieldAsLive(field, KeepReason.fieldReferencedIn(currentMethod));
      return true;
    }

    @Override
    public boolean registerConstClass(DexType type) {
      return registerConstClassOrCheckCast(type);
    }

    @Override
    public boolean registerCheckCast(DexType type) {
      return registerConstClassOrCheckCast(type);
    }

    @Override
    public boolean registerTypeReference(DexType type) {
      DexType baseType = type.toBaseType(appInfo.dexItemFactory);
      if (baseType.isClassType()) {
        markTypeAsLive(baseType);
        return true;
      }
      return false;
    }

    @Override
    public void registerCallSite(DexCallSite callSite) {
      super.registerCallSite(callSite);
      for (DexEncodedMethod method :
          appInfo.lookupLambdaImplementedMethods(callSite, options.reporter)) {
        markLambdaInstantiated(method.method.holder, currentMethod);
      }
    }

    private boolean registerConstClassOrCheckCast(DexType type) {
      if (forceProguardCompatibility) {
        DexType baseType = type.toBaseType(appInfo.dexItemFactory);
        if (baseType.isClassType()) {
          DexClass baseClass = appInfo.definitionFor(baseType);
          if (baseClass != null && baseClass.isProgramClass()
              && baseClass.hasDefaultInitializer()) {
            markClassAsInstantiatedWithCompatRule(baseClass);
          } else {
            // This also handles reporting of missing classes.
            markTypeAsLive(baseType);
          }
          return true;
        }
        return false;
      } else {
        return registerTypeReference(type);
      }
    }
  }

  private DexMethod getInvokeSuperTarget(DexMethod method, DexEncodedMethod currentMethod) {
    DexClass holderClass = appInfo.definitionFor(currentMethod.method.getHolder());
    if (holderClass == null || holderClass.superType == null) {
      // We do not know better.
      return method;
    }
    // Return the invoked method on the supertype.
    return appInfo.dexItemFactory.createMethod(holderClass.superType, method.proto, method.name);
  }

  //
  // Actual actions performed.
  //

  private void markTypeAsLive(DexType type) {
    assert type.isClassType();
    if (liveTypes.add(type)) {
      if (Log.ENABLED) {
        Log.verbose(getClass(), "Type `%s` has become live.", type);
      }
      DexClass holder = appInfo.definitionFor(type);
      if (holder == null) {
        reportMissingClass(type);
        return;
      }
      for (DexType iface : holder.interfaces.values) {
        markTypeAsLive(iface);
      }
      if (holder.superType != null) {
        markTypeAsLive(holder.superType);
        if (holder.isLibraryClass()) {
          // Library classes may only extend other implement library classes.
          ensureFromLibraryOrThrow(holder.superType, type);
          for (DexType iface : holder.interfaces.values) {
            ensureFromLibraryOrThrow(iface, type);
          }
        }
      }
      if (!holder.annotations.isEmpty()) {
        processAnnotations(holder.annotations.annotations);
      }
      // We also need to add the corresponding <clinit> to the set of live methods, as otherwise
      // static field initialization (and other class-load-time sideeffects) will not happen.
      if (!holder.isLibraryClass() && holder.hasNonTrivialClassInitializer()) {
        DexEncodedMethod clinit = holder.getClassInitializer();
        if (clinit != null) {
          assert clinit.method.holder == holder.type;
          markDirectStaticOrConstructorMethodAsLive(clinit, KeepReason.reachableFromLiveType(type));
        }
      }

      // If this type has deferred annotations, we have to process those now, too.
      Set<DexAnnotation> annotations = deferredAnnotations.remove(type);
      if (annotations != null) {
        annotations.forEach(this::handleAnnotationOfLiveType);
      }

      if (forceProguardCompatibility) {
        // Add all dependent members to the workqueue.
        enqueueRootItems(rootSet.getDependentItems(type));
      } else {
        // Add all dependent static members to the workqueue.
        enqueueRootItems(rootSet.getDependentStaticMembers(type));
      }
    }
  }

  private void handleAnnotationOfLiveType(DexAnnotation annotation) {
    AnnotationReferenceMarker referenceMarker = new AnnotationReferenceMarker(
        annotation.annotation.type, appInfo.dexItemFactory);
    annotation.annotation.collectIndexedItems(referenceMarker);
  }

  private void processAnnotations(DexAnnotation[] annotations) {
    for (DexAnnotation annotation : annotations) {
      processAnnotation(annotation);
    }
  }

  private void processAnnotation(DexAnnotation annotation) {
    DexType type = annotation.annotation.type;
    if (liveTypes.contains(type)) {
      // The type of this annotation is already live, so pick up its dependencies.
      handleAnnotationOfLiveType(annotation);
    } else {
      // Remember this annotation for later.
      deferredAnnotations.computeIfAbsent(type, ignore -> new HashSet<>()).add(annotation);
    }
  }

  private void handleInvokeOfStaticTarget(DexMethod method, KeepReason reason) {
    // We have to mark the resolved method as targeted even if it cannot actually be invoked
    // to make sure the invocation will keep failing in the appropriate way.
    ResolutionResult resolutionResult = appInfo.resolveMethod(method.holder, method);
    if (resolutionResult == null) {
      reportMissingMethod(method);
      return;
    }
    resolutionResult.forEachTarget(m -> markMethodAsTargeted(m, reason));
    // Only mark methods for which invocation will succeed at runtime live.
    DexEncodedMethod targetMethod = appInfo.dispatchStaticInvoke(resolutionResult);
    if (targetMethod != null) {
      markDirectStaticOrConstructorMethodAsLive(targetMethod, reason);
    }
  }

  private void handleInvokeOfDirectTarget(DexMethod method, KeepReason reason) {
    // We have to mark the resolved method as targeted even if it cannot actually be invoked
    // to make sure the invocation will keep failing in the appropriate way.
    ResolutionResult resolutionResult = appInfo.resolveMethod(method.holder, method);
    if (resolutionResult == null) {
      reportMissingMethod(method);
      return;
    }
    resolutionResult.forEachTarget(m -> markMethodAsTargeted(m, reason));
    // Only mark methods for which invocation will succeed at runtime live.
    DexEncodedMethod target = appInfo.dispatchDirectInvoke(resolutionResult);
    if (target != null) {
      markDirectStaticOrConstructorMethodAsLive(target, reason);
    }
  }

  private void ensureFromLibraryOrThrow(DexType type, DexType context) {
    if (tracingMainDex) {
      // b/72312389: android.jar contains parts of JUnit and most developers include JUnit in
      // their programs. This leads to library classes extending program classes. When tracing
      // main dex lists we allow this.
      return;
    }

    DexClass holder = appInfo.definitionFor(type);
    if (holder != null && !holder.isLibraryClass()) {
      Diagnostic message = new StringDiagnostic("Library class " + context.toSourceString()
          + (holder.isInterface() ? " implements " : " extends ")
          + "program class " + type.toSourceString());
      if (forceProguardCompatibility) {
        options.reporter.warning(message);
      } else {
        options.reporter.error(message);
      }
    }
  }

  private void reportMissingClass(DexType clazz) {
    if (Log.ENABLED && reportedMissing.add(clazz)) {
      Log.verbose(Enqueuer.class, "Class `%s` is missing.", clazz);
    }
  }

  private void reportMissingMethod(DexMethod method) {
    if (Log.ENABLED && reportedMissing.add(method)) {
      Log.verbose(Enqueuer.class, "Method `%s` is missing.", method);
    }
  }

  private void reportMissingField(DexField field) {
    if (Log.ENABLED && reportedMissing.add(field)) {
      Log.verbose(Enqueuer.class, "Field `%s` is missing.", field);
    }
  }

  private void markMethodAsTargeted(DexEncodedMethod encodedMethod, KeepReason reason) {
    markTypeAsLive(encodedMethod.method.holder);
    if (Log.ENABLED) {
      Log.verbose(getClass(), "Method `%s` is targeted.", encodedMethod.method);
    }
    targetedMethods.add(encodedMethod, reason);
    if (forceProguardCompatibility) {
      // Keep targeted default methods in compatibility mode. The tree pruner will otherwise make
      // these methods abstract, whereas Proguard does not (seem to) touch their code.
      DexClass clazz = appInfo.definitionFor(encodedMethod.method.holder);
      if (!encodedMethod.accessFlags.isAbstract()
          && clazz.isInterface() && !clazz.isLibraryClass()) {
        markMethodAsKeptWithCompatRule(encodedMethod);
      }
    }
  }

  /**
   * Adds the class to the set of instantiated classes and marks its fields and methods live
   * depending on the currently seen invokes and field reads.
   */
  private void processNewlyInstantiatedClass(DexClass clazz, KeepReason reason) {
    if (!instantiatedTypes.add(clazz.type, reason)) {
      return;
    }

    collectProguardCompatibilityRule(reason);
    if (Log.ENABLED) {
      Log.verbose(getClass(), "Class `%s` is instantiated, processing...", clazz);
    }
    // This class becomes live, so it and all its supertypes become live types.
    markTypeAsLive(clazz.type);
    // For all methods of the class, if we have seen a call, mark the method live.
    // We only do this for virtual calls, as the other ones will be done directly.
    transitionMethodsForInstantiatedClass(clazz.type);
    // For all instance fields visible from the class, mark them live if we have seen a read.
    transitionFieldsForInstantiatedClass(clazz.type);
    // Add all dependent members to the workqueue.
    enqueueRootItems(rootSet.getDependentItems(clazz.type));
  }

  /**
   * Marks all methods live that can be reached by calls previously seen.
   * <p>
   * <p>This should only be invoked if the given type newly becomes instantiated. In essence, this
   * method replays all the invokes we have seen so far that could apply to this type and marks the
   * corresponding methods live.
   * <p>
   * <p>Only methods that are visible in this type are considered. That is, only those methods that
   * are either defined directly on this type or that are defined on a supertype but are not
   * shadowed by another inherited method. Furthermore, default methods from implemented interfaces
   * that are not otherwise shadowed are considered, too.
   */
  private void transitionMethodsForInstantiatedClass(DexType instantiatedType) {
    ScopedDexMethodSet seen = new ScopedDexMethodSet();
    Set<DexType> interfaces = Sets.newIdentityHashSet();
    DexType type = instantiatedType;
    do {
      DexClass clazz = appInfo.definitionFor(type);
      if (clazz == null) {
        reportMissingClass(type);
        // TODO(herhut): In essence, our subtyping chain is broken here. Handle that case better.
        break;
      }
      // We only have to look at virtual methods here, as only those can actually be executed at
      // runtime. Illegal dispatch situations and the corresponding exceptions are already handled
      // by the reachability logic.
      SetWithReason<DexEncodedMethod> reachableMethods = reachableVirtualMethods.get(type);
      if (reachableMethods != null) {
        transitionNonAbstractMethodsToLiveAndShadow(reachableMethods.getItems(), instantiatedType,
            seen);
      }
      Collections.addAll(interfaces, clazz.interfaces.values);
      type = clazz.superType;
    } while (type != null && !instantiatedTypes.contains(type));
    // The set now contains all virtual methods on the type and its supertype that are reachable.
    // In a second step, we now look at interfaces. We have to do this in this order due to JVM
    // semantics for default methods. A default method is only reachable if it is not overridden in
    // any superclass. Also, it is not defined which default method is chosen if multiple
    // interfaces define the same default method. Hence, for every interface (direct or indirect),
    // we have to look at the interface chain and mark default methods as reachable, not taking
    // the shadowing of other interface chains into account.
    // See https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-5.html#jvms-5.4.3.3
    for (DexType iface : interfaces) {
      DexClass clazz = appInfo.definitionFor(iface);
      if (clazz == null) {
        reportMissingClass(iface);
        // TODO(herhut): In essence, our subtyping chain is broken here. Handle that case better.
        break;
      }
      transitionDefaultMethodsForInstantiatedClass(iface, instantiatedType, seen);
    }
  }

  private void transitionDefaultMethodsForInstantiatedClass(DexType iface, DexType instantiatedType,
      ScopedDexMethodSet seen) {
    DexClass clazz = appInfo.definitionFor(iface);
    assert clazz != null : "Missing class " + iface.toSourceString();
    assert clazz.accessFlags.isInterface();
    SetWithReason<DexEncodedMethod> reachableMethods = reachableVirtualMethods.get(iface);
    if (reachableMethods != null) {
      transitionNonAbstractMethodsToLiveAndShadow(
          reachableMethods.getItems(), instantiatedType, seen.newNestedScope());
    }
    for (DexType subInterface : clazz.interfaces.values) {
      transitionDefaultMethodsForInstantiatedClass(subInterface, instantiatedType, seen);
    }
  }

  private void transitionNonAbstractMethodsToLiveAndShadow(Iterable<DexEncodedMethod> reachable,
      DexType instantiatedType, ScopedDexMethodSet seen) {
    for (DexEncodedMethod encodedMethod : reachable) {
      if (seen.addMethod(encodedMethod)) {
        // Abstract methods do shadow implementations but they cannot be live, as they have no
        // code.
        if (!encodedMethod.accessFlags.isAbstract()) {
          markVirtualMethodAsLive(encodedMethod,
              KeepReason.reachableFromLiveType(instantiatedType));
        }
      }
    }
  }

  /**
   * Marks all fields live that can be reached by a read assuming that the given type or one of its
   * subtypes is instantiated.
   */
  private void transitionFieldsForInstantiatedClass(DexType type) {
    do {
      DexClass clazz = appInfo.definitionFor(type);
      if (clazz == null) {
        // TODO(herhut) The subtype chain is broken. We need a way to deal with this better.
        reportMissingClass(type);
        break;
      }
      SetWithReason<DexEncodedField> reachableFields = reachableInstanceFields.get(type);
      if (reachableFields != null) {
        for (DexEncodedField field : reachableFields.getItems()) {
          markInstanceFieldAsLive(field, KeepReason.reachableFromLiveType(type));
        }
      }
      type = clazz.superType;
    } while (type != null && !instantiatedTypes.contains(type));
  }

  private void markStaticFieldAsLive(DexField field, KeepReason reason) {
    // Mark the type live here, so that the class exists at runtime. Note that this also marks all
    // supertypes as live, so even if the field is actually on a supertype, its class will be live.
    markTypeAsLive(field.clazz);
    if (field.type.isClassType()) {
      markTypeAsLive(field.type);
    }
    // Find the actual field.
    DexEncodedField encodedField = appInfo.resolveFieldOn(field.clazz, field);
    if (encodedField == null) {
      reportMissingField(field);
      return;
    }
    // This field might be an instance field reachable from a static context, e.g. a getStatic that
    // resolves to an instance field. We have to keep the instance field nonetheless, as otherwise
    // we might unmask a shadowed static field and hence change semantics.

    if (encodedField.accessFlags.isStatic()) {
      if (Log.ENABLED) {
        Log.verbose(getClass(), "Adding static field `%s` to live set.", encodedField.field);
      }
    } else {
      if (Log.ENABLED) {
        Log.verbose(getClass(), "Adding instance field `%s` to live set (static context).",
            encodedField.field);
      }
    }
    liveFields.add(encodedField, reason);
    collectProguardCompatibilityRule(reason);
    // Add all dependent members to the workqueue.
    enqueueRootItems(rootSet.getDependentItems(encodedField));
  }

  private void markInstanceFieldAsLive(DexEncodedField field, KeepReason reason) {
    assert field != null;
    markTypeAsLive(field.field.clazz);
    if (field.field.type.isClassType()) {
      markTypeAsLive(field.field.type);
    }
    if (Log.ENABLED) {
      Log.verbose(getClass(), "Adding instance field `%s` to live set.", field.field);
    }
    liveFields.add(field, reason);
    collectProguardCompatibilityRule(reason);
    // Add all dependent members to the workqueue.
    enqueueRootItems(rootSet.getDependentItems(field));
  }

  private void markInstantiated(DexType type, DexEncodedMethod method) {
    if (instantiatedTypes.contains(type)) {
      return;
    }
    DexClass clazz = appInfo.definitionFor(type);
    if (clazz == null) {
      reportMissingClass(type);
      return;
    }
    if (Log.ENABLED) {
      Log.verbose(getClass(), "Register new instantiation of `%s`.", clazz);
    }
    workList.add(Action.markInstantiated(clazz, KeepReason.instantiatedIn(method)));
  }

  private void markLambdaInstantiated(DexType itf, DexEncodedMethod method) {
    instantiatedLambdas.add(itf, KeepReason.instantiatedIn(method));
  }

  private void markDirectStaticOrConstructorMethodAsLive(
      DexEncodedMethod encodedMethod, KeepReason reason) {
    assert encodedMethod != null;
    if (!liveMethods.contains(encodedMethod)) {
      markTypeAsLive(encodedMethod.method.holder);
      markMethodAsTargeted(encodedMethod, reason);
      // For granting inner/outer classes access to their private constructors, javac generates
      // additional synthetic constructors. These constructors take a synthetic class
      // as argument. As it is not possible to express a keep rule for these synthetic classes
      // always keep synthetic arguments to synthetic constructors. See b/69825683.
      if (encodedMethod.isInstanceInitializer() && encodedMethod.isSyntheticMethod()) {
        for (DexType type : encodedMethod.method.proto.parameters.values) {
          type = type.isArrayType() ? type.toBaseType(appInfo.dexItemFactory) : type;
          if (type.isPrimitiveType()) {
            continue;
          }
          DexClass clazz = appInfo.definitionFor(type);
          if (clazz != null && clazz.accessFlags.isSynthetic()) {
            markTypeAsLive(type);
          }
        }
      }
      if (Log.ENABLED) {
        Log.verbose(getClass(), "Method `%s` has become live due to direct invoke",
            encodedMethod.method);
      }
      workList.add(Action.markMethodLive(encodedMethod, reason));
    }
  }

  private void markVirtualMethodAsLive(DexEncodedMethod method, KeepReason reason) {
    assert method != null;
    assert !method.accessFlags.isAbstract();
    if (!liveMethods.contains(method)) {
      if (Log.ENABLED) {
        Log.verbose(getClass(), "Adding virtual method `%s` to live set.", method.method);
      }
      workList.add(Action.markMethodLive(method, reason));
    }
  }

  private boolean isInstantiatedOrHasInstantiatedSubtype(DexType type) {
    return instantiatedTypes.contains(type)
        || appInfo.subtypes(type).stream().anyMatch(instantiatedTypes::contains);
  }

  private void markInstanceFieldAsReachable(DexField field, KeepReason reason) {
    if (Log.ENABLED) {
      Log.verbose(getClass(), "Marking instance field `%s` as reachable.", field);
    }
    DexEncodedField encodedField = appInfo.resolveFieldOn(field.clazz, field);
    if (encodedField == null) {
      reportMissingField(field);
      return;
    }
    // We might have a instance field access that is dispatched to a static field. In such case,
    // we have to keep the static field, so that the dispatch fails at runtime in the same way that
    // it did before. We have to keep the field even if the receiver has no live inhabitants, as
    // field resolution happens before the receiver is inspected.
    if (encodedField.accessFlags.isStatic()) {
      markStaticFieldAsLive(encodedField.field, reason);
    } else {
      SetWithReason<DexEncodedField> reachable = reachableInstanceFields
          .computeIfAbsent(encodedField.field.clazz, ignore -> new SetWithReason<>());
      if (reachable.add(encodedField, reason) && isInstantiatedOrHasInstantiatedSubtype(
          encodedField.field.clazz)) {
        // We have at least one live subtype, so mark it as live.
        markInstanceFieldAsLive(encodedField, reason);
      }
    }
  }

  private void markVirtualMethodAsReachable(DexMethod method, boolean interfaceInvoke,
      KeepReason reason) {
    if (!virtualTargetsMarkedAsReachable.add(method)) {
      return;
    }
    if (Log.ENABLED) {
      Log.verbose(getClass(), "Marking virtual method `%s` as reachable.", method);
    }
    if (method.holder.isArrayType()) {
      // This is an array type, so the actual class will be generated at runtime. We treat this
      // like an invoke on a direct subtype of java.lang.Object that has no further subtypes.
      // As it has no subtypes, it cannot affect liveness of the program we are processing.
      // Ergo, we can ignore it. We need to make sure that the element type is available, though.
      DexType baseType = method.holder.toBaseType(appInfo.dexItemFactory);
      if (baseType.isClassType()) {
        markTypeAsLive(baseType);
      }
      return;
    }
    DexClass holder = appInfo.definitionFor(method.holder);
    if (holder == null) {
      reportMissingClass(method.holder);
      return;
    }
    DexEncodedMethod topTarget = interfaceInvoke
        ? appInfo.resolveMethodOnInterface(method.holder, method).asResultOfResolve()
        : appInfo.resolveMethodOnClass(method.holder, method).asResultOfResolve();
    if (topTarget == null) {
      reportMissingMethod(method);
      return;
    }
    // We have to mark this as targeted, as even if this specific instance never becomes live, we
    // need at least an abstract version of it so that we have a target for the corresponding
    // invoke.
    markMethodAsTargeted(topTarget, reason);
    Set<DexEncodedMethod> targets = interfaceInvoke
        ? appInfo.lookupInterfaceTargets(method)
        : appInfo.lookupVirtualTargets(method);
    for (DexEncodedMethod encodedMethod : targets) {
      SetWithReason<DexEncodedMethod> reachable = reachableVirtualMethods
          .computeIfAbsent(encodedMethod.method.holder, (ignore) -> new SetWithReason<>());
      if (reachable.add(encodedMethod, reason)) {
        // Abstract methods cannot be live.
        if (!encodedMethod.accessFlags.isAbstract()) {
          // If the holder type is instantiated, the method is live. Otherwise check whether we find
          // a subtype that does not shadow this methods but is instantiated.
          // Note that library classes are always considered instantiated, as we do not know where
          // they are instantiated.
          if (isInstantiatedOrHasInstantiatedSubtype(encodedMethod.method.holder)) {
            if (instantiatedTypes.contains(encodedMethod.method.holder)) {
              markVirtualMethodAsLive(encodedMethod,
                  KeepReason.reachableFromLiveType(encodedMethod.method.holder));
            } else {
              Deque<DexType> worklist = new ArrayDeque<>();
              fillWorkList(worklist, encodedMethod.method.holder);
              while (!worklist.isEmpty()) {
                DexType current = worklist.pollFirst();
                DexClass currentHolder = appInfo.definitionFor(current);
                // If this class shadows the virtual, abort the search. Note, according to JVM spec,
                // shadowing is independent of whether a method is public or private.
                if (currentHolder == null
                    || currentHolder.lookupMethod(encodedMethod.method) != null) {
                  continue;
                }
                if (instantiatedTypes.contains(current)) {
                  markVirtualMethodAsLive(encodedMethod, KeepReason.reachableFromLiveType(current));
                  break;
                }
                fillWorkList(worklist, current);
              }
            }
          }
        }
      }
    }
  }

  private static void fillWorkList(Deque<DexType> worklist, DexType type) {
    if (type.isInterface()) {
      // We need to check if the method is shadowed by a class that directly implements
      // the interface and go recursively down to the sub interfaces to reach class
      // implementing the interface
      type.forAllImplementsSubtypes(worklist::addLast);
      type.forAllExtendsSubtypes(worklist::addLast);
    } else {
      type.forAllExtendsSubtypes(worklist::addLast);
    }
  }

  private void markSuperMethodAsReachable(DexMethod method, DexEncodedMethod from) {
    // We have to mark the immediate target of the descriptor as targeted, as otherwise
    // the invoke super will fail in the resolution step with a NSM error.
    // See <a
    // href="https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.invokespecial">
    // the JVM spec for invoke-special.
    DexEncodedMethod resolutionTarget = appInfo.resolveMethod(method.holder, method)
        .asResultOfResolve();
    if (resolutionTarget == null) {
      brokenSuperInvokes.add(method);
      reportMissingMethod(method);
      return;
    }
    markMethodAsTargeted(resolutionTarget, KeepReason.targetedBySuperFrom(from));
    // Now we need to compute the actual target in the context.
    DexEncodedMethod target = appInfo.lookupSuperTarget(method, from.method.holder);
    if (target == null) {
      // The actual implementation in the super class is missing.
      reportMissingMethod(method);
      return;
    }
    if (target.accessFlags.isPrivate()) {
      brokenSuperInvokes.add(method);
    }
    assert !superInvokeDependencies.containsKey(from) || !superInvokeDependencies.get(from)
        .contains(target);
    if (Log.ENABLED) {
      Log.verbose(getClass(), "Adding super constraint from `%s` to `%s`", from.method,
          target.method);
    }
    superInvokeDependencies.computeIfAbsent(from, ignore -> Sets.newIdentityHashSet()).add(target);
    if (liveMethods.contains(from)) {
      markMethodAsTargeted(target, KeepReason.invokedViaSuperFrom(from));
      if (!target.accessFlags.isAbstract()) {
        markVirtualMethodAsLive(target, KeepReason.invokedViaSuperFrom(from));
      }
    }
  }

  public ReasonPrinter getReasonPrinter(Set<DexItem> queriedItems) {
    // If no reason was asked, just return a no-op printer to avoid computing the information.
    // This is the common path.
    if (queriedItems.isEmpty()) {
      return ReasonPrinter.getNoOpPrinter();
    }
    Map<DexItem, KeepReason> reachability = new HashMap<>();
    for (SetWithReason<DexEncodedMethod> mappings : reachableVirtualMethods.values()) {
      reachability.putAll(mappings.getReasons());
    }
    for (SetWithReason<DexEncodedField> mappings : reachableInstanceFields.values()) {
      reachability.putAll(mappings.getReasons());
    }
    return new ReasonPrinter(queriedItems, liveFields.getReasons(), liveMethods.getReasons(),
        reachability, instantiatedTypes.getReasons());
  }

  public AppInfoWithLiveness traceMainDex(
      RootSet rootSet, ExecutorService executorService, Timing timing) throws ExecutionException {
    this.tracingMainDex = true;
    this.rootSet = rootSet;
    // Translate the result of root-set computation into enqueuer actions.
    enqueueRootItems(rootSet.noShrinking);
    AppInfoWithLiveness appInfo = trace(executorService, timing);
    options.reporter.failIfPendingErrors();
    return appInfo;
  }

  public AppInfoWithLiveness traceApplication(
      RootSet rootSet, ExecutorService executorService, Timing timing) throws ExecutionException {
    this.rootSet = rootSet;
    // Translate the result of root-set computation into enqueuer actions.
    enqueueRootItems(rootSet.noShrinking);
    appInfo.libraryClasses().forEach(this::markAllLibraryVirtualMethodsReachable);
    AppInfoWithLiveness result = trace(executorService, timing);
    options.reporter.failIfPendingErrors();
    return result;
  }

  private AppInfoWithLiveness trace(
      ExecutorService executorService, Timing timing) throws ExecutionException {
    timing.begin("Grow the tree.");
    try {
      while (true) {
        long numOfLiveItems = (long) liveTypes.size();
        numOfLiveItems += (long) liveMethods.items.size();
        numOfLiveItems += (long) liveFields.items.size();
        while (!workList.isEmpty()) {
          Action action = workList.poll();
          switch (action.kind) {
            case MARK_INSTANTIATED:
              processNewlyInstantiatedClass((DexClass) action.target, action.reason);
              break;
            case MARK_REACHABLE_FIELD:
              markInstanceFieldAsReachable((DexField) action.target, action.reason);
              break;
            case MARK_REACHABLE_VIRTUAL:
              markVirtualMethodAsReachable((DexMethod) action.target, false, action.reason);
              break;
            case MARK_REACHABLE_INTERFACE:
              markVirtualMethodAsReachable((DexMethod) action.target, true, action.reason);
              break;
            case MARK_REACHABLE_SUPER:
              markSuperMethodAsReachable((DexMethod) action.target,
                  (DexEncodedMethod) action.context);
              break;
            case MARK_METHOD_KEPT:
              markMethodAsKept((DexEncodedMethod) action.target, action.reason);
              break;
            case MARK_FIELD_KEPT:
              markFieldAsKept((DexEncodedField) action.target, action.reason);
              break;
            case MARK_METHOD_LIVE:
              processNewlyLiveMethod(((DexEncodedMethod) action.target), action.reason);
              break;
            default:
              throw new IllegalArgumentException(action.kind.toString());
          }
        }

        // Continue fix-point processing if -if rules are enabled by items that newly became live.
        long numOfLiveItemsAfterProcessing = (long) liveTypes.size();
        numOfLiveItemsAfterProcessing += (long) liveMethods.items.size();
        numOfLiveItemsAfterProcessing += (long) liveFields.items.size();
        if (numOfLiveItemsAfterProcessing > numOfLiveItems) {
          RootSetBuilder consequentSetBuilder =
              new RootSetBuilder(appInfo, rootSet.ifRules, options);
          ConsequentRootSet consequentRootSet = consequentSetBuilder.runForIfRules(
              executorService, liveTypes, liveMethods.getItems(), liveFields.getItems());
          enqueueRootItems(consequentRootSet.noShrinking);
          rootSet.noOptimization.addAll(consequentRootSet.noOptimization);
          rootSet.noObfuscation.addAll(consequentRootSet.noObfuscation);
          if (!workList.isEmpty()) {
            continue;
          }
        }

        // Continue fix-point processing while there are additional work items to ensure
        // Proguard compatibility.
        if (proguardCompatibilityWorkList.isEmpty()
            && pendingProguardReflectiveCompatibility.isEmpty()) {
          break;
        }
        pendingProguardReflectiveCompatibility.forEach(this::handleProguardReflectiveBehavior);
        workList.addAll(proguardCompatibilityWorkList);
        proguardCompatibilityWorkList.clear();
        pendingProguardReflectiveCompatibility.clear();
      }
      if (Log.ENABLED) {
        Set<DexEncodedMethod> allLive = Sets.newIdentityHashSet();
        for (Entry<DexType, SetWithReason<DexEncodedMethod>> entry : reachableVirtualMethods
            .entrySet()) {
          allLive.addAll(entry.getValue().getItems());
        }
        Set<DexEncodedMethod> reachableNotLive = Sets.difference(allLive, liveMethods.getItems());
        Log.debug(getClass(), "%s methods are reachable but not live", reachableNotLive.size());
        Log.info(getClass(), "Only reachable: %s", reachableNotLive);
        Set<DexType> liveButNotInstantiated =
            Sets.difference(liveTypes, instantiatedTypes.getItems());
        Log.debug(getClass(), "%s classes are live but not instantiated",
            liveButNotInstantiated.size());
        Log.info(getClass(), "Live but not instantiated: %s", liveButNotInstantiated);
        SetView<DexEncodedMethod> targetedButNotLive = Sets
            .difference(targetedMethods.getItems(), liveMethods.getItems());
        Log.debug(getClass(), "%s methods are targeted but not live", targetedButNotLive.size());
        Log.info(getClass(), "Targeted but not live: %s", targetedButNotLive);
      }
      assert liveTypes.stream().allMatch(DexType::isClassType);
      assert instantiatedTypes.getItems().stream().allMatch(DexType::isClassType);
    } finally {
      timing.end();
    }
    return new AppInfoWithLiveness(appInfo, this);
  }

  private void markMethodAsKept(DexEncodedMethod target, KeepReason reason) {
    DexClass holder = appInfo.definitionFor(target.method.holder);
    // If this method no longer has a corresponding class then we have shaken it away before.
    if (holder == null) {
      return;
    }
    if (target.isVirtualMethod()) {
      // A virtual method. Mark it as reachable so that subclasses, if instantiated, keep
      // their overrides. However, we don't mark it live, as a keep rule might not imply that
      // the corresponding class is live.
      markVirtualMethodAsReachable(target.method, holder.accessFlags.isInterface(), reason);
      // Reachability for default methods is based on live subtypes in general. For keep rules,
      // we need special handling as we essentially might have live subtypes that are outside of
      // our reach. Do this here, as we still know that this is due to a keep rule.
      if (holder.isInterface() && target.isNonAbstractVirtualMethod()) {
        markVirtualMethodAsLive(target, reason);
      }
    } else {
      markDirectStaticOrConstructorMethodAsLive(target, reason);
    }
  }

  private void markFieldAsKept(DexEncodedField target, KeepReason reason) {
    // If this field no longer has a corresponding class, then we have shaken it away before.
    if (appInfo.definitionFor(target.field.clazz) == null) {
      return;
    }
    if (target.accessFlags.isStatic()) {
      markStaticFieldAsLive(target.field, reason);
    } else {
      markInstanceFieldAsReachable(target.field, reason);
    }
  }

  private void markAllLibraryVirtualMethodsReachable(DexClass clazz) {
    assert clazz.isLibraryClass();
    if (Log.ENABLED) {
      Log.verbose(getClass(), "Marking all methods of library class `%s` as reachable.",
          clazz.type);
    }
    for (DexEncodedMethod encodedMethod : clazz.virtualMethods()) {
      markMethodAsTargeted(encodedMethod, KeepReason.isLibraryMethod());
      markVirtualMethodAsReachable(encodedMethod.method, clazz.isInterface(),
          KeepReason.isLibraryMethod());
    }
  }

  private void processNewlyLiveMethod(DexEncodedMethod method, KeepReason reason) {
    if (liveMethods.add(method, reason)) {
      collectProguardCompatibilityRule(reason);
      DexClass holder = appInfo.definitionFor(method.method.holder);
      assert holder != null;
      if (holder.isLibraryClass()) {
        // We do not process library classes.
        return;
      }
      Set<DexEncodedMethod> superCallTargets = superInvokeDependencies.get(method);
      if (superCallTargets != null) {
        for (DexEncodedMethod superCallTarget : superCallTargets) {
          if (Log.ENABLED) {
            Log.verbose(getClass(), "Found super invoke constraint on `%s`.",
                superCallTarget.method);
          }
          markMethodAsTargeted(superCallTarget, KeepReason.invokedViaSuperFrom(method));
          markVirtualMethodAsLive(superCallTarget, KeepReason.invokedViaSuperFrom(method));
        }
      }
      processAnnotations(method.annotations.annotations);
      method.parameterAnnotationsList.forEachAnnotation(this::processAnnotation);
      if (protoLiteExtension != null && protoLiteExtension.appliesTo(method)) {
        protoLiteExtension.processMethod(method, new UseRegistry(method), protoLiteFields);
      } else {
        method.registerCodeReferences(new UseRegistry(method));
      }
      // Add all dependent members to the workqueue.
      enqueueRootItems(rootSet.getDependentItems(method));
    }
  }

  private void collectProguardCompatibilityRule(KeepReason reason) {
    if (reason.isDueToProguardCompatibility() && compatibility != null) {
      compatibility.addRule(reason.getProguardKeepRule());
    }
  }

  private Map<DexField, Set<DexEncodedMethod>> collectFields(
      Map<DexType, Set<TargetWithContext<DexField>>> map) {
    Map<DexField, Set<DexEncodedMethod>> result = new IdentityHashMap<>();
    for (Map.Entry<DexType, Set<TargetWithContext<DexField>>> entry : map.entrySet()) {
      for (TargetWithContext<DexField> fieldWithContext : entry.getValue()) {
        DexField field = fieldWithContext.getTarget();
        DexEncodedMethod context = fieldWithContext.getContext();
        result.computeIfAbsent(field, k -> Sets.newIdentityHashSet())
            .add(context);
      }
    }
    return result;
  }

  Map<DexField, Set<DexEncodedMethod>> collectInstanceFieldsRead() {
    return Collections.unmodifiableMap(collectFields(instanceFieldsRead));
  }

  Map<DexField, Set<DexEncodedMethod>> collectInstanceFieldsWritten() {
    return Collections.unmodifiableMap(collectFields(instanceFieldsWritten));
  }

  Map<DexField, Set<DexEncodedMethod>> collectStaticFieldsRead() {
    return Collections.unmodifiableMap(collectFields(staticFieldsRead));
  }

  Map<DexField, Set<DexEncodedMethod>> collectStaticFieldsWritten() {
    return Collections.unmodifiableMap(collectFields(staticFieldsWritten));
  }

  private Set<DexField> collectReachedFields(
      Set<DexField> set, Function<DexField, DexField> lookup) {
    return set.stream()
        .map(lookup)
        .filter(Objects::nonNull)
        .collect(Collectors.toCollection(Sets::newIdentityHashSet));
  }

  private DexField tryLookupInstanceField(DexField field) {
    DexEncodedField target = appInfo.lookupInstanceTarget(field.clazz, field);
    return target == null ? null : target.field;
  }

  private DexField tryLookupStaticField(DexField field) {
    DexEncodedField target = appInfo.lookupStaticTarget(field.clazz, field);
    return target == null ? null : target.field;
  }

  SortedSet<DexField> mergeFieldAccesses(Set<DexField> instanceFields, Set<DexField> staticFields) {
    return ImmutableSortedSet.copyOf(PresortedComparable<DexField>::slowCompareTo,
        Sets.union(
            collectReachedFields(instanceFields, this::tryLookupInstanceField),
            collectReachedFields(staticFields, this::tryLookupStaticField)));
  }

  private void markClassAsInstantiatedWithCompatRule(DexClass clazz) {
    ProguardKeepRule rule =
        ProguardConfigurationUtils.buildDefaultInitializerKeepRule(clazz);
    proguardCompatibilityWorkList.add(
        Action.markInstantiated(clazz, KeepReason.dueToProguardCompatibilityKeepRule(rule)));
    if (clazz.hasDefaultInitializer()) {
      proguardCompatibilityWorkList.add(
          Action.markMethodLive(
              clazz.getDefaultInitializer(), KeepReason.dueToProguardCompatibilityKeepRule(rule)));
    }
  }

  private void markFieldAsKeptWithCompatRule(DexEncodedField field) {
    DexClass holderClass = appInfo.definitionFor(field.field.getHolder());
    ProguardKeepRule rule =
        ProguardConfigurationUtils.buildFieldKeepRule(holderClass, field);
    proguardCompatibilityWorkList.add(
        Action.markFieldKept(field, KeepReason.dueToProguardCompatibilityKeepRule(rule)));
  }

  private void markMethodAsKeptWithCompatRule(DexEncodedMethod method) {
    DexClass holderClass = appInfo.definitionFor(method.method.getHolder());
    ProguardKeepRule rule =
        ProguardConfigurationUtils.buildMethodKeepRule(holderClass, method);
    proguardCompatibilityWorkList.add(
        Action.markMethodLive(method, KeepReason.dueToProguardCompatibilityKeepRule(rule)));
  }

  private void handleProguardReflectiveBehavior(DexEncodedMethod method) {
    Origin origin = appInfo.originFor(method.method.holder);
    IRCode code = method.buildIR(appInfo, options, origin);
    code.instructionIterator().forEachRemaining(instr ->
        handleProguardReflectiveBehavior(instr, origin));
  }

  private void handleProguardReflectiveBehavior(Instruction instruction, Origin origin) {
    if (!instruction.isInvokeMethod()) {
      return;
    }
    InvokeMethod invoke = instruction.asInvokeMethod();
    DexMethod invokedMethod = invoke.getInvokedMethod();
    if (!isReflectionMethod(appInfo.dexItemFactory, invokedMethod)) {
      return;
    }
    DexItemBasedString itemBasedString = identifyIdentiferNameString(appInfo, invoke);
    if (itemBasedString == null) {
      if (options.proguardConfiguration.isObfuscating()) {
        warnUndeterminedIdentifier(options.reporter, invokedMethod, origin, instruction, null);
      }
      return;
    }
    if (itemBasedString.basedOn instanceof DexType) {
      DexClass clazz = appInfo.definitionFor((DexType) itemBasedString.basedOn);
      if (clazz != null) {
        markClassAsInstantiatedWithCompatRule(clazz);
      }
    } else if (itemBasedString.basedOn instanceof DexField) {
      DexEncodedField encodedField = appInfo.definitionFor((DexField) itemBasedString.basedOn);
      if (encodedField != null) {
        markFieldAsKeptWithCompatRule(encodedField);
      }
    } else {
      assert itemBasedString.basedOn instanceof DexMethod;
      DexEncodedMethod encodedMethod = appInfo.definitionFor((DexMethod) itemBasedString.basedOn);
      if (encodedMethod != null) {
        markMethodAsKeptWithCompatRule(encodedMethod);
      }
    }
  }

  private static class Action {

    final Kind kind;
    final DexItem target;
    final DexItem context;
    final KeepReason reason;

    private Action(Kind kind, DexItem target, DexItem context, KeepReason reason) {
      this.kind = kind;
      this.target = target;
      this.context = context;
      this.reason = reason;
    }

    public static Action markReachableVirtual(DexMethod method, KeepReason reason) {
      return new Action(Kind.MARK_REACHABLE_VIRTUAL, method, null, reason);
    }

    public static Action markReachableInterface(DexMethod method, KeepReason reason) {
      return new Action(Kind.MARK_REACHABLE_INTERFACE, method, null, reason);
    }

    public static Action markReachableSuper(DexMethod method, DexEncodedMethod from) {
      return new Action(Kind.MARK_REACHABLE_SUPER, method, from, null);
    }

    public static Action markReachableField(DexField field, KeepReason reason) {
      return new Action(Kind.MARK_REACHABLE_FIELD, field, null, reason);
    }

    public static Action markInstantiated(DexClass clazz, KeepReason reason) {
      return new Action(Kind.MARK_INSTANTIATED, clazz, null, reason);
    }

    public static Action markMethodLive(DexEncodedMethod method, KeepReason reason) {
      return new Action(Kind.MARK_METHOD_LIVE, method, null, reason);
    }

    public static Action markMethodKept(DexEncodedMethod method, KeepReason reason) {
      return new Action(Kind.MARK_METHOD_KEPT, method, null, reason);
    }

    public static Action markFieldKept(DexEncodedField field, KeepReason reason) {
      return new Action(Kind.MARK_FIELD_KEPT, field, null, reason);
    }

    private enum Kind {
      MARK_REACHABLE_VIRTUAL,
      MARK_REACHABLE_INTERFACE,
      MARK_REACHABLE_SUPER,
      MARK_REACHABLE_FIELD,
      MARK_INSTANTIATED,
      MARK_METHOD_LIVE,
      MARK_METHOD_KEPT,
      MARK_FIELD_KEPT
    }
  }

  /**
   * Encapsulates liveness and reachability information for an application.
   */
  public static class AppInfoWithLiveness extends AppInfoWithSubtyping {

    /**
     * Set of types that are mentioned in the program. We at least need an empty abstract classitem
     * for these.
     */
    public final SortedSet<DexType> liveTypes;
    /**
     * Set of types that are actually instantiated. These cannot be abstract.
     */
    final SortedSet<DexType> instantiatedTypes;
    /**
     * Set of methods that are the immediate target of an invoke. They might not actually be live
     * but are required so that invokes can find the method. If such a method is not live (i.e. not
     * contained in {@link #liveMethods}, it may be marked as abstract and its implementation may be
     * removed.
     */
    final SortedSet<DexMethod> targetedMethods;
    /**
     * Set of methods that belong to live classes and can be reached by invokes. These need to be
     * kept.
     */
    final SortedSet<DexMethod> liveMethods;
    /**
     * Set of fields that belong to live classes and can be reached by invokes. These need to be
     * kept.
     */
    public final SortedSet<DexField> liveFields;
    /**
     * Set of all fields which may be touched by a get operation. This is actual field definitions.
     */
    public final SortedSet<DexField> fieldsRead;
    /**
     * Set of all fields which may be touched by a put operation. This is actual field definitions.
     */
    public final SortedSet<DexField> fieldsWritten;
    /**
     * Set of all field ids used in instance field reads, along with access context.
     */
    public final Map<DexField, Set<DexEncodedMethod>> instanceFieldReads;
    /**
     * Set of all field ids used in instance field writes, along with access context.
     */
    public final Map<DexField, Set<DexEncodedMethod>> instanceFieldWrites;
    /**
     * Set of all field ids used in static field reads, along with access context.
     */
    public final Map<DexField, Set<DexEncodedMethod>> staticFieldReads;
    /**
     * Set of all field ids used in static field writes, along with access context.
     */
    public final Map<DexField, Set<DexEncodedMethod>> staticFieldWrites;
    /**
     * Set of all methods referenced in virtual invokes;
     */
    public final SortedSet<DexMethod> virtualInvokes;
    /**
     * Set of all methods referenced in interface invokes;
     */
    public final SortedSet<DexMethod> interfaceInvokes;
    /**
     * Set of all methods referenced in super invokes;
     */
    public final SortedSet<DexMethod> superInvokes;
    /**
     * Set of all methods referenced in direct invokes;
     */
    public final SortedSet<DexMethod> directInvokes;
    /**
     * Set of all methods referenced in static invokes;
     */
    public final SortedSet<DexMethod> staticInvokes;
    /**
     * Set of method signatures used in invoke-super instructions that either cannot be resolved or
     * resolve to a private method (leading to an IllegalAccessError).
     */
    public final SortedSet<DexMethod> brokenSuperInvokes;
    /**
     * Set of all items that have to be kept independent of whether they are used.
     */
    final Set<DexItem> pinnedItems;
    /**
     * All items with assumenosideeffects rule.
     */
    public final Map<DexItem, ProguardMemberRule> noSideEffects;
    /**
     * All items with assumevalues rule.
     */
    public final Map<DexItem, ProguardMemberRule> assumedValues;
    /**
     * All methods that should be inlined if possible due to a configuration directive.
     */
    public final Set<DexItem> alwaysInline;
    /**
     * All methods that *must* be inlined due to a configuration directive (testing only).
     */
    public final Set<DexItem> forceInline;
    /**
     * All methods that *must* never be inlined due to a configuration directive (testing only).
     */
    public final Set<DexItem> neverInline;
    /**
     * All items with -identifiernamestring rule.
     */
    public final Set<DexItem> identifierNameStrings;
    /**
     * Set of fields that have been identified as proto-lite fields by the
     * {@link ProtoLiteExtension}.
     */
    final Set<DexField> protoLiteFields;
    /**
     * A set of types that have been removed by the {@link TreePruner}.
     */
    final Set<DexType> prunedTypes;
    /**
     * A map from switchmap class types to their corresponding switchmaps.
     */
    final Map<DexField, Int2ReferenceMap<DexField>> switchMaps;
    /**
     * A map from enum types to their ordinal values.
     */
    final Map<DexType, Reference2IntMap<DexField>> ordinalsMaps;

    final ImmutableSortedSet<DexType> instantiatedLambdas;

    private AppInfoWithLiveness(AppInfoWithSubtyping appInfo, Enqueuer enqueuer) {
      super(appInfo);
      this.liveTypes = ImmutableSortedSet.copyOf(
          PresortedComparable<DexType>::slowCompareTo, enqueuer.liveTypes);
      this.instantiatedTypes = ImmutableSortedSet.copyOf(
          PresortedComparable<DexType>::slowCompareTo, enqueuer.instantiatedTypes.getItems());
      this.instantiatedLambdas =
          ImmutableSortedSet.copyOf(
              PresortedComparable<DexType>::slowCompareTo, enqueuer.instantiatedLambdas.getItems());
      this.targetedMethods = toSortedDescriptorSet(enqueuer.targetedMethods.getItems());
      this.liveMethods = toSortedDescriptorSet(enqueuer.liveMethods.getItems());
      this.liveFields = toSortedDescriptorSet(enqueuer.liveFields.getItems());
      this.instanceFieldReads = enqueuer.collectInstanceFieldsRead();
      this.instanceFieldWrites = enqueuer.collectInstanceFieldsWritten();
      this.staticFieldReads = enqueuer.collectStaticFieldsRead();
      this.staticFieldWrites = enqueuer.collectStaticFieldsWritten();
      this.fieldsRead = enqueuer.mergeFieldAccesses(
          instanceFieldReads.keySet(), staticFieldReads.keySet());
      this.fieldsWritten = enqueuer.mergeFieldAccesses(
          instanceFieldWrites.keySet(), staticFieldWrites.keySet());
      this.pinnedItems = rewritePinnedItemsToDescriptors(enqueuer.pinnedItems);
      this.virtualInvokes = joinInvokedMethods(enqueuer.virtualInvokes);
      this.interfaceInvokes = joinInvokedMethods(enqueuer.interfaceInvokes);
      this.superInvokes = joinInvokedMethods(enqueuer.superInvokes, TargetWithContext::getTarget);
      this.directInvokes = joinInvokedMethods(enqueuer.directInvokes);
      this.staticInvokes = joinInvokedMethods(enqueuer.staticInvokes);
      this.brokenSuperInvokes =
          ImmutableSortedSet.copyOf(DexMethod::slowCompareTo, enqueuer.brokenSuperInvokes);
      this.noSideEffects = enqueuer.rootSet.noSideEffects;
      this.assumedValues = enqueuer.rootSet.assumedValues;
      this.alwaysInline = enqueuer.rootSet.alwaysInline;
      this.forceInline = enqueuer.rootSet.forceInline;
      this.neverInline = enqueuer.rootSet.neverInline;
      this.identifierNameStrings =
          Sets.union(enqueuer.rootSet.identifierNameStrings, enqueuer.identifierNameStrings);
      this.protoLiteFields = enqueuer.protoLiteFields;
      this.prunedTypes = Collections.emptySet();
      this.switchMaps = Collections.emptyMap();
      this.ordinalsMaps = Collections.emptyMap();
      assert Sets.intersection(instanceFieldReads.keySet(), staticFieldReads.keySet()).isEmpty();
      assert Sets.intersection(instanceFieldWrites.keySet(), staticFieldWrites.keySet()).isEmpty();
    }

    private AppInfoWithLiveness(AppInfoWithLiveness previous, DexApplication application,
        Collection<DexType> removedClasses) {
      super(application);
      this.liveTypes = previous.liveTypes;
      this.instantiatedTypes = previous.instantiatedTypes;
      this.instantiatedLambdas = previous.instantiatedLambdas;
      this.targetedMethods = previous.targetedMethods;
      this.liveMethods = previous.liveMethods;
      this.liveFields = previous.liveFields;
      this.instanceFieldReads = previous.instanceFieldReads;
      this.instanceFieldWrites = previous.instanceFieldWrites;
      this.staticFieldReads = previous.staticFieldReads;
      this.staticFieldWrites = previous.staticFieldWrites;
      this.fieldsRead = previous.fieldsRead;
      // TODO(herhut): We remove fields that are only written, so maybe update this.
      this.fieldsWritten = previous.fieldsWritten;
      assert assertNoItemRemoved(previous.pinnedItems, removedClasses);
      this.pinnedItems = previous.pinnedItems;
      this.noSideEffects = previous.noSideEffects;
      this.assumedValues = previous.assumedValues;
      this.virtualInvokes = previous.virtualInvokes;
      this.interfaceInvokes = previous.interfaceInvokes;
      this.superInvokes = previous.superInvokes;
      this.directInvokes = previous.directInvokes;
      this.staticInvokes = previous.staticInvokes;
      this.brokenSuperInvokes = previous.brokenSuperInvokes;
      this.protoLiteFields = previous.protoLiteFields;
      this.alwaysInline = previous.alwaysInline;
      this.forceInline = previous.forceInline;
      this.neverInline = previous.neverInline;
      this.identifierNameStrings = previous.identifierNameStrings;
      this.prunedTypes = mergeSets(previous.prunedTypes, removedClasses);
      this.switchMaps = previous.switchMaps;
      this.ordinalsMaps = previous.ordinalsMaps;
      assert Sets.intersection(instanceFieldReads.keySet(), staticFieldReads.keySet()).isEmpty();
      assert Sets.intersection(instanceFieldWrites.keySet(), staticFieldWrites.keySet()).isEmpty();
    }

    private AppInfoWithLiveness(AppInfoWithLiveness previous,
        DirectMappedDexApplication application,
        GraphLense lense) {
      super(application, lense);
      this.liveTypes = rewriteItems(previous.liveTypes, lense::lookupType);
      this.instantiatedTypes = rewriteItems(previous.instantiatedTypes, lense::lookupType);
      this.instantiatedLambdas = rewriteItems(previous.instantiatedLambdas, lense::lookupType);
      this.targetedMethods = rewriteMethodsConservatively(previous.targetedMethods, lense);
      this.liveMethods = rewriteMethodsConservatively(previous.liveMethods, lense);
      this.liveFields = rewriteItems(previous.liveFields, lense::lookupField);
      this.instanceFieldReads =
          rewriteKeysWhileMergingValues(previous.instanceFieldReads, lense::lookupField);
      this.instanceFieldWrites =
          rewriteKeysWhileMergingValues(previous.instanceFieldWrites, lense::lookupField);
      this.staticFieldReads =
          rewriteKeysWhileMergingValues(previous.staticFieldReads, lense::lookupField);
      this.staticFieldWrites =
          rewriteKeysWhileMergingValues(previous.staticFieldWrites, lense::lookupField);
      this.fieldsRead = rewriteItems(previous.fieldsRead, lense::lookupField);
      this.fieldsWritten = rewriteItems(previous.fieldsWritten, lense::lookupField);
      this.pinnedItems = rewriteMixedItemsConservatively(previous.pinnedItems, lense);
      this.virtualInvokes = rewriteMethodsConservatively(previous.virtualInvokes, lense);
      this.interfaceInvokes = rewriteMethodsConservatively(previous.interfaceInvokes, lense);
      this.superInvokes = rewriteMethodsConservatively(previous.superInvokes, lense);
      this.directInvokes = rewriteMethodsConservatively(previous.directInvokes, lense);
      this.staticInvokes = rewriteMethodsConservatively(previous.staticInvokes, lense);
      this.brokenSuperInvokes = rewriteMethodsConservatively(previous.brokenSuperInvokes, lense);
      this.prunedTypes = rewriteItems(previous.prunedTypes, lense::lookupType);
      // TODO(herhut): Migrate these to Descriptors, as well.
      assert lense.assertNotModified(previous.noSideEffects.keySet());
      this.noSideEffects = previous.noSideEffects;
      assert lense.assertNotModified(previous.assumedValues.keySet());
      this.assumedValues = previous.assumedValues;
      assert lense.assertNotModified(previous.alwaysInline);
      this.alwaysInline = previous.alwaysInline;
      this.forceInline = previous.forceInline;
      this.neverInline = previous.neverInline;
      this.identifierNameStrings =
          rewriteMixedItemsConservatively(previous.identifierNameStrings, lense);
      // Switchmap classes should never be affected by renaming.
      assert lense.assertNotModified(
          previous.switchMaps.keySet().stream().map(this::definitionFor).filter(Objects::nonNull)
              .collect(Collectors.toList()));
      this.switchMaps = previous.switchMaps;
      this.ordinalsMaps = rewriteKeys(previous.ordinalsMaps, lense::lookupType);
      this.protoLiteFields = previous.protoLiteFields;
      // Sanity check sets after rewriting.
      assert Sets.intersection(instanceFieldReads.keySet(), staticFieldReads.keySet()).isEmpty();
      assert Sets.intersection(instanceFieldWrites.keySet(), staticFieldWrites.keySet()).isEmpty();
    }

    public AppInfoWithLiveness(AppInfoWithLiveness previous,
        Map<DexField, Int2ReferenceMap<DexField>> switchMaps,
        Map<DexType, Reference2IntMap<DexField>> ordinalsMaps) {
      super(previous);
      this.liveTypes = previous.liveTypes;
      this.instantiatedTypes = previous.instantiatedTypes;
      this.instantiatedLambdas = previous.instantiatedLambdas;
      this.targetedMethods = previous.targetedMethods;
      this.liveMethods = previous.liveMethods;
      this.liveFields = previous.liveFields;
      this.instanceFieldReads = previous.instanceFieldReads;
      this.instanceFieldWrites = previous.instanceFieldWrites;
      this.staticFieldReads = previous.staticFieldReads;
      this.staticFieldWrites = previous.staticFieldWrites;
      this.fieldsRead = previous.fieldsRead;
      this.fieldsWritten = previous.fieldsWritten;
      this.pinnedItems = previous.pinnedItems;
      this.noSideEffects = previous.noSideEffects;
      this.assumedValues = previous.assumedValues;
      this.virtualInvokes = previous.virtualInvokes;
      this.interfaceInvokes = previous.interfaceInvokes;
      this.superInvokes = previous.superInvokes;
      this.directInvokes = previous.directInvokes;
      this.staticInvokes = previous.staticInvokes;
      this.brokenSuperInvokes = previous.brokenSuperInvokes;
      this.protoLiteFields = previous.protoLiteFields;
      this.alwaysInline = previous.alwaysInline;
      this.forceInline = previous.forceInline;
      this.neverInline = previous.neverInline;
      this.identifierNameStrings = previous.identifierNameStrings;
      this.prunedTypes = previous.prunedTypes;
      this.switchMaps = switchMaps;
      this.ordinalsMaps = ordinalsMaps;
    }

    public Reference2IntMap<DexField> getOrdinalsMapFor(DexType enumClass) {
      return ordinalsMaps.get(enumClass);
    }

    public Int2ReferenceMap<DexField> getSwitchMapFor(DexField field) {
      return switchMaps.get(field);
    }

    private boolean assertNoItemRemoved(Collection<DexItem> items, Collection<DexType> types) {
      Set<DexType> typeSet = ImmutableSet.copyOf(types);
      for (DexItem item : items) {
        if (item instanceof DexType) {
          assert !typeSet.contains(item);
        } else if (item instanceof DexMethod) {
          assert !typeSet.contains(((DexMethod) item).getHolder());
        } else if (item instanceof DexField) {
          assert !typeSet.contains(((DexField) item).getHolder());
        } else {
          assert false;
        }
      }
      return true;
    }

    private SortedSet<DexMethod> joinInvokedMethods(Map<DexType, Set<DexMethod>> invokes) {
      return joinInvokedMethods(invokes, Function.identity());
    }

    private <T> SortedSet<DexMethod> joinInvokedMethods(Map<DexType, Set<T>> invokes,
        Function<T, DexMethod> getter) {
      return invokes.values().stream().flatMap(Set::stream).map(getter)
          .collect(ImmutableSortedSet.toImmutableSortedSet(PresortedComparable::slowCompare));
    }

    private <T extends PresortedComparable<T>> SortedSet<T> toSortedDescriptorSet(
        Set<? extends KeyedDexItem<T>> set) {
      ImmutableSortedSet.Builder<T> builder =
          new ImmutableSortedSet.Builder<>(PresortedComparable<T>::slowCompareTo);
      for (KeyedDexItem<T> item : set) {
        builder.add(item.getKey());
      }
      return builder.build();
    }

    private ImmutableSet<DexItem> rewritePinnedItemsToDescriptors(Collection<DexItem> source) {
      ImmutableSet.Builder<DexItem> builder = ImmutableSet.builder();
      for (DexItem item : source) {
        // TODO(b/67934123) There should be a common interface to extract this information.
        if (item instanceof DexClass) {
          builder.add(((DexClass) item).type);
        } else if (item instanceof DexEncodedMethod) {
          builder.add(((DexEncodedMethod) item).method);
        } else if (item instanceof DexEncodedField) {
          builder.add(((DexEncodedField) item).field);
        } else {
          throw new Unreachable();
        }
      }
      return builder.build();
    }

    private static ImmutableSortedSet<DexMethod> rewriteMethodsConservatively(
        Set<DexMethod> original, GraphLense lense) {
      ImmutableSortedSet.Builder<DexMethod> builder =
          new ImmutableSortedSet.Builder<>(PresortedComparable::slowCompare);
      if (lense.isContextFreeForMethods()) {
        for (DexMethod item : original) {
          builder.add(lense.lookupMethod(item));
        }
      } else {
        for (DexMethod item : original) {
          // Avoid using lookupMethodInAllContexts when possible.
          if (lense.isContextFreeForMethod(item)) {
            builder.add(lense.lookupMethod(item));
          } else {
            // The lense is context sensitive, but we do not have the context here. Therefore, we
            // conservatively look up the method in all contexts.
            builder.addAll(lense.lookupMethodInAllContexts(item));
          }
        }
      }
      return builder.build();
    }

    private static <T extends PresortedComparable<T>> ImmutableSortedSet<T> rewriteItems(
        Set<T> original, Function<T, T> rewrite) {
      ImmutableSortedSet.Builder<T> builder =
          new ImmutableSortedSet.Builder<>(PresortedComparable::slowCompare);
      for (T item : original) {
        builder.add(rewrite.apply(item));
      }
      return builder.build();
    }

    private static <T extends PresortedComparable<T>, S> ImmutableMap<T, S> rewriteKeys(
        Map<T, S> original, Function<T, T> rewrite) {
      ImmutableMap.Builder<T, S> builder = new ImmutableMap.Builder<>();
      for (T item : original.keySet()) {
        builder.put(rewrite.apply(item), original.get(item));
      }
      return builder.build();
    }

    private static <T extends PresortedComparable<T>, S>
        Map<T, Set<S>> rewriteKeysWhileMergingValues(
            Map<T, Set<S>> original, Function<T, T> rewrite) {
      Map<T, Set<S>> result = new IdentityHashMap<>();
      for (T item : original.keySet()) {
        T rewrittenKey = rewrite.apply(item);
        result.computeIfAbsent(rewrittenKey, k -> Sets.newIdentityHashSet())
            .addAll(original.get(item));
      }
      return Collections.unmodifiableMap(result);
    }

    private static ImmutableSet<DexItem> rewriteMixedItemsConservatively(
        Set<DexItem> original, GraphLense lense) {
      ImmutableSet.Builder<DexItem> builder = ImmutableSet.builder();
      for (DexItem item : original) {
        // TODO(b/67934123) There should be a common interface to perform the dispatch.
        if (item instanceof DexType) {
          builder.add(lense.lookupType((DexType) item));
        } else if (item instanceof DexMethod) {
          DexMethod method = (DexMethod) item;
          if (lense.isContextFreeForMethod(method)) {
            builder.add(lense.lookupMethod(method));
          } else {
            builder.addAll(lense.lookupMethodInAllContexts(method));
          }
        } else if (item instanceof DexField) {
          builder.add(lense.lookupField((DexField) item));
        } else {
          throw new Unreachable();
        }
      }
      return builder.build();
    }

    private static <T> Set<T> mergeSets(Collection<T> first, Collection<T> second) {
      ImmutableSet.Builder<T> builder = ImmutableSet.builder();
      builder.addAll(first);
      builder.addAll(second);
      return builder.build();
    }

    @Override
    public boolean hasLiveness() {
      return true;
    }

    @Override
    public AppInfoWithLiveness withLiveness() {
      return this;
    }

    // TODO(b/67934123) Unify into one method,
    public boolean isPinned(DexType item) {
      return pinnedItems.contains(item);
    }

    // TODO(b/67934123) Unify into one method,
    public boolean isPinned(DexMethod item) {
      return pinnedItems.contains(item);
    }

    // TODO(b/67934123) Unify into one method,
    public boolean isPinned(DexField item) {
      return pinnedItems.contains(item);
    }

    public boolean isProtoLiteField(DexField field) {
      return protoLiteFields.contains(field);
    }

    public Iterable<DexItem> getPinnedItems() {
      return pinnedItems;
    }

    /**
     * Returns a copy of this AppInfoWithLiveness where the set of classes is pruned using the given
     * DexApplication object.
     */
    public AppInfoWithLiveness prunedCopyFrom(DexApplication application,
        Collection<DexType> removedClasses) {
      return new AppInfoWithLiveness(this, application, removedClasses);
    }

    public AppInfoWithLiveness rewrittenWithLense(DirectMappedDexApplication application,
        GraphLense lense) {
      return new AppInfoWithLiveness(this, application, lense);
    }

    /**
     * Returns true if the given type was part of the original program but has been removed during
     * tree shaking.
     */
    public boolean wasPruned(DexType type) {
      return prunedTypes.contains(type);
    }

    public DexEncodedMethod lookup(Type type, DexMethod target, DexType invocationContext) {
      DexType holder = target.getHolder();
      if (!holder.isClassType()) {
        return null;
      }
      switch (type) {
        case VIRTUAL:
          return lookupSingleVirtualTarget(target);
        case INTERFACE:
          return lookupSingleInterfaceTarget(target);
        case DIRECT:
          return lookupDirectTarget(target);
        case STATIC:
          return lookupStaticTarget(target);
        case SUPER:
          return lookupSuperTarget(target, invocationContext);
        default:
          return null;
      }
    }

    /**
     * For mapping invoke virtual instruction to single target method.
     */
    public DexEncodedMethod lookupSingleVirtualTarget(DexMethod method) {
      return lookupSingleVirtualTarget(method, method.holder);
    }

    public DexEncodedMethod lookupSingleVirtualTarget(
        DexMethod method, DexType refinedReceiverType) {
      // This implements the logic from
      // https://docs.oracle.com/javase/specs/jvms/se9/html/jvms-6.html#jvms-6.5.invokevirtual
      assert method != null;
      assert refinedReceiverType.isSubtypeOf(method.holder, this);
      DexClass holder = definitionFor(method.holder);
      if (holder == null || holder.isLibraryClass() || holder.isInterface()) {
        return null;
      }
      boolean refinedReceiverIsStrictSubType = refinedReceiverType != method.holder;
      DexClass refinedHolder =
          refinedReceiverIsStrictSubType ? definitionFor(refinedReceiverType) : holder;
      assert refinedHolder != null;
      assert !refinedHolder.isLibraryClass();
      if (method.isSingleVirtualMethodCached(refinedReceiverType)) {
        return method.getSingleVirtualMethodCache(refinedReceiverType);
      }
      // For kept types we cannot ensure a single target.
      if (pinnedItems.contains(method.holder)) {
        method.setSingleVirtualMethodCache(refinedReceiverType, null);
        return null;
      }
      // First get the target for the holder type.
      ResolutionResult topMethod = resolveMethod(method.holder, method);
      // We might hit none or multiple targets. Both make this fail at runtime.
      if (!topMethod.hasSingleTarget() || !topMethod.asSingleTarget().isVirtualMethod()) {
        method.setSingleVirtualMethodCache(refinedReceiverType, null);
        return null;
      }
      // Now, resolve the target with the refined receiver type.
      if (refinedReceiverIsStrictSubType) {
        topMethod = resolveMethod(refinedReceiverType, method);
      }
      DexEncodedMethod topSingleTarget = topMethod.asSingleTarget();
      DexClass topHolder = definitionFor(topSingleTarget.method.holder);
      // We need to know whether the top method is from an interface, as that would allow it to be
      // shadowed by a default method from an interface further down.
      boolean topIsFromInterface = topHolder.isInterface();
      // Now look at all subtypes and search for overrides.
      DexEncodedMethod result = findSingleTargetFromSubtypes(refinedReceiverType, method,
          topSingleTarget, !refinedHolder.accessFlags.isAbstract(), topIsFromInterface);
      // Map the failure case of SENTINEL to null.
      result = result == DexEncodedMethod.SENTINEL ? null : result;
      method.setSingleVirtualMethodCache(refinedReceiverType, result);
      return result;
    }

    /**
     * Computes which methods overriding <code>method</code> are visible for the subtypes of type.
     * <p>
     * <code>candidate</code> is the definition further up the hierarchy that is visible from the
     * subtypes. If <code>candidateIsReachable</code> is true, the provided candidate is already a
     * target for a type further up the chain, so anything found in subtypes is a conflict. If it is
     * false, the target exists but is not reachable from a live type.
     * <p>
     * Returns <code>null</code> if the given type has no subtypes or all subtypes are abstract.
     * Returns {@link DexEncodedMethod#SENTINEL} if multiple live overrides were found. Returns the
     * single virtual target otherwise.
     */
    private DexEncodedMethod findSingleTargetFromSubtypes(DexType type, DexMethod method,
        DexEncodedMethod candidate,
        boolean candidateIsReachable, boolean checkForInterfaceConflicts) {
      // If the candidate is reachable, we already have a previous result.
      DexEncodedMethod result = candidateIsReachable ? candidate : null;
      if (pinnedItems.contains(type)) {
        // For kept types we do not know all subtypes, so abort.
        return DexEncodedMethod.SENTINEL;
      }
      for (DexType subtype : type.allExtendsSubtypes()) {
        DexClass clazz = definitionFor(subtype);
        DexEncodedMethod target = clazz.lookupMethod(method);
        if (target != null) {
          // We found a method on this class. If this class is not abstract it is a runtime
          // reachable override and hence a conflict.
          if (!clazz.accessFlags.isAbstract()) {
            if (result != null && result != target) {
              // We found a new target on this subtype that does not match the previous one. Fail.
              return DexEncodedMethod.SENTINEL;
            }
            // Add the first or matching target.
            result = target;
          }
        }
        if (checkForInterfaceConflicts) {
          // We have to check whether there are any default methods in implemented interfaces.
          if (interfacesMayHaveDefaultFor(clazz.interfaces, method)) {
            return DexEncodedMethod.SENTINEL;
          }
        }
        DexEncodedMethod newCandidate = target == null ? candidate : target;
        // If we have a new target and did not fail, it is not an override of a reachable method.
        // Whether the target is actually reachable depends on whether this class is abstract.
        // If we did not find a new target, the candidate is reachable if it was before, or if this
        // class is not abstract.
        boolean newCandidateIsReachable =
            !clazz.accessFlags.isAbstract() || ((target == null) && candidateIsReachable);
        DexEncodedMethod subtypeTarget = findSingleTargetFromSubtypes(subtype, method,
            newCandidate,
            newCandidateIsReachable, checkForInterfaceConflicts);
        if (subtypeTarget != null) {
          // We found a target in the subclasses. If we already have a different result, fail.
          if (result != null && result != subtypeTarget) {
            return DexEncodedMethod.SENTINEL;
          }
          // Remember this new result.
          result = subtypeTarget;
        }
      }
      return result;
    }

    /**
     * Checks whether any interface in the given list or their super interfaces implement a default
     * method.
     * <p>
     * This method is conservative for unknown interfaces and interfaces from the library.
     */
    private boolean interfacesMayHaveDefaultFor(DexTypeList ifaces, DexMethod method) {
      for (DexType iface : ifaces.values) {
        DexClass clazz = definitionFor(iface);
        if (clazz == null || clazz.isLibraryClass()) {
          return true;
        }
        DexEncodedMethod candidate = clazz.lookupMethod(method);
        if (candidate != null && !candidate.accessFlags.isAbstract()) {
          return true;
        }
        if (interfacesMayHaveDefaultFor(clazz.interfaces, method)) {
          return true;
        }
      }
      return false;
    }

    public DexEncodedMethod lookupSingleInterfaceTarget(DexMethod method) {
      return lookupSingleInterfaceTarget(method, method.holder);
    }

    public DexEncodedMethod lookupSingleInterfaceTarget(
        DexMethod method, DexType refinedReceiverType) {
      if (instantiatedLambdas.contains(method.holder)) {
        return null;
      }
      DexClass holder = definitionFor(method.holder);
      if ((holder == null) || holder.isLibraryClass() || !holder.accessFlags.isInterface()) {
        return null;
      }
      // First check that there is a target for this invoke-interface to hit. If there is none,
      // this will fail at runtime.
      ResolutionResult topTarget = resolveMethodOnInterface(method.holder, method);
      if (topTarget.asResultOfResolve() == null) {
        return null;
      }
      // For kept types we cannot ensure a single target.
      if (pinnedItems.contains(method.holder)) {
        return null;
      }
      DexEncodedMethod result = null;
      // The loop will ignore abstract classes that are not kept as they should not be a target
      // at runtime.
      Iterable<DexType> subTypesToExplore =
          refinedReceiverType == method.holder
              ? subtypes(method.holder)
              : Iterables.concat(
                  ImmutableList.of(refinedReceiverType), subtypes(refinedReceiverType));
      for (DexType type : subTypesToExplore) {
        if (pinnedItems.contains(type)) {
          // For kept classes we cannot ensure a single target.
          return null;
        }
        DexClass clazz = definitionFor(type);
        if (clazz.isInterface()) {
          // Default methods are looked up when looking at a specific subtype that does not
          // override them, so we ignore interface methods here. Otherwise, we would look up
          // default methods that are factually never used.
        } else if (!clazz.accessFlags.isAbstract()) {
          ResolutionResult resolutionResult = resolveMethodOnClass(type, method);
          if (resolutionResult.hasSingleTarget()) {
            if ((result != null) && (result != resolutionResult.asSingleTarget())) {
              return null;
            } else {
              result = resolutionResult.asSingleTarget();
            }
          } else {
            // This will fail at runtime.
            return null;
          }
        }
      }
      return result == null || !result.isVirtualMethod() ? null : result;
    }

    public AppInfoWithLiveness addSwitchMaps(Map<DexField, Int2ReferenceMap<DexField>> switchMaps) {
      assert this.switchMaps.isEmpty();
      return new AppInfoWithLiveness(this, switchMaps, ordinalsMaps);
    }

    public AppInfoWithLiveness addEnumOrdinalMaps(
        Map<DexType, Reference2IntMap<DexField>> ordinalsMaps) {
      assert this.ordinalsMaps.isEmpty();
      return new AppInfoWithLiveness(this, switchMaps, ordinalsMaps);
    }
  }

  private static class SetWithReason<T> {

    private final Set<T> items = Sets.newIdentityHashSet();
    private final Map<T, KeepReason> reasons = Maps.newIdentityHashMap();

    boolean add(T item, KeepReason reason) {
      if (items.add(item)) {
        reasons.put(item, reason);
        return true;
      }
      return false;
    }

    boolean contains(T item) {
      return items.contains(item);
    }

    Set<T> getItems() {
      return ImmutableSet.copyOf(items);
    }

    Map<T, KeepReason> getReasons() {
      return ImmutableMap.copyOf(reasons);
    }
  }

  private static final class TargetWithContext<T extends Descriptor<?, T>> {

    private final T target;
    private final DexEncodedMethod context;

    private TargetWithContext(T target, DexEncodedMethod context) {
      this.target = target;
      this.context = context;
    }

    public T getTarget() {
      return target;
    }

    public DexEncodedMethod getContext() {
      return context;
    }

    @Override
    public int hashCode() {
      return target.hashCode() * 31 + context.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof TargetWithContext)) {
        return false;
      }
      TargetWithContext other = (TargetWithContext) obj;
      return (this.target == other.target) && (this.context == other.context);
    }
  }

  private class AnnotationReferenceMarker implements IndexedItemCollection {

    private final DexItem annotationHolder;
    private final DexItemFactory dexItemFactory;

    private AnnotationReferenceMarker(DexItem annotationHolder, DexItemFactory dexItemFactory) {
      this.annotationHolder = annotationHolder;
      this.dexItemFactory = dexItemFactory;
    }

    @Override
    public boolean addClass(DexProgramClass dexProgramClass) {
      return false;
    }

    @Override
    public boolean addField(DexField field) {
      DexClass holder = appInfo.definitionFor(field.clazz);
      if (holder == null) {
        return false;
      }
      DexEncodedField target = holder.lookupStaticField(field);
      if (target != null) {
        // There is no dispatch on annotations, so only keep what is directly referenced.
        if (target.field == field) {
          markStaticFieldAsLive(field, KeepReason.referencedInAnnotation(annotationHolder));
        }
      } else {
        target = holder.lookupInstanceField(field);
        // There is no dispatch on annotations, so only keep what is directly referenced.
        if (target != null && target.field != field) {
          markInstanceFieldAsReachable(field, KeepReason.referencedInAnnotation(annotationHolder));
        }
      }
      return false;
    }

    @Override
    public boolean addMethod(DexMethod method) {
      DexClass holder = appInfo.definitionFor(method.holder);
      if (holder == null) {
        return false;
      }
      DexEncodedMethod target = holder.lookupDirectMethod(method);
      if (target != null) {
        // There is no dispatch on annotations, so only keep what is directly referenced.
        if (target.method == method) {
          markDirectStaticOrConstructorMethodAsLive(
              target, KeepReason.referencedInAnnotation(annotationHolder));
        }
      } else {
        target = holder.lookupVirtualMethod(method);
        // There is no dispatch on annotations, so only keep what is directly referenced.
        if (target != null && target.method == method) {
          markMethodAsTargeted(target, KeepReason.referencedInAnnotation(annotationHolder));
        }
      }
      return false;
    }

    @Override
    public boolean addString(DexString string) {
      return false;
    }

    @Override
    public boolean addProto(DexProto proto) {
      return false;
    }

    @Override
    public boolean addCallSite(DexCallSite callSite) {
      return false;
    }

    @Override
    public boolean addMethodHandle(DexMethodHandle methodHandle) {
      return false;
    }

    @Override
    public boolean addType(DexType type) {
      // Annotations can also contain the void type, which is not a class type, so filter it out
      // here.
      if (type != dexItemFactory.voidType) {
        markTypeAsLive(type);
      }
      return false;
    }
  }
}
