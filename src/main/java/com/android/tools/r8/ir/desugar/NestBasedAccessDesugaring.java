// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;


import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ClassAccessFlags;
import com.android.tools.r8.graph.ClasspathMethod;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.GenericSignature.ClassSignature;
import com.android.tools.r8.graph.NestMemberClassAttribute;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.code.Invoke;
import com.android.tools.r8.origin.SynthesizedOrigin;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.Pair;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

// NestBasedAccessDesugaring contains common code between the two subclasses
// which are specialized for d8 and r8
public abstract class NestBasedAccessDesugaring {

  // Short names to avoid creating long strings
  public static final String NEST_ACCESS_NAME_PREFIX = "-$$Nest$";
  private static final String NEST_ACCESS_METHOD_NAME_PREFIX = NEST_ACCESS_NAME_PREFIX + "m";
  private static final String NEST_ACCESS_STATIC_METHOD_NAME_PREFIX =
      NEST_ACCESS_NAME_PREFIX + "sm";
  private static final String NEST_ACCESS_FIELD_GET_NAME_PREFIX = NEST_ACCESS_NAME_PREFIX + "fget";
  private static final String NEST_ACCESS_STATIC_GET_FIELD_NAME_PREFIX =
      NEST_ACCESS_NAME_PREFIX + "sfget";
  private static final String NEST_ACCESS_FIELD_PUT_NAME_PREFIX = NEST_ACCESS_NAME_PREFIX + "fput";
  private static final String NEST_ACCESS_STATIC_PUT_FIELD_NAME_PREFIX =
      NEST_ACCESS_NAME_PREFIX + "sfput";
  public static final String NEST_CONSTRUCTOR_NAME = NEST_ACCESS_NAME_PREFIX + "Constructor";

  protected final AppView<?> appView;
  // Following maps are there to avoid creating the bridges multiple times
  // and remember the bridges to add once the nests are processed.
  final Map<DexMethod, ProgramMethod> bridges = new ConcurrentHashMap<>();
  final Map<DexField, ProgramMethod> getFieldBridges = new ConcurrentHashMap<>();
  final Map<DexField, ProgramMethod> putFieldBridges = new ConcurrentHashMap<>();
  // Common single empty class for nest based private constructors
  private final DexProgramClass nestConstructor;
  private boolean nestConstructorUsed = false;

  NestBasedAccessDesugaring(AppView<?> appView) {
    this.appView = appView;
    this.nestConstructor = createNestAccessConstructor();
  }

  DexType getNestConstructorType() {
    assert nestConstructor != null;
    return nestConstructor.type;
  }

  abstract void reportMissingNestHost(DexClass clazz);

  abstract void reportIncompleteNest(List<DexType> nest);

  DexClass definitionFor(DexType type) {
    return appView.definitionFor(appView.graphLens().lookupType(type));
  }

  private DexEncodedMethod lookupOnHolder(
      DexMethod method, DexClassAndMethod context, Invoke.Type invokeType) {
    DexMethod rewritten =
        appView.graphLens().lookupMethod(method, context.getReference(), invokeType).getReference();
    return rewritten.lookupOnClass(appView.definitionForHolder(rewritten));
  }

  private DexEncodedField lookupOnHolder(DexField field) {
    DexField rewritten = appView.graphLens().lookupField(field);
    return rewritten.lookupOnClass(appView.definitionForHolder(rewritten));
  }

  // Extract the list of types in the programClass' nest, of host hostClass
  private Pair<DexClass, List<DexType>> extractNest(DexClass clazz) {
    assert clazz != null;
    DexClass hostClass = clazz.isNestHost() ? clazz : definitionFor(clazz.getNestHost());
    if (hostClass == null) {
      reportMissingNestHost(clazz);
      // Missing nest host means the class is considered as not being part of a nest.
      clazz.clearNestHost();
      return null;
    }
    List<DexType> classesInNest =
        new ArrayList<>(hostClass.getNestMembersClassAttributes().size() + 1);
    for (NestMemberClassAttribute nestmate : hostClass.getNestMembersClassAttributes()) {
      classesInNest.add(nestmate.getNestMember());
    }
    classesInNest.add(hostClass.type);
    return new Pair<>(hostClass, classesInNest);
  }

  Future<?> asyncProcessNest(DexProgramClass clazz, ExecutorService executorService) {
    return executorService.submit(
        () -> {
          Pair<DexClass, List<DexType>> nest = extractNest(clazz);
          // Nest is null when nest host is missing, we do nothing in this case.
          if (nest != null) {
            processNest(nest.getFirst(), nest.getSecond());
          }
          return null; // we want a Callable not a Runnable to be able to throw
        });
  }

  private void processNest(DexClass host, List<DexType> nest) {
    boolean reported = false;
    for (DexType type : nest) {
      DexClass clazz = definitionFor(type);
      if (clazz == null) {
        if (!reported) {
          reportIncompleteNest(nest);
          reported = true;
        }
      } else {
        reportDesugarDependencies(host, clazz);
        if (shouldProcessClassInNest(clazz, nest)) {
          for (DexEncodedMethod definition : clazz.methods()) {
            if (clazz.isProgramClass()) {
              ProgramMethod method = new ProgramMethod(clazz.asProgramClass(), definition);
              method.registerCodeReferences(new NestBasedAccessDesugaringUseRegistry(method));
            } else if (clazz.isClasspathClass()) {
              ClasspathMethod method = new ClasspathMethod(clazz.asClasspathClass(), definition);
              method.registerCodeReferencesForDesugaring(
                  new NestBasedAccessDesugaringUseRegistry(method));
            } else {
              assert false;
            }
          }
        }
      }
    }
  }

  private void reportDesugarDependencies(DexClass host, DexClass clazz) {
    if (host == clazz) {
      return;
    }
    if (host.isProgramClass()) {
      InterfaceMethodRewriter.reportDependencyEdge(host.asProgramClass(), clazz, appView.options());
    }
    if (clazz.isProgramClass()) {
      InterfaceMethodRewriter.reportDependencyEdge(clazz.asProgramClass(), host, appView.options());
    }
  }

  protected abstract boolean shouldProcessClassInNest(DexClass clazz, List<DexType> nest);

  private DexProgramClass createNestAccessConstructor() {
    return new DexProgramClass(
        appView.dexItemFactory().nestConstructorType,
        null,
        new SynthesizedOrigin("Nest based access desugaring", getClass()),
        // Make the synthesized class public since shared in the whole program.
        ClassAccessFlags.fromDexAccessFlags(
            Constants.ACC_FINAL | Constants.ACC_SYNTHETIC | Constants.ACC_PUBLIC),
        appView.dexItemFactory().objectType,
        DexTypeList.empty(),
        appView.dexItemFactory().createString("nest"),
        null,
        Collections.emptyList(),
        null,
        Collections.emptyList(),
        ClassSignature.noSignature(),
        DexAnnotationSet.empty(),
        DexEncodedField.EMPTY_ARRAY,
        DexEncodedField.EMPTY_ARRAY,
        DexEncodedMethod.EMPTY_ARRAY,
        DexEncodedMethod.EMPTY_ARRAY,
        appView.dexItemFactory().getSkipNameValidationForTesting(),
        DexProgramClass::checksumFromType);
  }

  void synthesizeNestConstructor(DexApplication.Builder<?> builder) {
    if (nestConstructorUsed) {
      appView.appInfo().addSynthesizedClass(nestConstructor, true);
      builder.addSynthesizedClass(nestConstructor);
    }
  }

  private DexString computeMethodBridgeName(DexEncodedMethod method) {
    String methodName = method.method.name.toString();
    String fullName;
    if (method.isStatic()) {
      fullName = NEST_ACCESS_STATIC_METHOD_NAME_PREFIX + methodName;
    } else {
      fullName = NEST_ACCESS_METHOD_NAME_PREFIX + methodName;
    }
    return appView.dexItemFactory().createString(fullName);
  }

  private DexString computeFieldBridgeName(DexEncodedField field, boolean isGet) {
    String fieldName = field.field.name.toString();
    String fullName;
    if (isGet && !field.isStatic()) {
      fullName = NEST_ACCESS_FIELD_GET_NAME_PREFIX + fieldName;
    } else if (isGet) {
      fullName = NEST_ACCESS_STATIC_GET_FIELD_NAME_PREFIX + fieldName;
    } else if (!field.isStatic()) {
      fullName = NEST_ACCESS_FIELD_PUT_NAME_PREFIX + fieldName;
    } else {
      fullName = NEST_ACCESS_STATIC_PUT_FIELD_NAME_PREFIX + fieldName;
    }
    return appView.dexItemFactory().createString(fullName);
  }

  private DexMethod computeMethodBridge(DexEncodedMethod encodedMethod) {
    DexMethod method = encodedMethod.method;
    DexProto proto =
        encodedMethod.accessFlags.isStatic()
            ? method.proto
            : appView.dexItemFactory().prependHolderToProto(method);
    return appView
        .dexItemFactory()
        .createMethod(method.holder, proto, computeMethodBridgeName(encodedMethod));
  }

  private DexMethod computeInitializerBridge(DexMethod method) {
    DexProto newProto =
        appView.dexItemFactory().appendTypeToProto(method.proto, nestConstructor.type);
    return appView.dexItemFactory().createMethod(method.holder, newProto, method.name);
  }

  private DexMethod computeFieldBridge(DexEncodedField field, boolean isGet) {
    DexType holderType = field.holder();
    DexType fieldType = field.field.type;
    int bridgeParameterCount =
        BooleanUtils.intValue(!field.isStatic()) + BooleanUtils.intValue(!isGet);
    DexType[] parameters = new DexType[bridgeParameterCount];
    if (!isGet) {
      parameters[parameters.length - 1] = fieldType;
    }
    if (!field.isStatic()) {
      parameters[0] = holderType;
    }
    DexType returnType = isGet ? fieldType : appView.dexItemFactory().voidType;
    DexProto proto = appView.dexItemFactory().createProto(returnType, parameters);
    return appView
        .dexItemFactory()
        .createMethod(holderType, proto, computeFieldBridgeName(field, isGet));
  }

  boolean invokeRequiresRewriting(DexEncodedMethod method, DexClassAndMethod context) {
    assert method != null;
    // Rewrite only when targeting other nest members private fields.
    if (!method.accessFlags.isPrivate() || method.holder() == context.getHolderType()) {
      return false;
    }
    DexClass methodHolder = definitionFor(method.holder());
    assert methodHolder != null; // from encodedMethod
    return methodHolder.getNestHost() == context.getHolder().getNestHost();
  }

  boolean fieldAccessRequiresRewriting(DexEncodedField field, DexClassAndMethod context) {
    assert field != null;
    // Rewrite only when targeting other nest members private fields.
    if (!field.accessFlags.isPrivate() || field.holder() == context.getHolderType()) {
      return false;
    }
    DexClass fieldHolder = definitionFor(field.holder());
    assert fieldHolder != null; // from encodedField
    return fieldHolder.getNestHost() == context.getHolder().getNestHost();
  }

  private boolean holderRequiresBridge(DexClass holder) {
    // Bridges are added on program classes only.
    // Bridges on class paths are added in different compilation units.
    if (holder.isProgramClass()) {
      return false;
    } else if (holder.isClasspathClass()) {
      return true;
    }
    assert holder.isLibraryClass();
    Pair<DexClass, List<DexType>> nest = extractNest(holder);
    assert nest != null : "Should be a compilation error if missing nest host on library class.";
    reportIncompleteNest(nest.getSecond());
    throw new Unreachable(
        "Incomplete nest due to missing library class should raise a compilation error.");
  }

  DexMethod ensureFieldAccessBridge(DexEncodedField field, boolean isGet) {
    DexClass holder = definitionFor(field.holder());
    assert holder != null;
    DexMethod bridgeMethod = computeFieldBridge(field, isGet);
    if (holderRequiresBridge(holder)) {
      return bridgeMethod;
    }
    assert holder.isProgramClass();
    // The map is used to avoid creating multiple times the bridge
    // and remembers the bridges to add.
    Map<DexField, ProgramMethod> fieldMap = isGet ? getFieldBridges : putFieldBridges;
    assert holder.isProgramClass();
    fieldMap.computeIfAbsent(
        field.field,
        k ->
            DexEncodedMethod.createFieldAccessorBridge(
                new DexFieldWithAccess(field, isGet), holder.asProgramClass(), bridgeMethod));
    return bridgeMethod;
  }

  DexMethod ensureInvokeBridge(DexEncodedMethod method) {
    // We add bridges only when targeting other nest members.
    DexClass holder = definitionFor(method.holder());
    assert holder != null;
    DexMethod bridgeMethod;
    if (method.isInstanceInitializer()) {
      nestConstructorUsed = true;
      bridgeMethod = computeInitializerBridge(method.method);
    } else {
      bridgeMethod = computeMethodBridge(method);
    }
    if (holderRequiresBridge(holder)) {
      return bridgeMethod;
    }
    // The map is used to avoid creating multiple times the bridge
    // and remembers the bridges to add.
    assert holder.isProgramClass();
    bridges.computeIfAbsent(
        method.method,
        k ->
            method.isInstanceInitializer()
                ? method.toInitializerForwardingBridge(holder.asProgramClass(), bridgeMethod)
                : method.toStaticForwardingBridge(
                    holder.asProgramClass(), computeMethodBridge(method)));
    return bridgeMethod;
  }

  protected class NestBasedAccessDesugaringUseRegistry extends UseRegistry {

    private final DexClassAndMethod context;

    NestBasedAccessDesugaringUseRegistry(DexClassAndMethod context) {
      super(appView.options().itemFactory);
      this.context = context;
    }

    private void registerInvoke(DexMethod method, Invoke.Type invokeType) {
      // Calls to non class type are not done through nest based access control.
      // Work-around for calls to enum.clone().
      if (!method.holder.isClassType()) {
        return;
      }
      DexEncodedMethod encodedMethod = lookupOnHolder(method, context, invokeType);
      if (encodedMethod != null && invokeRequiresRewriting(encodedMethod, context)) {
        ensureInvokeBridge(encodedMethod);
      }
    }

    private void registerFieldAccess(DexField field, boolean isGet) {
      // Since we only need to desugar accesses to private fields, and all accesses to private
      // fields must be accessing the private field directly on its holder, we can lookup the field
      // on the holder instead of resolving the field.
      DexEncodedField encodedField = lookupOnHolder(field);
      if (encodedField != null && fieldAccessRequiresRewriting(encodedField, context)) {
        ensureFieldAccessBridge(encodedField, isGet);
      }
    }

    @Override
    public void registerInitClass(DexType clazz) {
      // Nothing to do since we always use a public field for initializing the class.
    }

    @Override
    public void registerInvokeVirtual(DexMethod method) {
      // Calls to class nest mate private methods are targeted by invokeVirtual in jdk11.
      // The spec recommends to do so, but do not enforce it, hence invokeDirect is also registered.
      registerInvoke(method, Invoke.Type.VIRTUAL);
    }

    @Override
    public void registerInvokeDirect(DexMethod method) {
      registerInvoke(method, Invoke.Type.DIRECT);
    }

    @Override
    public void registerInvokeStatic(DexMethod method) {
      registerInvoke(method, Invoke.Type.STATIC);
    }

    @Override
    public void registerInvokeInterface(DexMethod method) {
      // Calls to interface nest mate private methods are targeted by invokeInterface in jdk11.
      // The spec recommends to do so, but do not enforce it, hence invokeDirect is also registered.
      registerInvoke(method, Invoke.Type.INTERFACE);
    }

    @Override
    public void registerInvokeSuper(DexMethod method) {
      registerInvoke(method, Invoke.Type.SUPER);
    }

    @Override
    public void registerInstanceFieldWrite(DexField field) {
      registerFieldAccess(field, false);
    }

    @Override
    public void registerInstanceFieldRead(DexField field) {
      registerFieldAccess(field, true);
    }

    @Override
    public void registerNewInstance(DexType type) {
      // Unrelated to access based control.
      // The <init> method has to be rewritten instead
      // and <init> is called through registerInvoke.
    }

    @Override
    public void registerStaticFieldRead(DexField field) {
      registerFieldAccess(field, true);
    }

    @Override
    public void registerStaticFieldWrite(DexField field) {
      registerFieldAccess(field, false);
    }

    @Override
    public void registerTypeReference(DexType type) {
      // Unrelated to access based control.
    }

    @Override
    public void registerInstanceOf(DexType type) {
      // Unrelated to access based control.
    }
  }

  public static final class DexFieldWithAccess {

    private final DexEncodedField field;
    private final boolean isGet;

    DexFieldWithAccess(DexEncodedField field, boolean isGet) {
      this.field = field;
      this.isGet = isGet;
    }

    @Override
    public int hashCode() {
      return Objects.hash(field, isGet);
    }

    @Override
    public boolean equals(Object o) {
      if (o == null) {
        return false;
      }
      if (getClass() != o.getClass()) {
        return false;
      }
      DexFieldWithAccess other = (DexFieldWithAccess) o;
      return isGet == other.isGet && field == other.field;
    }

    public boolean isGet() {
      return isGet;
    }

    public boolean isStatic() {
      return field.accessFlags.isStatic();
    }

    public boolean isPut() {
      return !isGet();
    }

    public boolean isInstance() {
      return !isStatic();
    }

    public boolean isStaticGet() {
      return isStatic() && isGet();
    }

    public boolean isStaticPut() {
      return isStatic() && isPut();
    }

    public boolean isInstanceGet() {
      return isInstance() && isGet();
    }

    public boolean isInstancePut() {
      return isInstance() && isPut();
    }

    public DexType getType() {
      return field.field.type;
    }

    public DexType getHolder() {
      return field.holder();
    }

    public DexField getField() {
      return field.field;
    }
  }
}
