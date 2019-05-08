package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ClassAccessFlags;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.NestMemberClassAttribute;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.origin.SynthesizedOrigin;
import com.android.tools.r8.utils.ThreadUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

// NestBasedAccessDesugaring contains common code between the two subclasses
// which are specialized for d8 and r8
public abstract class NestBasedAccessDesugaring {

  // Short names to avoid creating long strings
  private static final String NEST_ACCESS_NAME_PREFIX = "-$$Nest$";
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
  private static final String FULL_NEST_CONTRUCTOR_NAME = "L" + NEST_CONSTRUCTOR_NAME + ";";

  protected final AppView<?> appView;
  // Following maps are there to avoid creating the bridges multiple times.
  private final Map<DexEncodedMethod, DexEncodedMethod> bridges = new ConcurrentHashMap<>();
  private final Map<DexEncodedField, DexEncodedMethod> getFieldBridges = new ConcurrentHashMap<>();
  private final Map<DexEncodedField, DexEncodedMethod> putFieldBridges = new ConcurrentHashMap<>();
  // The following map records the bridges to add in the program.
  // It may differ from the values of the previous maps
  // if come classes are on the classpath and not the program path.
  final Map<DexEncodedMethod, DexProgramClass> deferredBridgesToAdd = new ConcurrentHashMap<>();
  // Common single empty class for nest based private constructors
  private DexProgramClass nestConstructor;

  NestBasedAccessDesugaring(AppView<?> appView) {
    this.appView = appView;
  }

  DexType getNestConstructorType() {
    return nestConstructor == null ? null : nestConstructor.type;
  }

  // Extract the list of types in the programClass' nest, of host hostClass
  List<DexType> extractNest(DexClass hostClass, DexProgramClass programClass) {
    assert programClass != null;
    if (hostClass == null) {
      throw abortCompilationDueToMissingNestHost(programClass);
    }
    List<DexType> classesInNest =
        new ArrayList<>(hostClass.getNestMembersClassAttributes().size() + 1);
    for (NestMemberClassAttribute nestmate : hostClass.getNestMembersClassAttributes()) {
      classesInNest.add(nestmate.getNestMember());
    }
    classesInNest.add(hostClass.type);
    return classesInNest;
  }

  void processNestsConcurrently(List<List<DexType>> liveNests, ExecutorService executorService)
      throws ExecutionException {
    List<Future<?>> futures = new ArrayList<>();
    for (List<DexType> nest : liveNests) {
      futures.add(
          executorService.submit(
              () -> {
                processNest(nest);
                return null; // we want a Callable not a Runnable to be able to throw
              }));
    }
    ThreadUtils.awaitFutures(futures);
  }

  private void processNest(List<DexType> nest) {
    for (DexType type : nest) {
      DexClass clazz = appView.definitionFor(type);
      if (clazz == null) {
        // TODO(b/130529338) We could throw only a warning if a class is missing.
        throw abortCompilationDueToIncompleteNest(nest);
      }
      if (shouldProcessClassInNest(clazz, nest)) {
        NestBasedAccessDesugaringUseRegistry registry =
            new NestBasedAccessDesugaringUseRegistry(nest, clazz);
        for (DexEncodedMethod method : clazz.methods()) {
          method.registerCodeReferences(registry);
        }
      }
    }
  }

  protected abstract boolean shouldProcessClassInNest(DexClass clazz, List<DexType> nest);

  void addDeferredBridges() {
    for (Map.Entry<DexEncodedMethod, DexProgramClass> entry : deferredBridgesToAdd.entrySet()) {
      entry.getValue().addMethod(entry.getKey());
    }
  }

  private RuntimeException abortCompilationDueToIncompleteNest(List<DexType> nest) {
    List<String> programClassesFromNest = new ArrayList<>();
    List<String> unavailableClasses = new ArrayList<>();
    List<String> otherClasses = new ArrayList<>();
    for (DexType type : nest) {
      DexClass clazz = appView.definitionFor(type);
      if (clazz == null) {
        unavailableClasses.add(type.getName());
      } else if (clazz.isProgramClass()) {
        programClassesFromNest.add(type.getName());
      } else {
        assert clazz.isLibraryClass() || clazz.isClasspathClass();
        otherClasses.add(type.getName());
      }
    }
    StringBuilder stringBuilder =
        new StringBuilder("Classes ")
            .append(String.join(", ", programClassesFromNest))
            .append(" requires its nest mates ")
            .append(String.join(", ", unavailableClasses))
            .append(" to be on program or class path for compilation to succeed)");
    if (!otherClasses.isEmpty()) {
      stringBuilder
          .append("(Classes ")
          .append(String.join(", ", otherClasses))
          .append(" from the same nest were available).");
    }
    throw new CompilationError(stringBuilder.toString());
  }

  private RuntimeException abortCompilationDueToMissingNestHost(DexProgramClass compiledClass) {
    String nestHostName = compiledClass.getNestHostClassAttribute().getNestHost().getName();
    throw new CompilationError(
        "Class "
            + compiledClass.type.getName()
            + " requires its nest host "
            + nestHostName
            + " to be on program or class path for compilation to succeed.");
  }

  private RuntimeException abortCompilationDueToBridgeRequiredOnLibraryClass(
      DexClass compiledClass, DexClass libraryClass) {
    throw new CompilationError(
        "Class "
            + compiledClass.type.getName()
            + " requires the insertion of a bridge on the library class "
            + libraryClass.type.getName()
            + " which is impossible.");
  }

  protected abstract void shouldRewriteCalls(DexMethod method, DexMethod bridge);

  protected abstract void shouldRewriteInitializers(DexMethod method, DexMethod bridge);

  protected abstract void shouldRewriteStaticGetFields(DexField field, DexMethod bridge);

  protected abstract void shouldRewriteStaticPutFields(DexField field, DexMethod bridge);

  protected abstract void shouldRewriteInstanceGetFields(DexField field, DexMethod bridge);

  protected abstract void shouldRewriteInstancePutFields(DexField field, DexMethod bridge);

  private DexString methodBridgeName(DexEncodedMethod method) {
    String methodName = method.method.name.toString();
    String fullName;
    if (method.isStatic()) {
      fullName = NEST_ACCESS_STATIC_METHOD_NAME_PREFIX + methodName;
    } else {
      fullName = NEST_ACCESS_METHOD_NAME_PREFIX + methodName;
    }
    return appView.dexItemFactory().createString(fullName);
  }

  private DexString fieldBridgeName(DexFieldWithAccess access) {
    String fieldName = access.field.field.name.toString();
    String fullName;
    if (access.isInstanceGet()) {
      fullName = NEST_ACCESS_FIELD_GET_NAME_PREFIX + fieldName;
    } else if (access.isStaticGet()) {
      fullName = NEST_ACCESS_STATIC_GET_FIELD_NAME_PREFIX + fieldName;
    } else if (access.isInstancePut()) {
      fullName = NEST_ACCESS_FIELD_PUT_NAME_PREFIX + fieldName;
    } else {
      assert access.isStaticPut();
      fullName = NEST_ACCESS_STATIC_PUT_FIELD_NAME_PREFIX + fieldName;
    }
    return appView.dexItemFactory().createString(fullName);
  }

  private DexProgramClass ensureNestConstructorClass() {
    if (nestConstructor != null) {
      return nestConstructor;
    }
    nestConstructor =
        new DexProgramClass(
            appView.dexItemFactory().createType(FULL_NEST_CONTRUCTOR_NAME),
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
            DexAnnotationSet.empty(),
            DexEncodedField.EMPTY_ARRAY,
            DexEncodedField.EMPTY_ARRAY,
            DexEncodedMethod.EMPTY_ARRAY,
            DexEncodedMethod.EMPTY_ARRAY,
            appView.dexItemFactory().getSkipNameValidationForTesting());
    return nestConstructor;
  }

  void synthetizeNestConstructor(DexApplication.Builder<?> builder) {
    if (nestConstructor != null) {
      appView.appInfo().addSynthesizedClass(nestConstructor);
      builder.addSynthesizedClass(nestConstructor, true);
    }
  }

  public static boolean isNestConstructor(DexType type) {
    return type.getName().equals(NEST_CONSTRUCTOR_NAME);
  }

  boolean registerFieldAccess(
      DexField field, boolean isGet, List<DexType> nest, DexClass currentClass) {
    if (field.holder == currentClass.type || !nest.contains(field.holder)) {
      return false;
    }
    DexEncodedField target = appView.definitionFor(field);
    if (target == null || !target.accessFlags.isPrivate()) {
      return false;
    }
    Map<DexEncodedField, DexEncodedMethod> fieldMap = isGet ? getFieldBridges : putFieldBridges;
    DexEncodedMethod bridge =
        fieldMap.computeIfAbsent(
            target,
            k -> {
              DexFieldWithAccess fieldWithAccess = new DexFieldWithAccess(target, isGet);
              DexClass holder = appView.definitionFor(field.holder);
              DexEncodedMethod localBridge =
                  DexEncodedMethod.createFieldAccessorBridge(
                      fieldWithAccess, holder, appView, fieldBridgeName(fieldWithAccess));
              // Accesses to program classes private members require bridge insertion.
              if (holder.isProgramClass()) {
                deferredBridgesToAdd.put(localBridge, holder.asProgramClass());
              } else if (holder.isLibraryClass()) {
                throw abortCompilationDueToBridgeRequiredOnLibraryClass(currentClass, holder);
              }
              return localBridge;
            });
    // In program classes, any access to nest mate private member needs to be rewritten.
    if (currentClass.isProgramClass()) {
      if (isGet) {
        if (target.isStatic()) {
          shouldRewriteStaticGetFields(field, bridge.method);
        } else {
          shouldRewriteInstanceGetFields(field, bridge.method);
        }
      } else {
        if (target.isStatic()) {
          shouldRewriteStaticPutFields(field, bridge.method);
        } else {
          shouldRewriteInstancePutFields(field, bridge.method);
        }
      }
    }
    return true;
  }

  boolean registerInvoke(DexMethod method, List<DexType> nest, DexClass currentClass) {
    if (method.holder == currentClass.type || !nest.contains(method.holder)) {
      return false;
    }
    DexEncodedMethod target = appView.definitionFor(method);
    if (target == null || !target.accessFlags.isPrivate()) {
      return false;
    }
    DexEncodedMethod bridge =
        bridges.computeIfAbsent(
            target,
            k -> {
              DexClass holder = appView.definitionFor(method.holder);
              DexEncodedMethod localBridge =
                  target.isInstanceInitializer()
                      ? target.toInitializerForwardingBridge(
                          holder, appView, ensureNestConstructorClass())
                      : target.toStaticForwardingBridge(holder, appView, methodBridgeName(target));
              // Accesses to program classes private members require bridge insertion.
              if (holder.isProgramClass()) {
                deferredBridgesToAdd.put(localBridge, holder.asProgramClass());
              } else if (holder.isLibraryClass()) {
                throw abortCompilationDueToBridgeRequiredOnLibraryClass(currentClass, holder);
              }
              return localBridge;
            });
    // In program classes, any access to nest mate private member needs to be rewritten.
    if (currentClass.isProgramClass()) {
      if (target.isInstanceInitializer()) {
        shouldRewriteInitializers(method, bridge.method);
      } else {
        shouldRewriteCalls(method, bridge.method);
      }
    }
    return true;
  }

  protected class NestBasedAccessDesugaringUseRegistry extends UseRegistry {

    private final List<DexType> nest;
    private final DexClass currentClass;

    NestBasedAccessDesugaringUseRegistry(List<DexType> nest, DexClass currentClass) {
      super(appView.options().itemFactory);
      this.nest = nest;
      this.currentClass = currentClass;
    }

    @Override
    public boolean registerInvokeVirtual(DexMethod method) {
      // Calls to class nest mate private methods are targeted by invokeVirtual in jdk11.
      // The spec recommends to do so, but do not enforce it, hence invokeDirect is also registered.
      return registerInvoke(method, nest, currentClass);
    }

    @Override
    public boolean registerInvokeDirect(DexMethod method) {
      return registerInvoke(method, nest, currentClass);
    }

    @Override
    public boolean registerInvokeStatic(DexMethod method) {
      return registerInvoke(method, nest, currentClass);
    }

    @Override
    public boolean registerInvokeInterface(DexMethod method) {
      // Calls to interface nest mate private methods are targeted by invokeInterface in jdk11.
      // The spec recommends to do so, but do not enforce it, hence invokeDirect is also registered.
      return registerInvoke(method, nest, currentClass);
    }

    @Override
    public boolean registerInvokeSuper(DexMethod method) {
      // Cannot target private method.
      return false;
    }

    @Override
    public boolean registerInstanceFieldWrite(DexField field) {
      return registerFieldAccess(field, false, nest, currentClass);
    }

    @Override
    public boolean registerInstanceFieldRead(DexField field) {
      return registerFieldAccess(field, true, nest, currentClass);
    }

    @Override
    public boolean registerNewInstance(DexType type) {
      // Unrelated to access based control.
      // The <init> method has to be rewritten instead
      // and <init> is called through registerInvoke.
      return false;
    }

    @Override
    public boolean registerStaticFieldRead(DexField field) {
      return registerFieldAccess(field, true, nest, currentClass);
    }

    @Override
    public boolean registerStaticFieldWrite(DexField field) {
      return registerFieldAccess(field, false, nest, currentClass);
    }

    @Override
    public boolean registerTypeReference(DexType type) {
      // Unrelated to access based control.
      return false;
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
      return field.field.holder;
    }

    public DexField getField() {
      return field.field;
    }

    public int bridgeParameterCount() {
      if (isGet() && isStatic()) {
        return 0;
      }
      if (isPut() && isInstance()) {
        return 2;
      }
      return 1;
    }
  }
}
