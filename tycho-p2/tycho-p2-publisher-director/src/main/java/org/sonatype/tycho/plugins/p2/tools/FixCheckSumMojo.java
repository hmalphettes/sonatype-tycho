package org.sonatype.tycho.plugins.p2.tools;

import java.io.File;
import java.io.IOException;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.zip.ZipUnArchiver;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.sonatype.tycho.plugins.p2.AbstractP2AppInvokerMojo;

/**
 * Invoke the application RecreateRepositoryApplication; aniefer mentions that it
 * will take care of packed repositories and update their checksum.
 * <p>
 * This will not make any difference if the repository generated was not packed and/or signed.
 * There is some glue to unzip a repository generated by a packAndSign process elsewhere.
 * For example the jetty toolchain will pack and sign the repo remotely and will download
 * the result file into the target folder as a zip.
 * <br/> The glue unzips the file inside the target/repository folder after
 * archiving the content of the previous repository.
 * </p>
 * @goal fix-checksums
 */
public class FixCheckSumMojo extends AbstractP2AppInvokerMojo {
	
	public static String PUBLISHER_BUNDLE_ID = "org.sonatype.tycho.p2.publisher";
	public static String RECREATE_REPOSITORY_APPLICATION = PUBLISHER_BUNDLE_ID + ".RecreateRepository";
	
	/**
	 * Set to true to force to enable this.
	 * When false, if enablePackAndSign is true then we do it anyways.
	 * @parameter default-value="false"
	 */
	private boolean recreateRepository;
	
	/**
	 * Path to the folder or zip file that is the signed and repacked repository
	 * eventually produced by some other mojos.<br/>
	 * For example: ${project.build.directory}/packed/${project.artifactId}-${project.version}.zip
	 * <p>
	 * When defined, renamed the current target/repository to target/repository-before-packAndSign
	 * then rename the signedAndRepackedTargetRepository to target/repository
	 * </p>
	 * @parameter 
	 */
	private String signedAndRepackedTargetRepository;

    /**
     * unpack path for the zipped signedAndRepackedTargetRepository.
     * If the folders are stored in a top-level folder inside signedAndRepackedTargetRepository
     * This is the path to it.
     * If null we assume that we can find plugins, features and binaries folder at the root of the zip.
     * @parameter
     */
	private String unpackPath;

	public void execute() throws MojoExecutionException, MojoFailureException {
		if (!recreateRepository && !enablePackAndSign)
		{
			return;
		}
		
		if (signedAndRepackedTargetRepository != null)
		{
			File signedRepo = new File(signedAndRepackedTargetRepository);
			if (!signedRepo.canRead())
			{
				throw new MojoExecutionException("Unable to find the signed repository " + signedAndRepackedTargetRepository);
			}
			//move the current repository:
			try
			{
				FileUtils.rename(targetRepository, new File(targetRepository.getParentFile(), targetRepository.getName() + "-not-signed"));
			}
			catch (IOException e)
			{
				throw new MojoExecutionException("Unable to archive the un-signed repository " + targetRepository.getAbsolutePath(), e);
			}
			targetRepository.mkdirs();
			if (signedRepo.isFile())
			{
				//unzip it in place.
				try
				{
					ZipUnArchiver unzipper = new ZipUnArchiver(signedRepo);
					unzipper.extract(unpackPath != null ? unpackPath : "", targetRepository);
				}
				catch (ArchiverException ae)
				{
					throw new MojoExecutionException("Unable to unzip the signed repository " + signedAndRepackedTargetRepository, ae);
				}
			}
			else
			{
				try
				{
					FileUtils.rename(signedRepo, targetRepository);
				}
				catch (IOException e)
				{
					throw new MojoExecutionException("Unable to rename the signed repository " + targetRepository.getAbsolutePath(), e);
				}
			}
		}
		//debug that indeed the RecreateRepository application does do stuff. (yes it does).
//		try {
//			FileUtils.copyFile(new File(targetRepository, "artifacts.jar"), new File(targetRepository, "artifacts.before-recreate.jar"));
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
		try
		{
			Commandline cli = super.getCommandLine(RECREATE_REPOSITORY_APPLICATION);
			cli.addArguments(new String[] {"-artifactRepository", targetRepository.toURI().toURL().toExternalForm()});
			super.execute(cli, null);
		}
		catch (IOException ioe)
		{
			throw new MojoExecutionException("Error invoking the RecreateRepository application", ioe);
		}
	}

	
}