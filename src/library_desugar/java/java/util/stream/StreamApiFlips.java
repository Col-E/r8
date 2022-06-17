// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package java.util.stream;

import static java.util.ConversionRuntimeException.exception;

import java.util.HashSet;
import java.util.Set;

public class StreamApiFlips {

  public static RuntimeException exceptionCharacteristics(Object suffix) {
    throw exception("java.util.stream.Collector.Characteristics", suffix);
  }

  public static Set<?> flipCharacteristicSet(Set<?> characteristicSet) {
    if (characteristicSet == null || characteristicSet.isEmpty()) {
      return characteristicSet;
    }
    HashSet<Object> convertedSet = new HashSet<>();
    Object guineaPig = characteristicSet.iterator().next();
    if (guineaPig instanceof java.util.stream.Collector.Characteristics) {
      for (Object item : characteristicSet) {
        java.util.stream.Collector.Characteristics characteristics;
        try {
          characteristics = (java.util.stream.Collector.Characteristics) item;
        } catch (ClassCastException cce) {
          throw exceptionCharacteristics(cce);
        }
        convertedSet.add(j$.util.stream.Collector.Characteristics.wrap_convert(characteristics));
      }
      return convertedSet;
    }
    if (guineaPig instanceof j$.util.stream.Collector.Characteristics) {
      for (Object item : characteristicSet) {
        j$.util.stream.Collector.Characteristics characteristics;
        try {
          characteristics = (j$.util.stream.Collector.Characteristics) item;
        } catch (ClassCastException cce) {
          throw exceptionCharacteristics(cce);
        }
        convertedSet.add(j$.util.stream.Collector.Characteristics.wrap_convert(characteristics));
      }
      return convertedSet;
    }
    throw exceptionCharacteristics(guineaPig.getClass());
  }
}
