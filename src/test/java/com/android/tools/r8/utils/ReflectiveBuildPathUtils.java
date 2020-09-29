// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.android.tools.r8.ToolHelper;
import com.google.common.collect.Iterables;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class ReflectiveBuildPathUtils {

  public interface PackageUtils {
    String getPackageName() throws Exception;

    Path getPackagePath() throws Exception;

    PackageUtils getParentPackageUtils() throws Exception;

    Iterable<PackageUtils> getAllPackageUtils();
  }

  public interface ClassUtils {
    String getClassName() throws Exception;

    String getSimpleClassName() throws Exception;

    Path getClassPath() throws Exception;
  }

  public abstract static class ExamplesRootPackage implements PackageUtils {
    public abstract Path getPackagePath();

    @Override
    public String getPackageName() {
      return "";
    }

    @Override
    public PackageUtils getParentPackageUtils() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Iterable<PackageUtils> getAllPackageUtils() {
      return instantiateAllPackageUtils(getClass());
    }
  }

  public abstract static class ExamplesJava11RootPackage extends ExamplesRootPackage {
    @Override
    public Path getPackagePath() {
      return Paths.get(ToolHelper.EXAMPLES_JAVA11_BUILD_DIR);
    }
  }

  public abstract static class ExamplesPackage implements PackageUtils {
    public List<String> getName() {
      return Collections.singletonList(getClass().getSimpleName());
    }

    @Override
    public PackageUtils getParentPackageUtils() throws Exception {
      return (PackageUtils) getClass().getDeclaringClass().getConstructor().newInstance();
    }

    @Override
    public Path getPackagePath() throws Exception {
      Path path = getParentPackageUtils().getPackagePath();
      for (String folder : getName()) {
        path = path.resolve(folder);
      }
      return path;
    }

    @Override
    public String getPackageName() throws Exception {
      return getParentPackageUtils().getPackageName() + String.join(".", getName()) + ".";
    }

    @Override
    public Iterable<PackageUtils> getAllPackageUtils() {
      return instantiateAllPackageUtils(getClass());
    }
  }

  public static class ExamplesClass implements PackageUtils, ClassUtils {
    @Override
    public PackageUtils getParentPackageUtils() throws Exception {
      return (PackageUtils) getClass().getDeclaringClass().getConstructor().newInstance();
    }

    public String getSimpleClassName() throws Exception {
      Object parent = getClass().getDeclaringClass().getConstructor().newInstance();
      if (parent instanceof ClassUtils) {
        return ((ClassUtils) parent).getSimpleClassName() + "$" + getClass().getSimpleName();
      } else {
        return getClass().getSimpleName();
      }
    }

    @Override
    public String getClassName() throws Exception {
      return getParentPackageUtils().getPackageName() + getSimpleClassName();
    }

    @Override
    public Path getClassPath() throws Exception {
      return getParentPackageUtils().getPackagePath().resolve(getSimpleClassName() + ".class");
    }

    @Override
    public String getPackageName() throws Exception {
      return getParentPackageUtils().getPackageName();
    }

    @Override
    public Path getPackagePath() throws Exception {
      return getParentPackageUtils().getPackagePath();
    }

    @Override
    public Iterable<PackageUtils> getAllPackageUtils() {
      return instantiateAllPackageUtils(getClass());
    }
  }

  public static Iterable<PackageUtils> instantiateAllPackageUtils(Class<?> parentClazz) {
    Collection<PackageUtils> children = instantiatePackageUtils(parentClazz);
    return Iterables.concat(
        children,
        Iterables.concat(Iterables.transform(children, PackageUtils::getAllPackageUtils)));
  }

  public static Collection<PackageUtils> instantiatePackageUtils(Class<?> parentClazz) {
    Collection<PackageUtils> packageUtils = new ArrayList<>();
    for (Class<?> clazz : parentClazz.getDeclaredClasses()) {
      try {
        Object obj = clazz.getConstructor().newInstance();
        if (obj instanceof PackageUtils) {
          packageUtils.add((PackageUtils) obj);
        }
      } catch (Exception ex) {
      }
    }
    return packageUtils;
  }

  public static String resolveClassName(Class<? extends ExamplesClass> clazz) throws Exception {
    return clazz.getConstructor().newInstance().getClassName();
  }

  public static Collection<Path> allClassFiles(Class<? extends ExamplesRootPackage> clazz)
      throws Exception {
    Collection<Path> classFiles = new ArrayList<>();
    for (PackageUtils util : clazz.getConstructor().newInstance().getAllPackageUtils()) {
      if (util instanceof ClassUtils) {
        classFiles.add(((ClassUtils) util).getClassPath());
      }
    }
    return classFiles;
  }
}
