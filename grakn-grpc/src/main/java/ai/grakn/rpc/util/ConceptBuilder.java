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

package ai.grakn.rpc.util;

import ai.grakn.concept.AttributeType;
import ai.grakn.concept.Concept;
import ai.grakn.concept.Role;
import ai.grakn.concept.Thing;
import ai.grakn.rpc.generated.GrpcConcept;
import ai.grakn.util.CommonUtil;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

/**
 * A utility class to build RPC Concepts by converting them from Grakn Concepts.
 *
 * @author Grakn Warriors
 */
public class ConceptBuilder {
    public static GrpcConcept.Concept concept(Concept concept) {
        return GrpcConcept.Concept.newBuilder()
                .setId(concept.getId().getValue())
                .setBaseType(getBaseType(concept))
                .build();
    }

    public static GrpcConcept.BaseType getBaseType(Concept concept) {
        if (concept.isEntityType()) {
            return GrpcConcept.BaseType.ENTITY_TYPE;
        } else if (concept.isRelationshipType()) {
            return GrpcConcept.BaseType.RELATIONSHIP_TYPE;
        } else if (concept.isAttributeType()) {
            return GrpcConcept.BaseType.ATTRIBUTE_TYPE;
        } else if (concept.isEntity()) {
            return GrpcConcept.BaseType.ENTITY;
        } else if (concept.isRelationship()) {
            return GrpcConcept.BaseType.RELATIONSHIP;
        } else if (concept.isAttribute()) {
            return GrpcConcept.BaseType.ATTRIBUTE;
        } else if (concept.isRole()) {
            return GrpcConcept.BaseType.ROLE;
        } else if (concept.isRule()) {
            return GrpcConcept.BaseType.RULE;
        } else if (concept.isType()) {
            return GrpcConcept.BaseType.META_TYPE;
        } else {
            throw CommonUtil.unreachableStatement("Unrecognised concept " + concept);
        }
    }

    public static GrpcConcept.RolePlayer rolePlayer(Role role, Thing player) {
        return GrpcConcept.RolePlayer.newBuilder()
                .setRole(ConceptBuilder.concept(role))
                .setPlayer(ConceptBuilder.concept(player))
                .build();
    }

    public static GrpcConcept.Concepts concepts(Stream<? extends Concept> concepts) {
        GrpcConcept.Concepts.Builder grpcConcepts = GrpcConcept.Concepts.newBuilder();
        grpcConcepts.addAllConcepts(concepts.map(ConceptBuilder::concept).collect(toList()));
        return grpcConcepts.build();
    }

    public static GrpcConcept.AttributeValue attributeValue(Object value) {
        GrpcConcept.AttributeValue.Builder builder = GrpcConcept.AttributeValue.newBuilder();
        if (value instanceof String) {
            builder.setString((String) value);
        } else if (value instanceof Boolean) {
            builder.setBoolean((boolean) value);
        } else if (value instanceof Integer) {
            builder.setInteger((int) value);
        } else if (value instanceof Long) {
            builder.setLong((long) value);
        } else if (value instanceof Float) {
            builder.setFloat((float) value);
        } else if (value instanceof Double) {
            builder.setDouble((double) value);
        } else if (value instanceof LocalDateTime) {
            builder.setDate(((LocalDateTime) value).atZone(ZoneId.of("Z")).toInstant().toEpochMilli());
        } else {
            throw CommonUtil.unreachableStatement("Unrecognised " + value);
        }

        return builder.build();
    }

    public static GrpcConcept.DataType dataType(AttributeType.DataType<?> dataType) {
        if (dataType.equals(AttributeType.DataType.STRING)) {
            return GrpcConcept.DataType.String;
        } else if (dataType.equals(AttributeType.DataType.BOOLEAN)) {
            return GrpcConcept.DataType.Boolean;
        } else if (dataType.equals(AttributeType.DataType.INTEGER)) {
            return GrpcConcept.DataType.Integer;
        } else if (dataType.equals(AttributeType.DataType.LONG)) {
            return GrpcConcept.DataType.Long;
        } else if (dataType.equals(AttributeType.DataType.FLOAT)) {
            return GrpcConcept.DataType.Float;
        } else if (dataType.equals(AttributeType.DataType.DOUBLE)) {
            return GrpcConcept.DataType.Double;
        } else if (dataType.equals(AttributeType.DataType.DATE)) {
            return GrpcConcept.DataType.Date;
        } else {
            throw CommonUtil.unreachableStatement("Unrecognised " + dataType);
        }
    }
}
