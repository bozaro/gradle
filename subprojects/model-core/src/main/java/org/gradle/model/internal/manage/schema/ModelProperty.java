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

package org.gradle.model.internal.manage.schema;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import net.jcip.annotations.ThreadSafe;
import org.gradle.internal.Cast;
import org.gradle.model.internal.method.WeaklyTypeReferencingMethod;
import org.gradle.model.internal.type.ModelType;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

@ThreadSafe
public class ModelProperty<T> {

    private final String name;
    private final ModelType<T> type;
    private final boolean managed;
    private final boolean writable;
    private final Set<ModelType<?>> declaredBy;
    private final Map<Class<? extends Annotation>, Annotation> annotations;
    private final Map<Class<? extends Annotation>, Annotation> setterAnnotations;
    private final WeaklyTypeReferencingMethod<?, T> getter;

    private ModelProperty(ModelType<T> type, String name, boolean managed, boolean writable, Set<ModelType<?>> declaredBy, WeaklyTypeReferencingMethod<?, T> getter, Map<Class<? extends Annotation>, Annotation> annotations, Map<Class<? extends Annotation>, Annotation> setterAnnotations) {
        this.name = name;
        this.type = type;
        this.managed = managed;
        this.writable = writable;
        this.declaredBy = ImmutableSet.copyOf(declaredBy);
        this.getter = getter;
        this.annotations = ImmutableMap.copyOf(annotations);
        this.setterAnnotations = ImmutableMap.copyOf(setterAnnotations);
    }

    public static <T> ModelProperty<T> of(ModelType<T> type, String name, boolean managed, boolean writable, Set<ModelType<?>> declaredBy, WeaklyTypeReferencingMethod<?, T> getter, Map<Class<? extends Annotation>, Annotation> annotations, Map<Class<? extends Annotation>, Annotation> setterAnnotations) {
        return new ModelProperty<T>(type, name, managed, writable, declaredBy, getter, annotations, setterAnnotations);
    }

    public String getName() {
        return name;
    }

    public ModelType<T> getType() {
        return type;
    }

    /**
     * Returns whether the state of the property is managed or not.
     */
    public boolean isManaged() {
        return managed;
    }

    public boolean isWritable() {
        return writable;
    }

    public Set<ModelType<?>> getDeclaredBy() {
        return declaredBy;
    }

    @SuppressWarnings("unchecked")
    public <I> T getPropertyValue(I instance) {
        Preconditions.checkNotNull(instance, "instance");
        return ((WeaklyTypeReferencingMethod<I, T>) getter).invoke(instance);
    }

    public boolean isAnnotationPresent(Class<? extends Annotation> annotationType) {
        return annotations.containsKey(annotationType);
    }

    public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
        return Cast.uncheckedCast(annotations.get(annotationType));
    }

    public Collection<Annotation> getAnnotations() {
        return annotations.values();
    }

    /*
     * Only stored so that validators can access them and complain about.
     */
    public Map<Class<? extends Annotation>, Annotation> getSetterAnnotations() {
        return setterAnnotations;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ModelProperty<?> that = (ModelProperty<?>) o;

        return name.equals(that.name) && type.equals(that.type) && writable == that.writable;
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + type.hashCode();
        result = 31 * result + Boolean.valueOf(writable).hashCode();
        return result;
    }

    @Override
    public String toString() {
        return (managed ? "" : "unmanaged ") + getName() + "(" + getType().getSimpleName() + ")";
    }
}
