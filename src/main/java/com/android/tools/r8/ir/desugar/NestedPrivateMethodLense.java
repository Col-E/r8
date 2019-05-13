package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLense;
import com.android.tools.r8.graph.GraphLense.NestedGraphLense;
import com.android.tools.r8.ir.code.Invoke;
import com.google.common.collect.ImmutableMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class NestedPrivateMethodLense extends NestedGraphLense {

  private final AppView<?> appView;
  // Map from nestHost to nest members including nest hosts
  private final Map<DexType, List<DexType>> nestMap;
  private final DexType nestConstructorType;

  public NestedPrivateMethodLense(
      AppView<?> appView,
      Map<DexType, List<DexType>> nestMap,
      DexType nestConstructorType,
      GraphLense previousLense) {
    super(
        ImmutableMap.of(),
        ImmutableMap.of(),
        ImmutableMap.of(),
        null,
        null,
        previousLense,
        appView.dexItemFactory());
    this.appView = appView;
    // We do not want a concurrent Map here.
    assert nestMap instanceof IdentityHashMap;
    this.nestMap = nestMap;
    this.nestConstructorType = nestConstructorType;
  }

  private List<DexType> getNestFor(DexType type) {
    DexClass clazz = appView.definitionFor(type);
    DexType hostType = clazz.isNestHost() ? clazz.type : clazz.getNestHost();
    return nestMap.get(hostType);
  }

  private DexMethod lookupFieldForMethod(DexField field, DexMethod context, boolean isGet) {
    DexEncodedField encodedField = appView.definitionFor(field);
    if (encodedField != null
        && NestBasedAccessDesugaring.fieldAccessRequiresRewriting(
            encodedField, getNestFor(context.holder), context.holder)) {
      return NestBasedAccessDesugaring.computeFieldBridge(encodedField, isGet, appView);
    }
    return null;
  }

  @Override
  public DexMethod lookupStaticGetFieldForMethod(DexField field, DexMethod context) {
    return lookupFieldForMethod(field, context, true);
  }

  @Override
  public DexMethod lookupStaticPutFieldForMethod(DexField field, DexMethod context) {
    return lookupFieldForMethod(field, context, false);
  }

  @Override
  public DexMethod lookupInstanceGetFieldForMethod(DexField field, DexMethod context) {
    return lookupFieldForMethod(field, context, true);
  }

  @Override
  public DexMethod lookupInstancePutFieldForMethod(DexField field, DexMethod context) {
    return lookupFieldForMethod(field, context, false);
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
    if (context == null) {
      return previous;
    }
    DexEncodedMethod encodedMethod = appView.definitionFor(method);
    if (encodedMethod == null
        || !NestBasedAccessDesugaring.invokeRequiresRewriting(
            encodedMethod, getNestFor(context.holder), context.holder)) {
      return previous;
    }
    DexMethod bridge;
    Invoke.Type invokeType;
    if (encodedMethod.isInstanceInitializer()) {
      bridge =
          NestBasedAccessDesugaring.computeInitializerBridge(method, appView, nestConstructorType);
      invokeType = Invoke.Type.DIRECT;
    } else {
      bridge = NestBasedAccessDesugaring.computeMethodBridge(encodedMethod, appView);
      invokeType = Invoke.Type.STATIC;
    }

    return new GraphLenseLookupResult(bridge, invokeType);
  }

}
