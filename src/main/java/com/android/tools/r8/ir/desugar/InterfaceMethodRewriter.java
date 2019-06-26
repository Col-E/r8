// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ClassAccessFlags;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexApplication.Builder;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexLibraryClass;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.graph.GraphLense;
import com.android.tools.r8.graph.GraphLense.NestedGraphLense;
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
import com.android.tools.r8.origin.SynthesizedOrigin;
import com.android.tools.r8.position.MethodPosition;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Pair;
import com.android.tools.r8.utils.StringDiagnostic;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

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
  public static final String EMULATE_LIBRARY_CLASS_NAME_SUFFIX = "$-EL";
  public static final String DISPATCH_CLASS_NAME_SUFFIX = "$-DC";
  public static final String COMPANION_CLASS_NAME_SUFFIX = "$-CC";
  public static final String DEFAULT_METHOD_PREFIX = "$default$";
  public static final String PRIVATE_METHOD_PREFIX = "$private$";

  private final AppView<?> appView;
  private final IRConverter converter;
  private final InternalOptions options;
  final DexItemFactory factory;
  private final Map<DexType, DexType> emulatedInterfaces = new IdentityHashMap<>();
  // In emulatedInterfacesHierarchy, keys are interfaces and values the list of emulated
  // interfaces the interface implements. The list is in a specific order (top-down).
  private final Map<DexType, List<DexType>> emulatedInterfacesHierarchy = new IdentityHashMap<>();
  private final Map<String, String> prefixRewritingInterfaces = new IdentityHashMap<>();

  // All forwarding methods generated during desugaring. We don't synchronize access
  // to this collection since it is only filled in ClassProcessor running synchronously.
  private final Set<DexEncodedMethod> synthesizedMethods = Sets.newIdentityHashSet();

  // Caches default interface method info for already processed interfaces.
  private final Map<DexType, DefaultMethodsHelper.Collection> cache = new ConcurrentHashMap<>();

  /** Interfaces requiring dispatch classes to be created, and appropriate callers. */
  private final ConcurrentMap<DexLibraryClass, Set<DexProgramClass>> requiredDispatchClasses =
      new ConcurrentHashMap<>();

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

  public Map<String, String> getPrefixRewritingInterfaces() {
    return prefixRewritingInterfaces;
  }

  public InterfaceMethodRewriter(AppView<?> appView, IRConverter converter) {
    assert converter != null;
    this.appView = appView;
    this.converter = converter;
    this.options = appView.options();
    this.factory = appView.dexItemFactory();
    for (String interfaceName : options.emulateLibraryInterface.keySet()) {
      emulatedInterfaces.put(
          factory.createType(DescriptorUtils.javaTypeToDescriptor(interfaceName)),
          factory.createType(
              DescriptorUtils.javaTypeToDescriptor(
                  options.emulateLibraryInterface.get(interfaceName))));
    }
    if (!emulatedInterfaces.isEmpty()) {
      computeEmulatedInterfaceHierarchy();
    }
  }

  private void computeEmulatedInterfaceHierarchy() {
    // itfToEmulatedItf maps each interface to the list of interfaces it implements.
    Map<DexType, Set<DexType>> itfToEmulatedItf = new IdentityHashMap<>();
    Set<DexType> processed = Sets.newIdentityHashSet();
    for (DexType theInterface : emulatedInterfaces.keySet()) {
      itfToEmulatedItf.put(theInterface, ImmutableSet.of(theInterface));
      emulatedInterfacesHierarchy.put(theInterface, new ArrayList<>());
      processed.add(theInterface);
    }
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      if (clazz.isInterface()) {
        processInterfaceInHierarchy(clazz, processed, itfToEmulatedItf);
      }
    }
    // TODO(b/134732760): prune interfaces in emulatedInterfacesHierarchy, keep only relevant ones
    // TODO(b/134732760): deal with retarget-core-library-member
  }

  private void processInterfaceInHierarchy(
      DexClass theInterface, Set<DexType> processed, Map<DexType, Set<DexType>> itfToEmulatedItf) {
    // Since we assume interface inheritance do not go very deep we use recursive algorithm.
    assert theInterface.isInterface();
    if (processed.contains(theInterface.type)) {
      return;
    }
    processed.add(theInterface.type);
    for (DexType superInterfaceType : theInterface.interfaces.values) {
      DexClass superInterfaceClass = appView.definitionFor(superInterfaceType);
      if (superInterfaceClass == null) {
        warnMissingEmulatedInterfaceOnLookup(superInterfaceType);
      } else {
        processInterfaceInHierarchy(superInterfaceClass, processed, itfToEmulatedItf);
        if (itfToEmulatedItf.containsKey(superInterfaceType)) {
          itfToEmulatedItf.putIfAbsent(theInterface.type, Sets.newIdentityHashSet());
          itfToEmulatedItf.get(theInterface.type).addAll(itfToEmulatedItf.get(superInterfaceType));
        }
      }
    }
    // Here we are guaranteed all superInterfaces have been processed before.
    // Hence the list in emulatedInterfacesHierarchy is top-down regarding the inheritance tree.
    if (itfToEmulatedItf.containsKey(theInterface.type)) {
      for (DexType itf : itfToEmulatedItf.get(theInterface.type)) {
        emulatedInterfacesHierarchy.get(itf).add(theInterface.type);
      }
    }
  }

  // Rewrites the references to static and default interface methods.
  // NOTE: can be called for different methods concurrently.
  public void rewriteMethodReferences(DexEncodedMethod encodedMethod, IRCode code) {
    if (synthesizedMethods.contains(encodedMethod)) {
      return;
    }

    ListIterator<BasicBlock> blocks = code.listIterator();
    AppInfo appInfo = appView.appInfo();
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
          DexClass clazz = appInfo.definitionFor(method.holder);
          if (BackportedMethodRewriter.hasRewrittenMethodPrefix(method.holder)) {
            // We did not create this code yet, but it will not require rewriting.
            continue;
          }
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
                    .add(appInfo.definitionFor(encodedMethod.method.holder).asProgramClass());
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
          DexClass clazz = appInfo.definitionFor(method.holder);
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
            DexMethod amendedMethod =
                amendDefaultMethod(appInfo.definitionFor(encodedMethod.method.holder), method);
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

          DexClass clazz = appInfo.definitionFor(method.holder);
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

  private void warnMissingEmulatedInterfaceOnLookup(DexType interfaceType) {
    StringDiagnostic warning =
        new StringDiagnostic(
            "Cannot lookup interface type "
                + interfaceType.getName()
                + " (might break interface emulation).");
    options.reporter.warning(warning);
  }

  private void warnMissingEmulatedInterface(DexType interfaceType) {
    StringDiagnostic warning =
        new StringDiagnostic(
            "Cannot emulate interface "
                + interfaceType.getName()
                + " because the interface is missing.");
    options.reporter.warning(warning);
  }

  private void generateEmulateInterfaceLibrary(Builder<?> builder) {
    for (DexType interfaceType : emulatedInterfaces.keySet()) {
      DexClass theInterface = appView.definitionFor(interfaceType);
      if (theInterface == null) {
        warnMissingEmulatedInterface(interfaceType);
      } else if (theInterface.isProgramClass()) {
        DexProgramClass synthesizedClass =
            synthetizeEmulateInterfaceLibraryClass(theInterface.asProgramClass());
        if (synthesizedClass != null) {
          builder.addSynthesizedClass(synthesizedClass, isInMainDexList(interfaceType));
          appView.appInfo().addSynthesizedClass(synthesizedClass);
        }
      }
    }
  }

  private static DexMethod emulateInterfaceLibraryMethod(DexMethod method, DexItemFactory factory) {
    return factory.createMethod(
        getEmulateLibraryInterfaceClassType(method.holder, factory),
        factory.prependTypeToProto(method.holder, method.proto),
        factory.createString(method.name.toString()));
  }

  private boolean assertSubInterfaceCompatible(DexType subInterfaceType, DexType interfaceType) {
    assert interfaceType.getName().equals(emulatedInterfaces.get(interfaceType).getName());
    assert subInterfaceType.getPackageDescriptor().startsWith(interfaceType.getPackageDescriptor());
    return true;
  }

  private DexType getCompanionClassTypeUsingPrefix(
      DexType subInterfaceType, DexType interfaceType) {
    // Answers the companion class name and request extra rewriting for it.
    assert assertSubInterfaceCompatible(subInterfaceType, interfaceType);
    String initialPrefix = interfaceType.getPackageName();
    String rewrittenPrefix = emulatedInterfaces.get(interfaceType).getPackageName();
    String suffix = subInterfaceType.toString().substring(initialPrefix.length());
    prefixRewritingInterfaces.put(
        getCompanionClassType(subInterfaceType).toString(),
        rewrittenPrefix + suffix + COMPANION_CLASS_NAME_SUFFIX);
    return getCompanionClassType(subInterfaceType);
  }

  private DexProgramClass synthetizeEmulateInterfaceLibraryClass(DexProgramClass theInterface) {
    List<DexEncodedMethod> emulationMethods = new ArrayList<>();
    for (DexEncodedMethod method : theInterface.methods()) {
      if (method.isDefaultMethod()) {
        DexMethod libraryMethod =
            factory.createMethod(
                emulatedInterfaces.get(theInterface.type), method.method.proto, method.method.name);
        DexMethod originalCompanionMethod =
            method.isStatic()
                ? staticAsMethodOfCompanionClass(method.method)
                : instanceAsMethodOfCompanionClass(method.method, DEFAULT_METHOD_PREFIX, factory);
        DexMethod companionMethod =
            factory.createMethod(
                getCompanionClassType(emulatedInterfaces.get(theInterface.type)),
                originalCompanionMethod.proto,
                originalCompanionMethod.name);

        // To properly emulate the library interface call, we need to compute the interfaces
        // inheriting from the interface and manually implement the dispatch with instance of.
        // The list guarantees that an interface is always after interfaces it extends,
        // hence reverse iteration.
        List<DexType> subInterfaces = emulatedInterfacesHierarchy.get(theInterface.type);
        List<Pair<DexType, DexMethod>> extraDispatchCases = new ArrayList<>();
        for (int i = subInterfaces.size() - 1; i >= 0; i--) {
          DexClass subInterfaceClass = appView.definitionFor(subInterfaces.get(i));
          assert subInterfaceClass != null; // Else computation of subInterface would have failed.
          // if the method is implemented, extra dispatch is required.
          for (DexEncodedMethod subItfMethod : subInterfaceClass.virtualMethods()) {
            if (subItfMethod.method.match(method)) {
              extraDispatchCases.add(
                  new Pair<>(
                      subInterfaceClass.type,
                      factory.createMethod(
                          getCompanionClassTypeUsingPrefix(
                              subInterfaceClass.type, theInterface.type),
                          factory.protoWithDifferentFirstParameter(
                              originalCompanionMethod.proto, subInterfaceClass.type),
                          originalCompanionMethod.name)));
            }
          }
        }

        emulationMethods.add(
            method.toEmulateInterfaceLibraryMethod(
                emulateInterfaceLibraryMethod(method.method, factory),
                companionMethod,
                libraryMethod,
                extraDispatchCases,
                appView));
      }
    }
    if (emulationMethods.isEmpty()) {
      return null;
    }
    DexType emulateLibraryClassType =
        getEmulateLibraryInterfaceClassType(theInterface.type, factory);
    ClassAccessFlags emulateLibraryClassFlags = theInterface.accessFlags.copy();
    emulateLibraryClassFlags.unsetAbstract();
    emulateLibraryClassFlags.unsetInterface();
    emulateLibraryClassFlags.unsetAnnotation();
    emulateLibraryClassFlags.setFinal();
    emulateLibraryClassFlags.setSynthetic();
    emulateLibraryClassFlags.setPublic();
    synthesizedMethods.addAll(emulationMethods);
    return new DexProgramClass(
        emulateLibraryClassType,
        null,
        new SynthesizedOrigin("interface desugaring (libs)", getClass()),
        emulateLibraryClassFlags,
        factory.objectType,
        DexTypeList.empty(),
        theInterface.sourceFile,
        null,
        Collections.emptyList(),
        null,
        Collections.emptyList(),
        DexAnnotationSet.empty(),
        DexEncodedField.EMPTY_ARRAY,
        DexEncodedField.EMPTY_ARRAY,
        // All synthetized methods are static in this case.
        emulationMethods.toArray(DexEncodedMethod.EMPTY_ARRAY),
        DexEncodedMethod.EMPTY_ARRAY,
        factory.getSkipNameValidationForTesting(),
        Collections.singletonList(theInterface));
  }

  private static String getEmulateLibraryInterfaceClassDescriptor(String descriptor) {
    return descriptor.substring(0, descriptor.length() - 1)
        + EMULATE_LIBRARY_CLASS_NAME_SUFFIX
        + ";";
  }

  static DexType getEmulateLibraryInterfaceClassType(DexType type, DexItemFactory factory) {
    assert type.isClassType();
    String descriptor = type.descriptor.toString();
    String elTypeDescriptor = getEmulateLibraryInterfaceClassDescriptor(descriptor);
    return factory.createType(elTypeDescriptor);
  }

  private void reportStaticInterfaceMethodHandle(DexMethod referencedFrom, DexMethodHandle handle) {
    if (handle.type.isInvokeStatic()) {
      DexClass holderClass = appView.definitionFor(handle.asMethod().holder);
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

  public static String getCompanionClassDescriptor(String descriptor) {
    return descriptor.substring(0, descriptor.length() - 1) + COMPANION_CLASS_NAME_SUFFIX + ";";
  }

  // Gets the companion class for the interface `type`.
  static DexType getCompanionClassType(DexType type, DexItemFactory factory) {
    assert type.isClassType();
    String descriptor = type.descriptor.toString();
    String ccTypeDescriptor = getCompanionClassDescriptor(descriptor);
    return factory.createType(ccTypeDescriptor);
  }

  DexType getCompanionClassType(DexType type) {
    return getCompanionClassType(type, factory);
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
  public static boolean isCompanionClassType(DexType type) {
    return type.descriptor.toString().endsWith(COMPANION_CLASS_NAME_SUFFIX + ";");
  }

  // Gets the interface class for a companion class `type`.
  private DexType getInterfaceClassType(DexType type) {
    return getInterfaceClassType(type, factory);
  }

  // Gets the interface class for a companion class `type`.
  public static DexType getInterfaceClassType(DexType type, DexItemFactory factory) {
    assert isCompanionClassType(type);
    String descriptor = type.descriptor.toString();
    String interfaceTypeDescriptor = descriptor.substring(0,
        descriptor.length() - 1 - COMPANION_CLASS_NAME_SUFFIX.length()) + ";";
    return factory.createType(interfaceTypeDescriptor);
  }

  private boolean isInMainDexList(DexType iface) {
    return appView.appInfo().isInMainDexList(iface);
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

  private static DexMethod instanceAsMethodOfCompanionClass(
      DexMethod method, String prefix, DexItemFactory factory) {
    // Add an implicit argument to represent the receiver.
    DexType[] params = method.proto.parameters.values;
    DexType[] newParams = new DexType[params.length + 1];
    newParams[0] = method.holder;
    System.arraycopy(params, 0, newParams, 1, params.length);

    // Add prefix to avoid name conflicts.
    return factory.createMethod(
        getCompanionClassType(method.holder, factory),
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
  public static DexMethod defaultAsMethodOfCompanionClass(
      DexMethod method, DexItemFactory factory) {
    return instanceAsMethodOfCompanionClass(method, DEFAULT_METHOD_PREFIX, factory);
  }

  DexMethod defaultAsMethodOfCompanionClass(DexMethod method) {
    return defaultAsMethodOfCompanionClass(method, factory);
  }

  // Represent a private instance interface method as a method of companion class.
  static DexMethod privateAsMethodOfCompanionClass(DexMethod method, DexItemFactory factory) {
    // Add an implicit argument to represent the receiver.
    return instanceAsMethodOfCompanionClass(method, PRIVATE_METHOD_PREFIX, factory);
  }

  DexMethod privateAsMethodOfCompanionClass(DexMethod method) {
    return privateAsMethodOfCompanionClass(method, factory);
  }

  /**
   * Move static and default interface methods to companion classes, add missing methods to forward
   * to moved default methods implementation.
   */
  public void desugarInterfaceMethods(
      Builder<?> builder,
      Flavor flavour,
      ExecutorService executorService)
      throws ExecutionException {
    // Emulated library interfaces should generate the Emulated Library EL dispatch class
    generateEmulateInterfaceLibrary(builder);

    // Process all classes first. Add missing forwarding methods to
    // replace desugared default interface methods.
    synthesizedMethods.addAll(processClasses(builder, flavour));

    // Process interfaces, create companion or dispatch class if needed, move static
    // methods to companion class, copy default interface methods to companion classes,
    // make original default methods abstract, remove bridge methods, create dispatch
    // classes if needed.
    AppInfo appInfo = appView.appInfo();
    for (Entry<DexType, DexProgramClass> entry : processInterfaces(builder, flavour).entrySet()) {
      // Don't need to optimize synthesized class since all of its methods
      // are just moved from interfaces and don't need to be re-processed.
      DexProgramClass synthesizedClass = entry.getValue();
      builder.addSynthesizedClass(synthesizedClass, isInMainDexList(entry.getKey()));
      appInfo.addSynthesizedClass(synthesizedClass);
    }

    converter.optimizeSynthesizedMethodsConcurrently(synthesizedMethods, executorService);

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
    NestedGraphLense.Builder graphLensBuilder = GraphLense.builder();
    InterfaceProcessor processor = new InterfaceProcessor(appView, this);
    for (DexProgramClass clazz : builder.getProgramClasses()) {
      if (shouldProcess(clazz, flavour, true)) {
        processor.process(clazz.asProgramClass(), graphLensBuilder);
      }
    }
    for (Entry<DexLibraryClass, Set<DexProgramClass>> entry : requiredDispatchClasses.entrySet()) {
      synthesizedMethods.addAll(processor.process(entry.getKey(), entry.getValue()));
    }
    if (appView.enableWholeProgramOptimizations()) {
      appView.setGraphLense(graphLensBuilder.build(appView.dexItemFactory(), appView.graphLense()));
    }
    return processor.syntheticClasses;
  }

  private Set<DexEncodedMethod> processClasses(Builder<?> builder, Flavor flavour) {
    ClassProcessor processor = new ClassProcessor(appView, this);
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
    options.warningMissingInterfaceForDesugar(classToDesugar, implementing, missing);
  }

  private void warnMissingType(DexMethod referencedFrom, DexType missing) {
    DexMethod method = appView.graphLense().getOriginalMethodSignature(referencedFrom);
    Origin origin = getMethodOrigin(method);
    MethodPosition position = new MethodPosition(method);
    options.warningMissingTypeForDesugar(origin, position, missing, method.holder);
  }

  private Origin getMethodOrigin(DexMethod method) {
    DexType holder = method.holder;
    if (isCompanionClassType(holder)) {
      holder = getInterfaceClassType(holder);
    }
    DexClass clazz = appView.definitionFor(holder);
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
    DexClass definedInterface = appView.definitionFor(iface);
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
