// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.ProguardMapConsumer;
import java.util.ArrayList;
import java.util.List;

public class MultiProguardMapConsumer extends ProguardMapConsumer {

  private final List<ProguardMapConsumer> proguardMapConsumers;

  public MultiProguardMapConsumer(List<ProguardMapConsumer> proguardMapConsumers) {
    this.proguardMapConsumers = proguardMapConsumers;
  }

  @Override
  public void finished(DiagnosticsHandler handler) {
    proguardMapConsumers.forEach(consumer -> consumer.finished(handler));
  }

  @Override
  public void accept(ProguardMapMarkerInfo markerInfo, ClassNameMapper classNameMapper) {
    proguardMapConsumers.forEach(consumer -> consumer.accept(markerInfo, classNameMapper));
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {

    private final List<ProguardMapConsumer> proguardMapConsumers = new ArrayList<>();

    public Builder addProguardMapConsumer(ProguardMapConsumer consumer) {
      proguardMapConsumers.add(consumer);
      return this;
    }

    public MultiProguardMapConsumer build() {
      return new MultiProguardMapConsumer(proguardMapConsumers);
    }
  }
}
