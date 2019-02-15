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
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.StringDiagnostic;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

/** A description of the services and their implementations found in META-INF/services/. */
public class AppServices {

  public static final String SERVICE_DIRECTORY_NAME = "META-INF/services/";

  private final AppView<? extends AppInfo> appView;

  // Mapping from service types to service implementation types.
  private final Map<DexType, Set<DexType>> services;

  private AppServices(AppView<? extends AppInfo> appView, Map<DexType, Set<DexType>> services) {
    this.appView = appView;
    this.services = services;
  }

  public Set<DexType> allServiceTypes() {
    assert verifyRewrittenWithLens();
    return services.keySet();
  }

  public Set<DexType> serviceImplementationsFor(DexType serviceType) {
    assert verifyRewrittenWithLens();
    assert services.containsKey(serviceType);
    Set<DexType> serviceImplementationTypes = services.get(serviceType);
    if (serviceImplementationTypes == null) {
      assert false
          : "Unexpected attempt to get service implementations for non-service type `"
              + serviceType.toSourceString()
              + "`";
      return ImmutableSet.of();
    }
    return serviceImplementationTypes;
  }

  public AppServices rewrittenWithLens(GraphLense graphLens) {
    ImmutableMap.Builder<DexType, Set<DexType>> rewrittenServices = ImmutableMap.builder();
    for (Entry<DexType, Set<DexType>> entry : services.entrySet()) {
      DexType rewrittenServiceType = graphLens.lookupType(entry.getKey());
      ImmutableSet.Builder<DexType> rewrittenServiceImplementationTypes = ImmutableSet.builder();
      for (DexType serviceImplementationType : entry.getValue()) {
        rewrittenServiceImplementationTypes.add(graphLens.lookupType(serviceImplementationType));
      }
      rewrittenServices.put(rewrittenServiceType, rewrittenServiceImplementationTypes.build());
    }
    return new AppServices(appView, rewrittenServices.build());
  }

  private boolean verifyRewrittenWithLens() {
    for (Entry<DexType, Set<DexType>> entry : services.entrySet()) {
      assert entry.getKey() == appView.graphLense().lookupType(entry.getKey());
      for (DexType type : entry.getValue()) {
        assert type == appView.graphLense().lookupType(type);
      }
    }
    return true;
  }

  public static Builder builder(AppView<? extends AppInfo> appView) {
    return new Builder(appView);
  }

  public static class Builder {

    private final AppView<? extends AppInfo> appView;
    private final Map<DexType, Set<DexType>> services = new IdentityHashMap<>();

    private Builder(AppView<? extends AppInfo> appView) {
      this.appView = appView;
    }

    public AppServices build() {
      Iterable<ProgramResourceProvider> programResourceProviders =
          appView.appInfo().app.programResourceProviders;
      for (ProgramResourceProvider programResourceProvider : programResourceProviders) {
        DataResourceProvider dataResourceProvider =
            programResourceProvider.getDataResourceProvider();
        if (dataResourceProvider != null) {
          readServices(dataResourceProvider);
        }
      }
      return new AppServices(appView, services);
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
              DexType serviceType = appView.dexItemFactory().createType(serviceDescriptor);
              byte[] bytes = ByteStreams.toByteArray(file.getByteStream());
              String contents = new String(bytes, Charset.defaultCharset());
              services.put(
                  serviceType, readServiceImplementationsForService(contents, file.getOrigin()));
            }
          }
        } catch (IOException | ResourceException e) {
          throw new CompilationError(e.getMessage(), e);
        }
      }

      private Set<DexType> readServiceImplementationsForService(String contents, Origin origin) {
        if (contents != null) {
          return Arrays.stream(contents.split(System.lineSeparator()))
              .map(String::trim)
              .filter(line -> !line.isEmpty())
              .filter(DescriptorUtils::isValidJavaType)
              .map(DescriptorUtils::javaTypeToDescriptor)
              .map(appView.dexItemFactory()::createType)
              .filter(
                  serviceImplementationType -> {
                    if (!serviceImplementationType.isClassType()) {
                      // Should never happen.
                      appView
                          .options()
                          .reporter
                          .warning(
                              new StringDiagnostic(
                                  "Unexpected service implementation found in META-INF/services/: `"
                                      + serviceImplementationType.toSourceString()
                                      + "`.",
                                  origin));
                      return false;
                    }
                    return true;
                  })
              .collect(Collectors.toSet());
        }
        return ImmutableSet.of();
      }
    }
  }
}
