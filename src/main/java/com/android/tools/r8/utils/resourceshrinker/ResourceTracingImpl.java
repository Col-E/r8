// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.resourceshrinker;

import com.android.build.shrinker.r8integration.R8ResourceShrinkerState;
import com.android.tools.r8.AndroidResourceConsumer;
import com.android.tools.r8.AndroidResourceProvider;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.utils.ResourceTracing;
import java.util.Collections;
import java.util.List;

public class ResourceTracingImpl implements ResourceTracing {

  private AndroidResourceConsumer consumer;
  private AndroidResourceProvider provider;

  @Override
  public List<LiveMethod> traceResourceId(int id) {
    return Collections.emptyList();
  }

  @Override
  public void done(DiagnosticsHandler diagnosticsHandler) {
    ResourceTracing.copyProviderToConsumer(diagnosticsHandler, provider, consumer);
  }

  @Override
  public void setProvider(AndroidResourceProvider provider) {
    this.provider = provider;
    new R8ResourceShrinkerState();
    // TODO(b/287398085): Instantiate with resource table, currently the resource table in the tests
    // is not valid. This just ensures that we can access the class from r8 with the new gradle
    // setup.
  }

  @Override
  public void setConsumer(AndroidResourceConsumer consumer) {
    this.consumer = consumer;
  }
}
