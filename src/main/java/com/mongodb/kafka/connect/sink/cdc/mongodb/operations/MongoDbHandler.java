/*
 * Copyright 2008-present MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Original Work: Apache License, Version 2.0, Copyright 2017 Hans-Peter Grahsl.
 */

package com.mongodb.kafka.connect.sink.cdc.mongodb.operations;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.kafka.connect.errors.DataException;
import org.bson.BsonDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.client.model.WriteModel;
import com.mongodb.kafka.connect.sink.MongoSinkTopicConfig;
import com.mongodb.kafka.connect.sink.cdc.CdcOperation;
import com.mongodb.kafka.connect.sink.cdc.mongodb.MongoDBCdcHandler;
import com.mongodb.kafka.connect.sink.cdc.mongodb.OperationType;
import com.mongodb.kafka.connect.sink.converter.SinkDocument;

public class MongoDbHandler extends MongoDBCdcHandler {
    static final String ID_FIELD = "_id";
    static final String JSON_ID_FIELD = "_id";
    private static final Map<OperationType, CdcOperation> DEFAULT_OPERATIONS = new HashMap<OperationType, CdcOperation>(){{
        put(OperationType.INSERT, new MongoDbInsert());
        put(OperationType.REPLACE, new MongoDbReplace());
        put(OperationType.UPDATE, new MongoDbUpdate());
        put(OperationType.DELETE, new MongoDbDelete());
    }};
    private static final Logger LOGGER = LoggerFactory.getLogger(MongoDbHandler.class);

    public MongoDbHandler(final MongoSinkTopicConfig config) {
        this(config, DEFAULT_OPERATIONS);
    }

    public MongoDbHandler(final MongoSinkTopicConfig config,
                          final Map<OperationType, CdcOperation> operations) {
        super(config);
        registerOperations(operations);
    }

    @Override
    public Optional<WriteModel<BsonDocument>> handle(final SinkDocument doc) {

        BsonDocument keyDoc = doc.getKeyDoc().orElseThrow(
                () -> new DataException("Error: key document must not be missing for CDC mode")
        );

        BsonDocument valueDoc = doc.getValueDoc()
                .orElseGet(BsonDocument::new);

        if (keyDoc.containsKey(JSON_ID_FIELD)
                && valueDoc.isEmpty()) {
            LOGGER.debug("skipping debezium tombstone event for kafka topic compaction");
            return Optional.empty();
        }

        LOGGER.debug("key: " + keyDoc.toString());
        LOGGER.debug("value: " + valueDoc.toString());

        return Optional.of(getCdcOperation(valueDoc).perform(doc));
    }

}