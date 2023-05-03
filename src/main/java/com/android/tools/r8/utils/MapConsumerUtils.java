// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.naming.MapConsumer;
import com.android.tools.r8.naming.ProguardMapMarkerInfo;
import java.util.function.Function;

public class MapConsumerUtils {

  public static MapConsumer wrapExistingMapConsumer(
      MapConsumer existingMapConsumer, MapConsumer newConsumer) {
    if (existingMapConsumer == null) {
      return newConsumer;
    }
    return new MapConsumer() {
      @Override
      public void accept(
          DiagnosticsHandler diagnosticsHandler,
          ProguardMapMarkerInfo makerInfo,
          ClassNameMapper classNameMapper) {
        existingMapConsumer.accept(diagnosticsHandler, makerInfo, classNameMapper);
        newConsumer.accept(diagnosticsHandler, makerInfo, classNameMapper);
      }

      @Override
      public void finished(DiagnosticsHandler handler) {
        existingMapConsumer.finished(handler);
        newConsumer.finished(handler);
      }
    };
  }

  public static <T> MapConsumer wrapExistingMapConsumerIfNotNull(
      MapConsumer existingMapConsumer, T object, Function<T, MapConsumer> producer) {
    if (object == null) {
      return existingMapConsumer;
    }
    return wrapExistingMapConsumer(existingMapConsumer, producer.apply(object));
  }
}
