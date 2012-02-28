package de.is24.nexus.yum.repository;

import static de.is24.nexus.yum.execution.ExecutionUtil.execCommand;
import static de.is24.nexus.yum.repository.YumRepository.REPOMD_XML;
import static de.is24.nexus.yum.repository.YumRepository.YUM_REPOSITORY_DIR_NAME;
import static org.apache.commons.lang.StringUtils.isNotBlank;
import static org.sonatype.scheduling.TaskState.RUNNING;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.codehaus.plexus.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.nexus.scheduling.AbstractNexusTask;
import org.sonatype.scheduling.ScheduledTask;
import org.sonatype.scheduling.SchedulerTask;

/**
 * Create a yum-repository directory via 'createrepo' command line tool.
 * 
 * @author sherold
 * 
 */
@Component(role = SchedulerTask.class, hint = YumMetadataGenerationTask.ID, instantiationStrategy = "per-lookup")
public class YumMetadataGenerationTask extends AbstractNexusTask<YumRepository> implements ListFileFactory {
  public static final String ID = "YumMetadataGenerationTask";
	private static final String PACKAGE_FILE_DIR_NAME = ".packageFiles";
	private static final Logger LOG = LoggerFactory.getLogger(YumMetadataGenerationTask.class);
  public static final int MAXIMAL_PARALLEL_RUNS = 10;
	private static boolean activated = true;

	private YumGeneratorConfiguration config;

	@Override
	protected YumRepository doRun() throws Exception {
		if (activated) {
			LOG.info("Generating Yum-Repository for '{}' ...", config.getBaseRpmDir());
			try {
				config.getBaseRepoDir().mkdirs();

				File rpmListFile = createRpmListFile();
				execCommand(buildCreateRepositoryCommand(rpmListFile));

				replaceUrl();
			} catch (IOException e) {
				LOG.warn("Generating Yum-Repo failed", e);
				throw new IOException("Generating Yum-Repo failed", e);
			}
			Thread.sleep(100);
			LOG.info("Generation complete.");
			return new YumRepository(config.getBaseRepoDir(), config.getId(), config.getVersion());
    }

		return null;
  }

  @Override
  protected String getAction() {
    return "Generation YUM repository metadata";
  }

  @Override
  protected String getMessage() {
    return "Generation YUM repository metadata";
  }

  public void setConfiguration(YumGeneratorConfiguration config) {
		this.config = config;
  }

  @Override
  public boolean allowConcurrentExecution(Map<String, List<ScheduledTask<?>>> activeTasks) {

		if (activeTasks.containsKey(ID)) {
      int activeRunningTasks = 0;
      for (ScheduledTask<?> scheduledTask : activeTasks.get(ID)) {
				if (RUNNING.equals(scheduledTask.getTaskState())) {
					if (conflictsWith((YumMetadataGenerationTask) scheduledTask.getTask())) {
						return false;
          }
          activeRunningTasks++;
        }
      }
      return activeRunningTasks < MAXIMAL_PARALLEL_RUNS;
    } else {
      return true;
    }
  }

	private boolean conflictsWith(YumMetadataGenerationTask task) {
		return getConfig().conflictsWith(task.getConfig());
  }

	private File createRpmListFile() throws IOException {
		return new RpmListWriter(config, this).writeList();
	}

	private File createCacheDir() {
		File cacheDir = new File(config.getBaseCacheDir(), getRepositoryIdVersion());
		cacheDir.mkdirs();
		return cacheDir;
	}

	private String getRepositoryIdVersion() {
		return config.getId() + (isNotBlank(config.getVersion()) ? ("-version-" + config.getVersion()) : "");
	}

	private void replaceUrl() throws IOException {
		File repomd = new File(config.getBaseRepoDir(), YUM_REPOSITORY_DIR_NAME + File.separator + REPOMD_XML);
		if (activated && repomd.exists()) {
			String repomdStr = FileUtils.readFileToString(repomd);
			repomdStr = repomdStr.replace(config.getBaseRpmUrl(), config.getBaseRepoUrl());
			FileUtils.writeStringToFile(repomd, repomdStr);
		}
	}

	private String buildCreateRepositoryCommand(File packageList) {
		String baseRepoDir = config.getBaseRepoDir().getAbsolutePath();
		String baseRpmUrl = config.getBaseRpmUrl();
		String packageFile = packageList.getAbsolutePath();
		String cacheDir = createCacheDir().getAbsolutePath();
		String baseRpmDir = config.getBaseRpmDir().getAbsolutePath();
		return String.format("createrepo --update -o %s -u %s  -v -d -i %s -c %s %s", baseRepoDir, baseRpmUrl, packageFile, cacheDir,
				baseRpmDir);
	}

	public static void deactivate() {
		activated = false;
	}

	public static void activate() {
		activated = true;
	}

	@Override
	public File getRpmListFile(String repositoryId) {
		return new File(createBasePackageDir(), config.getId() + ".txt");
	}

	private File createBasePackageDir() {
		File basePackageDir = new File(config.getBaseCacheDir(), PACKAGE_FILE_DIR_NAME);
		basePackageDir.mkdirs();
		return basePackageDir;
	}

	@Override
	public File getRpmListFile(String repositoryId, String version) {
		return new File(createBasePackageDir(), config.getId() + "-" + version + ".txt");
	}

	public static boolean isActive() {
		return activated;
	}

	public String getRepositoryId() {
		return config.getId();
	}

  private YumGeneratorConfiguration getConfig() {
		return config;
  }

}
