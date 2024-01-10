package net.ravendb.client.documents.operations.compareExchange;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Defaults;
import net.ravendb.client.Constants;
import net.ravendb.client.documents.conventions.DocumentConventions;
import net.ravendb.client.json.MetadataAsDictionary;

import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

public class CompareExchangeValueResultParser<T> {

    public static <T> Map<String, CompareExchangeValue<T>> getValues(Class<T> clazz, String response,
                                                                     boolean materializeMetadata,
                                                                     DocumentConventions conventions) throws IOException {
        Map<String, CompareExchangeValue<T>> results = new TreeMap<>(String::compareToIgnoreCase);
        if (response == null) { // 404
            return results;
        }

        JsonNode jsonResponse = conventions.getEntityMapper().readTree(response);

        JsonNode items = jsonResponse.get("Results");
        if (items == null || items.isNull()) {
            throw new IllegalStateException("Response is invalid. Results is missing.");
        }

        for (JsonNode item : items) {
            if (item == null) {
                throw new IllegalStateException("Response is invalid. Item is null");
            }

            CompareExchangeValue<T> value = getSingleValue(clazz, (ObjectNode) item, materializeMetadata, conventions);
            results.put(value.getKey(), value);
        }

        return results;
    }

    public static <T> CompareExchangeValue<T> getValue(Class<T> clazz, String response,
                                                       boolean materializeMetadata,
                                                       DocumentConventions conventions) throws IOException {
        if (response == null) {
            return null;
        }

        Map<String, CompareExchangeValue<T>> values = getValues(clazz, response, materializeMetadata, conventions);
        if (values.isEmpty()) {
            return null;
        }
        return values.values().iterator().next();
    }

    public static <T> CompareExchangeValue<T> getSingleValue(Class<T> clazz, ObjectNode item, boolean materializeMetadata, DocumentConventions conventions) {
        if (item == null || item.isNull()) {
            return null;
        }

        JsonNode keyNode = item.get("Key");

        if (keyNode == null || keyNode.isNull()) {
            throw new IllegalStateException("Response is invalid. Key is missing.");
        }

        JsonNode indexNode = item.get("Index");
        if (indexNode == null || indexNode.isNull()) {
            throw new IllegalStateException("Response is invalid. Index is missing");
        }

        JsonNode rawJsonNode = item.get("Value");
        if (rawJsonNode == null) {
            throw new IllegalStateException("Response is invalid. Value is missing.");
        }

        JsonNode rawCvNode = item.get("ChangeVector");
        String cv = rawCvNode != null && !rawCvNode.isNull() ? rawCvNode.asText() : null;

        ObjectNode raw = rawJsonNode.isObject() ? (ObjectNode) rawJsonNode : null;

        String key = keyNode.asText();
        long index = indexNode.asLong();

        if (raw == null) {
            return new CompareExchangeValue<>(key, index, null, cv, null);
        }

        MetadataAsDictionary metadata = null;
        JsonNode bjro = raw.get(Constants.Documents.Metadata.KEY);
        if (bjro != null && bjro.isObject()) {
            metadata = !materializeMetadata ? new MetadataAsDictionary((ObjectNode) bjro) : MetadataAsDictionary.materializeFromJson((ObjectNode) bjro);
        }

        T value = deserializeObject(clazz, raw, conventions);
        return new CompareExchangeValue<>(key, index, value, cv, metadata);
    }

    protected static <T> T deserializeObject(Class<T> clazz, ObjectNode raw, DocumentConventions conventions) {

        if (raw == null) {
            return Defaults.defaultValue(clazz);
        }

        if (clazz.isPrimitive() || String.class.equals(clazz)) {
            // simple
            T value;

            JsonNode rawValue = raw.get(Constants.CompareExchange.OBJECT_FIELD_NAME);
            value = conventions.getEntityMapper().convertValue(rawValue, clazz);

            return value;
        } else if (ObjectNode.class.equals(clazz)) {
            if (!raw.has(Constants.CompareExchange.OBJECT_FIELD_NAME)) {
                return null;
            }

            Object rawValue = raw.get(Constants.CompareExchange.OBJECT_FIELD_NAME);
            if (rawValue == null) {
                return null;
            } else if (rawValue instanceof ObjectNode) {
                return (T) rawValue;
            } else {
                return (T) raw;
            }
        } else {
            if (!raw.has(Constants.CompareExchange.OBJECT_FIELD_NAME)) {
                return conventions.getEntityMapper().convertValue(raw, clazz);
            }

            JsonNode object = raw.get(Constants.CompareExchange.OBJECT_FIELD_NAME);
            if (object == null || object.isNull()) {
                return Defaults.defaultValue(clazz);
            } else {
                T converted = conventions.getEntityMapper().convertValue(object, clazz);
                return converted;
            }
        }
    }
}
