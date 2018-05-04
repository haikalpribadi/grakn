/*
 * Grakn - A Distributed Semantic Database
 * Copyright (C) 2016-2018 Grakn Labs Limited
 *
 * Grakn is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Grakn is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Grakn. If not, see <http://www.gnu.org/licenses/agpl.txt>.
 */

package ai.grakn.util;

import ai.grakn.concept.ConceptId;

/**
 * Graql syntax keywords
 *
 * @author Haikal Pribadi
 */
public class GraqlSyntax {

    public static final String MATCH = "match";


    // Graql Queries
    public static final String COMPUTE = "compute";


    // Miscellaneous
    public static final String EQUAL = "=";
    public static final String SEMICOLON = ";";
    public static final String SPACE = " ";
    public static final String COMMA = ",";
    public static final String COMMA_SPACE = ", ";
    public static final String SQUARE_OPEN = "[";
    public static final String SQUARE_CLOSE = "]";
    public static final String QUOTE = "\"";

    /**
     * Graql Compute syntax keyword
     */
    public static class Compute {
        

        public enum Method {
            COUNT("setNumber"),
            MIN("min"),
            MAX("max"),
            MEDIAN("median"),
            MEAN("mean"),
            STD("std"),
            SUM("sum"),
            PATH("path"),
            CENTRALITY("centrality"),
            CLUSTER("cluster");

            private final String method;

            Method(String algorithm) {
                this.method = algorithm;
            }

            @Override
            public String toString(){
                return this.method;
            }
        }
        /**
         * Graql Compute conditions keyword
         */
        public enum Condition {
            FROM("from"),
            TO("to"),
            OF("of"),
            IN("in"),
            USING("using"),
            WHERE("where");

            private final String condition;

            Condition(String algorithm) {
                this.condition = algorithm;
            }

            @Override
            public String toString(){
                return this.condition;
            }
        }

        /**
         * Graql Compute algorithm names
         */
        public enum Algorithm {
            DEGREE("degree"),
            K_CORE("k-core"),
            CONNECTED_COMPONENT("connected-component");

            private final String algorithm;

            Algorithm(String algorithm) {
                this.algorithm = algorithm;
            }

            @Override
            public String toString(){
                return this.algorithm;
            }
        }

        //TODO: Move this class over into ComputeQuery (nested) once we replace Graql interfaces with classes
        public static class Argument<T> {
            /**
             * Graql Compute argument keywords
             */
            public enum Type {
                MIN_K("min-k"),
                K("k"),
                CONTAINS("contains"),
                MEMBERS("members"),
                SIZE("size");

                private final String arg;

                Type(String arg) {
                    this.arg = arg;
                }

                @Override
                public String toString(){
                    return this.arg;
                }
            }

            public final static long DEFAULT_MIN_K = 2L;
            public final static long DEFAULT_K = 2L;
            public final static boolean DEFAULT_MEMBERS = false;

            private Type type;
            private T arg;

            private Argument(Type type, T arg) {
                this.type = type;
                this.arg = arg;
            }

            public final Type type() {return this.type;}

            public final T get() {return this.arg;}

            public static Argument<Long> min_k(long minK) { return new Argument<>(Type.MIN_K, minK); }

            public static Argument<Long> k(long k) {
                return new Argument<>(Type.K, k);
            }

            public static Argument<Long> size(long size) { return new Argument<>(Type.SIZE, size); }

            public static Argument<Boolean> members(boolean members) { return new Argument<>(Type.MEMBERS, members); }

            public static Argument<ConceptId> contains(ConceptId conceptId) {
                return new Argument<>(Type.CONTAINS, conceptId);
            }

            @Override
            public String toString() {
                return type + EQUAL + arg.toString();
            }
        }
    }
}