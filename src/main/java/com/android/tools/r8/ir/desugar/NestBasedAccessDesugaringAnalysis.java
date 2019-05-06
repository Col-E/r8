package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.GraphLense;

public class NestBasedAccessDesugaringAnalysis extends NestBasedAccessDesugaring {

  private final NestedPrivateMethodLense.Builder builder;

  public NestBasedAccessDesugaringAnalysis(AppView<?> appView) {
    super(appView);
    this.builder = NestedPrivateMethodLense.builder(appView);
  }

  public GraphLense run() {
    if (appView.options().canUseNestBasedAccess()) {
      return appView.graphLense();
    }
    analyzeNests();
    return builder.build(appView.graphLense(), getConstructorType());
  }

  @Override
  protected void shouldRewriteInitializers(DexMethod method, DexMethod bridge) {
    builder.mapInitializer(method, bridge);
  }

  @Override
  protected void shouldRewriteCalls(DexMethod method, DexMethod bridge) {
    builder.map(method, bridge);
  }

  @Override
  protected void shouldRewriteStaticGetFields(DexField field, DexMethod bridge) {
    builder.mapStaticGet(field, bridge);
  }

  @Override
  protected void shouldRewriteStaticPutFields(DexField field, DexMethod bridge) {
    builder.mapStaticPut(field, bridge);
  }

  @Override
  protected void shouldRewriteInstanceGetFields(DexField field, DexMethod bridge) {
    builder.mapInstanceGet(field, bridge);
  }

  @Override
  protected void shouldRewriteInstancePutFields(DexField field, DexMethod bridge) {
    builder.mapInstancePut(field, bridge);
  }
}
