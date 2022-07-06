package nu.studer.teamcity.buildscan.agent.maven

import jetbrains.buildServer.agent.AgentLifeCycleListener
import jetbrains.buildServer.util.EventDispatcher
import nu.studer.teamcity.buildscan.agent.BuildScanServiceMessageInjector
import nu.studer.teamcity.buildscan.agent.ExtensionApplicationListener
import nu.studer.teamcity.buildscan.agent.TcPluginConfig
import nu.studer.teamcity.buildscan.agent.TestBuildRunnerContext
import spock.lang.Specification
import spock.lang.TempDir

class BaseExtensionApplicationTest extends Specification {

    static final List<JdkCompatibleMavenVersion> SUPPORTED_MAVEN_VERSIONS = [
        new JdkCompatibleMavenVersion('3.5.0', 7, 11),
        new JdkCompatibleMavenVersion('3.5.4', 7, 11),
        new JdkCompatibleMavenVersion('3.6.0', 7, 11),
        new JdkCompatibleMavenVersion('3.6.3', 7, 11),
        new JdkCompatibleMavenVersion('3.8.1', 7, 11),
        new JdkCompatibleMavenVersion('3.8.6', 7, 11)
    ]

    @TempDir
    File checkoutDir

    @TempDir
    File agentTempDir

    @TempDir
    File agentMavenInstallation

    ExtensionApplicationListener extensionApplicationListener

    void setup() {
        extensionApplicationListener = Mock(ExtensionApplicationListener)
    }

    String run(String mavenVersion, Project project, TcPluginConfig tcPluginConfig) {
        run(mavenVersion, project, tcPluginConfig, new MavenBuildStepConfig(checkoutDir: checkoutDir))
    }

    String run(String mavenVersion, Project project, TcPluginConfig tcPluginConfig, MavenBuildStepConfig mavenBuildStepConfig) {
        def injector = new BuildScanServiceMessageInjector(EventDispatcher.create(AgentLifeCycleListener.class), extensionApplicationListener)

        def configParameters = tcPluginConfig.toConfigParameters()
        def runnerParameters = mavenBuildStepConfig.toRunnerParameters()

        def context = new TestBuildRunnerContext("Maven2", agentTempDir, configParameters, runnerParameters)
        injector.beforeRunnerStart(context)

        def runner = new MavenRunner(
            version: mavenVersion,
            installationDir: agentMavenInstallation,
            projectDir: new File(runnerParameters.get('teamcity.build.workingDir') ?: checkoutDir.absolutePath),
            multiModuleProjectDir: project.dotMvn.parentFile,
            arguments: ("${runnerParameters.get('goals')} ${runnerParameters.get('runnerArgs')}".toString().trim().split(/\s+/))
        )

        if (runnerParameters.containsKey('pomLocation')) {
            runner.arguments += ['-f', new File(runnerParameters.get('teamcity.build.checkoutDir'), runnerParameters.get('pomLocation')).absolutePath]
        }

        return runner.run()
    }

    static final class JdkCompatibleMavenVersion {

        final String mavenVersion
        private final Integer jdkMin
        private final Integer jdkMax

        JdkCompatibleMavenVersion(String mavenVersion, Integer jdkMin, Integer jdkMax) {
            this.mavenVersion = mavenVersion
            this.jdkMin = jdkMin
            this.jdkMax = jdkMax
        }

        boolean isJvmVersionCompatible() {
            def jvmVersion = getJvmVersion()
            jdkMin <= jvmVersion && jvmVersion <= jdkMax
        }

        private static int getJvmVersion() {
            String version = System.getProperty('java.version')
            if (version.startsWith('1.')) {
                Integer.parseInt(version.substring(2, 3))
            } else {
                Integer.parseInt(version.substring(0, version.indexOf('.')))
            }
        }

        @Override
        String toString() {
            return "JdkCompatibleMavenVersion{" +
                "Maven " + mavenVersion +
                ", JDK " + jdkMin + "-" + jdkMax +
                '}'
        }

    }

}