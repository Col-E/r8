// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.errors;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;
import com.android.tools.r8.profile.startup.profile.StartupProfile;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import java.util.List;

@KeepForApi
public class StartupClassesNonStartupFractionDiagnostic implements Diagnostic {

  private final int numberOfStartupClasses;
  private final int numberOfStartupMethods;
  private final int numberOfNonStartupMethods;

  private final Int2IntMap numberOfStartupClassesByNumberOfStartupMethods;

  StartupClassesNonStartupFractionDiagnostic(
      int numberOfStartupClasses,
      int numberOfStartupMethods,
      int numberOfNonStartupMethods,
      Int2IntMap numberOfStartupClassesByNumberOfStartupMethods) {
    this.numberOfStartupClasses = numberOfStartupClasses;
    this.numberOfStartupMethods = numberOfStartupMethods;
    this.numberOfNonStartupMethods = numberOfNonStartupMethods;
    this.numberOfStartupClassesByNumberOfStartupMethods =
        numberOfStartupClassesByNumberOfStartupMethods;
  }

  @Override
  public Origin getOrigin() {
    return Origin.unknown();
  }

  @Override
  public Position getPosition() {
    return Position.UNKNOWN;
  }

  @Override
  public String getDiagnosticMessage() {
    return String.format(
        "Startup DEX files contains %d classes and %d methods of which %d (%d%%) are non-startup "
            + "methods. Distribution of classes by their number of startup methods:\n"
            + "0: %d classes\n"
            + "1: %d classes\n"
            + "2-3: %d classes\n"
            + "4-5: %d classes\n"
            + "6-10: %d classes\n"
            + "11+: %d classes\n",
        numberOfStartupClasses,
        numberOfStartupMethods + numberOfNonStartupMethods,
        numberOfNonStartupMethods,
        Math.round(
            (((double) numberOfNonStartupMethods)
                    / (numberOfStartupMethods + numberOfNonStartupMethods))
                * 100.0),
        numberOfStartupClassesByNumberOfStartupMethods.get(0),
        numberOfStartupClassesByNumberOfStartupMethods.get(1),
        numberOfStartupClassesByNumberOfStartupMethods.get(2)
            + numberOfStartupClassesByNumberOfStartupMethods.get(3),
        numberOfStartupClassesByNumberOfStartupMethods.get(4)
            + numberOfStartupClassesByNumberOfStartupMethods.get(5),
        numberOfStartupClassesByNumberOfStartupMethods.get(6)
            + numberOfStartupClassesByNumberOfStartupMethods.get(7)
            + numberOfStartupClassesByNumberOfStartupMethods.get(8)
            + numberOfStartupClassesByNumberOfStartupMethods.get(9)
            + numberOfStartupClassesByNumberOfStartupMethods.get(10),
        numberOfStartupClassesByNumberOfStartupMethods.int2IntEntrySet().stream()
            .map(entry -> entry.getIntKey() > 10 ? entry.getIntValue() : 0)
            .reduce(0, Integer::sum));
  }

  // Public factory to keep the constructor of the diagnostic out of the public API.
  public static class Factory {

    public static StartupClassesNonStartupFractionDiagnostic
        createStartupClassesNonStartupFractionDiagnostic(
            List<DexProgramClass> startupClasses, StartupProfile startupProfile) {
      assert !startupClasses.isEmpty();
      assert !startupProfile.isEmpty();
      int numberOfStartupClasses = startupClasses.size();
      int numberOfStartupMethods = 0;
      int numberOfNonStartupMethods = 0;
      Int2IntMap numberOfStartupClassesByNumberOfStartupMethods = new Int2IntOpenHashMap();
      numberOfStartupClassesByNumberOfStartupMethods.defaultReturnValue(0);
      for (DexProgramClass clazz : startupClasses) {
        assert startupProfile.isStartupClass(clazz.getType());
        int numberOfStartupMethodsInClass = 0;
        for (DexEncodedMethod method : clazz.methods()) {
          if (startupProfile.containsMethodRule(method.getReference())) {
            numberOfStartupMethodsInClass++;
          } else {
            numberOfNonStartupMethods++;
          }
        }
        numberOfStartupMethods += numberOfStartupMethodsInClass;
        numberOfStartupClassesByNumberOfStartupMethods.put(
            numberOfStartupMethodsInClass,
            numberOfStartupClassesByNumberOfStartupMethods.get(numberOfStartupMethodsInClass) + 1);
      }
      return createStartupClassesNonStartupFractionDiagnostic(
          numberOfStartupClasses,
          numberOfStartupMethods,
          numberOfNonStartupMethods,
          numberOfStartupClassesByNumberOfStartupMethods);
    }

    public static StartupClassesNonStartupFractionDiagnostic
        createStartupClassesNonStartupFractionDiagnostic(
            int numberOfStartupClasses,
            int numberOfStartupMethods,
            int numberOfNonStartupMethods,
            Int2IntMap numberOfStartupClassesByNumberOfStartupMethods) {
      return new StartupClassesNonStartupFractionDiagnostic(
          numberOfStartupClasses,
          numberOfStartupMethods,
          numberOfNonStartupMethods,
          numberOfStartupClassesByNumberOfStartupMethods);
    }
  }
}
