/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.integtests.tooling.r26

import org.gradle.api.GradleException
import org.gradle.integtests.tooling.TestLauncherSpec
import org.gradle.integtests.tooling.fixture.*
import org.gradle.tooling.*
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.events.test.TestOperationDescriptor
import org.gradle.tooling.exceptions.UnsupportedBuildArgumentException
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

@ToolingApiVersion(">=2.6")
@TargetGradleVersion(">=1.0-milestone-8")
class TestLauncherCrossVersionSpec extends TestLauncherSpec {

    @TargetGradleVersion(">=2.6")
    def "test launcher api fires progress events"() {
        given:
        collectDescriptorsFromBuild()
        when:
        launchTests(testDescriptors("example.MyTest"));
        then:
        events.assertIsABuild()
        events.operation("Task :compileJava").successful
        events.operation("Task :processResources").successful
        events.operation("Task :classes").successful
        events.operation("Task :compileTestJava").successful
        events.operation("Task :processTestResources").successful
        events.operation("Task :testClasses").successful
        events.operation("Task :test").successful
        events.operation("Task :secondTest").successful

        events.operation("Gradle Test Run :test").successful
        events.operation("Gradle Test Executor 1").successful
        events.operation("Gradle Test Run :secondTest").successful
        events.operation("Gradle Test Executor 2").successful
        events.tests.findAll { it.descriptor.displayName == "Test class example.MyTest" }.size() == 2
        events.tests.findAll { it.descriptor.displayName == "Test foo(example.MyTest)" }.size() == 2
        events.tests.findAll { it.descriptor.displayName == "Test foo2(example.MyTest)" }.size() == 2
        events.tests.findAll { it.descriptor.displayName == "Test foo2(example.MyTest)" }.size() == 2
        events.tests.findAll { it.descriptor.displayName == "Test class example2.MyOtherTest" }.size() == 2
    }

    @TargetGradleVersion(">=2.6")
    def "can run specific test class passed via test descriptor"() {
        given:
        collectDescriptorsFromBuild()
        when:
        launchTests(testDescriptors("example.MyTest"));
        then:
        assertTaskExecuted(":test")
        assertTaskExecuted(":secondTest")

        assertTestExecuted(className: "example.MyTest", methodName: "foo", task: ":test")
        assertTestExecuted(className: "example.MyTest", methodName: "foo", task: ":secondTest")
        assertTestExecuted(className: "example.MyTest", methodName: "foo2", task: ":test")
        assertTestExecuted(className: "example.MyTest", methodName: "foo2", task: ":secondTest")
        assertTestExecuted(className: "example2.MyOtherTest", methodName: null) // TODO clarify if this is by design

        assertTestNotExecuted(className: "example2.MyOtherTest", methodName: "bar", task: ":test")
        assertTestNotExecuted(className: "example2.MyOtherTest", methodName: "bar", task: ":secondTest")
    }

    @TargetGradleVersion(">=2.6")
    def "can run specific test method passed via test descriptor"() {
        given:
        collectDescriptorsFromBuild()
        when:
        launchTests(testDescriptors("example.MyTest", "foo"));
        then:
        assertTaskExecuted(":test")
        assertTaskExecuted(":secondTest")

        assertTestExecuted(className: "example.MyTest", methodName: "foo", task: ":test")
        assertTestExecuted(className: "example.MyTest", methodName: "foo", task: ":secondTest")

        assertTestNotExecuted(className: "example.MyTest", methodName: "foo2", task: ":secondTest")
        assertTestNotExecuted(className: "example.MyTest", methodName: "foo2", task: ":test")
    }

    @TargetGradleVersion(">=2.6")
    def "runs only test task linked in test descriptor"() {
        given:
        collectDescriptorsFromBuild()
        when:
        launchTests(testDescriptors("example.MyTest", null, ":secondTest"));
        then:
        assertTaskExecuted(":secondTest")
        assertTaskNotExecuted(":test")

        assertTestExecuted(className: "example.MyTest", methodName: "foo", task: ":secondTest")
        assertTestExecuted(className: "example.MyTest", methodName: "foo2", task: ":secondTest")

        assertTestNotExecuted(className: "example.MyTest", methodName: "foo", task: ":test")
        assertTestNotExecuted(className: "example.MyTest", methodName: "foo2", task: ":test")
    }

    @TargetGradleVersion(">=2.6")
    def "tests can be executed multiple times without task being up-to-date"() {
        given:
        collectDescriptorsFromBuild()
        and:
        launchTests(testDescriptors("example.MyTest", null, ":secondTest"))
        when:
        launchTests(testDescriptors("example.MyTest", null, ":secondTest"));
        then:
        assertTaskNotUpToDate(":secondTest")
        assertTestExecuted(className: "example.MyTest", methodName: "foo", task: ":secondTest")
        assertTestExecuted(className: "example.MyTest", methodName: "foo2", task: ":secondTest")
        assertTaskNotExecuted(":test")
    }

    @TargetGradleVersion(">=2.6")
    @Requires(TestPrecondition.JDK7_OR_LATER)
    def "can run and cancel testlauncher in continuous mode"() {
        given:
        collectDescriptorsFromBuild()
        when:
        withConnection {
            def cancellationTokenSource = GradleConnector.newCancellationTokenSource()
            launchTests(it, new TestResultHandler(), cancellationTokenSource) { TestLauncher launcher ->
                def testsToLaunch = testDescriptors("example.MyTest", null, ":secondTest")
                launcher
                    .withTests(testsToLaunch.toArray(new TestOperationDescriptor[testsToLaunch.size()]))
                    .withArguments("-t")
            }
            waitingForBuild()
            assertTaskExecuted(":secondTest")
            assertTaskNotExecuted(":test")
            assertTestExecuted(className: "example.MyTest", methodName: "foo", task: ":secondTest")
            assertTestExecuted(className: "example.MyTest", methodName: "foo2", task: ":secondTest")
            assertTestNotExecuted(className: "example.MyTest", methodName: "foo3", task: ":secondTest")
            assertTestNotExecuted(className: "example.MyTest", methodName: "foo4", task: ":secondTest")
            events.clear()
            changeTestSource()
            waitingForBuild()

            cancellationTokenSource.cancel()
        }

        then:
        assertBuildCancelled()
        assertTaskExecuted(":secondTest")
        assertTaskNotExecuted(":test")
        assertTestExecuted(className: "example.MyTest", methodName: "foo", task: ":secondTest")
        assertTestExecuted(className: "example.MyTest", methodName: "foo2", task: ":secondTest")
        assertTestExecuted(className: "example.MyTest", methodName: "foo3", task: ":secondTest")
        assertTestExecuted(className: "example.MyTest", methodName: "foo4", task: ":secondTest")
    }

    @TargetGradleVersion(">=2.6")
    def "listener errors are rethrown on client side"() {
        given:
        collectDescriptorsFromBuild()
        def descriptors = testDescriptors("example.MyTest")
        def failingProgressListener = failingProgressListener()
        when:
        withConnection { ProjectConnection connection ->
            def testLauncher = connection.newTestLauncher()
            testLauncher.addProgressListener(failingProgressListener)
            testLauncher.withTests(descriptors.toArray(new TestOperationDescriptor[descriptors.size()]))
            testLauncher.run()
        };
        then:
        def e = thrown(ListenerFailedException)
        e.cause.message == "failing progress listener"
    }

    @TargetGradleVersion(">=2.6")
    def "fails with meaningful error when no tests declared"() {
        when:
        launchTests([])
        then:
        def e = thrown(TestExecutionException)
        e.message == "No test declared for execution."
    }

    @TargetGradleVersion(">=2.6")
    def "fails with meaningful error when declared class has not tests"() {
        given:
        file("src/test/java/util/TestUtil.java") << """
            package util;
            public class TestUtil {
                static void someUtilsMethod(){}
            }
        """
        when:
        launchTests { TestLauncher launcher ->
            launcher.withJvmTestClasses("util.TestUtil")
        }
        then:
        def e = thrown(TestExecutionException)
        e.cause.message == "Tests configured in TestLauncher not found in any candidate test task."
    }

    @TargetGradleVersion(">=2.6")
    def "fails with meaningful error when test no longer exists"() {
        given:
        collectDescriptorsFromBuild()
        and:
        testClassRemoved()
        when:
        launchTests(testDescriptors("example.MyTest", null, ":test"));
        then:
        assertTaskExecuted(":test")
        assertTaskNotExecuted(":secondTest")

        def e = thrown(TestExecutionException)
        e.cause.message == "No tests found for given includes: [example.MyTest.*]"
    }

    @TargetGradleVersion(">=2.6")
    def "build succeeds if test class is only available in one test task"() {
        given:
        file("src/moreTests/java/more/MoreTest.java") << """
            package more;
            public class MoreTest {
                @org.junit.Test public void bar() throws Exception {
                     org.junit.Assert.assertEquals(2, 2);
                }
            }
        """
        when:
        launchTests { TestLauncher launcher ->
            launcher.withJvmTestClasses("more.MoreTest")
        }
        then:
        assertTaskExecuted(":secondTest")
        assertTaskExecuted(":test")
    }

    @TargetGradleVersion(">=2.6")
    def "fails with meaningful error when test class not available for any test task"() {
        when:
        withConnection { ProjectConnection connection ->
            def testLauncher = connection.newTestLauncher()
            testLauncher.withJvmTestClasses("org.acme.NotExistingTestClass")
            testLauncher.run()
        };
        then:
        assertTaskNotExecuted(":test")
        assertTaskNotExecuted(":secondTest")

        def e = thrown(TestExecutionException)
        e.cause.message == "Tests configured in TestLauncher not found in any candidate test task."
    }

    @TargetGradleVersion(">=2.6")
    def "fails with meaningful error when test task no longer exists"() {
        given:
        collectDescriptorsFromBuild()
        and:
        buildFile.text = simpleJavaProject()
        when:
        launchTests(testDescriptors("example.MyTest", null, ":secondTest"));
        then:
        assertTaskNotExecuted(":secondTest")
        assertTaskNotExecuted(":test")

        def e = thrown(TestExecutionException)
        e.cause.message == "Requested test task with path ':secondTest' cannot be found."
    }

    @TargetGradleVersion(">=1.0-milestone-8 <2.6")
    def "fails with meaningful error when running against unsupported target version"() {
        when:
        withConnection { ProjectConnection connection ->
            connection.newTestLauncher().withJvmTestClasses("org.acme.Test").run()
        }

        then:
        def e = thrown(UnsupportedVersionException)
        e.message == "The version of Gradle you are using (${targetDist.version.version}) does not support the TestLauncher API. Support for this is available in Gradle 2.6 and all later versions."
    }

    @TargetGradleVersion(">=2.6")
    def "fails with meaningful error when passing invalid arguments"() {
        when:
        launchTests { TestLauncher launcher ->
            launcher.withJvmTestClasses("example.MyTest")
                .withArguments("--someInvalidArgument")
        }
        then:
        def e = thrown(UnsupportedBuildArgumentException)
        e.message.contains("Unknown command-line option '--someInvalidArgument'.")
    }

    @TargetGradleVersion(">=2.6")
    def "fails with BuildException when build fails"() {
        given:
        buildFile << "some invalid build code"
        when:
        launchTests { TestLauncher launcher ->
            launcher.withJvmTestClasses("example.MyTest")
        }
        then:
        thrown(BuildException)
    }

    @TargetGradleVersion(">=2.6")
    def "throws BuildCancelledException when build canceled"() {
        given:
        buildFile << "some invalid build code"
        when:
        launchTests { TestLauncher launcher ->
            launcher.withJvmTestClasses("example.MyTest")

            def tokenSource = GradleConnector.newCancellationTokenSource()
            launcher.withCancellationToken(tokenSource.token())
            tokenSource.cancel()
        }
        then:
        thrown(BuildCancelledException)
    }

    @TargetGradleVersion(">=2.6")
    def "can execute test class passed by name"() {
        when:
        launchTests { TestLauncher testLauncher ->
            testLauncher.withJvmTestClasses("example.MyTest")
        }
        then:
        assertTaskExecuted(":test")
        assertTaskExecuted(":secondTest")

        assertTestExecuted(className: "example.MyTest", methodName: "foo", task: ":test")
        assertTestExecuted(className: "example.MyTest", methodName: "foo", task: ":secondTest")
        assertTestExecuted(className: "example.MyTest", methodName: "foo2", task: ":test")
        assertTestExecuted(className: "example.MyTest", methodName: "foo2", task: ":secondTest")

        assertTestNotExecuted(className: "example2.MyOtherTest", methodName: "bar", task: ":test")
        assertTestNotExecuted(className: "example2.MyOtherTest", methodName: "bar", task: ":secondTest")
    }

    @TargetGradleVersion(">=2.6")
    def "can execute multiple test classes passed by name"() {
        setup: "add testcase that should not be exeucted"
        file("src/test/java/example/MyFailingTest.java") << """
            package example;
            public class MyFailingTest {
                @org.junit.Test public void failing1() throws Exception {
                     org.junit.Assert.assertEquals(1, 2);
                }
            }
        """

        when:
        launchTests { TestLauncher testLauncher ->
            testLauncher.withJvmTestClasses("example.MyTest")
            testLauncher.withJvmTestClasses("example2.MyOtherTest")
        }
        then:
        assertTaskExecuted(":test")
        assertTaskExecuted(":secondTest")

        assertTestExecuted(className: "example.MyTest", methodName: "foo", task: ":test")
        assertTestExecuted(className: "example.MyTest", methodName: "foo", task: ":secondTest")
        assertTestExecuted(className: "example.MyTest", methodName: "foo2", task: ":test")
        assertTestExecuted(className: "example.MyTest", methodName: "foo2", task: ":secondTest")
        assertTestExecuted(className: "example2.MyOtherTest", methodName: "bar", task: ":test")
        assertTestExecuted(className: "example2.MyOtherTest", methodName: "bar", task: ":secondTest")

        assertTestNotExecuted(className: "example.MyFailingTest", methodName: "failing1", task: ":test")
        assertTestNotExecuted(className: "example.MyFailingTest", methodName: "failing1", task: ":secondTest")
    }

    @TargetGradleVersion(">=2.6")
    def "runs all test tasks in multi project build when test class passed by name"() {
        setup:
        settingsFile << "include ':sub1', 'sub2', ':sub2:sub3', ':sub4'"
        ["sub1", "sub2/sub3"].each { projectFolderName ->
            file("${projectFolderName}/src/test/java/example/MyTest.java") << """
                package example;
                public class MyTest {
                    @org.junit.Test public void foo() throws Exception {
                         org.junit.Assert.assertEquals(1, 1);
                    }
                }
            """
        }

        file("sub2/src/test/java/example2/MyOtherTest.java") << """
            package example2;
            public class MyOtherTest {
                @org.junit.Test public void bar() throws Exception {
                     org.junit.Assert.assertEquals(1, 1);
                }
            }
            """
        when:
        launchTests { TestLauncher testLauncher ->
            testLauncher.withJvmTestClasses("example.MyTest")
            testLauncher.withJvmTestClasses("example2.MyOtherTest")
        }
        then:
        assertTaskExecuted(":test")
        assertTaskExecuted(":secondTest")
        assertTaskExecuted(":sub1:test")
        assertTaskExecuted(":sub2:test")
        assertTaskExecuted(":sub2:sub3:test")
        assertTaskExecuted(":sub4:test")

        assertTestExecuted(className: "example.MyTest", methodName: "foo", task: ":test")
        assertTestExecuted(className: "example.MyTest", methodName: "foo", task: ":secondTest")
        assertTestExecuted(className: "example.MyTest", methodName: "foo2", task: ":test")
        assertTestExecuted(className: "example.MyTest", methodName: "foo2", task: ":secondTest")
        assertTestExecuted(className: "example2.MyOtherTest", methodName: "bar", task: ":test")
        assertTestExecuted(className: "example2.MyOtherTest", methodName: "bar", task: ":secondTest")
        assertTestExecuted(className: "example.MyTest", methodName: "foo", task: ":sub1:test")
        assertTestExecuted(className: "example2.MyOtherTest", methodName: "bar", task: ":sub2:test")
        assertTestExecuted(className: "example.MyTest", methodName: "foo", task: ":sub2:sub3:test")
        assertTestExecuted(className: "example.MyTest", methodName: "foo2", task: ":secondTest")
    }

    @TargetGradleVersion(">=2.6")
    def "compatible with configure on demand"() {
        setup:
        10.times {
            settingsFile << "include ':sub$it'\n"
            file("sub$it/src/test/java/example/MyTest.java") << """
                package example;
                public class MyTest {
                    @org.junit.Test public void foo() throws Exception {
                         org.junit.Assert.assertEquals(1, 1);
                    }
                }
            """
        }
        when:
        launchTests { TestLauncher testLauncher ->
            testLauncher.withArguments("--configure-on-demand")
            testLauncher.withJvmTestClasses("example.MyTest")
        }
        then:
        assertTaskExecuted(":test")
        assertTaskExecuted(":sub0:test")
        assertTaskExecuted(":sub1:test")
        assertTaskExecuted(":sub2:test")
        assertTaskExecuted(":sub3:test")
        assertTaskExecuted(":sub4:test")
        assertTaskExecuted(":sub5:test")
        assertTaskExecuted(":sub6:test")
        assertTaskExecuted(":sub7:test")
        assertTaskExecuted(":sub8:test")
        assertTaskExecuted(":sub9:test")
    }

    ProgressListener failingProgressListener() {
        new ProgressListener() {
            @Override
            void statusChanged(ProgressEvent event) {
                throw new GradleException("failing progress listener")
            }
        }
    }
}
