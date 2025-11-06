package org.maibot.launcher.resolver;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.maibot.launcher.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public class ResolverService implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger("Launcher.ResolverService");

    public final RepositorySystem                         repoSystem;
    public final RepositorySystemSession.CloseableSession repoSession;

    public ResolverService(String workDir) {
        this.repoSystem = ResolverBooter.newRepositorySystem();

        var localRepoPath = Path.of(workDir, "libs");
        Utils.ensureDirExists(localRepoPath);
        log.debug("本地仓库路径: {}", localRepoPath);
        this.repoSession = ResolverBooter.newRepositorySystemSession(
          this.repoSystem,
          localRepoPath.toString()
        );
    }

    @Override
    public void close() {
        repoSession.close();
        repoSystem.close();
    }
}
