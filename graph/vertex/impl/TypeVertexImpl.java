/*
 * Copyright (C) 2020 Grakn Labs
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package grakn.core.graph.vertex.impl;

import grakn.core.common.iterator.ResourceIterator;
import grakn.core.common.parameters.Label;
import grakn.core.graph.SchemaGraph;
import grakn.core.graph.adjacency.SchemaAdjacency;
import grakn.core.graph.adjacency.impl.SchemaAdjacencyImpl;
import grakn.core.graph.iid.IndexIID;
import grakn.core.graph.iid.VertexIID;
import grakn.core.graph.util.Encoding;
import grakn.core.graph.vertex.TypeVertex;

import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static grakn.common.collection.Collections.list;
import static grakn.core.common.collection.Bytes.join;
import static grakn.core.common.iterator.Iterators.link;
import static grakn.core.graph.util.Encoding.Edge.Type.OWNS;
import static grakn.core.graph.util.Encoding.Edge.Type.OWNS_KEY;
import static grakn.core.graph.util.Encoding.Edge.Type.PLAYS;
import static grakn.core.graph.util.Encoding.Edge.Type.RELATES;
import static grakn.core.graph.util.Encoding.Property.ABSTRACT;
import static grakn.core.graph.util.Encoding.Property.LABEL;
import static grakn.core.graph.util.Encoding.Property.REGEX;
import static grakn.core.graph.util.Encoding.Property.SCOPE;
import static grakn.core.graph.util.Encoding.Property.VALUE_TYPE;
import static java.lang.Math.toIntExact;

public abstract class TypeVertexImpl extends SchemaVertexImpl<VertexIID.Type, Encoding.Vertex.Type> implements TypeVertex {

    protected String scope;
    protected Boolean isAbstract; // needs to be declared as the Boolean class
    protected Encoding.ValueType valueType;
    protected Pattern regex;

    private final AtomicInteger outOwnsCount;
    private final AtomicInteger outPlaysCount;
    private final AtomicInteger outRelatesCount;
    private final AtomicInteger inOwnsCount;
    private final AtomicInteger inPlaysCount;


    TypeVertexImpl(SchemaGraph graph, VertexIID.Type iid, String label, @Nullable String scope) {
        super(graph, iid, label);
        assert iid.isType();
        this.scope = scope;
        outOwnsCount = new AtomicInteger(-1);
        outPlaysCount = new AtomicInteger(-1);
        outRelatesCount = new AtomicInteger(-1);
        inOwnsCount = new AtomicInteger(-1);
        inPlaysCount = new AtomicInteger(-1);
    }

    public Encoding.Vertex.Type encoding() {
        return iid.encoding();
    }

    @Override
    public boolean isType() { return true; }

    @Override
    public TypeVertex asType() { return this; }

    @Override
    public String scope() {
        return scope;
    }

    @Override
    public String scopedLabel() {
        return Encoding.Vertex.Type.scopedLabel(label, scope);
    }

    @Override
    public Label properLabel() {
        return Label.of(label, scope);
    }

    @Override
    public int outOwnsCount(boolean isKey) {
        return edgeCount(outOwnsCount, () -> {
            if (isKey) return toIntExact(outs.edge(OWNS_KEY).to().stream().count());
            else return toIntExact(link(list(outs.edge(OWNS).to(), outs.edge(OWNS_KEY).to())).stream().count());
        });
    }

    @Override
    public int inOwnsCount(boolean isKey) {
        return edgeCount(inOwnsCount, () -> {
            if (isKey) return toIntExact(ins.edge(OWNS_KEY).from().stream().count());
            else return toIntExact(link(list(ins.edge(OWNS).from(), ins.edge(OWNS_KEY).from())).stream().count());
        });
    }

    @Override
    public int outPlaysCount() {
        return edgeCount(outPlaysCount, () -> toIntExact(outs.edge(PLAYS).to().stream().count()));
    }

    @Override
    public int inPlaysCount() {
        return edgeCount(inPlaysCount, () -> toIntExact(ins.edge(PLAYS).from().stream().count()));
    }

    @Override
    public int outRelatesCount() {
        return edgeCount(outRelatesCount, () -> toIntExact(outs.edge(RELATES).to().stream().count()));
    }

    private int edgeCount(AtomicInteger cache, Supplier<Integer> function) {
        if (graph.isReadOnly()) {
            cache.compareAndSet(-1, function.get());
            return cache.get();
        } else {
            return function.get();
        }
    }

    void deleteVertexFromGraph() {
        graph.delete(this);
    }

    public static class Buffered extends TypeVertexImpl {

        private final AtomicBoolean isCommitted;

        public Buffered(SchemaGraph graph, VertexIID.Type iid, String label, @Nullable String scope) {
            super(graph, iid, label, scope);
            this.isCommitted = new AtomicBoolean(false);
            setModified();
        }

        @Override
        protected SchemaAdjacency newAdjacency(Encoding.Direction direction) {
            return new SchemaAdjacencyImpl.Buffered(this, direction);
        }

        @Override
        public void label(String label) {
            graph.update(this, this.label, scope, label, scope);
            this.label = label;
        }

        @Override
        public void scope(String scope) {
            graph.update(this, label, this.scope, label, scope);
            this.scope = scope;
        }

        @Override
        public Encoding.Status status() {
            return isCommitted.get() ? Encoding.Status.COMMITTED : Encoding.Status.BUFFERED;
        }

        @Override
        public boolean isAbstract() {
            return isAbstract != null ? isAbstract : false;
        }

        @Override
        public TypeVertexImpl isAbstract(boolean isAbstract) {
            this.isAbstract = isAbstract;
            this.setModified();
            return this;
        }

        @Override
        public Encoding.ValueType valueType() {
            return valueType;
        }

        @Override
        public TypeVertexImpl valueType(Encoding.ValueType valueType) {
            this.valueType = valueType;
            this.setModified();
            return this;
        }

        @Override
        public Pattern regex() {
            return regex;
        }

        @Override
        public TypeVertexImpl regex(Pattern regex) {
            this.regex = regex;
            this.setModified();
            return this;
        }

        @Override
        public void delete() {
            if (isDeleted.compareAndSet(false, true)) {
                deleteEdges();
                deleteVertexFromGraph();
            }
        }

        @Override
        public void commit() {
            if (isCommitted.compareAndSet(false, true)) {
                commitVertex();
                commitProperties();
                commitEdges();
            }
        }

        private void commitVertex() {
            graph.storage().put(iid.bytes());
            graph.storage().put(IndexIID.Type.of(label, scope).bytes(), iid.bytes());
        }

        private void commitProperties() {
            commitPropertyLabel();
            if (scope != null) commitPropertyScope();
            if (isAbstract != null && isAbstract) commitPropertyAbstract();
            if (valueType != null) commitPropertyValueType();
            if (regex != null) commitPropertyRegex();
        }

        private void commitPropertyScope() {
            graph.storage().put(join(iid.bytes(), SCOPE.infix().bytes()), scope.getBytes());
        }

        private void commitPropertyAbstract() {
            graph.storage().put(join(iid.bytes(), ABSTRACT.infix().bytes()));
        }

        private void commitPropertyLabel() {
            graph.storage().put(join(iid.bytes(), LABEL.infix().bytes()), label.getBytes());
        }

        private void commitPropertyValueType() {
            graph.storage().put(join(iid.bytes(), VALUE_TYPE.infix().bytes()), valueType.bytes());
        }

        private void commitPropertyRegex() {
            graph.storage().put(join(iid.bytes(), REGEX.infix().bytes()), regex.pattern().getBytes());
        }
    }

    public static class Persisted extends TypeVertexImpl {

        private boolean regexLookedUp;

        public Persisted(SchemaGraph graph, VertexIID.Type iid, String label, @Nullable String scope) {
            super(graph, iid, label, scope);
            regexLookedUp = false;
        }

        public Persisted(SchemaGraph graph, VertexIID.Type iid) {
            super(graph, iid,
                  new String(graph.storage().get(join(iid.bytes(), LABEL.infix().bytes()))),
                  getScope(graph, iid));
        }

        @Nullable
        private static String getScope(SchemaGraph graph, VertexIID.Type iid) {
            final byte[] scopeBytes = graph.storage().get(join(iid.bytes(), SCOPE.infix().bytes()));
            if (scopeBytes != null) return new String(scopeBytes);
            else return null;
        }

        @Override
        protected SchemaAdjacency newAdjacency(Encoding.Direction direction) {
            return new SchemaAdjacencyImpl.Persisted(this, direction);
        }

        @Override
        public Encoding.Status status() {
            return Encoding.Status.PERSISTED;
        }

        @Override
        public void label(String label) {
            graph.update(this, this.label, scope, label, scope);
            graph.storage().put(join(iid.bytes(), LABEL.infix().bytes()), label.getBytes());
            graph.storage().delete(IndexIID.Type.of(this.label, scope).bytes());
            graph.storage().put(IndexIID.Type.of(label, scope).bytes(), iid.bytes());
            this.label = label;
        }

        @Override
        public void scope(String scope) {
            graph.update(this, label, this.scope, label, scope);
            graph.storage().put(join(iid.bytes(), SCOPE.infix().bytes()), scope.getBytes());
            graph.storage().delete(IndexIID.Type.of(label, this.scope).bytes());
            graph.storage().put(IndexIID.Type.of(label, scope).bytes(), iid.bytes());
            this.scope = scope;
        }

        @Override
        public boolean isAbstract() {
            if (isAbstract != null) return isAbstract;
            final byte[] flag = graph.storage().get(join(iid.bytes(), ABSTRACT.infix().bytes()));
            isAbstract = flag != null;
            return isAbstract;
        }

        @Override
        public TypeVertexImpl isAbstract(boolean isAbstract) {
            if (isAbstract) graph.storage().put(join(iid.bytes(), ABSTRACT.infix().bytes()));
            else graph.storage().delete(join(iid.bytes(), ABSTRACT.infix().bytes()));
            this.isAbstract = isAbstract;
            this.setModified();
            return this;
        }

        @Override
        public Encoding.ValueType valueType() {
            if (valueType != null) return valueType;
            final byte[] val = graph.storage().get(join(iid.bytes(), VALUE_TYPE.infix().bytes()));
            if (val != null) valueType = Encoding.ValueType.of(val[0]);
            return valueType;
        }

        @Override
        public TypeVertexImpl valueType(Encoding.ValueType valueType) {
            graph.storage().put(join(iid.bytes(), VALUE_TYPE.infix().bytes()), valueType.bytes());
            this.valueType = valueType;
            this.setModified();
            return this;
        }

        @Override
        public Pattern regex() {
            if (regexLookedUp) return regex;
            final byte[] val = graph.storage().get(join(iid.bytes(), REGEX.infix().bytes()));
            if (val != null) regex = Pattern.compile(new String(val));
            regexLookedUp = true;
            return regex;
        }

        @Override
        public TypeVertexImpl regex(Pattern regex) {
            if (regex == null) graph.storage().delete(join(iid.bytes(), REGEX.infix().bytes()));
            else graph.storage().put(join(iid.bytes(), REGEX.infix().bytes()), regex.pattern().getBytes());
            this.regex = regex;
            this.setModified();
            return this;
        }

        @Override
        public void commit() {
            commitEdges();
        }

        @Override
        public void delete() {
            if (isDeleted.compareAndSet(false, true)) {
                deleteEdges();
                deleteVertexFromGraph();
                deleteVertexFromStorage();
            }
        }

        private void deleteVertexFromStorage() {
            graph.storage().delete(IndexIID.Type.of(label, scope).bytes());
            final ResourceIterator<byte[]> keys = graph.storage().iterate(iid.bytes(), (iid, value) -> iid);
            while (keys.hasNext()) graph.storage().delete(keys.next());
        }
    }
}
