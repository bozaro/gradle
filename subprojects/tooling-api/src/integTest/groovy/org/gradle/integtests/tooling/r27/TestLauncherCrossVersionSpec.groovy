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

package org.gradle.integtests.tooling.r27
import org.gradle.integtests.tooling.TestLauncherSpec
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.TestLauncher

@ToolingApiVersion(">=2.7")
@TargetGradleVersion(">=2.7")
class TestLauncherCrossVersionSpec extends TestLauncherSpec {

    def "can specify test by class and method name"() {
        when:
        launchTests { TestLauncher launcher ->
            launcher.withJvmTestMethods("example.MyTest", "foo")
        }
        then:

        assertTestExecuted(className: "example.MyTest", methodName: "foo", task: ":test")
        assertTestExecuted(className: "example.MyTest", methodName: "foo", task: ":secondTest")

        assertTestNotExecuted(className: "example.MyTest", methodName: "foo2", task: ":secondTest")
        assertTestNotExecuted(className: "example.MyTest", methodName: "foo2", task: ":test")
    }
}
