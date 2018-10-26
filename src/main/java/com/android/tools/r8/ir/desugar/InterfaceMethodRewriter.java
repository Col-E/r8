// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.graph.DexApplication.Builder;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItem;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexLibraryClass;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.InvokeSuper;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.ir.desugar.DefaultMethodsHelper.Collection;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.StringDiagnostic;
import com.google.common.collect.Sets;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

//
// Default and static interface method desugaring rewriter (note that lambda
// desugaring should have already processed the code before this rewriter).
//
// In short, during default and static interface method desugaring
// the following actions are performed:
//
//   (1) All static interface methods are moved into companion classes. All calls
//       to these methods are redirected appropriately. All references to these
//       methods from method handles are reported as errors.
//
// Companion class is a synthesized class (<interface-name>-CC) created to host
// static and former default interface methods (see below) from the interface.
//
//   (2) All default interface methods are made static and moved into companion
//       class.
//
//   (3) All calls to default interface methods made via 'super' are changed
//       to directly call appropriate static methods in companion classes.
//
//   (4) All other calls or references to default interface methods are not changed.
//
//   (5) For all program classes explicitly implementing interfaces we analyze the
//       set of default interface methods missing and add them, the created methods
//       forward the call to an appropriate method in interface companion class.
//
public final class InterfaceMethodRewriter {

  // Public for testing.
  public static final String DISPATCH_CLASS_NAME_SUFFIX = "$-DC";
  public static final String COMPANION_CLASS_NAME_SUFFIX = "$-CC";
  public static final String DEFAULT_METHOD_PREFIX = "$default$";
  public static final String PRIVATE_METHOD_PREFIX = "$private$";

  private final IRConverter converter;
  private final InternalOptions options;
  final DexItemFactory factory;

  // All forwarding methods generated during desugaring. We don't synchronize access
  // to this collection since it is only filled in ClassProcessor running synchronously.
  private final Set<DexEncodedMethod> synthesizedMethods = Sets.newIdentityHashSet();

  // Caches default interface method info for already processed interfaces.
  private final Map<DexType, DefaultMethodsHelper.Collection> cache = new ConcurrentHashMap<>();

  /** Interfaces requiring dispatch classes to be created, and appropriate callers. */
  private final ConcurrentMap<DexLibraryClass, Set<DexProgramClass>> requiredDispatchClasses =
      new ConcurrentHashMap<>();

  /**
   * A set of dexitems we have reported missing to dedupe warnings.
   */
  private final Set<DexItem> reportedMissing = Sets.newConcurrentHashSet();

  /**
   * Defines a minor variation in desugaring.
   */
  public enum Flavor {
    /**
     * Process all application resources.
     */
    IncludeAllResources,
    /**
     * Process all but DEX application resources.
     */
    ExcludeDexResources
  }

  public InterfaceMethodRewriter(IRConverter converter, InternalOptions options) {
    assert converter != null;
    this.converter = converter;
    this.options = options;
    this.factory = options.itemFactory;
  }

  // Rewrites the references to static and default interface methods.
  // NOTE: can be called for different methods concurrently.
  public void rewriteMethodReferences(DexEncodedMethod encodedMethod, IRCode code) {
    if (synthesizedMethods.contains(encodedMethod)) {
      return;
    }

    ListIterator<BasicBlock> blocks = code.listIterator();
    while (blocks.hasNext()) {
      BasicBlock block = blocks.next();
      InstructionListIterator instructions = block.listIterator();
      while (instructions.hasNext()) {
        Instruction instruction = instructions.next();

        if (instruction.isInvokeCustom()) {
          // Check that static interface methods are not referenced
          // from invoke-custom instructions via method handles.
          DexCallSite callSite = instruction.asInvokeCustom().getCallSite();
          reportStaticInterfaceMethodHandle(encodedMethod.method, callSite.bootstrapMethod);
          for (DexValue arg : callSite.bootstrapArgs) {
            if (arg instanceof DexValue.DexValueMethodHandle) {
              reportStaticInterfaceMethodHandle(encodedMethod.method,
                  ((DexValue.DexValueMethodHandle) arg).value);
            }
          }
          continue;
        }

        if (instruction.isInvokeStatic()) {
          InvokeStatic invokeStatic = instruction.asInvokeStatic();
          DexMethod method = invokeStatic.getInvokedMethod();
          DexClass clazz = findDefinitionFor(method.holder);
          if (clazz == null) {
            // NOTE: leave unchanged those calls to undefined targets. This may lead to runtime
            // exception but we can not report it as error since it can also be the intended
            // behavior.
            warnMissingType(encodedMethod.method, method.holder);
          } else if (clazz.isInterface()) {
            if (clazz.isLibraryClass()) {
              // NOTE: we intentionally don't desugar static calls into static interface
              // methods coming from android.jar since it is only possible in case v24+
              // version of android.jar is provided.
              //
              // We assume such calls are properly guarded by if-checks like
              //    'if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.XYZ) { ... }'
              //
              // WARNING: This may result in incorrect code on older platforms!
              // Retarget call to an appropriate method of companion class.

              if (!options.canLeaveStaticInterfaceMethodInvokes()) {
                // On pre-L devices static calls to interface methods result in verifier
                // rejecting the whole class. We have to create special dispatch classes,
                // so the user class is not rejected because it make this call directly.
                instructions.replaceCurrentInstruction(
                    new InvokeStatic(staticAsMethodOfDispatchClass(method),
                        invokeStatic.outValue(), invokeStatic.arguments()));
                requiredDispatchClasses
                    .computeIfAbsent(clazz.asLibraryClass(), k -> Sets.newConcurrentHashSet())
                    .add(findDefinitionFor(encodedMethod.method.holder).asProgramClass());
              }
            } else {
              instructions.replaceCurrentInstruction(
                  new InvokeStatic(staticAsMethodOfCompanionClass(method),
                      invokeStatic.outValue(), invokeStatic.arguments()));
            }
          }
          continue;
        }

        if (instruction.isInvokeSuper()) {
          InvokeSuper invokeSuper = instruction.asInvokeSuper();
          DexMethod method = invokeSuper.getInvokedMethod();
          DexClass clazz = findDefinitionFor(method.holder);
          if (clazz == null) {
            // NOTE: leave unchanged those calls to undefined targets. This may lead to runtime
            // exception but we can not report it as error since it can also be the intended
            // behavior.
            warnMissingType(encodedMethod.method, method.holder);
          } else if (clazz.isInterface() && !clazz.isLibraryClass()) {
            // NOTE: we intentionally don't desugar super calls into interface methods
            // coming from android.jar since it is only possible in case v24+ version
            // of android.jar is provided.
            //
            // We assume such calls are properly guarded by if-checks like
            //    'if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.XYZ) { ... }'
            //
            // WARNING: This may result in incorrect code on older platforms!
            // Retarget call to an appropriate method of companion class.
            DexMethod amendedMethod = amendDefaultMethod(
                findDefinitionFor(encodedMethod.method.holder), method);
            instructions.replaceCurrentInstruction(
                new InvokeStatic(defaultAsMethodOfCompanionClass(amendedMethod),
                    invokeSuper.outValue(), invokeSuper.arguments()));
          }
          continue;
        }

        if (instruction.isInvokeDirect()) {
          InvokeDirect invokeDirect = instruction.asInvokeDirect();
          DexMethod method = invokeDirect.getInvokedMethod();
          if (factory.isConstructor(method)) {
            continue;
          }

          DexClass clazz = findDefinitionFor(method.holder);
          if (clazz == null) {
            // Report missing class since we don't know if it is an interface.
            warnMissingType(encodedMethod.method, method.holder);

          } else if (clazz.isInterface()) {
            if (clazz.isLibraryClass()) {
              throw new CompilationError("Unexpected call to a private method " +
                  "defined in library class " + clazz.toSourceString(),
                  getMethodOrigin(encodedMethod.method));

            }

            // This might be either private method call, or a call to default
            // interface method made via invoke-direct.
            DexEncodedMethod virtualTarget = null;
            for (DexEncodedMethod candidate : clazz.virtualMethods()) {
              if (candidate.method == method) {
                virtualTarget = candidate;
                break;
              }
            }

            if (virtualTarget != null) {
              // This is a invoke-direct call to a virtual method.
              instructions.replaceCurrentInstruction(
                  new InvokeStatic(defaultAsMethodOfCompanionClass(method),
                      invokeDirect.outValue(), invokeDirect.arguments()));

            } else {
              // Otherwise this must be a private instance method call. Note that the referenced
              // method is expected to be in the current class since it is private, but desugaring
              // may move some methods or their code into other classes.

              instructions.replaceCurrentInstruction(
                  new InvokeStatic(privateAsMethodOfCompanionClass(method),
                      invokeDirect.outValue(), invokeDirect.arguments()));
            }
          }
        }
      }
    }
  }

  private void reportStaticInterfaceMethodHandle(DexMethod referencedFrom, DexMethodHandle handle) {
    if (handle.type.isInvokeStatic()) {
      DexClass holderClass = findDefinitionFor(handle.asMethod().holder);
      // NOTE: If the class definition is missing we can't check. Let it be handled as any other
      // missing call target.
      if (holderClass == null) {
        warnMissingType(referencedFrom, handle.asMethod().holder);
      } else if (holderClass.isInterface()) {
        throw new Unimplemented(
            "Desugaring of static interface method handle as in `"
                + referencedFrom.toSourceString() + "` in is not yet supported.");
      }
    }
  }

  /**
   * Returns the class definition for the specified type.
   *
   * @return may return null if no definition for the given type is available.
   */
  final DexClass findDefinitionFor(DexType type) {
    return converter.definitionFor(type);
  }

  // Gets the companion class for the interface `type`.
  final DexType getCompanionClassType(DexType type) {
    assert type.isClassType();
    String descriptor = type.descriptor.toString();
    String ccTypeDescriptor = descriptor.substring(0, descriptor.length() - 1)
        + COMPANION_CLASS_NAME_SUFFIX + ";";
    return factory.createType(ccTypeDescriptor);
  }

  // Gets the forwarding class for the interface `type`.
  final DexType getDispatchClassType(DexType type) {
    assert type.isClassType();
    String descriptor = type.descriptor.toString();
    String dcTypeDescriptor = descriptor.substring(0, descriptor.length() - 1)
        + DISPATCH_CLASS_NAME_SUFFIX + ";";
    return factory.createType(dcTypeDescriptor);
  }

  // Checks if `type` is a companion class.
  private boolean isCompanionClassType(DexType type) {
    return type.descriptor.toString().endsWith(COMPANION_CLASS_NAME_SUFFIX + ";");
  }

  // Gets the interface class for a companion class `type`.
  private DexType getInterfaceClassType(DexType type) {
    assert isCompanionClassType(type);
    String descriptor = type.descriptor.toString();
    String interfaceTypeDescriptor = descriptor.substring(0,
        descriptor.length() - 1 - COMPANION_CLASS_NAME_SUFFIX.length()) + ";";
    return factory.createType(interfaceTypeDescriptor);
  }

  private boolean isInMainDexList(DexType iface) {
    return converter.appInfo.isInMainDexList(iface);
  }

  // Represent a static interface method as a method of companion class.
  final DexMethod staticAsMethodOfCompanionClass(DexMethod method) {
    // No changes for static methods.
    return factory.createMethod(getCompanionClassType(method.holder), method.proto, method.name);
  }

  // Represent a static interface method as a method of dispatch class.
  final DexMethod staticAsMethodOfDispatchClass(DexMethod method) {
    return factory.createMethod(getDispatchClassType(method.holder), method.proto, method.name);
  }

  // Checks if the type ends with dispatch class suffix.
  public static boolean hasDispatchClassSuffix(DexType clazz) {
    return clazz.getName().endsWith(DISPATCH_CLASS_NAME_SUFFIX);
  }

  private DexMethod instanceAsMethodOfCompanionClass(DexMethod method, String prefix) {
    // Add an implicit argument to represent the receiver.
    DexType[] params = method.proto.parameters.values;
    DexType[] newParams = new DexType[params.length + 1];
    newParams[0] = method.holder;
    System.arraycopy(params, 0, newParams, 1, params.length);

    // Add prefix to avoid name conflicts.
    return factory.createMethod(getCompanionClassType(method.holder),
        factory.createProto(method.proto.returnType, newParams),
        factory.createString(prefix + method.name.toString()));
  }

  // It is possible that referenced method actually points to an interface which does
  // not define this default methods, but inherits it. We are making our best effort
  // to find an appropriate method, but still use the original one in case we fail.
  private DexMethod amendDefaultMethod(DexClass classToDesugar, DexMethod method) {
    DexMethod singleCandidate = getOrCreateInterfaceInfo(
        classToDesugar, classToDesugar, method.holder).getSingleCandidate(method);
    return singleCandidate != null ? singleCandidate : method;
  }

  // Represent a default interface method as a method of companion class.
  final DexMethod defaultAsMethodOfCompanionClass(DexMethod method) {
    return instanceAsMethodOfCompanionClass(method, DEFAULT_METHOD_PREFIX);
  }

  // Represent a private instance interface method as a method of companion class.
  final DexMethod privateAsMethodOfCompanionClass(DexMethod method) {
    // Add an implicit argument to represent the receiver.
    return instanceAsMethodOfCompanionClass(method, PRIVATE_METHOD_PREFIX);
  }

  /**
   * Move static and default interface methods to companion classes,
   * add missing methods to forward to moved default methods implementation.
   */
  public void desugarInterfaceMethods(Builder<?> builder, Flavor flavour) {
    // Process all classes first. Add missing forwarding methods to
    // replace desugared default interface methods.
    synthesizedMethods.addAll(processClasses(builder, flavour));

    // Process interfaces, create companion or dispatch class if needed, move static
    // methods to companion class, copy default interface methods to companion classes,
    // make original default methods abstract, remove bridge methods, create dispatch
    // classes if needed.
    Map<DexType, DexProgramClass> synthesizedClasses = processInterfaces(builder, flavour);

    for (Map.Entry<DexType, DexProgramClass> entry : synthesizedClasses.entrySet()) {
      // Don't need to optimize synthesized class since all of its methods
      // are just moved from interfaces and don't need to be re-processed.
      builder.addSynthesizedClass(entry.getValue(), isInMainDexList(entry.getKey()));
    }

    for (DexEncodedMethod method : synthesizedMethods) {
      converter.optimizeSynthesizedMethod(method);
    }

    // Cached data is not needed any more.
    clear();
  }

  private void clear() {
    this.cache.clear();
    this.synthesizedMethods.clear();
    this.requiredDispatchClasses.clear();
  }

  private static boolean shouldProcess(
      DexProgramClass clazz, Flavor flavour, boolean mustBeInterface) {
    return (!clazz.originatesFromDexResource() || flavour == Flavor.IncludeAllResources)
        && clazz.isInterface() == mustBeInterface;
  }

  private Map<DexType, DexProgramClass> processInterfaces(Builder<?> builder, Flavor flavour) {
    InterfaceProcessor processor = new InterfaceProcessor(this);
    for (DexProgramClass clazz : builder.getProgramClasses()) {
      if (shouldProcess(clazz, flavour, true)) {
        processor.process(clazz.asProgramClass());
      }
    }
    for (Entry<DexLibraryClass, Set<DexProgramClass>> entry : requiredDispatchClasses.entrySet()) {
      synthesizedMethods.addAll(processor.process(entry.getKey(), entry.getValue()));
    }
    if (converter.enableWholeProgramOptimizations &&
        (!processor.methodsWithMovedCode.isEmpty() || !processor.movedMethods.isEmpty())) {
      converter.appView.setGraphLense(
          new InterfaceMethodDesugaringLense(
              processor.movedMethods,
              processor.methodsWithMovedCode,
              converter.appView.graphLense(),
              factory));
    }
    return processor.syntheticClasses;
  }

  private Set<DexEncodedMethod> processClasses(Builder<?> builder, Flavor flavour) {
    ClassProcessor processor = new ClassProcessor(this);
    for (DexProgramClass clazz : builder.getProgramClasses()) {
      if (shouldProcess(clazz, flavour, false)) {
        processor.process(clazz);
      }
    }
    return processor.getForwardMethods();
  }

  final boolean isDefaultMethod(DexEncodedMethod method) {
    assert !method.accessFlags.isConstructor();
    assert !method.accessFlags.isStatic();

    if (method.accessFlags.isAbstract()) {
      return false;
    }
    if (method.accessFlags.isNative()) {
      throw new Unimplemented("Native default interface methods are not yet supported.");
    }
    if (!method.accessFlags.isPublic()) {
      // NOTE: even though the class is allowed to have non-public interface methods
      // with code, for example private methods, all such methods we are aware of are
      // created by the compiler for stateful lambdas and they must be converted into
      // static methods by lambda desugaring by this time.
      throw new Unimplemented("Non public default interface methods are not yet supported.");
    }
    return true;
  }

  public void warnMissingInterface(
      DexClass classToDesugar, DexClass implementing, DexType missing) {
    // TODO think about using a common deduplicating mechanic with Enqueuer
    if (!reportedMissing.add(missing)) {
      return;
    }
    StringBuilder builder = new StringBuilder();
    builder
        .append("Interface `")
        .append(missing.toSourceString())
        .append("` not found. It's needed to make sure desugaring of `")
        .append(classToDesugar.toSourceString())
        .append("` is correct. Desugaring will assume that this interface has no default method.");
    if (classToDesugar != implementing) {
      builder
          .append(" This missing interface is declared in the direct hierarchy of `")
          .append(implementing)
          .append("`");
    }
    options.reporter.warning(
        new StringDiagnostic(builder.toString(), classToDesugar.getOrigin()));
  }

  private void warnMissingType(DexMethod referencedFrom, DexType missing) {
    // TODO think about using a common deduplicating mechanic with Enqueuer
    if (!reportedMissing.add(missing)) {
      return;
    }
    DexMethod originalReferencedFrom =
        converter.graphLense().getOriginalMethodSignature(referencedFrom);
    StringBuilder builder = new StringBuilder();
    builder
        .append("Type `")
        .append(missing.toSourceString())
        .append("` was not found, ")
        .append("it is required for default or static interface methods desugaring of `")
        .append(originalReferencedFrom.toSourceString())
        .append("`");
    options.reporter.warning(
        new StringDiagnostic(builder.toString(), getMethodOrigin(originalReferencedFrom)));
  }

  private Origin getMethodOrigin(DexMethod method) {
    DexType holder = method.getHolder();
    if (isCompanionClassType(holder)) {
      holder = getInterfaceClassType(holder);
    }
    DexClass clazz = converter.appInfo.definitionFor(holder);
    return clazz == null ? Origin.unknown() : clazz.getOrigin();
  }

  final DefaultMethodsHelper.Collection getOrCreateInterfaceInfo(
      DexClass classToDesugar,
      DexClass implementing,
      DexType iface) {
    DefaultMethodsHelper.Collection collection = cache.get(iface);
    if (collection != null) {
      return collection;
    }
    collection = createInterfaceInfo(classToDesugar, implementing, iface);
    Collection existing = cache.putIfAbsent(iface, collection);
    return existing != null ? existing : collection;
  }

  private DefaultMethodsHelper.Collection createInterfaceInfo(
      DexClass classToDesugar,
      DexClass implementing,
      DexType iface) {
    DefaultMethodsHelper helper = new DefaultMethodsHelper();
    DexClass definedInterface = findDefinitionFor(iface);
    if (definedInterface == null) {
      warnMissingInterface(classToDesugar, implementing, iface);
      return helper.wrapInCollection();
    }

    if (!definedInterface.isInterface()) {
      throw new CompilationError(
          "Type " + iface.toSourceString() + " is referenced as an interface from `"
              + implementing.toString() + "`.");
    }

    if (definedInterface.isLibraryClass()) {
      // NOTE: We intentionally ignore all candidates coming from android.jar
      // since it is only possible in case v24+ version of android.jar is provided.
      // WARNING: This may result in incorrect code if something else than Android bootclasspath
      // classes are given as libraries!
      return helper.wrapInCollection();
    }

    // Merge information from all superinterfaces.
    for (DexType superinterface : definedInterface.interfaces.values) {
      helper.merge(getOrCreateInterfaceInfo(classToDesugar, definedInterface, superinterface));
    }

    // Hide by virtual methods of this interface.
    for (DexEncodedMethod virtual : definedInterface.virtualMethods()) {
      helper.hideMatches(virtual.method);
    }

    // Add all default methods of this interface.
    for (DexEncodedMethod encoded : definedInterface.virtualMethods()) {
      if (isDefaultMethod(encoded)) {
        helper.addDefaultMethod(encoded);
      }
    }

    return helper.wrapInCollection();
  }
}
