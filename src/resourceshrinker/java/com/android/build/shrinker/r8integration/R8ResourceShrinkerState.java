// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.build.shrinker.r8integration;

import com.android.aapt.Resources.ResourceTable;
import com.android.build.shrinker.NoDebugReporter;
import com.android.build.shrinker.ResourceShrinkerModel;
import com.android.build.shrinker.ResourceTableUtilKt;
import com.android.build.shrinker.ShrinkerDebugReporter;
import com.android.ide.common.resources.ResourcesUtil;
import com.android.ide.common.resources.usage.ResourceUsageModel;
import com.android.ide.common.resources.usage.ResourceUsageModel.Resource;
import com.android.resources.ResourceType;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

public class R8ResourceShrinkerState {

  private R8ResourceShrinkerModel r8ResourceShrinkerModel;

  public List<String> trace(int id) {
    Resource resource = r8ResourceShrinkerModel.getResourceStore().getResource(id);
    ResourceUsageModel.markReachable(resource);
    return Collections.emptyList();
  }

  public void setResourceTableInput(InputStream inputStream) {
    r8ResourceShrinkerModel = new R8ResourceShrinkerModel(NoDebugReporter.INSTANCE, true);
    r8ResourceShrinkerModel.instantiateFromResourceTable(inputStream);
  }

  public R8ResourceShrinkerModel getR8ResourceShrinkerModel() {
    return r8ResourceShrinkerModel;
  }

  public static class R8ResourceShrinkerModel extends ResourceShrinkerModel {

    public R8ResourceShrinkerModel(
        ShrinkerDebugReporter debugReporter, boolean supportMultipackages) {
      super(debugReporter, supportMultipackages);
    }

    // Similar to instantiation in ProtoResourceTableGatherer, but using an inputstream.
    void instantiateFromResourceTable(InputStream inputStream) {
      try {
        ResourceTable resourceTable = ResourceTable.parseFrom(inputStream);
        instantiateFromResourceTable(resourceTable);
      } catch (IOException ex) {
        throw new RuntimeException(ex);
      }
    }

    void instantiateFromResourceTable(ResourceTable resourceTable) {
      ResourceTableUtilKt.entriesSequence(resourceTable)
          .iterator()
          .forEachRemaining(
              entryWrapper -> {
                ResourceType resourceType = ResourceType.fromClassName(entryWrapper.getType());
                if (resourceType != ResourceType.STYLEABLE) {
                  this.addResource(
                      resourceType,
                      entryWrapper.getPackageName(),
                      ResourcesUtil.resourceNameToFieldName(entryWrapper.getEntry().getName()),
                      entryWrapper.getId());
                }
              });
    }
  }
}
