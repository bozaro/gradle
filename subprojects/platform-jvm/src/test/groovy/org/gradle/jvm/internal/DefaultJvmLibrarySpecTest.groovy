/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.jvm.internal

import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.language.base.FunctionalSourceSet
import org.gradle.language.base.LanguageSourceSet
import org.gradle.language.base.ProjectSourceSet
import org.gradle.language.base.internal.DefaultFunctionalSourceSet
import org.gradle.model.internal.fixture.ModelRegistryHelper
import org.gradle.platform.base.component.BaseComponentFixtures
import org.gradle.platform.base.internal.DefaultComponentSpecIdentifier
import spock.lang.Specification

class DefaultJvmLibrarySpecTest extends Specification {
    def libraryId = new DefaultComponentSpecIdentifier(":project-path", "jvm-lib")
    FunctionalSourceSet mainSourceSet

    def setup(){
        mainSourceSet = new DefaultFunctionalSourceSet("testFss", DirectInstantiator.INSTANCE, Stub(ProjectSourceSet));
    }

    def "library has name and path"() {
        when:
        def library = createJvmLibrarySpec()

        then:
        library.name == "jvm-lib"
        library.projectPath == ":project-path"
        library.displayName == "JVM library 'jvm-lib'"
    }

    def "contains sources of associated main sourceSet"() {
        when:
        def lss1 = languageSourceSet("lss1")
        mainSourceSet.add(lss1)

        and:
        def library = createJvmLibrarySpec()
        def lss2 = languageSourceSet("lss2")
        mainSourceSet.add(lss2)

        then:
        library.sources as List == [lss1, lss2]
    }

    private DefaultJvmLibrarySpec createJvmLibrarySpec() {
        BaseComponentFixtures.create(DefaultJvmLibrarySpec, new ModelRegistryHelper(), libraryId, mainSourceSet, DirectInstantiator.INSTANCE)
    }

    def languageSourceSet(String name) {
        Stub(LanguageSourceSet) {
            getName() >> name
        }
    }
}
