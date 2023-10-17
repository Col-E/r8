// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.android.tools.r8.AndroidResourceConsumer;
import com.android.tools.r8.AndroidResourceInput;
import com.android.tools.r8.AndroidResourceOutput;
import com.android.tools.r8.AndroidResourceProvider;
import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.ResourceException;
import com.android.tools.r8.ResourcePath;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.resourceshrinker.ResourceTracingImpl;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.util.List;

public interface ResourceTracing {

  // Mark the resource with the given ID as live.
  List<LiveMethod> traceResourceId(int id);

  // Notify that no more resources will be marked as live, assumes that the consumer is passed
  // the shrunken resource entries.

  void done(DiagnosticsHandler diagnosticsHandler);

  void setProvider(AndroidResourceProvider provider);

  void setConsumer(AndroidResourceConsumer consumer);

  // The resource shrinker will report back methods that become live due to newly live resources.
  class LiveMethod {

    private final String clazz;
    private final String method;

    public LiveMethod(String clazz, String method) {
      this.clazz = clazz;
      this.method = method;
    }

    public String getClazz() {
      return clazz;
    }

    public String getMethod() {
      return method;
    }
  }

  static ResourceTracing getImpl() {
    return new ResourceTracingImpl();
  }

  static void copyProviderToConsumer(
      DiagnosticsHandler diagnosticsHandler,
      AndroidResourceProvider provider,
      AndroidResourceConsumer consumer) {
    try {
      for (AndroidResourceInput androidResource : provider.getAndroidResources()) {
        consumer.accept(
            new AndroidResourceOutput() {
              @Override
              public ResourcePath getPath() {
                return androidResource.getPath();
              }

              @Override
              public ByteDataView getByteDataView() {
                try {
                  return ByteDataView.of(ByteStreams.toByteArray(androidResource.getByteStream()));
                } catch (IOException | ResourceException e) {
                  diagnosticsHandler.error(new ExceptionDiagnostic(e, androidResource.getOrigin()));
                }
                return null;
              }

              @Override
              public Origin getOrigin() {
                return androidResource.getOrigin();
              }
            },
            diagnosticsHandler);
      }
    } catch (ResourceException e) {
      diagnosticsHandler.error(new ExceptionDiagnostic(e));
    } finally {
      consumer.finished(diagnosticsHandler);
      provider.finished(diagnosticsHandler);
    }
  }
}
