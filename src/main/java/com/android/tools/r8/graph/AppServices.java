// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.DataDirectoryResource;
import com.android.tools.r8.DataEntryResource;
import com.android.tools.r8.DataResourceProvider;
import com.android.tools.r8.DataResourceProvider.Visitor;
import com.android.tools.r8.ProgramResourceProvider;
import com.android.tools.r8.ResourceException;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.utils.DescriptorUtils;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** A description of the services and their implementations found in META-INF/services/. */
public class AppServices {

  private final Map<DexType, Set<DexType>> services;

  private AppServices(Map<DexType, Set<DexType>> services) {
    this.services = services;
  }

  public static Builder builder(DexApplication application) {
    return new Builder(application);
  }

  public static class Builder {

    private static final String SERVICE_DIRECTORY_NAME = "META-INF/services/";

    private final DexApplication app;
    private final Map<DexType, Set<DexType>> services = new IdentityHashMap<>();

    private Builder(DexApplication app) {
      this.app = app;
    }

    public AppServices build() {
      for (ProgramResourceProvider programResourceProvider : app.programResourceProviders) {
        DataResourceProvider dataResourceProvider =
            programResourceProvider.getDataResourceProvider();
        if (dataResourceProvider != null) {
          readServices(dataResourceProvider);
        }
      }
      return new AppServices(services);
    }

    private void readServices(DataResourceProvider dataResourceProvider) {
      try {
        dataResourceProvider.accept(new DataResourceProviderVisitor());
      } catch (ResourceException e) {
        throw new CompilationError(e.getMessage(), e);
      }
    }

    private class DataResourceProviderVisitor implements Visitor {

      @Override
      public void visit(DataDirectoryResource directory) {
        // Ignore.
      }

      @Override
      public void visit(DataEntryResource file) {
        try {
          String name = file.getName();
          if (name.startsWith(SERVICE_DIRECTORY_NAME)) {
            String serviceName = name.substring(SERVICE_DIRECTORY_NAME.length());
            if (DescriptorUtils.isValidJavaType(serviceName)) {
              String serviceDescriptor = DescriptorUtils.javaTypeToDescriptor(serviceName);
              DexType serviceType = app.dexItemFactory.createType(serviceDescriptor);
              byte[] bytes = ByteStreams.toByteArray(file.getByteStream());
              String contents = new String(bytes, Charset.defaultCharset());
              services.put(serviceType, readServiceImplementationsForService(contents));
            }
          }
        } catch (IOException | ResourceException e) {
          throw new CompilationError(e.getMessage(), e);
        }
      }

      private Set<DexType> readServiceImplementationsForService(String contents) {
        if (contents != null) {
          return Arrays.stream(contents.split(System.lineSeparator()))
              .map(String::trim)
              .filter(line -> !line.isEmpty())
              .filter(DescriptorUtils::isValidJavaType)
              .map(DescriptorUtils::javaTypeToDescriptor)
              .map(app.dexItemFactory::createType)
              .collect(Collectors.toSet());
        }
        return ImmutableSet.of();
      }
    }
  }
}
