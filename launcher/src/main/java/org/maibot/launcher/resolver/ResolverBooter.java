package org.maibot.launcher.resolver;

import org.eclipse.aether.DefaultRepositoryCache;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.impl.ArtifactDescriptorReader;
import org.eclipse.aether.impl.RemoteRepositoryManager;
import org.eclipse.aether.impl.VersionRangeResolver;
import org.eclipse.aether.internal.impl.collect.DependencyCollectorDelegate;
import org.eclipse.aether.internal.impl.collect.bf.BfDependencyCollector;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.resolution.ArtifactDescriptorPolicy;
import org.eclipse.aether.resolution.ResolutionErrorPolicy;
import org.eclipse.aether.supplier.RepositorySystemSupplier;
import org.eclipse.aether.util.graph.transformer.ConfigurableVersionSelector;
import org.eclipse.aether.util.graph.transformer.ConflictResolver;
import org.eclipse.aether.util.graph.transformer.PathConflictResolver;
import org.eclipse.aether.util.graph.transformer.SimpleOptionalitySelector;
import org.eclipse.aether.util.repository.SimpleArtifactDescriptorPolicy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ResolverBooter {
    public static final List<String> SCOPE_PRIORITY = List.of("compile", "runtime", "provided", "system", "test");

    public static RepositorySystem newRepositorySystem() {
        RepositorySystemSupplier supplier = new RepositorySystemSupplier() {
            @Override
            protected Map<String, DependencyCollectorDelegate> createDependencyCollectorDelegates() {
                RemoteRepositoryManager remoteRepositoryManager = getRemoteRepositoryManager();
                ArtifactDescriptorReader artifactDescriptorReader = getArtifactDescriptorReader();
                VersionRangeResolver versionRangeResolver = getVersionRangeResolver();
                HashMap<String, DependencyCollectorDelegate> result = new HashMap<>();
                result.put(
                  BfDependencyCollector.NAME, new BfDependencyCollector(
                    remoteRepositoryManager,
                    artifactDescriptorReader,
                    versionRangeResolver,
                    getArtifactDecoratorFactories()
                  )
                );
                return result;
            }
        };

        return supplier.get();
    }

    public static RepositorySystemSession.CloseableSession newRepositorySystemSession(
      RepositorySystem system,
      String localRepoPath
    ) {
        // 依赖冲突解决：优先最近，只保留运行时依赖
        var depGraphTransformer = new PathConflictResolver(
          new ConfigurableVersionSelector(new ConfigurableVersionSelector.Nearest()),
          new ConflictResolver.ScopeSelector() {
              @Override
              public void selectScope(ConflictResolver.ConflictContext context)
              throws RepositoryException {
                  String finalScope = getFinalScope(context);
                  context.setScope(finalScope);
              }

              private String getFinalScope(ConflictResolver.ConflictContext context)
              throws RepositoryException {
                  // 优先级：compile > runtime > provided > test > system
                  // 保留非可选依赖的最高优先级范围。
                  // 对于可选依赖，只要有一条路径是非可选依赖，就按非可选依赖处理。

                  var items = context.getItems();   // 获取冲突的依赖项列表
                  String finalScope = null; // 最终确定的范围
                  for (var item : items) {
                      if (item.getDependency().isOptional()) {
                          // 可选依赖，跳过
                          continue;
                      }

                      String scope = item.getDependency().getScope();
                      if (!SCOPE_PRIORITY.contains(scope)) {
                          throw new RepositoryException("未知的依赖范围：" + scope);
                      }
                      if (finalScope == null) {
                          finalScope = scope;
                      } else {
                          // 比较优先级
                          int currentPriority = SCOPE_PRIORITY.indexOf(scope);
                          int finalPriority = SCOPE_PRIORITY.indexOf(finalScope);
                          if (currentPriority < finalPriority) {
                              finalScope = scope;
                          }
                      }
                  }
                  return finalScope;
              }
          },
          new SimpleOptionalitySelector(),
          new ConflictResolver.ScopeDeriver() {
              @Override
              public void deriveScope(ConflictResolver.ScopeContext context)
              throws RepositoryException {
                  context.setDerivedScope(getDerivedScope(context.getParentScope(), context.getChildScope()));
              }

              private String getDerivedScope(String parentScope, String childScope) {
                  if (parentScope.equals("compile") || parentScope.equals("runtime")) {
                      return childScope;
                  } else {
                      return parentScope;   // 传递依赖保持父依赖范围
                  }
              }
          }
        );

        return system.createSessionBuilder()
          .withLocalRepositories(new LocalRepository(localRepoPath))
          .setSystemProperties(System.getProperties())
          .setDependencyGraphTransformer(depGraphTransformer)
          .setCache(new DefaultRepositoryCache())
          .setDependencySelector(new RequiredCompileDependencySelector())
          .setArtifactDescriptorPolicy(new SimpleArtifactDescriptorPolicy(ArtifactDescriptorPolicy.IGNORE_INVALID))
          .build();
    }

    public static List<RemoteRepository> newRemoteRepositories() {
        // Maven Central 仓库
        var mavenCentral = new RemoteRepository.Builder(
          "central",
          "default",
          "https://repo1.maven.org/maven2/"
        ).setReleasePolicy(new RepositoryPolicy(
          true,
          RepositoryPolicy.UPDATE_POLICY_NEVER,
          RepositoryPolicy.UPDATE_POLICY_DAILY,
          RepositoryPolicy.CHECKSUM_POLICY_FAIL
        )).setSnapshotPolicy(new RepositoryPolicy(
          false,
          RepositoryPolicy.UPDATE_POLICY_NEVER,
          RepositoryPolicy.UPDATE_POLICY_DAILY,
          RepositoryPolicy.CHECKSUM_POLICY_FAIL
        )).build();

        var tencentMirror = new RemoteRepository.Builder(
          "tencent-mirror",
          "default",
          "https://mirrors.cloud.tencent.com/nexus/repository/maven-public"
        ).setReleasePolicy(new RepositoryPolicy(
          true,
          RepositoryPolicy.UPDATE_POLICY_NEVER,
          RepositoryPolicy.UPDATE_POLICY_DAILY,
          RepositoryPolicy.CHECKSUM_POLICY_FAIL
        )).setSnapshotPolicy(new RepositoryPolicy(
          false,
          RepositoryPolicy.UPDATE_POLICY_NEVER,
          RepositoryPolicy.UPDATE_POLICY_DAILY,
          RepositoryPolicy.CHECKSUM_POLICY_FAIL
        )).build();

        var localRepo = new RemoteRepository.Builder(
          "local-repo",
          "default",
          "file://" + System.getProperty("user.home") + "/.m2/repository"
        ).setPolicy(new RepositoryPolicy(
          true,
          RepositoryPolicy.UPDATE_POLICY_ALWAYS,
          RepositoryPolicy.UPDATE_POLICY_ALWAYS,
          RepositoryPolicy.CHECKSUM_POLICY_WARN
        )).build();

        return List.of(mavenCentral, tencentMirror, localRepo);
    }

    public static class Utils {
        public static void recursivePrintDependencyTree(DependencyNode node, String indent) {
            if (node.getArtifact() != null) {
                var artifact = node.getArtifact();
                System.out.printf("%s:%s:%s\n", artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion());
            } else {
                System.out.printf("%s\n", node.getData().get("A-ID"));
            }

            for (int idx = 0; idx < node.getChildren().size(); idx++) {
                DependencyNode child = node.getChildren().get(idx);
                if (idx == node.getChildren().size() - 1) {
                    if (child.getChildren().isEmpty()) {
                        System.out.printf("%s└─── ", indent);
                    } else {
                        System.out.printf("%s└─┬─ ", indent);
                    }
                    recursivePrintDependencyTree(child, indent + "  ");
                } else {
                    if (child.getChildren().isEmpty()) {
                        System.out.printf("%s├─── ", indent);
                    } else {
                        System.out.printf("%s├─┬─ ", indent);
                    }
                    recursivePrintDependencyTree(child, indent + "│ ");
                }
            }
        }
    }
}
