// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.features;

import com.android.tools.r8.DataResourceConsumer;
import com.android.tools.r8.DataResourceProvider;
import com.android.tools.r8.FeatureSplit;
import com.android.tools.r8.ProgramResourceProvider;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FeatureSplitConfiguration {

  private final List<FeatureSplit> featureSplits;

  public FeatureSplitConfiguration(List<FeatureSplit> featureSplits) {
    this.featureSplits = featureSplits;
  }

  public static class DataResourceProvidersAndConsumer {
    private final Set<DataResourceProvider> providers;
    private final DataResourceConsumer consumer;

    public DataResourceProvidersAndConsumer(
        Set<DataResourceProvider> providers, DataResourceConsumer consumer) {
      this.providers = providers;
      this.consumer = consumer;
    }

    public Set<DataResourceProvider> getProviders() {
      return providers;
    }

    public DataResourceConsumer getConsumer() {
      return consumer;
    }
  }

  public Collection<DataResourceProvidersAndConsumer> getDataResourceProvidersAndConsumers() {
    List<DataResourceProvidersAndConsumer> result = new ArrayList<>();
    for (FeatureSplit featureSplit : featureSplits) {
      DataResourceConsumer dataResourceConsumer =
          featureSplit.getProgramConsumer().getDataResourceConsumer();
      if (dataResourceConsumer != null) {
        Set<DataResourceProvider> dataResourceProviders = new HashSet<>();
        for (ProgramResourceProvider programResourceProvider :
            featureSplit.getProgramResourceProviders()) {
          DataResourceProvider dataResourceProvider =
              programResourceProvider.getDataResourceProvider();
          if (dataResourceProvider != null) {
            dataResourceProviders.add(dataResourceProvider);
          }
        }
        if (!dataResourceProviders.isEmpty()) {
          result.add(
              new DataResourceProvidersAndConsumer(dataResourceProviders, dataResourceConsumer));
        }
      }
    }
    return result;
  }

  public List<FeatureSplit> getFeatureSplits() {
    return featureSplits;
  }
}
