package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLense;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

// R8 specific desugaring of nest based access.
// Summary:
// - Computes all the live nests reachable from Program Classes (Sequential).
// - Process all live nests finding nest based access (Nests processes concurrently).
// - Add bridges to be processed by further passes (Sequential).
public class NestBasedAccessDesugaringAnalysis extends NestBasedAccessDesugaring {

  private final NestedPrivateMethodLense.Builder builder;

  public NestBasedAccessDesugaringAnalysis(AppView<?> appView, ExecutorService executorService) {
    super(appView);
    this.builder = NestedPrivateMethodLense.builder(appView);
  }

  public GraphLense run(ExecutorService executorService) throws ExecutionException {
    if (appView.options().canUseNestBasedAccess()) {
      return appView.graphLense();
    }
    List<List<DexType>> liveNests = computeLiveNests();
    processNestsConcurrently(liveNests, executorService);
    addDeferredBridges();
    return builder.build(appView.graphLense(), getNestConstructorType());
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
        DexType hostType = clazz.isNestHost() ? clazz.type : clazz.getNestHost();
        if (!computedNestHosts.contains(hostType)) {
          computedNestHosts.add(hostType);
          DexClass host = clazz.isNestHost() ? clazz : appView.definitionFor(clazz.getNestHost());
          liveNests.add(extractNest(host, clazz));
        }
      }
    }
    return liveNests;
  }

  // In R8, all classes are processed ahead of time.
  @Override
  protected boolean shouldProcessClassInNest(DexClass clazz, List<DexType> nest) {
    return true;
  }

  @Override
  protected synchronized void shouldRewriteInitializers(DexMethod method, DexMethod bridge) {
    builder.mapInitializer(method, bridge);
  }

  @Override
  protected synchronized void shouldRewriteCalls(DexMethod method, DexMethod bridge) {
    builder.map(method, bridge);
  }

  @Override
  protected synchronized void shouldRewriteStaticGetFields(DexField field, DexMethod bridge) {
    builder.mapStaticGet(field, bridge);
  }

  @Override
  protected synchronized void shouldRewriteStaticPutFields(DexField field, DexMethod bridge) {
    builder.mapStaticPut(field, bridge);
  }

  @Override
  protected synchronized void shouldRewriteInstanceGetFields(DexField field, DexMethod bridge) {
    builder.mapInstanceGet(field, bridge);
  }

  @Override
  protected synchronized void shouldRewriteInstancePutFields(DexField field, DexMethod bridge) {
    builder.mapInstancePut(field, bridge);
  }
}
