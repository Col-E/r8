package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.GraphLense;

public class NestBasedAccessDesugaringAnalysis extends NestBasedAccessDesugaring {

  private final NestedPrivateMethodLense.Builder builder;

  public NestBasedAccessDesugaringAnalysis(AppView<?> appView) {
    super(appView);
    this.builder = NestedPrivateMethodLense.builder(appView);
  }

  public GraphLense run() {
    AppView<?> appView = this.getAppView();
    if (appView.options().canUseNestBasedAccess()) {
      return appView.graphLense();
    }
    analyzeNests();
    return builder.build(appView.graphLense());
  }

  @Override
  protected void shouldRewriteCalls(DexMethod method, DexMethod bridge) {
    builder.map(method, bridge);
  }
}
