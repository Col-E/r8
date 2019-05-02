package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.NestMemberClassAttribute;
import com.android.tools.r8.graph.UseRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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

  private final AppView<?> appView;
  private final HashMap<DexEncodedMethod, DexEncodedMethod> bridges = new HashMap<>();
  private final HashMap<DexFieldWithAccess, DexEncodedMethod> fieldBridges = new HashMap<>();
  private final HashMap<DexEncodedMethod, DexProgramClass> deferredBridgesToAdd = new HashMap<>();

  public NestBasedAccessDesugaring(AppView<?> appView) {
    this.appView = appView;
  }

  public AppView<?> getAppView() {
    return appView;
  }

  public void analyzeNests() {
    // TODO(b/130529338) we don't need to compute a list with all live nests.
    // we just need to iterate all live nests.
    List<List<DexType>> liveNests = computeLiveNests();
    processLiveNests(liveNests);
    addDeferredBridges();
  }

  private void addDeferredBridges() {
    for (Map.Entry<DexEncodedMethod, DexProgramClass> entry : deferredBridgesToAdd.entrySet()) {
      entry.getValue().addMethod(entry.getKey());
    }
  }

  private void processLiveNests(List<List<DexType>> liveNests) {
    for (List<DexType> nest : liveNests) {
      for (DexType type : nest) {
        DexClass clazz = appView.definitionFor(type);
        if (clazz == null) {
          // TODO(b/130529338) We could throw only a warning if a class is missing.
          throw abortCompilationDueToIncompleteNest(nest);
        }
        NestBasedAccessDesugaringUseRegistry registry =
            new NestBasedAccessDesugaringUseRegistry(nest, clazz);
        for (DexEncodedMethod method : clazz.methods()) {
          method.registerCodeReferences(registry);
        }
      }
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

  private List<List<DexType>> computeLiveNests() {
    List<List<DexType>> liveNests = new ArrayList<>();
    // It is possible that a nest member is on the program path but its nest host
    // is only in the class path. Nests are therefore computed the first time a
    // nest member is met, host or not. The computedNestHosts list is there to
    // avoid processing multiple times the same nest.
    Set<DexType> computedNestHosts = new HashSet<>();
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      if (clazz.isInANest()) {
        DexType hostType =
            clazz.isNestHost() ? clazz.type : clazz.getNestHostClassAttribute().getNestHost();
        if (!computedNestHosts.contains(hostType)) {
          computedNestHosts.add(hostType);
          DexClass host =
              clazz.isNestHost()
                  ? clazz
                  : appView.definitionFor(clazz.getNestHostClassAttribute().getNestHost());
          if (host == null) {
            throw abortCompilationDueToMissingNestHost(clazz);
          }
          List<DexType> classesInNest = new ArrayList<>();
          for (NestMemberClassAttribute nestmate : host.getNestMembersClassAttributes()) {
            classesInNest.add(nestmate.getNestMember());
          }
          classesInNest.add(host.type);
          liveNests.add(classesInNest);
        }
      }
    }
    return liveNests;
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

  protected abstract void shouldRewriteCalls(DexMethod method, DexMethod bridge);

  protected abstract void shouldRewriteFields(DexFieldWithAccess fieldKey, DexMethod bridge);

  private RuntimeException abortCompilationDueToBridgeRequiredOnLibraryClass(
      DexClass compiledClass, DexClass libraryClass) {
    throw new CompilationError(
        "Class "
            + compiledClass.type.getName()
            + " requires the insertion of a bridge on the library class "
            + libraryClass.type.getName()
            + " which is impossible.");
  }

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

  private class NestBasedAccessDesugaringUseRegistry extends UseRegistry {

    private final List<DexType> nest;
    private final DexClass currentClass;

    NestBasedAccessDesugaringUseRegistry(List<DexType> nest, DexClass currentClass) {
      super(appView.options().itemFactory);
      this.nest = nest;
      this.currentClass = currentClass;
    }

    private boolean registerInvoke(DexMethod method) {
      if (method.holder == currentClass.type || !nest.contains(method.holder)) {
        return false;
      }
      DexEncodedMethod target = appView.definitionFor(method);
      if (target == null || !target.accessFlags.isPrivate()) {
        return false;
      }
      if (target.isInstanceInitializer()) {
        // TODO(b/130529338): support initializer
        return false;
      }
      DexEncodedMethod bridge =
          bridges.computeIfAbsent(
              target,
              k -> {
                DexClass holder = appView.definitionFor(method.holder);
                DexEncodedMethod localBridge =
                    target.toStaticForwardingBridge(holder, appView, methodBridgeName(target));
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
        shouldRewriteCalls(method, bridge.method);
      }
      return true;
    }

    private boolean registerFieldAccess(DexField field, boolean isGet) {
      if (field.holder == currentClass.type || !nest.contains(field.holder)) {
        return false;
      }
      DexEncodedField target = appView.definitionFor(field);
      if (target == null || !target.accessFlags.isPrivate()) {
        return false;
      }
      DexFieldWithAccess key = new DexFieldWithAccess(target, isGet);
      DexEncodedMethod bridge =
          fieldBridges.computeIfAbsent(
              key,
              k -> {
                DexClass holder = appView.definitionFor(field.holder);
                DexEncodedMethod localBridge =
                    DexEncodedMethod.createFieldAccessorBridge(
                        key, holder, appView, fieldBridgeName(key));
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
        shouldRewriteFields(key, bridge.method);
      }
      return true;
    }

    @Override
    public boolean registerInvokeVirtual(DexMethod method) {
      // Calls to class nest mate private methods are targeted by invokeVirtual in jdk11.
      // The spec recommends to do so, but do not enforce it, hence invokeDirect is also registered.
      return registerInvoke(method);
    }

    @Override
    public boolean registerInvokeDirect(DexMethod method) {
      return registerInvoke(method);
    }

    @Override
    public boolean registerInvokeStatic(DexMethod method) {
      return registerInvoke(method);
    }

    @Override
    public boolean registerInvokeInterface(DexMethod method) {
      // Calls to interface nest mate private methods are targeted by invokeInterface in jdk11.
      // The spec recommends to do so, but do not enforce it, hence invokeDirect is also registered.
      return registerInvoke(method);
    }

    @Override
    public boolean registerInvokeSuper(DexMethod method) {
      // Cannot target private method.
      return false;
    }

    @Override
    public boolean registerInstanceFieldWrite(DexField field) {
      return registerFieldAccess(field, false);
    }

    @Override
    public boolean registerInstanceFieldRead(DexField field) {
      return registerFieldAccess(field, true);
    }

    @Override
    public boolean registerNewInstance(DexType type) {
      // TODO(b/130529338): support initializer
      return false;
    }

    @Override
    public boolean registerStaticFieldRead(DexField field) {
      return registerFieldAccess(field, true);
    }

    @Override
    public boolean registerStaticFieldWrite(DexField field) {
      return registerFieldAccess(field, false);
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
