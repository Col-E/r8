package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.GraphLense;
import com.android.tools.r8.graph.GraphLense.NestedGraphLense;
import com.android.tools.r8.ir.code.Invoke;
import com.google.common.collect.ImmutableMap;
import java.util.Map;

public class NestedPrivateMethodLense extends NestedGraphLense {

  public static class Builder extends GraphLense.Builder {

    private final AppView<? extends AppInfo> appView;

    protected Builder(AppView<? extends AppInfo> appView) {
      this.appView = appView;
    }

    public GraphLense build(GraphLense previousLense) {
      if (methodMap.isEmpty() && fieldMap.isEmpty()) {
        return previousLense;
      }
      return new NestedPrivateMethodLense(appView, methodMap, fieldMap, previousLense);
    }
  }

  private final AppView<?> appView;

  public NestedPrivateMethodLense(
      AppView<?> appView,
      Map<DexMethod, DexMethod> methodMap,
      Map<DexField, DexField> fieldMap,
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
