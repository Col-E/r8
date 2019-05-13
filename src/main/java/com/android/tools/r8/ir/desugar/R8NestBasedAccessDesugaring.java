package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLense;
import com.android.tools.r8.utils.ThreadUtils;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

// Summary:
// - Computes all the live nests reachable from Program Classes (Sequential).
// - Process all live nests finding nest based access (Nests processes concurrently).
// - Add bridges to be processed by further passes (Sequential).
public class R8NestBasedAccessDesugaring extends NestBasedAccessDesugaring {

  private Map<DexType, List<DexType>> nestMap = new IdentityHashMap<>();

  public R8NestBasedAccessDesugaring(AppView<?> appView) {
    super(appView);
  }

  public GraphLense run(ExecutorService executorService) throws ExecutionException {
    if (appView.options().canUseNestBasedAccess()) {
      return appView.graphLense();
    }
    computeAndProcessNestsConcurrently(executorService);
    if (nestMap.isEmpty()) {
      return appView.graphLense();
    }
    addDeferredBridges();
    return new NestedPrivateMethodLense(
        appView, nestMap, getNestConstructorType(), appView.graphLense());
  }

  private void computeAndProcessNestsConcurrently(ExecutorService executorService)
      throws ExecutionException {
    List<Future<?>> futures = new ArrayList<>();
    // It is possible that a nest member is on the program path but its nest host
    // is only in the class path (or missing, raising an error).
    // Nests are therefore computed the first time a nest member is met, host or not.
    // The computedNestHosts list is there to avoid processing multiple times the same nest.
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      if (clazz.isInANest()) {
        DexType hostType = clazz.getNestHost();
        if (!nestMap.containsKey(hostType)) {
          DexClass host = clazz.isNestHost() ? clazz : appView.definitionFor(clazz.getNestHost());
          List<DexType> nest = extractNest(host, clazz);
          nestMap.put(hostType, nest);
          futures.add(asyncProcessNest(nest, executorService));
        }
      }
    }
    ThreadUtils.awaitFutures(futures);
  }

  // In R8, all classes are processed ahead of time.
  @Override
  protected boolean shouldProcessClassInNest(DexClass clazz, List<DexType> nest) {
    return true;
  }
}
