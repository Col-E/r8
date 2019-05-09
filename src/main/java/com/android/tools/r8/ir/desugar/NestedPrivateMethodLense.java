package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLense;
import com.android.tools.r8.graph.GraphLense.NestedGraphLense;
import com.android.tools.r8.ir.code.Invoke;
import com.google.common.collect.ImmutableMap;
import java.util.IdentityHashMap;
import java.util.Map;

public class NestedPrivateMethodLense extends NestedGraphLense {

  public static class Builder extends GraphLense.Builder {

    private final AppView<? extends AppInfo> appView;
    private final Map<DexField, DexMethod> staticGetToMethodMap = new IdentityHashMap<>();
    private final Map<DexField, DexMethod> staticPutToMethodMap = new IdentityHashMap<>();
    private final Map<DexField, DexMethod> instanceGetToMethodMap = new IdentityHashMap<>();
    private final Map<DexField, DexMethod> instancePutToMethodMap = new IdentityHashMap<>();
    private final Map<DexMethod, DexMethod> initializerMap = new IdentityHashMap<>();

    protected Builder(AppView<? extends AppInfo> appView) {
      this.appView = appView;
    }

    public void mapInstanceGet(DexField field, DexMethod method) {
      instanceGetToMethodMap.put(field, method);
    }

    public void mapInitializer(DexMethod initializer, DexMethod method) {
      initializerMap.put(initializer, method);
    }

    public void mapInstancePut(DexField field, DexMethod method) {
      instancePutToMethodMap.put(field, method);
    }

    public void mapStaticGet(DexField field, DexMethod method) {
      staticGetToMethodMap.put(field, method);
    }

    public void mapStaticPut(DexField field, DexMethod method) {
      staticPutToMethodMap.put(field, method);
    }

    public GraphLense build(GraphLense previousLense, DexType nestConstructorType) {
      if (methodMap.isEmpty()
          && staticGetToMethodMap.isEmpty()
          && staticPutToMethodMap.isEmpty()
          && instanceGetToMethodMap.isEmpty()
          && instancePutToMethodMap.isEmpty()
          && initializerMap.isEmpty()) {
        return previousLense;
      }
      return new NestedPrivateMethodLense(
          appView,
          methodMap,
          fieldMap,
          staticGetToMethodMap,
          staticPutToMethodMap,
          instanceGetToMethodMap,
          instancePutToMethodMap,
          initializerMap,
          nestConstructorType,
          previousLense);
    }
  }

  private final AppView<?> appView;

  private final Map<DexField, DexMethod> staticGetToMethodMap;
  private final Map<DexField, DexMethod> staticPutToMethodMap;
  private final Map<DexField, DexMethod> instanceGetToMethodMap;
  private final Map<DexField, DexMethod> instancePutToMethodMap;
  private final Map<DexMethod, DexMethod> initializerMap;
  private final DexType nestConstructorType;

  public NestedPrivateMethodLense(
      AppView<?> appView,
      Map<DexMethod, DexMethod> methodMap,
      Map<DexField, DexField> fieldMap,
      Map<DexField, DexMethod> staticGetToMethodMap,
      Map<DexField, DexMethod> staticPutToMethodMap,
      Map<DexField, DexMethod> instanceGetToMethodMap,
      Map<DexField, DexMethod> instancePutToMethodMap,
      Map<DexMethod, DexMethod> initializerMap,
      DexType nestConstructorType,
      GraphLense previousLense) {
    super(
        ImmutableMap.of(),
        methodMap,
        fieldMap,
        null,
        null,
        previousLense,
        appView.dexItemFactory());
    this.appView = appView;
    this.staticGetToMethodMap = staticGetToMethodMap;
    this.staticPutToMethodMap = staticPutToMethodMap;
    this.instanceGetToMethodMap = instanceGetToMethodMap;
    this.instancePutToMethodMap = instancePutToMethodMap;
    this.initializerMap = initializerMap;
    this.nestConstructorType = nestConstructorType;
  }

  @Override
  public DexMethod lookupStaticGetFieldForMethod(DexField field, DexMethod context) {
    return lookupFieldAccessForMethod(field, context, staticGetToMethodMap);
  }

  @Override
  public DexMethod lookupStaticPutFieldForMethod(DexField field, DexMethod context) {
    return lookupFieldAccessForMethod(field, context, staticPutToMethodMap);
  }

  @Override
  public DexMethod lookupInstanceGetFieldForMethod(DexField field, DexMethod context) {
    return lookupFieldAccessForMethod(field, context, instanceGetToMethodMap);
  }

  @Override
  public DexMethod lookupInstancePutFieldForMethod(DexField field, DexMethod context) {
    return lookupFieldAccessForMethod(field, context, instancePutToMethodMap);
  }

  private DexMethod lookupFieldAccessForMethod(
      DexField field, DexMethod context, Map<DexField, DexMethod> map) {
    DexMethod replacementMethod = map.get(field);
    // We replace the field access by the bridge only if on a different class than context.
    if (replacementMethod != null && context.holder != replacementMethod.holder) {
      return replacementMethod;
    }
    return null;
  }

  @Override
  public boolean isContextFreeForMethods() {
    return false;
  }

  // This is true because mappings specific to this class can be filled.
  @Override
  protected boolean isLegitimateToHaveEmptyMappings() {
    return true;
  }

  @Override
  public RewrittenPrototypeDescription lookupPrototypeChanges(DexMethod method) {
    DexType[] parameters = method.proto.parameters.values;
    if (parameters.length == 0) {
      return previousLense.lookupPrototypeChanges(method);
    }
    DexType lastParameterType = parameters[parameters.length - 1];
    if (lastParameterType == nestConstructorType) {
      // This is an access bridge for a constructor that has been synthesized during
      // nest-based access desugaring.
      assert previousLense.lookupPrototypeChanges(method).isEmpty();
      return RewrittenPrototypeDescription.none().withExtraNullParameter();
    }
    return previousLense.lookupPrototypeChanges(method);
  }

  @Override
  public GraphLenseLookupResult lookupMethod(
      DexMethod method, DexMethod context, Invoke.Type type) {
    DexMethod previousContext =
        originalMethodSignatures != null
            ? originalMethodSignatures.getOrDefault(context, context)
            : context;
    GraphLenseLookupResult previous = previousLense.lookupMethod(method, previousContext, type);
    DexMethod newMethod = methodMap.get(previous.getMethod());
    Invoke.Type newType;
    if (newMethod != null) {
      // All generated non-initializer bridges are static.
      newType = Invoke.Type.STATIC;
    } else {
      newMethod = initializerMap.get(previous.getMethod());
      if (newMethod == null) {
        return previous;
      }
      // All generated initializer bridges are direct.
      newType = Invoke.Type.DIRECT;
    }
    if (context != null && newMethod.holder == context.holder) {
      // Without context we conservatively rewrite the method.
      // Private calls inside a class do not need to be rewritten.
      return previous;
    }
    return new GraphLenseLookupResult(newMethod, newType);
  }

  public static Builder builder(AppView<?> appView) {
    return new Builder(appView);
  }
}
