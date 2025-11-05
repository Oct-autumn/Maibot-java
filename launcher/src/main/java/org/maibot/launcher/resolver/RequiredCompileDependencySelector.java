package org.maibot.launcher.resolver;

import org.eclipse.aether.collection.DependencyCollectionContext;
import org.eclipse.aether.collection.DependencySelector;
import org.eclipse.aether.graph.Dependency;

import java.util.Set;

public class RequiredCompileDependencySelector implements DependencySelector {
    private static final Set<String> REQUIRED_SCOPES = Set.of("compile", "runtime");

    @Override
    public boolean selectDependency(Dependency dependency) {
        return doSelection(dependency);
    }

    private boolean doSelection(Dependency dependency) {
        String scope = dependency.getScope();
        boolean optional = dependency.isOptional();

        // 仅选择 REQUIRED 范围且非可选的依赖
        return REQUIRED_SCOPES.contains(scope) && !optional;
    }

    @Override
    public DependencySelector deriveChildSelector(DependencyCollectionContext context) {
        return this;
    }
}
