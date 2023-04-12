// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion.callgraph;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.ImmediateProgramSubtypingInfo;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.KeepMethodInfo;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.classhierarchy.MethodOverridesCollector;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.google.common.collect.Sets;
import java.util.Set;

public abstract class CallSiteInformation {

  /**
   * Check if the <code>method</code> is guaranteed to only have a single call site.
   *
   * <p>For pinned methods (methods kept through Proguard keep rules) this will always answer <code>
   * false</code>.
   */
  public abstract boolean hasSingleCallSite(ProgramMethod method);

  public abstract boolean isMultiCallerInlineCandidate(ProgramMethod method);

  public abstract void unsetCallSiteInformation(ProgramMethod method);

  public static CallSiteInformation empty() {
    return EmptyCallSiteInformation.EMPTY_INFO;
  }

  private static class EmptyCallSiteInformation extends CallSiteInformation {

    private static final EmptyCallSiteInformation EMPTY_INFO = new EmptyCallSiteInformation();

    @Override
    public boolean hasSingleCallSite(ProgramMethod method) {
      return false;
    }

    @Override
    public boolean isMultiCallerInlineCandidate(ProgramMethod method) {
      return false;
    }

    @Override
    public void unsetCallSiteInformation(ProgramMethod method) {
      // Intentionally empty.
    }
  }

  static class CallGraphBasedCallSiteInformation extends CallSiteInformation {

    private final Set<DexMethod> singleCallerMethods = Sets.newIdentityHashSet();
    private final Set<DexMethod> multiCallerInlineCandidates = Sets.newIdentityHashSet();

    CallGraphBasedCallSiteInformation(AppView<AppInfoWithLiveness> appView, CallGraph graph) {
      InternalOptions options = appView.options();
      ProgramMethodSet pinned =
          MethodOverridesCollector.findAllMethodsAndOverridesThatMatches(
              appView,
              ImmediateProgramSubtypingInfo.create(appView),
              appView.appInfo().classes(),
              method -> {
                KeepMethodInfo keepInfo = appView.getKeepInfo(method);
                return !keepInfo.isClosedWorldReasoningAllowed(options)
                    || keepInfo.isPinned(options);
              });

      for (Node node : graph.getNodes()) {
        ProgramMethod method = node.getProgramMethod();
        DexMethod reference = method.getReference();

        // For non-pinned methods and methods that override library methods we do not know the exact
        // number of call sites.
        if (pinned.contains(method)) {
          continue;
        }

        if (appView.options().inlinerOptions().disableInliningOfLibraryMethodOverrides
            && method.getDefinition().isLibraryMethodOverride().isTrue()) {
          continue;
        }

        if (method.getDefinition().isDefaultInstanceInitializer()
            && appView.hasProguardCompatibilityActions()
            && appView.getProguardCompatibilityActions().isCompatInstantiated(method.getHolder())) {
          continue;
        }

        int numberOfCallSites = node.getNumberOfCallSites();
        if (numberOfCallSites == 1) {
          singleCallerMethods.add(reference);
        } else if (numberOfCallSites > 1) {
          multiCallerInlineCandidates.add(reference);
        }
      }
    }

    /**
     * Checks if the given method only has a single call site.
     *
     * <p>For pinned methods (methods kept through Proguard keep rules) and methods that override a
     * library method this always returns false.
     */
    @Override
    public boolean hasSingleCallSite(ProgramMethod method) {
      return singleCallerMethods.contains(method.getReference());
    }

    /**
     * Checks if the given method only has two call sites.
     *
     * <p>For pinned methods (methods kept through Proguard keep rules) and methods that override a
     * library method this always returns false.
     */
    @Override
    public boolean isMultiCallerInlineCandidate(ProgramMethod method) {
      return multiCallerInlineCandidates.contains(method.getReference());
    }

    @Override
    public void unsetCallSiteInformation(ProgramMethod method) {
      singleCallerMethods.remove(method.getReference());
      multiCallerInlineCandidates.remove(method.getReference());
    }
  }
}
