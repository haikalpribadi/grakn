/*
 * Copyright (C) 2021 Grakn Labs
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
 */

package grakn.core.server.rpc.concept;

import grakn.core.common.exception.GraknException;
import grakn.core.concept.ConceptManager;
import grakn.core.concept.thing.Thing;
import grakn.core.concept.type.AttributeType;
import grakn.core.concept.type.EntityType;
import grakn.core.concept.type.RelationType;
import grakn.core.concept.type.ThingType;
import grakn.core.server.rpc.TransactionRPC;
import grakn.protocol.ConceptProto;
import grakn.protocol.TransactionProto;
import grakn.protocol.TransactionProto.Transaction;

import static grakn.core.common.exception.ErrorMessage.Server.BAD_VALUE_TYPE;
import static grakn.core.common.exception.ErrorMessage.Server.UNKNOWN_REQUEST_TYPE;
import static grakn.core.server.rpc.common.ResponseBuilder.Concept.thing;
import static grakn.core.server.rpc.common.ResponseBuilder.Concept.type;

public class ConceptManagerHandler {

    private final TransactionRPC transactionRPC;
    private final ConceptManager conceptManager;

    public ConceptManagerHandler(TransactionRPC transactionRPC, ConceptManager conceptManager) {
        this.transactionRPC = transactionRPC;
        this.conceptManager = conceptManager;
    }

    public void handleRequest(Transaction.Req request) {
        ConceptProto.ConceptManager.Req conceptManagerReq = request.getConceptManagerReq();
        switch (conceptManagerReq.getReqCase()) {
            case GET_THING_TYPE_REQ:
                getThingType(request, conceptManagerReq.getGetThingTypeReq().getLabel());
                return;
            case GET_THING_REQ:
                getThing(request, conceptManagerReq.getGetThingReq().getIid().toByteArray());
                return;
            case PUT_ENTITY_TYPE_REQ:
                putEntityType(request, conceptManagerReq.getPutEntityTypeReq().getLabel());
                return;
            case PUT_ATTRIBUTE_TYPE_REQ:
                putAttributeType(request, conceptManagerReq.getPutAttributeTypeReq());
                return;
            case PUT_RELATION_TYPE_REQ:
                putRelationType(request, conceptManagerReq.getPutRelationTypeReq().getLabel());
                return;
            default:
            case REQ_NOT_SET:
                throw GraknException.of(UNKNOWN_REQUEST_TYPE);
        }
    }

    private static TransactionProto.Transaction.Res response(Transaction.Req request, ConceptProto.ConceptManager.Res.Builder res) {
        return TransactionProto.Transaction.Res.newBuilder().setId(request.getId()).setConceptManagerRes(res).build();
    }

    private void getThingType(Transaction.Req request, String label) {
        ThingType thingType = conceptManager.getThingType(label);
        ConceptProto.ConceptManager.GetThingType.Res.Builder getThingTypeRes = ConceptProto.ConceptManager.GetThingType.Res.newBuilder();
        if (thingType != null) getThingTypeRes.setThingType(type(thingType));
        transactionRPC.respond(response(request, ConceptProto.ConceptManager.Res.newBuilder().setGetThingTypeRes(getThingTypeRes)));
    }

    private void getThing(Transaction.Req request, byte[] iid) {
        Thing thing = conceptManager.getThing(iid);
        ConceptProto.ConceptManager.GetThing.Res.Builder getThingRes = ConceptProto.ConceptManager.GetThing.Res.newBuilder();
        if (thing != null) getThingRes.setThing(thing(thing));
        transactionRPC.respond(response(request, ConceptProto.ConceptManager.Res.newBuilder().setGetThingRes(getThingRes)));
    }

    private void putEntityType(Transaction.Req request, String label) {
        EntityType entityType = conceptManager.putEntityType(label);
        ConceptProto.ConceptManager.Res.Builder res = ConceptProto.ConceptManager.Res.newBuilder()
                .setPutEntityTypeRes(ConceptProto.ConceptManager.PutEntityType.Res.newBuilder().setEntityType(type(entityType)));
        transactionRPC.respond(response(request, res));
    }

    private void putAttributeType(Transaction.Req request, ConceptProto.ConceptManager.PutAttributeType.Req attributeTypeReq) {
        ConceptProto.AttributeType.ValueType valueTypeProto = attributeTypeReq.getValueType();
        AttributeType.ValueType valueType;
        switch (valueTypeProto) {
            case STRING:
                valueType = AttributeType.ValueType.STRING;
                break;
            case BOOLEAN:
                valueType = AttributeType.ValueType.BOOLEAN;
                break;
            case LONG:
                valueType = AttributeType.ValueType.LONG;
                break;
            case DOUBLE:
                valueType = AttributeType.ValueType.DOUBLE;
                break;
            case DATETIME:
                valueType = AttributeType.ValueType.DATETIME;
                break;
            case OBJECT:
            case UNRECOGNIZED:
            default:
                throw GraknException.of(BAD_VALUE_TYPE, valueTypeProto);
        }
        AttributeType attributeType = conceptManager.putAttributeType(attributeTypeReq.getLabel(), valueType);
        ConceptProto.ConceptManager.Res.Builder res = ConceptProto.ConceptManager.Res.newBuilder()
                .setPutAttributeTypeRes(ConceptProto.ConceptManager.PutAttributeType.Res.newBuilder().setAttributeType(type(attributeType)));
        transactionRPC.respond(response(request, res));
    }

    private void putRelationType(Transaction.Req request, String label) {
        RelationType relationType = conceptManager.putRelationType(label);
        ConceptProto.ConceptManager.Res.Builder res = ConceptProto.ConceptManager.Res.newBuilder()
                .setPutRelationTypeRes(ConceptProto.ConceptManager.PutRelationType.Res.newBuilder().setRelationType(type(relationType)));
        transactionRPC.respond(response(request, res));
    }

}
