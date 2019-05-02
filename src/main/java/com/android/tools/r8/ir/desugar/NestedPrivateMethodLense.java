package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
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

    protected Builder(AppView<? extends AppInfo> appView) {
      this.appView = appView;
    }

    public void mapInstanceGet(DexField field, DexMethod method) {
      instanceGetToMethodMap.put(field, method);
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

    public GraphLense build(GraphLense previousLense) {
      if (methodMap.isEmpty() && fieldMap.isEmpty()) {
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
          previousLense);
    }
  }

  private final AppView<?> appView;

  private final Map<DexField, DexMethod> staticGetToMethodMap;
  private final Map<DexField, DexMethod> staticPutToMethodMap;
  private final Map<DexField, DexMethod> instanceGetToMethodMap;
  private final Map<DexField, DexMethod> instancePutToMethodMap;

  public NestedPrivateMethodLense(
      AppView<?> appView,
      Map<DexMethod, DexMethod> methodMap,
      Map<DexField, DexField> fieldMap,
      Map<DexField, DexMethod> staticGetToMethodMap,
      Map<DexField, DexMethod> staticPutToMethodMap,
      Map<DexField, DexMethod> instanceGetToMethodMap,
      Map<DexField, DexMethod> instancePutToMethodMap,
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
  }

  @Override
  public DexMethod lookupStaticGetFieldForMethod(DexField field) {
    return staticGetToMethodMap.get(field);
  }

  @Override
  public DexMethod lookupStaticPutFieldForMethod(DexField field) {
    return staticPutToMethodMap.get(field);
  }

  @Override
  public DexMethod lookupInstanceGetFieldForMethod(DexField field) {
    return instanceGetToMethodMap.get(field);
  }

  @Override
  public DexMethod lookupInstancePutFieldForMethod(DexField field) {
    return instancePutToMethodMap.get(field);
  }

  @Override
  public boolean isContextFreeForMethods() {
    return false;
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
    if (newMethod == null) {
      return previous;
    }
    if (newMethod == context) {
      // Bridges should not rewrite themselves.
      // TODO(b/130529338): Best would be that if we are in Class A and targeting class
      // A private method, we do not rewrite it.
      return previous;
    }
    // Use invokeStatic since all generated bridges are always static.
    return new GraphLenseLookupResult(newMethod, Invoke.Type.STATIC);
  }

  public static Builder builder(AppView<?> appView) {
    return new Builder(appView);
  }
}
